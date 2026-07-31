#!/usr/bin/env python3
"""
Generates the Stalker's 64x64 texture: raw flesh, exposed bone, congealed blood.

The layout is not decorative — it matches the UV origins in stalker.geo.json. The bottom-right quadrant
is bone because every bone cube (ribs, vertebrae, jaw, pelvis, the skinned right arm) has its uv in
32..64 / 32..64. Change one and you must change the other.

    python3 tools/gen_stalker_texture.py
"""
import pathlib
import random
import struct
import zlib

OUT = (pathlib.Path(__file__).resolve().parent.parent
       / "src/main/resources/assets/unseen/textures/entity/stalker.png")

W = H = 64
random.seed(31)

FLESH_DARK = (74, 26, 24)
FLESH_MID = (116, 44, 38)
FLESH_WET = (150, 58, 48)
BLOOD = (46, 12, 12)
BONE = (204, 194, 166)
BONE_SHADOW = (150, 140, 116)
VOID = (8, 6, 6)


SCLERA = (206, 198, 172)
IRIS = (238, 226, 178)
PUPIL_BLOWN = (14, 10, 10)

# UV origins of the eleven 1x1x1 eye cubes, in the same order as the bones in stalker.geo.json:
# eye_left, eye_right, then eye_extra_0..8. gen_stalker_glowmask.py imports this list — the
# glowmask must light exactly the pixels this file paints as iris, so there is one source of truth.
EYE_UVS = [(26, 14), (26, 18), (26, 22), (26, 26), (30, 14), (30, 18),
           (30, 22), (30, 26), (26, 30), (30, 30), (30, 34)]
# Two of them are blown wide and dead. They do not glow, so at low sanity a couple of the eyes
# that open are simply black holes among the lit ones.
BLOWN = {3, 7}


def eye_front_pixel(uv):
    """Front face of a 1x1x1 cube sits one pixel in and one down from the uv origin."""
    return uv[0] + 1, uv[1] + 1


def eye_pixel(x, y):
    """Sclera across the whole 4x2 footprint, iris on the front face. None if not an eye pixel."""
    for index, (u, v) in enumerate(EYE_UVS):
        if u <= x < u + 4 and v <= y < v + 2:
            if (x, y) == eye_front_pixel((u, v)):
                return PUPIL_BLOWN if index in BLOWN else IRIS
            return jitter(SCLERA, 8)
    return None


def jitter(colour, amount=10):
    return tuple(max(0, min(255, c + random.randint(-amount, amount))) for c in colour)


def _blotches():
    """
    Coarse value noise on a 4px grid. Modular arithmetic on x and y produces diagonal stripes that read
    as corduroy rather than meat, so tone is chosen per patch instead and jittered per pixel.
    """
    cells = 17  # 64/4 rounded up, plus a margin
    return [[random.choice((FLESH_DARK, FLESH_MID, FLESH_MID, FLESH_WET))
             for _ in range(cells)] for _ in range(cells)]


BLOTCH = _blotches()

# Irregular runnels of dried blood, seeded once so they are continuous down the sheet.
RUNNELS = sorted(random.sample(range(W), 9))


def flesh(x, y):
    """Mottled muscle in organic patches, with wet highlights and runnels of dried blood."""
    colour = jitter(BLOTCH[y // 4][x // 4], 13)
    # Runnels wander a little as they descend rather than falling in straight lines.
    for runnel in RUNNELS:
        if abs(x - (runnel + (y // 7) % 3)) < 1:
            return jitter(BLOOD, 9)
    return colour


BONE_BLOTCH = [[random.choice((BONE, BONE, BONE_SHADOW)) for _ in range(17)] for _ in range(17)]
# Short irregular cracks rather than a diagonal lattice.
CRACKS = {(random.randrange(32, 64), random.randrange(32, 64)) for _ in range(26)}


def bone(x, y):
    """Pale bone, dirty in the crevices, wet where it tears out of the meat."""
    colour = jitter(BONE_BLOTCH[y // 4][x // 4], 8)
    if (x, y) in CRACKS or (x - 1, y) in CRACKS:
        colour = jitter(BONE_SHADOW, 12)
    # Blood soaked into the top edge of the bone zone, where bone meets flesh.
    if y < 36:
        colour = jitter((120, 70, 58), 10)
    return colour


def pixel(x, y):
    # Eyes win over everything: their footprints were allocated into free UV space, but the bone
    # zone starts at x 32 y 32 and two of them land inside it.
    eye = eye_pixel(x, y)
    if eye is not None:
        return eye

    in_bone_zone = x >= 32 and y >= 32
    colour = bone(x, y) if in_bone_zone else flesh(x, y)

    # --- Head, uv [0,0], box 7x6x7. Front face lands at x 7..14, y 7..13. ---
    if 7 <= x < 14 and 7 <= y < 13:
        fx, fy = x - 7, y - 7
        # Two sunken sockets, no eyes in them.
        if fy in (1, 2, 3) and fx in (1, 2, 5, 6):
            return VOID
        # Split mouth running down past the jawline.
        if fx == 3 and fy >= 3:
            return VOID
        if fy == 5 and 1 <= fx <= 5:
            return jitter(BLOOD, 6)
        # Skin stripped from the cheekbones.
        if fy == 4 and fx in (0, 6):
            return jitter(BONE, 8)
    # Top of the skull, uv x 7..14 y 0..7 — bare bone.
    if 7 <= x < 14 and y < 7:
        return jitter(BONE, 10) if (x + y) % 5 else jitter(BONE_SHADOW, 8)

    # --- Hanging flesh strips, uv [34,10]: keep them dark and wet, not bony. ---
    if 34 <= x < 40 and 10 <= y < 18:
        return jitter(FLESH_DARK, 14)

    return colour


def main():
    rows = []
    for y in range(H):
        row = bytearray([0])  # PNG filter byte
        for x in range(W):
            r, g, b = pixel(x, y)
            row += bytes((r, g, b, 255))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    OUT.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    print("wrote {} ({} bytes)".format(OUT.name, OUT.stat().st_size))


if __name__ == "__main__":
    main()
