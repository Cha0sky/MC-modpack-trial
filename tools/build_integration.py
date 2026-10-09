"""Build a resource-only NeoForge mod with optional engineering recipes."""
from pathlib import Path
import json
import zipfile
import hashlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "integration" / "src"
TIERS = ["dirt", "wood", "stone", "copper", "iron", "gold", "diamond", "netherite"]


def write(relative, value):
    destination = SOURCE / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    metadata = SOURCE / "META-INF" / "neoforge.mods.toml"
    metadata.parent.mkdir(parents=True, exist_ok=True)
    metadata.write_text('''modLoader="lowcodefml"
loaderVersion="[4,)"
license="MIT"
[[mods]]
modId="stargarden"
version="0.1.0"
displayName="星庭远征 · 联动配方"
description="机械动力与无限之核的可选工程合成路线。保留原始配方，不限制提前探索。"
[[dependencies.stargarden]]
modId="neoforge"
type="required"
versionRange="[21.1.248,21.2)"
[[dependencies.stargarden]]
modId="minecraft"
type="required"
versionRange="[1.21.1]"
[[dependencies.stargarden]]
modId="create"
type="required"
versionRange="[6.0.10,6.1)"
[[dependencies.stargarden]]
modId="infinitecore"
type="required"
versionRange="[1.0.0,2)"
''', encoding="utf-8")
    rows = []
    for index, tier in enumerate(TIERS):
        if index <= 2:
            casing, mechanism, fitting = "create:andesite_casing", "create:andesite_alloy", "create:cogwheel"
        elif index <= 5:
            casing, mechanism, fitting = "create:brass_casing", "create:precision_mechanism", "create:large_cogwheel"
        else:
            casing, mechanism, fitting = "create:brass_casing", "create:precision_mechanism", "create:sturdy_sheet"
        recipe = {"type": "minecraft:crafting_shaped", "pattern": ["TMT", "FCF", "TMT"], "key": {"T": {"item": f"infinitecore:template_{tier}"}, "M": {"item": mechanism}, "F": {"item": fitting}, "C": {"item": casing}}, "result": {"id": f"infinitecore:{tier}_generator", "count": 1}}
        write(f"data/stargarden/recipe/engineering_{tier}_generator.json", recipe)
        rows.append({"tier": tier, "output": recipe["result"]["id"], "inputs": {f"infinitecore:template_{tier}": 4, mechanism: 2, fitting: 2, casing: 1}, "original_recipe_preserved": True})
    # NeoForge supplies version-correct resource/data pack metadata for mod JARs.
    (SOURCE / "pack.mcmeta").unlink(missing_ok=True)
    (SOURCE / "LICENSE").write_text("MIT License\nCopyright (c) 2026 StarGarden modpack contributors\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies, subject to inclusion of this notice.\n\nTHE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED. IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY.\n", encoding="utf-8")
    jar_path = ROOT / "staging" / "mods" / "stargarden-integration-0.1.0.jar"
    jar_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for file in sorted(SOURCE.rglob("*")):
            if file.is_file():
                jar.write(file, str(file.relative_to(SOURCE)).replace("\\", "/"))
    (ROOT / "evidence" / "integration-recipes.json").write_text(json.dumps({"jar": jar_path.name, "sha256": hashlib.sha256(jar_path.read_bytes()).hexdigest(), "recipes": rows, "purpose": "Alternative engineering recipes; preserve original routes and production rates"}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"jar": jar_path.name, "new_recipes": len(rows)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
