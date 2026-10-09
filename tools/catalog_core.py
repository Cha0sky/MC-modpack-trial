"""Extract relevant public resources from installed mod JARs for design."""
from pathlib import Path
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODS = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版\mods")


def main():
    result = {}
    for mod in ["infinitecore", "evolvingblade", "rpgleveling", "create", "touhou_little_maid", "farmersdelight", "irons_spellbooks", "goety", "cataclysm", "confluence", "aeronautics", "avaritia", "maid_restaurant", "touhou_maid_affection"]:
        found = []
        for file in MODS.glob("*.jar"):
            with zipfile.ZipFile(file) as jar:
                lang_path = f"assets/{mod}/lang/zh_cn.json"
                if lang_path not in jar.namelist():
                    continue
                lang = json.loads(jar.read(lang_path))
                rows = {k: v for k, v in lang.items() if k.startswith(("item." + mod + ".", "block." + mod + ".", "entity." + mod + "."))}
                recipes = {p: json.loads(jar.read(p)) for p in jar.namelist() if p.startswith(f"data/{mod}/recipe/") and p.endswith(".json")}
                found.append({"jar": file.name, "names": rows, "recipes": recipes if mod in ["infinitecore", "evolvingblade", "rpgleveling"] else {}, "recipe_count": len(recipes)})
        result[mod] = found
        print(mod, json.dumps([{"jar": x["jar"], "names_count": len(x["names"]), "recipe_count": x["recipe_count"]} for x in found], ensure_ascii=False))
    (ROOT / "evidence" / "core-resources.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
