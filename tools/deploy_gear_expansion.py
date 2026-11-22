"""Prepare the four accessory mods and install them together with their field guide."""
import argparse
from io import BytesIO
import json
from pathlib import Path
import tomllib
from urllib.request import Request, urlopen
from zipfile import ZipFile

from deploy_equipment_atlas import atlas_updates
from deploy_world_turn import INSTANCE, ROOT, install_payloads

LOCK = ROOT / "docs/mods/gear-expansion.lock.json"
CACHE = ROOT / "work/gear-expansion-20261123/payload/mods"


def mod_ids(jar):
    path = "META-INF/neoforge.mods.toml"
    if path not in jar.namelist():
        return {}
    meta = tomllib.loads(jar.read(path).decode("utf-8-sig"))
    return {mod["modId"]: mod.get("version") for mod in meta.get("mods", [])}


def prepare(rows, cache):
    cache.mkdir(parents=True, exist_ok=True)
    for row in rows:
        path = cache / row["file"]
        if path.is_file():
            with ZipFile(path) as jar:
                if mod_ids(jar).get(row["id"]) == row["version"]: continue
            raise ValueError(f"Unexpected version in {path}")
        with urlopen(Request(row["url"], headers={"User-Agent": "Stargarden-modpack/1.0"}), timeout=90) as response:
            data = response.read()
        with ZipFile(BytesIO(data)) as jar:
            if mod_ids(jar).get(row["id"]) != row["version"]:
                raise ValueError(f"Release metadata mismatch for {path.name}")
        tmp = path.with_suffix(".download")
        tmp.write_bytes(data)
        tmp.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    rows = json.loads(LOCK.read_bytes())["files"]
    prepare(rows, CACHE)
    if args.prepare_only: return
    versions = {row["id"]: row["version"] for row in rows}
    installed = {}
    for file in (INSTANCE / "mods").glob("*.jar"):
        with ZipFile(file) as jar:
            for mod, version in mod_ids(jar).items():
                if mod not in versions: continue
                if mod in installed or version != versions[mod]:
                    raise ValueError(f"Review installed {mod} {version} at {file}")
                installed[mod] = file
    updates = atlas_updates(INSTANCE)
    for row in rows:
        if row["id"] not in installed:
            target = Path("mods") / row["file"]
            if (INSTANCE / target).exists(): raise FileExistsError(INSTANCE / target)
            updates[target] = (CACHE / row["file"]).read_bytes()
    if updates:
        install_payloads(INSTANCE, updates, "gear-expansion", None, args.apply, module_versions=versions)
    else:
        print("All seven release files and the equipment chapters are installed.")


if __name__ == "__main__":
    main()
