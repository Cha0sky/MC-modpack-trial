"""Install staged FTB components and first quest content into clone only."""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
TARGET = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    target = TARGET.resolve(strict=True)
    if target.name != "星庭远征-开发版" or target.is_junction():
        raise RuntimeError("Unexpected deployment target")
    files = [p for p in (ROOT / "staging").rglob("*") if p.is_file()]
    previous_receipt = ROOT / "evidence" / "ftb-deployment.json"
    previous = json.loads(previous_receipt.read_text(encoding="utf-8")) if previous_receipt.exists() else {"files": []}
    previous_hashes = {row["path"]: row["sha256"] for row in previous["files"]}
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backups = ROOT / "backups" / stamp
    records = []
    for source in files:
        relative = source.relative_to(ROOT / "staging")
        if relative.parts[0] not in ["mods", "config"]:
            raise RuntimeError("Unexpected staged file")
        destination = target / relative
        if not destination.resolve().is_relative_to(target):
            raise RuntimeError("Path escapes target")
        changed = destination.exists() and digest(source) != digest(destination)
        if changed and previous_hashes.get(str(relative)) != digest(destination):
            raise RuntimeError("Unexpected user change; review required: " + str(relative))
        records.append({"path": str(relative), "sha256": digest(source), "already_present": destination.exists(), "updated": changed})
    for record in records:
        source = ROOT / "staging" / record["path"]
        destination = target / record["path"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        if record["updated"]:
            backup = backups / record["path"]
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(destination, backup)
        if not destination.exists() or record["updated"]:
            shutil.copy2(source, destination)
        if digest(destination) != record["sha256"]:
            raise RuntimeError("Deployment verification failed")
    receipt = {"completed_at": datetime.now().astimezone().isoformat(), "target": str(target), "files": records, "launch_tested": False}
    receipt["backup_dir"] = str(backups) if backups.exists() else None
    if previous_receipt.exists():
        shutil.copy2(previous_receipt, ROOT / "evidence" / ("deployment-before-" + stamp + ".json"))
    previous_receipt.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"installed_files": len(records), "target": str(target)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
