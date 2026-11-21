"""Install the approved chapter and its core in the development instance."""

import argparse
from datetime import datetime
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile

from deploy_quest_refresh import BOOK_PATH, CORE_TARGET, language_prefixes
from deploy_workshop import INSTANCE, QUESTS, ROOT, closed
from quest_language import read_language

VERSION = "0.5.1-expedition"
BASELINE = "9442c74"
CHAPTER = "03_world_turn.snbt"


def core_version(path):
    with zipfile.ZipFile(path) as jar:
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    return next(mod["version"] for mod in metadata["mods"] if mod["modId"] == "world_progression_core")


def deploy_chapter(*, version, previous_version, chapter_name, revised_keys, baseline, receipt_name, apply):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance path")

    core = ROOT / f"modules/world-progression-core/build/libs/world-progression-core-{version}.jar"
    installed = instance / CORE_TARGET
    if core_version(core) != version or core_version(installed) not in {previous_version, version}:
        raise RuntimeError("Unexpected core version in the source or development instance")

    chapter = (QUESTS / "chapters" / chapter_name).read_bytes()
    prefixes = language_prefixes(json.loads(chapter))
    payloads = {CORE_TARGET: core.read_bytes(), BOOK_PATH / "chapters" / chapter_name: chapter}
    chapter_target = instance / BOOK_PATH / "chapters" / chapter_name
    if chapter_target.exists() and chapter_target.read_bytes() != chapter:
        old = subprocess.run(["git", "show", f"{baseline}:staging/{BOOK_PATH.as_posix()}/chapters/{chapter_name}"],
                             cwd=ROOT, capture_output=True)
        try:
            matches_base = old.returncode == 0 and json.loads(old.stdout) == json.loads(chapter_target.read_bytes())
        except ValueError:
            matches_base = False
        if not matches_base:
            raise RuntimeError(f"Review local edits to {chapter_name} before overwriting it")

    for locale in ("zh_cn", "en_us"):
        relative = BOOK_PATH / "lang" / f"{locale}.snbt"
        existing = read_language(instance / relative)
        previous = json.loads(subprocess.check_output(
            ["git", "show", f"{baseline}:staging/{relative.as_posix()}"], cwd=ROOT, encoding="utf-8"))
        staged = read_language(QUESTS / "lang" / f"{locale}.snbt")
        for key, value in staged.items():
            if key in revised_keys or key.startswith(prefixes):
                if key in existing and existing[key] not in (previous.get(key), value):
                    raise RuntimeError(f"Review the local translation change in {locale}: {key}")
                existing[key] = value
        payloads[relative] = (json.dumps(existing, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    install_payloads(instance, payloads, receipt_name, version, apply)


def install_payloads(instance, payloads, receipt_name, version, apply, *, module_versions=None, retire=()):
    """Back up and install only the prepared files, or display the same plan in a dry run."""
    paths = [*payloads, *retire]
    if len(set(paths)) != len(paths):
        raise ValueError("A deployment cannot both install and retire the same path")
    for relative in paths:
        if not (instance / relative).resolve().is_relative_to(instance):
            raise RuntimeError(f"Deployment path escapes instance: {relative}")

    now = datetime.now().astimezone()
    backup = ROOT / "backups" / (receipt_name + now.strftime("-%Y%m%d-%H%M%S-%f"))
    files = [{"path": relative.as_posix(), "existed": (instance / relative).is_file()}
             for relative in paths]
    receipt = {
        "date": now.isoformat(), "instance": str(instance), "applied": apply,
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "files": files,
        "runtime_acceptance": "pending user playtest",
    }
    if version is not None:
        receipt["core_version"] = version
    if retire:
        receipt["retired"] = [path.as_posix() for path in retire]
    if module_versions:
        receipt["module_versions"] = module_versions
    if apply:
        closed()
        for entry in files:
            if entry["existed"]:
                saved = backup / entry["path"]
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(instance / entry["path"], saved)
        closed()
        try:
            for relative, content in payloads.items():
                (instance / relative).write_bytes(content)
            for relative in retire:
                (instance / relative).unlink()
        except OSError:
            for entry in files:
                target = instance / entry["path"]
                if entry["existed"]:
                    shutil.copy2(backup / entry["path"], target)
                else:
                    target.unlink(missing_ok=True)
            raise
        receipt["backup"] = str(backup)
        output = json.dumps(receipt, ensure_ascii=False, indent=2) + "\n"
        (backup / "receipt.json").write_text(output, encoding="utf-8")
        (ROOT / f"evidence/{receipt_name}-deployment.json").write_text(output, encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    deploy_chapter(version=VERSION, previous_version="0.5.0-field-equipment", chapter_name=CHAPTER,
                   revised_keys=(), baseline=BASELINE, receipt_name="world-turn-expedition", apply=args.apply)


if __name__ == "__main__":
    main()
