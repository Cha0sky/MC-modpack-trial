"""Install staged FTB components and first quest content into clone only."""
from pathlib import Path
from datetime import datetime
import argparse
import hashlib
import json
import re
import shutil
from deploy_campaign_refresh import mod_version
from deploy_world_turn import install_payloads
from fetch_ftb import QUEST_FILENAME, QUEST_VERSION, stage_quests

ROOT = Path(__file__).resolve().parents[1]
TARGET = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\星庭远征-开发版")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def install_quests(*, apply):
    target = TARGET.resolve(strict=True)
    if target != TARGET:
        raise RuntimeError("Unexpected development instance")
    libraries = list((target / "mods").glob("*ftb-library-neoforge-*.jar"))
    if len(libraries) != 1:
        raise RuntimeError("Expected one installed FTB Library")
    library = mod_version(libraries[0], "ftblibrary")
    release = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", library)
    if not release or tuple(map(int, release.groups())) < (2101, 1, 35):
        raise RuntimeError(f"FTB Quests {QUEST_VERSION} requires FTB Library 2101.1.35+; found {library}")
    staged = ROOT / "staging/mods"
    stage_quests(staged)
    old = Path("mods/ftb-quests-neoforge-2101.1.27.jar")
    new = Path("mods") / QUEST_FILENAME
    active = {p.relative_to(target) for p in (target / "mods").glob("*ftb-quests-neoforge-*.jar")}
    if active == {new} and mod_version(target / new, "ftbquests") == QUEST_VERSION:
        print(f"FTB Quests {QUEST_VERSION} is already installed")
        return
    if active and (active != {old} or mod_version(target / old, "ftbquests") != "2101.1.27"):
        raise RuntimeError(f"Review installed FTB Quests files: {active}")
    install_payloads(target, {new: (staged / QUEST_FILENAME).read_bytes()}, "ftb-quest-zoom", None, apply,
                     module_versions={"ftbquests": QUEST_VERSION}, retire=(old,) if old in active else ())


def main():
    target = TARGET.resolve(strict=True)
    if target.name != "星庭远征-开发版" or target.is_junction():
        raise RuntimeError("Unexpected deployment target")
    files = [p for p in (ROOT / "staging").rglob("*") if p.is_file()
             and not p.match("mods/*ftb-quests-neoforge-*.jar")]
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
    install_quests(apply=True)
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--quests-only", action="store_true", help="Only replace FTB Quests")
    parser.add_argument("--dry-run", action="store_true", help="Preview the FTB Quests replacement")
    args = parser.parse_args()
    if args.quests_only:
        install_quests(apply=not args.dry_run)
    elif args.dry_run:
        parser.error("--dry-run is supported with --quests-only")
    else:
        main()
