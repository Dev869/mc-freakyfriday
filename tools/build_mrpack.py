#!/usr/bin/env python3
"""
Builds a Modrinth modpack (.mrpack) for The Unseen Architecture.

Dependencies that live on Modrinth are referenced by URL + hash in modrinth.index.json, which is what
Modrinth requires — a pack may not redistribute other people's jars. This mod is not on Modrinth, so it
ships inside overrides/ instead, which is the sanctioned route for a pack's own content.

Builds the mod jar itself, so the pack can never ship stale code:
    python3 tools/build_mrpack.py
"""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent

PACK_NAME = "The Unseen Architecture"
PACK_VERSION = "0.1.0"
PACK_SUMMARY = ("Psychological horror for Minecraft. Vanilla hostiles are gone; a Director AI paces a "
                "single Stalker that hunts by sound, punishes you for looking at it, and is taken away "
                "before you get used to it.")

MINECRAFT = "1.21.1"
FABRIC_LOADER = "0.19.3"

# env values: "required" | "optional" | "unsupported"
DEPENDENCIES = [
    {"slug": "fabric-api", "env": {"client": "required", "server": "required"}},
    # Hard dependency: the Stalker's model and animations are GeckoLib.
    {"slug": "geckolib", "env": {"client": "required", "server": "required"}},
    # Hard dependency: the Storybook Meadow is a TerraBlender region, and fabric.mod.json requires it,
    # so a pack without this refuses to launch at all rather than merely losing the biome.
    {"slug": "terrablender", "env": {"client": "required", "server": "required"}},
    # Chosen companions. Client-only: they change what you hear, not what the server simulates.
    {"slug": "sound-physics-remastered", "env": {"client": "required", "server": "unsupported"}},
    {"slug": "mambience", "env": {"client": "required", "server": "unsupported"}},
    # Seamless see-through dimension travel. Needed on both sides — it changes how the world renders
    # AND how teleportation is simulated.
    {"slug": "immersiveportals", "env": {"client": "required", "server": "required"}},
    # Worldgen library. Required by ChoiceTheorem's Overhauled Village.
    {"slug": "lithostitched", "env": {"client": "required", "server": "required"}},
    # The villages are the fairytale. Vanilla ones are too plain to sell "somewhere worth losing", and
    # this only replaces the buildings — it does not touch biome distribution, so the Storybook Meadow
    # stays exactly as common as it was. Terralith was considered here and rejected: ~100 new biomes
    # would make the meadow rare, and spawning in the meadow is the whole opening act.
    {"slug": "ct-overhaul-village", "env": {"client": "required", "server": "required"}},
]


def api(url):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def resolve(slug):
    """Newest 1.21.1 Fabric version of a Modrinth project, as an index file entry."""
    query = ("https://api.modrinth.com/v2/project/{}/version"
             "?game_versions=%5B%22{}%22%5D&loaders=%5B%22fabric%22%5D").format(slug, MINECRAFT)
    versions = api(query)
    if not versions:
        sys.exit("No {} build for Fabric {} — pack would be broken, refusing to write it.".format(slug, MINECRAFT))
    version = versions[0]
    files = [f for f in version["files"] if f.get("primary")] or version["files"]
    primary = files[0]
    return version["version_number"], {
        "path": "mods/" + primary["filename"],
        "hashes": {"sha1": primary["hashes"]["sha1"], "sha512": primary["hashes"]["sha512"]},
        "downloads": [primary["url"]],
        "fileSize": primary["size"],
    }


def main():
    # Build first rather than trusting that someone did. Shipping a stale jar is silent: the pack
    # assembles, uploads and installs perfectly well, and simply is not the code in the tree. Comparing
    # timestamps instead was worse than useless — Gradle skips the jar task when content has not changed,
    # so a stale-looking timestamp produced an error no amount of rebuilding could clear.
    print("Building the mod jar...")
    build = subprocess.run([str(ROOT / "gradlew"), "build", "-q", "--console=plain"], cwd=ROOT)
    if build.returncode != 0:
        sys.exit("./gradlew build failed — refusing to write a pack around a jar that did not compile.")

    jar = ROOT / "build" / "libs" / "unseen-{}.jar".format(PACK_VERSION)
    config = ROOT / "run" / "config" / "unseen.json"
    # Not optional. TerraBlender's vanilla overworld region weight defaults to 10, which puts deserts
    # and badlands back into a world whose entire premise is that you cannot walk out of the meadow.
    terrablender = ROOT / "run" / "config" / "terrablender.toml"
    if not jar.exists():
        sys.exit("Build succeeded but {} is missing — has PACK_VERSION drifted from the mod version?"
                 .format(jar.name))
    for needed in (config, terrablender):
        if not needed.exists():
            sys.exit("Missing {} — run ./gradlew runServer once to generate defaults.".format(needed))
    if "vanilla_overworld_region_weight = 0" not in terrablender.read_text():
        sys.exit("terrablender.toml does not zero the vanilla overworld region weight — the meadow "
                 "would not be endless in the shipped pack.")

    files = []
    print("Resolving dependencies from Modrinth:")
    for dep in DEPENDENCIES:
        number, entry = resolve(dep["slug"])
        entry["env"] = dep["env"]
        files.append(entry)
        print("  {:28} {:22} {:>10,} bytes".format(dep["slug"], number, entry["fileSize"]))

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": PACK_VERSION,
        "name": PACK_NAME,
        "summary": PACK_SUMMARY,
        "files": files,
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": FABRIC_LOADER},
    }

    out = ROOT / "dist" / "unseen-architecture-{}.mrpack".format(PACK_VERSION)
    out.parent.mkdir(exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp:
        staging = pathlib.Path(tmp)
        (staging / "modrinth.index.json").write_text(json.dumps(index, indent=2) + "\n")

        mods = staging / "overrides" / "mods"
        mods.mkdir(parents=True)
        shutil.copy2(jar, mods / jar.name)

        conf = staging / "overrides" / "config"
        conf.mkdir(parents=True)
        shutil.copy2(config, conf / "unseen.json")
        shutil.copy2(terrablender, conf / "terrablender.toml")

        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(staging.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(staging).as_posix())

    digest = hashlib.sha1(out.read_bytes()).hexdigest()
    print("\nWrote {} ({:,} bytes)".format(out.relative_to(ROOT), out.stat().st_size))
    print("sha1 {}".format(digest))
    print("Bundled in overrides/: mods/{}, config/unseen.json, config/terrablender.toml"
          .format(jar.name))


if __name__ == "__main__":
    main()
