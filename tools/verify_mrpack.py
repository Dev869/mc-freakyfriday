#!/usr/bin/env python3
"""
Checks that a built .mrpack would actually install and run.

Building a pack proves nothing: the file assembles, uploads and installs perfectly well while being
wrong in ways nobody sees until a player's launcher tries it. Every failure this looks for has either
already happened here or is one line away from happening.

    python3 tools/verify_mrpack.py                 # structure, and that every download is really there
    python3 tools/verify_mrpack.py --download      # also fetch every mod and check its hash

Exits non-zero on the first thing a player would hit.
"""
import argparse
import hashlib
import io
import json
import pathlib
import sys
import urllib.error
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"

# What the mod itself declares it cannot run without. A pack that does not ship these does not launch:
# Fabric Loader hard-fails on an unmet `depends` rather than starting without the feature.
MOD_JSON = ROOT / "src" / "main" / "resources" / "fabric.mod.json"
# Mod ids that are satisfied by something other than a file in mods/.
PROVIDED_BY_PLATFORM = {"fabricloader", "minecraft", "java"}


def squash(text):
    """Mod ids and file names spell the same mod differently: fabric-api vs fabric_api vs fabricapi."""
    return text.lower().replace("-", "").replace("_", "").replace(" ", "")

failures = []


def check(condition, message):
    if condition:
        print("  ok    %s" % message)
    else:
        print("  FAIL  %s" % message)
        failures.append(message)
    return condition


def newest_pack():
    packs = sorted(DIST.glob("*.mrpack"), key=lambda p: p.stat().st_mtime)
    if not packs:
        sys.exit("No .mrpack in dist/ — run tools/build_mrpack.py first.")
    return packs[-1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true",
                        help="fetch every mod and verify its sha1, not just that the URL resolves")
    parser.add_argument("pack", nargs="?", help="path to a .mrpack (default: newest in dist/)")
    args = parser.parse_args()

    pack = pathlib.Path(args.pack) if args.pack else newest_pack()
    print("Verifying %s (%s bytes)\n" % (pack.name, format(pack.stat().st_size, ",")))

    with zipfile.ZipFile(pack) as archive:
        names = set(archive.namelist())
        if not check("modrinth.index.json" in names, "modrinth.index.json is present"):
            sys.exit(1)
        index = json.loads(archive.read("modrinth.index.json"))
        overrides = {n for n in names if n.startswith("overrides/")}

        print("\nStructure")
        check(index.get("formatVersion") == 1, "index formatVersion is 1")
        check(bool(index.get("name")), "pack has a name")
        deps = index.get("dependencies", {})
        check("minecraft" in deps and "fabric-loader" in deps,
              "index pins minecraft and fabric-loader (%s / %s)"
              % (deps.get("minecraft"), deps.get("fabric-loader")))

        print("\nOverrides")
        jars = [n for n in overrides if n.startswith("overrides/mods/") and n.endswith(".jar")]
        check(len(jars) == 1, "exactly one mod jar bundled (%d)" % len(jars))
        check("overrides/config/unseen.json" in overrides, "config/unseen.json bundled")
        # Without this the vanilla overworld region weight defaults back to 10 and the meadow, whose
        # entire premise is that it has no outside, quietly gets deserts again.
        if check("overrides/config/terrablender.toml" in overrides, "config/terrablender.toml bundled"):
            toml = archive.read("overrides/config/terrablender.toml").decode()
            check("vanilla_overworld_region_weight = 0" in toml,
                  "bundled terrablender.toml zeroes the vanilla overworld region weight")
        if jars:
            inner = zipfile.ZipFile(io.BytesIO(archive.read(jars[0])))
            check("fabric.mod.json" in inner.namelist(), "bundled jar has a fabric.mod.json")
            check(any(n.startswith("data/unseen/worldgen/") for n in inner.namelist()),
                  "bundled jar contains the worldgen datapack")

        print("\nDependency coverage")
        declared = json.loads(MOD_JSON.read_text()).get("depends", {})
        # File names are not mod ids, so match on the filename containing the id. Crude, and enough:
        # this is the check that caught terrablender being declared required and never shipped.
        filenames = squash(" ".join(f["path"] for f in index.get("files", [])))
        for mod_id in declared:
            if mod_id in PROVIDED_BY_PLATFORM:
                continue
            check(squash(mod_id) in filenames, "required dependency '%s' is shipped" % mod_id)

        print("\nDownloads (%d)" % len(index.get("files", [])))
        for entry in index.get("files", []):
            name = entry["path"].split("/")[-1]
            url = entry["downloads"][0]
            try:
                if args.download:
                    with urllib.request.urlopen(url, timeout=60) as response:
                        body = response.read()
                    ok = (len(body) == entry["fileSize"]
                          and hashlib.sha1(body).hexdigest() == entry["hashes"]["sha1"])
                    check(ok, "%s downloads and matches its sha1" % name)
                else:
                    request = urllib.request.Request(url, method="HEAD")
                    with urllib.request.urlopen(request, timeout=30) as response:
                        length = int(response.headers.get("Content-Length", -1))
                    check(length == entry["fileSize"],
                          "%s resolves, %s bytes as declared" % (name, format(length, ",")))
            except (urllib.error.URLError, OSError) as e:
                check(False, "%s is unreachable (%s)" % (name, e))
            # A pack may not redistribute other people's jars, so every dependency has to be a link.
            check(not any(n.endswith(name) for n in overrides),
                  "%s is linked, not redistributed" % name)
            # And it must be the Fabric build. Several of these ship one filename for three loaders
            # with three different hashes, and the NeoForge jar loads without complaint and then does
            # nothing -- a failure only visible by grepping the loaded-mod list. The hash names exactly
            # one file, so ask Modrinth which loader that file is for.
            try:
                with urllib.request.urlopen(
                        "https://api.modrinth.com/v2/version_file/" + entry["hashes"]["sha1"],
                        timeout=30) as response:
                    loaders = json.load(response).get("loaders", [])
                check("fabric" in loaders,
                      "%s is the fabric build (%s)" % (name, ", ".join(loaders) or "none reported"))
            except (urllib.error.URLError, OSError) as e:
                check(False, "could not confirm the loader for %s (%s)" % (name, e))

    print()
    if failures:
        print("%d problem(s):" % len(failures))
        for f in failures:
            print("  - %s" % f)
        sys.exit(1)
    print("Pack is installable.")


if __name__ == "__main__":
    main()
