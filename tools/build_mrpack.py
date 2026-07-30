#!/usr/bin/env python3
"""
Builds a Modrinth modpack (.mrpack) for The Unseen Architecture.

Dependencies that live on Modrinth are referenced by URL + hash in modrinth.index.json, which is what
Modrinth requires — a pack may not redistribute other people's jars. This mod is not on Modrinth, so it
ships inside overrides/ instead, which is the sanctioned route for a pack's own content.

Run after ./gradlew build:
    python3 tools/build_mrpack.py
"""
import hashlib
import json
import pathlib
import shutil
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
    # Chosen companions. Client-only: they change what you hear, not what the server simulates.
    {"slug": "sound-physics-remastered", "env": {"client": "required", "server": "unsupported"}},
    {"slug": "mambience", "env": {"client": "required", "server": "unsupported"}},
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
    jar = ROOT / "build" / "libs" / "unseen-{}.jar".format(PACK_VERSION)
    config = ROOT / "run" / "config" / "unseen.json"
    if not jar.exists():
        sys.exit("Missing {} — run ./gradlew build first.".format(jar))
    if not config.exists():
        sys.exit("Missing {} — run ./gradlew runServer once to generate defaults.".format(config))

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

        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(staging.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(staging).as_posix())

    digest = hashlib.sha1(out.read_bytes()).hexdigest()
    print("\nWrote {} ({:,} bytes)".format(out.relative_to(ROOT), out.stat().st_size))
    print("sha1 {}".format(digest))
    print("Bundled in overrides/: mods/{}, config/unseen.json".format(jar.name))


if __name__ == "__main__":
    main()
