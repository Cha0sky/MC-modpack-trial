"""Full verified copy of the user-authorized test instance; never overwrite."""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import shutil
import os

SOURCE = Path(r"D:\PCL 正式版 2.10.6 (1)\.minecraft\versions\mod test")
TARGET = SOURCE.parent / "星庭远征-开发版"
PROJECT = Path(__file__).resolve().parents[1]


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def manifest(root):
    return {str(p.relative_to(root)): {"bytes": p.stat().st_size, "sha256": sha256(p)} for p in sorted(root.rglob("*")) if p.is_file()}


def main():
    source = SOURCE.resolve(strict=True)
    parent = source.parent.resolve(strict=True)
    target = TARGET.resolve()
    if source.parent != parent or target.parent != parent or target == source:
        raise RuntimeError("Invalid source/target relationship")
    if target.exists():
        raise RuntimeError("Target already exists; will not overwrite")
    for path in [source, *source.rglob("*")]:
        if path.is_symlink() or path.is_junction():
            raise RuntimeError(f"Reparse point requires separate review: {path}")
    baseline = manifest(source)
    total = sum(row["bytes"] for row in baseline.values())
    if shutil.disk_usage(parent).free < total + 1024 * 1024 * 1024:
        raise RuntimeError("Insufficient disk space")
    evidence = PROJECT / "evidence"
    evidence.mkdir(exist_ok=True)
    (evidence / "clone-source-manifest.json").write_text(json.dumps(baseline, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Copying {len(baseline)} files / {total} bytes", flush=True)
    shutil.copytree(source, target, copy_function=shutil.copy2)
    copied = manifest(target)
    if copied != baseline:
        raise RuntimeError("Copied file manifest differs from baseline; target retained for inspection")
    if manifest(source) != baseline:
        raise RuntimeError("Source changed during copy; target retained for inspection")
    source_dirs = {str(p.relative_to(source)) for p in source.rglob("*") if p.is_dir()}
    target_dirs = {str(p.relative_to(target)) for p in target.rglob("*") if p.is_dir()}
    if source_dirs != target_dirs:
        raise RuntimeError("Directory manifests differ")
    changes = []
    old_json = target / (source.name + ".json")
    new_json = target / (target.name + ".json")
    doc = json.loads(old_json.read_text(encoding="utf-8-sig"))
    if doc.get("id") != source.name:
        raise RuntimeError("Unexpected descriptor identity")
    doc["id"] = target.name
    if doc.get("jar") == source.name:
        doc["jar"] = target.name
    new_json.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    old_json.unlink()
    changes.append({"from": source.name + ".json", "to": target.name + ".json", "action": "rename and update version id"})
    old_jar = target / (source.name + ".jar")
    new_jar = target / (target.name + ".jar")
    old_jar.rename(new_jar)
    if sha256(new_jar) != baseline[source.name + ".jar"]["sha256"]:
        raise RuntimeError("Renamed base JAR differs")
    changes.append({"from": source.name + ".jar", "to": target.name + ".jar", "action": "rename only"})
    final = manifest(target)
    expected = set(baseline) - {source.name + ".json", source.name + ".jar"} | {target.name + ".json", target.name + ".jar"}
    if set(final) != expected:
        raise RuntimeError("Unexpected target file changes")
    for relative in set(baseline) - {source.name + ".json", source.name + ".jar"}:
        if baseline[relative] != final[relative]:
            raise RuntimeError(f"Unexpected content change: {relative}")
    if manifest(source) != baseline:
        raise RuntimeError("Source changed after rename")
    receipt = {
        "completed_at": datetime.now().astimezone().isoformat(),
        "source": str(source), "target": str(target),
        "files_copied": len(baseline), "bytes_copied": total,
        "directories_copied": len(source_dirs),
        "full_copy_verified": True, "source_unchanged": True,
        "identity_changes": changes,
        "target_manifest": final,
        "launch_tested": False,
    }
    (evidence / "clone-receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in receipt.items() if k != "target_manifest"}, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
