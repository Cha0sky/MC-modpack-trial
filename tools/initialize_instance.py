"""Reversible first changes to the verified clone only."""
from pathlib import Path
from datetime import datetime
import json
from clone_instance import SOURCE, TARGET, PROJECT, manifest, sha256


def main():
    evidence = PROJECT / "evidence"
    receipt = json.loads((evidence / "clone-receipt.json").read_text(encoding="utf-8"))
    baseline = json.loads((evidence / "clone-source-manifest.json").read_text(encoding="utf-8"))
    target = TARGET.resolve(strict=True)
    source = SOURCE.resolve(strict=True)
    if target == source or target.parent != source.parent or str(target) != receipt["target"]:
        raise RuntimeError("Unexpected target")
    if manifest(target) != receipt["target_manifest"]:
        raise RuntimeError("Clone has changed since verified copy; review before modifying")
    if manifest(source) != baseline:
        raise RuntimeError("Source no longer matches original snapshot")
    chosen = [
        ("星空之刃模组-starsword-1.0.0.jar", "用户允许排除 starsword"),
        ("curios-neoforge-9.1.4+1.21.0.jar", "保留最近运行使用的 9.5.1"),
        ("[钠 · 扩展] sodium-extra-neoforge-0.9.3+mc1.21.1.jar", "保留最近运行使用的 0.9.4"),
        ("[钠] sodium-neoforge-0.8.13-beta.2+mc1.21.1.jar", "保留最近运行使用的 0.8.13 正式版"),
    ]
    changes = []
    for filename, reason in chosen:
        old = target / "mods" / filename
        new = target / "mods" / (filename + ".disabled")
        if old.resolve().parent != (target / "mods").resolve() or not old.is_file() or new.exists():
            raise RuntimeError("Unexpected mod path/state")
        changes.append({"from": str(old.relative_to(target)), "to": str(new.relative_to(target)), "reason": reason})
    for change in changes:
        (target / change["from"]).rename(target / change["to"])
    final = manifest(target)
    expected = dict(receipt["target_manifest"])
    for change in changes:
        expected[change["to"]] = expected.pop(change["from"])
    if final != expected or manifest(source) != baseline:
        raise RuntimeError("Unexpected instance change")
    record = {
        "completed_at": datetime.now().astimezone().isoformat(),
        "target": str(target), "changes": changes,
        "source_unchanged": True, "only_expected_changes": True,
        "gameplay_configs_changed": False, "launch_tested": False,
    }
    (evidence / "initial-changes.json").write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(record, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
