"""Compile against already available exact NeoForge artifacts, without tests."""
from pathlib import Path
import json
import subprocess
import zipfile
import shutil
import hashlib

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
SOURCE = ROOT / "sanctuary" / "src" / "main"
JDK = Path(r"C:\Users\俞皓天\AppData\Roaming\.minecraft\runtime\java-runtime-delta\bin")
MC = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft")


def write(relative, data):
    path = SOURCE / "resources" / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    resources = SOURCE / "resources"
    metadata = resources / "META-INF" / "neoforge.mods.toml"
    metadata.parent.mkdir(parents=True, exist_ok=True)
    metadata.write_text('''modLoader="javafml"
loaderVersion="[4,)"
license="MIT"
[[mods]]
modId="stargarden_sanctuary"
version="0.1.0"
displayName="星庭远征 · 庇护石"
description="可摆放、可扩建的基地庇护机制。服务器保存保护区域，防止敌对进入、敌方伤害和标准爆炸破坏。"
[[dependencies.stargarden_sanctuary]]
modId="neoforge"
type="required"
versionRange="[21.1.248,21.2)"
[[dependencies.stargarden_sanctuary]]
modId="minecraft"
type="required"
versionRange="[1.21.1]"
''', encoding="utf-8")
    lang = {"block.stargarden_sanctuary.sanctuary_ward": "庇护石", "tooltip.stargarden_sanctuary.ward_1": "放置启用 · 拆除解除 · 多块可扩建", "tooltip.stargarden_sanctuary.ward_2": "默认水平半径48格，覆盖全部高度", "tooltip.stargarden_sanctuary.ward_3": "不生成击杀收益；刷怪场请放在保护区外"}
    write("assets/stargarden_sanctuary/lang/zh_cn.json", lang)
    write("assets/stargarden_sanctuary/lang/en_us.json", lang)
    write("assets/stargarden_sanctuary/blockstates/sanctuary_ward.json", {"variants": {"": {"model": "stargarden_sanctuary:block/sanctuary_ward"}}})
    write("assets/stargarden_sanctuary/models/block/sanctuary_ward.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/amethyst_block"}})
    write("assets/stargarden_sanctuary/models/item/sanctuary_ward.json", {"parent": "stargarden_sanctuary:block/sanctuary_ward"})
    write("data/stargarden_sanctuary/recipe/sanctuary_ward.json", {"type": "minecraft:crafting_shaped", "pattern": ["GCG", "CTC", "GCG"], "key": {"G": {"item": "minecraft:glass"}, "C": {"item": "minecraft:copper_ingot"}, "T": {"item": "minecraft:torch"}}, "result": {"id": "stargarden_sanctuary:sanctuary_ward", "count": 1}})
    write("data/stargarden_sanctuary/loot_table/blocks/sanctuary_ward.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "stargarden_sanctuary:sanctuary_ward"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    doc = json.loads((MC / "versions" / "星庭远征-开发版" / "星庭远征-开发版.json").read_text("utf-8"))
    cp = [WORKSPACE / "evolvingblade" / "build" / "moddev" / "artifacts" / "neoforge-21.1.248.jar"]
    for lib in doc["libraries"]:
        rel = lib.get("downloads", {}).get("artifact", {}).get("path")
        if rel and (MC / "libraries" / rel).exists(): cp.append(MC / "libraries" / rel)
    classes = ROOT / "sanctuary" / "build" / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    java_files = sorted((SOURCE / "java").rglob("*.java"))
    command = [str(JDK / "javac.exe"), "-encoding", "UTF-8", "--release", "21", "-proc:none", "-classpath", ";".join(map(str, cp)), "-d", str(classes)] + list(map(str, java_files))
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace")
    (ROOT / "evidence" / "sanctuary-compile.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        print(result.stdout + result.stderr)
        raise SystemExit(result.returncode)
    jar_path = ROOT / "staging" / "mods" / "stargarden-sanctuary-0.1.0.jar"
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for base in [classes, resources]:
            for file in sorted(base.rglob("*")):
                if file.is_file(): jar.write(file, str(file.relative_to(base)).replace("\\", "/"))
    receipt = {"jar": jar_path.name, "sha256": hashlib.sha256(jar_path.read_bytes()).hexdigest(), "java_sources": len(java_files), "compiled": True, "tests_run": False, "gameplay_verified": False}
    (ROOT / "evidence" / "sanctuary-build.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False))


if __name__ == "__main__":
    main()
