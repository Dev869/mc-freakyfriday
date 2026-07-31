#!/usr/bin/env python3
"""
Checks the Stalker's animations against its model and against the controller layout.

Three failures this catches that nothing else does. A bone typo is silent — GeckoLib just never
animates it, and you are left squinting at a model wondering which joint is dead. A bone claimed by
two locomotion controllers is undefined behaviour that shows up as a limb snapping between poses,
usually only once the entity changes state in front of a player. And an animation length that shares
a factor with another controller's re-syncs the body into a visible loop, which is the exact thing
the three-controller split exists to prevent.

    python3 tools/check_animations.py
"""
import itertools
import json
import math
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from gen_stalker_texture import EYE_UVS

ROOT = pathlib.Path(__file__).resolve().parent.parent
GEO = ROOT / "src/main/resources/assets/unseen/geo/entity/stalker.geo.json"
ANIM = ROOT / "src/main/resources/assets/unseen/animations/entity/stalker.animation.json"
MODEL = ROOT / "src/client/java/com/unseen/client/StalkerModel.java"

# Must match registerControllers in StalkerEntity. Order is the registration order.
CONTROLLERS = {
    "legs": ["idle_legs", "stalk_legs"],
    "arms": ["idle_arms", "stalk_arms"],
    "head": ["idle_head", "stalk_head"],
}
# Triggered, registered last, deliberately overlapping the others. Exempt from the disjointness rule.
OVERRIDES = ["twitch"]

TICKS_PER_SECOND = 20


def bones_of(geo):
    return {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}


def eye_uvs_of(geo):
    """UV origin of every eye cube in the model, in bone declaration order."""
    return [tuple(cube["uv"])
            for bone in geo["minecraft:geometry"][0]["bones"] if bone["name"].startswith("eye")
            for cube in bone.get("cubes", [])]


def main():
    geo = json.loads(GEO.read_text())
    geo_bones = bones_of(geo)
    animations = json.loads(ANIM.read_text())["animations"]
    errors = []

    # 0. Bones named in Java exist in the model, and the texture generator agrees with the geo about
    # where the eyes are. Both of these fail silently: a mistyped bone is simply never touched, and a
    # drifted UV paints an eye onto blank sheet while the cube samples flesh somewhere else.
    referenced = set(re.findall(r'"(eye[a-z0-9_]*)"', MODEL.read_text()))
    for missing in sorted(referenced - geo_bones):
        errors.append(f"StalkerModel.java references bone {missing!r}, which is not in stalker.geo.json")

    geo_uvs = eye_uvs_of(geo)
    if geo_uvs != EYE_UVS:
        errors.append(
            f"eye UVs in stalker.geo.json {geo_uvs} do not match EYE_UVS in "
            f"gen_stalker_texture.py {EYE_UVS}; the texture and the model disagree"
        )

    known = set(itertools.chain(*CONTROLLERS.values(), OVERRIDES))
    for missing in sorted(known - set(animations)):
        errors.append(f"animation {missing!r} is referenced by a controller but not defined")
    for orphan in sorted(set(animations) - known):
        errors.append(f"animation {orphan!r} is defined but no controller plays it")

    # 1. Every animated bone exists in the model.
    for name, anim in animations.items():
        for bone in anim.get("bones", {}):
            if bone not in geo_bones:
                errors.append(f"{name!r} animates {bone!r}, which is not a bone in stalker.geo.json")

    # 2. No bone is claimed by two locomotion controllers.
    owned = {}
    for controller, names in CONTROLLERS.items():
        for name in names:
            for bone in animations.get(name, {}).get("bones", {}):
                prior = owned.setdefault(bone, controller)
                if prior != controller:
                    errors.append(
                        f"bone {bone!r} is animated by both the {prior!r} and {controller!r} "
                        f"controllers (via {name!r}); GeckoLib does not define this"
                    )

    # 3. The moving cycles stay coprime, so the composite pose does not re-sync.
    periods = {}
    for controller, names in CONTROLLERS.items():
        stalk = next(n for n in names if n.startswith("stalk_"))
        ticks = animations[stalk]["animation_length"] * TICKS_PER_SECOND
        if abs(ticks - round(ticks)) > 1e-6:
            errors.append(f"{stalk!r} is {ticks} ticks long; use a whole number of ticks")
        periods[controller] = round(ticks)

    for (a, ta), (b, tb) in itertools.combinations(periods.items(), 2):
        common = math.gcd(ta, tb)
        if common > 2:
            errors.append(
                f"{a} ({ta}t) and {b} ({tb}t) share a factor of {common}; they will re-sync every "
                f"{ta * tb // common} ticks and the loop becomes readable"
            )

    if errors:
        for e in errors:
            print(f"FAIL: {e}", file=sys.stderr)
        return 1

    lcm = math.lcm(*periods.values())
    print(f"ok: {len(animations)} animations, {len(geo_bones)} bones, no controller overlap")
    print(f"ok: {len(EYE_UVS)} eyes, geo UVs match the texture generator, "
          f"{len(referenced)} bone names in StalkerModel all resolve")
    print(f"ok: periods {periods} -> composite repeats every {lcm} ticks ({lcm / 1200:.1f} min)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
