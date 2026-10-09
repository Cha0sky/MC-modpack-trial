"""Read the development instance; write only project-local inventory evidence."""
from pathlib import Path
from collections import defaultdict
import hashlib
import json
import tomllib
import zipfile
from datetime import datetime

INSTANCE = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")
OUT = Path(__file__).resolve().parents[1] / "evidence"


def scan_jar(data, origin):
    result = {"origin": origin, "mods": [], "nested": []}
    import io
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names = set(jar.namelist())
        for name in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
            if name in names:
                doc = tomllib.loads(jar.read(name).decode("utf-8-sig"))
                result["metadata_file"] = name
                result["mods"] = [
                    {k: m.get(k) for k in ("modId", "displayName", "version", "authors", "description")}
                    for m in doc.get("mods", [])
                ]
                result["dependencies"] = doc.get("dependencies", {})
                break
        if "META-INF/jarjar/metadata.json" in names:
            for item in json.loads(jar.read("META-INF/jarjar/metadata.json")).get("jars", []):
                nested_path = item.get("path")
                if nested_path in names:
                    try:
                        result["nested"].append(scan_jar(jar.read(nested_path), origin + "!" + nested_path))
                    except Exception as exc:
                        result["nested"].append({"origin": origin + "!" + nested_path, "error": str(exc)})
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    descriptor = json.loads((INSTANCE / (INSTANCE.name + ".json")).read_text(encoding="utf-8-sig"))
    game_args = descriptor.get("arguments", {}).get("game", [])
    def argument_value(key):
        for index, value in enumerate(game_args[:-1]):
            if value == key:
                return game_args[index + 1]
        return None
    inventory = {
        "scanned_at": datetime.now().astimezone().isoformat(),
        "instance": str(INSTANCE),
        "minecraft": argument_value("--fml.mcVersion"),
        "neoforge": argument_value("--fml.neoForgeVersion"),
        "java": descriptor.get("javaVersion"),
        "loader_libraries": [x["name"] for x in descriptor.get("libraries", []) if "neoforge" in x.get("name", "") or "net.neoforged:fml" in x.get("name", "")],
        "files": [],
        "config_files": sorted(str(p.relative_to(INSTANCE / "config")) for p in (INSTANCE / "config").rglob("*") if p.is_file()),
        "defaultconfigs": sorted(str(p.relative_to(INSTANCE / "defaultconfigs")) for p in (INSTANCE / "defaultconfigs").rglob("*") if p.is_file()),
    }
    by_id = defaultdict(list)
    for path in sorted((INSTANCE / "mods").iterdir()):
        if not path.is_file():
            continue
        row = {"file": path.name, "size": path.stat().st_size, "enabled_extension": path.suffix.lower() == ".jar"}
        if row["enabled_extension"] or path.name.endswith(".jar.disabled"):
            data = path.read_bytes()
            row["sha256"] = hashlib.sha256(data).hexdigest()
            try:
                row["jar"] = scan_jar(data, path.name)
                if row["enabled_extension"]:
                    for m in row["jar"]["mods"]:
                        by_id[m["modId"]].append(path.name)
            except Exception as exc:
                row["error"] = str(exc)
        inventory["files"].append(row)
    inventory["duplicate_top_level_ids"] = {k: v for k, v in by_id.items() if len(v) > 1}
    inventory["ftb_top_level_ids"] = sorted(k for k in by_id if k.startswith("ftb"))
    config_snapshot = {}
    for relative, sections in {
        "evolvingblade-server.toml": ["growth", "stages"],
        "rpgleveling-common.toml": ["general", "xp"],
        "l2configs/l2hostility-server.toml": ["scaling", "difficulty"],
    }.items():
        path = INSTANCE / "config" / relative
        raw = path.read_bytes()
        doc = tomllib.loads(raw.decode("utf-8-sig"))
        config_snapshot[relative] = {
            "sha256": hashlib.sha256(raw).hexdigest(),
            "selected_sections": {s: doc.get(s) for s in sections},
        }
    (OUT / "selected-configs.json").write_text(json.dumps(config_snapshot, ensure_ascii=False, indent=2), encoding="utf-8")
    log_path = INSTANCE / "logs" / "latest.log"
    log_raw = log_path.read_bytes()
    runtime_lines = [line for line in log_raw.decode("utf-8", errors="replace").splitlines() if line.startswith("\t\t")]
    inventory["runtime_log"] = {"path": str(log_path), "sha256": hashlib.sha256(log_raw).hexdigest(), "modified_at": datetime.fromtimestamp(log_path.stat().st_mtime).astimezone().isoformat(), "provenance": "Inherited mod test log; development instance not launch-tested", "mod_list_lines": runtime_lines}
    (OUT / "inventory.json").write_text(json.dumps(inventory, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# 模组清单快照", "", "扫描时间：" + inventory["scanned_at"], "", "仅表示目录和 JAR 元数据，不等于运行时全部成功加载。内嵌模组详见 inventory.json。", "", "| 文件 | 顶层模组 ID | 元数据版本 | 状态 |", "|---|---|---|---|"]
    for row in inventory["files"]:
        mods = row.get("jar", {}).get("mods", [])
        lines.append("| " + " | ".join([row["file"].replace("|", "\\|"), ", ".join(str(m.get("modId")) for m in mods), ", ".join(str(m.get("version")) for m in mods), "候选启用" if row["enabled_extension"] else "停用/其他"]) + " |")
    (OUT / "mod-list.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"files": len(inventory["files"]), "jar_candidates": sum(x["enabled_extension"] for x in inventory["files"]), "duplicates": inventory["duplicate_top_level_ids"], "ftb": inventory["ftb_top_level_ids"], "metadata_errors": [x["file"] for x in inventory["files"] if "error" in x]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
