"""Read-only instance mod audit; write a dated manifest inside the repository only."""
import argparse
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path

from inventory import INSTANCE, scan_jar

ROOT = Path(__file__).resolve().parents[1]
NEW_IDS = {"mowziesmobs", "lootr", "explorerscompass", "naturescompass"}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def delta(before, after):
    return {
        "added": sorted(after.keys()-before.keys()),
        "removed": sorted(before.keys()-after.keys()),
        "same_name_changed": sorted(n for n in before.keys() & after.keys() if before[n] != after[n]),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True, help="Prior instance manifest, read only")
    parser.add_argument("--date", required=True, help="YYYY-MM-DD evidence filename")
    args = parser.parse_args()
    datetime.strptime(args.date, "%Y-%m-%d")
    before_raw = args.baseline.read_bytes()
    # The prior chapter snapshot includes non-mod files; never publish those paths/contents.
    before = {Path(k.replace("\\", "/")).name: v
              for k, v in json.loads(before_raw).items() if k.replace("\\", "/").startswith("mods/")}
    rows, ids = [], defaultdict(list)
    for p in sorted((INSTANCE / "mods").iterdir()):
        if not p.is_file():
            continue
        raw = p.read_bytes()
        row = {"file": p.name, "bytes": len(raw), "sha256": sha(raw), "enabled": p.suffix.lower() == ".jar"}
        if row["enabled"] or p.name.endswith(".jar.disabled"):
            row["jar"] = scan_jar(raw, p.name)
        for mod in row.get("jar", {}).get("mods", []):
            if row["enabled"]:
                ids[mod["modId"]].append(p.name)
        rows.append(row)
    after = {r["file"]: r["sha256"] for r in rows}
    old_inventory = json.loads((ROOT / "evidence/inventory.json").read_text("utf-8-sig"))
    old_repo = {r["file"]: r["sha256"] for r in old_inventory["files"] if "sha256" in r}
    related_configs = []
    for folder in ("config", "defaultconfigs"):
        for p in sorted((INSTANCE / folder).rglob("*")):
            if p.is_file() and any(term in str(p.relative_to(INSTANCE)).lower()
                                   for term in ("mowzie", "lootr", "compass")):
                related_configs.append({"path": p.relative_to(INSTANCE).as_posix(), "sha256": sha(p.read_bytes())})
    new_rows = [r for r in rows if any(m["modId"] in NEW_IDS for m in r.get("jar", {}).get("mods", []))]
    warnings = []
    for row in new_rows:
        for dependencies in row["jar"].get("dependencies", {}).values():
            for dep in dependencies:
                if dep["modId"] == "minecraft" and dep.get("versionRange") == "[1.21,1.21.1)":
                    warnings.append({"file": row["file"], "code": "minecraft_range_excludes_1.21.1",
                                     "declared_range": dep["versionRange"], "resolved": False})
    local_builds = {}
    for name in ("infinitecore-1.1.2.jar", "world-progression-core-0.1.0.jar"):
        module = "infinitecore" if name.startswith("infinitecore-") else "world-progression-core"
        p = ROOT / "modules" / module / "build/libs" / name
        local_builds[name] = {"local_artifact_sha256": sha(p.read_bytes()) if p.is_file() else None,
                             "instance_sha256": after.get(name)}
    report = {
        "date": args.date, "scanned_at": datetime.now(timezone(timedelta(hours=8))).isoformat(),
        "instance": str(INSTANCE), "minecraft": "1.21.1", "neoforge": "21.1.248",
        "scope": "Top-level mod files plus nested jar metadata; no launch or instance/save writes",
        "baseline": {"description": "Prior chapter-one instance snapshot, mods subset only",
                     "source_sha256": sha(before_raw), "mods": before},
        "changes_since_chapter1_snapshot": delta(before, after),
        "changes_since_repository_inventory": delta(old_repo, after),
        "counts": {"files": len(rows), "enabled_jars": sum(r["enabled"] for r in rows)},
        "duplicate_top_level_mod_ids": {k: v for k, v in ids.items() if len(v) > 1},
        "new_mod_ids_found": sorted(NEW_IDS & ids.keys()), "local_build_artifacts": local_builds,
        "new_mod_related_config_files": related_configs, "metadata_findings": warnings,
        "new_mod_runtime_verified": False, "files": rows,
    }
    assert not report["duplicate_top_level_mod_ids"], "Duplicate enabled top-level mod IDs"
    assert report["new_mod_ids_found"] == sorted(NEW_IDS), "Expected user additions are missing"
    assert all(d["local_artifact_sha256"] == d["instance_sha256"] and d["instance_sha256"]
               for d in local_builds.values()), "Local build and instance jar differ"
    output = ROOT / "evidence" / f"development-mods-{args.date}.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"output": str(output), "counts": report["counts"],
                      "changes": report["changes_since_chapter1_snapshot"],
                      "metadata_findings": warnings, "related_configs": related_configs}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
