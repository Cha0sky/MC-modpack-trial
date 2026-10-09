"""Check the actual built JAR against the user's binary baseline and recipe change.

No game bootstrap. Report writes require --write-report and stay in this module.
"""
import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path
from PIL import Image
from build_assets import MODULE, BASELINE, TIERS, MATERIALS


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-report", action="store_true")
    args = parser.parse_args()
    version = re.search(r"^mod_version=(.+)$", (MODULE / "gradle.properties").read_text(), re.M)[1]
    artifact = MODULE / "build/libs" / f"infinitecore-{version}.jar"
    baseline_hash = hashlib.sha256(BASELINE.read_bytes()).hexdigest()
    require(baseline_hash == "10ec43204404817c8738088670571d5044bbf4b992a9247ab8d67b986aa2e400", "Baseline changed")
    with zipfile.ZipFile(BASELINE) as old, zipfile.ZipFile(artifact) as new:
        require(new.testzip() is None, "JAR CRC error")
        paths = new.namelist()
        recipes = [p for p in paths if p.startswith("data/infinitecore/recipe/") and p.endswith(".json")]
        require(len(recipes) == 17, f"Expected 17 recipes, found {len(recipes)}")
        require(not any("blank_template" in p for p in recipes), "Blank template recipe still exists")
        for tier, material in zip(TIERS, MATERIALS):
            path = f"data/infinitecore/recipe/{tier}_generator.json"
            require(new.read(path) == old.read(path), f"Machine recipe changed: {tier}")
            direct = json.loads(new.read(f"data/infinitecore/recipe/template_{tier}.json"))
            require(direct == {
                "type": "minecraft:crafting_shaped", "pattern": ["XXX", "XXX", "XXX"],
                "key": {"X": {"item": "minecraft:" + material}}, "result": {"id": "infinitecore:template_" + tier, "count": 1}
            }, f"Direct recipe differs from approved nine-material pattern: {tier}")
            require(f"assets/infinitecore/models/item/blank_template_{tier}.json" in paths, f"Legacy icon missing: {tier}")
        require(new.read("data/infinitecore/recipe/template_customize.json") ==
                (MODULE / "src/main/resources/data/infinitecore/recipe/template_customize.json").read_bytes(), "Custom recipe not packed")
        json_count = png_count = 0
        for path in paths:
            if path.endswith(".json"):
                data = json.loads(new.read(path))
                json_count += 1
                if path.startswith("assets/infinitecore/models/"):
                    parent = data.get("parent", "")
                    if parent.startswith("infinitecore:"):
                        require("assets/infinitecore/models/" + parent.split(":", 1)[1] + ".json" in paths, f"Missing model parent: {path}")
                    for texture in data.get("textures", {}).values():
                        if texture.startswith("infinitecore:"):
                            require("assets/infinitecore/textures/" + texture.split(":", 1)[1] + ".png" in paths, f"Missing texture: {path}")
            elif path.endswith(".png"):
                image = Image.open(io.BytesIO(new.read(path)))
                image.load()
                expected = (256, 256) if "/gui/" in path else (16, 16)
                require(image.size == expected, f"Incorrect dimensions: {path}")
                png_count += 1
            elif path.endswith(".class"):
                require(int.from_bytes(new.read(path)[6:8], "big") == 65, f"Not Java 21: {path}")
                require(not path.endswith("GenerationMathCheck.class"), "Verification class leaked into artifact")
        languages = [json.loads(new.read(f"assets/infinitecore/lang/{locale}.json")) for locale in ("zh_cn", "en_us")]
        require(set(languages[0]) == set(languages[1]), "Translation keys differ between locales")
        for source in (MODULE / "src/main/java").rglob("*.java"):
            for key in re.findall(r'Component\.translatable\("([^"\n]+)"', source.read_text(encoding="utf-8")):
                if key.endswith("."): continue  # Runtime suffixes checked by the complete locale key sets.
                require(key in languages[0], f"Missing translation {key} in {source.name}")
        metadata = new.read("META-INF/neoforge.mods.toml").decode()
        require(f'version="{version}"' in metadata and "${" not in metadata, "Unexpanded metadata")
        require('modId="infinitecore"' in metadata, "Mod ID changed")
        registration = new.read("com/infinite/core/registry/ModContent.class")
        for symbol in (b"blank_template_", b"template_", b"_generator", b"target_item", b"template_customize", b"infinite_core"):
            require(symbol in registration, f"Missing registration pattern {symbol}")
        require(not any("loot_table/" in p for p in paths), "Unapproved loot rules introduced")
        require(not any("world_progress" in p for p in paths), "Unapproved stage integration introduced")
    report = {
        "date": "2026-10-10", "version": version, "artifact": str(artifact.relative_to(MODULE)),
        "artifact_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "artifact_bytes": artifact.stat().st_size,
        "baseline_sha256": baseline_hash, "checks": {
            "jar_crc": "pass", "machine_recipes_byte_identical": 8, "approved_direct_template_recipes": 8,
            "recipe_count": 17, "blank_recipes": 0, "legacy_item_models_retained": 8,
            "json_parsed": json_count, "png_decoded": png_count,
            "local_model_texture_references": "pass", "locale_keys_and_literal_source_keys": "pass",
            "java_21_class_versions": "pass", "metadata_expansion": "pass", "no_verification_classes_in_jar": "pass"
        }, "runtime_verification": {"game_launched": False, "deployed": False, "save_compatibility_verified": False}
    }
    if args.write_report:
        output = MODULE / "docs/resource-verification.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
