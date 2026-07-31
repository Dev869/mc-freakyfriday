#!/usr/bin/env python3
"""
Generates stalker_glowmask.png: fully transparent except the eyes.

GeckoLib's AutoGlowingGeoLayer renders every non-transparent pixel of this sheet at full brightness
regardless of world light. So in an unlit corridor the eyes are the only part of the Stalker a player
can see, and the first thing they ever meet is points of light at head height with no body around them.

The `_glowmask` suffix is not configurable — GeckoLib derives this path from stalker.png by appending
it, so the filename is load-bearing.

The lit pixels are read from gen_stalker_texture.EYE_UVS rather than duplicated, because a glowmask
that lights a pixel the base texture did not paint as an eye produces a floating light with no eye
under it, and the two files drifting apart is the obvious way for that to happen.

    python3 tools/gen_stalker_glowmask.py
"""
import pathlib
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_stalker_texture import BLOWN, EYE_UVS, IRIS, eye_front_pixel  # noqa: E402

OUT = (pathlib.Path(__file__).resolve().parent.parent
       / "src/main/resources/assets/unseen/textures/entity/stalker_glowmask.png")

W = H = 64

# Only the iris of each non-blown eye. The sclera stays dark so the eye reads as a pinpoint rather
# than a glowing block, and the two blown pupils stay dead — at low sanity a couple of the eyes that
# open are simply black holes among the lit ones.
LIT = {eye_front_pixel(uv): IRIS
       for index, uv in enumerate(EYE_UVS) if index not in BLOWN}


def main():
    rows = []
    for y in range(H):
        row = bytearray([0])  # PNG filter byte
        for x in range(W):
            colour = LIT.get((x, y))
            row += bytes(colour + (255,)) if colour else bytes((0, 0, 0, 0))
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
    print("wrote {} ({} bytes, {} lit pixels)".format(OUT.name, OUT.stat().st_size, len(LIT)))


if __name__ == "__main__":
    main()
