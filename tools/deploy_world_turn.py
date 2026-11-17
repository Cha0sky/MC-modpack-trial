"""Install the approved chapter and its core in the development instance."""

import argparse
from datetime import datetime
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile

from build_heritage_quests import uid
from deploy_quest_refresh import BOOK_PATH, CORE_TARGET, language_prefixes
from deploy_workshop import INSTANCE, QUESTS, ROOT, closed
from quest_language import read_language

VERSION = "0.4.0-world-turn"
BASELINE = "6de29e2"
CHAPTER = "03_world_turn.snbt"
REVISED_KEYS = {
    f"quest.{uid(name)}.quest_desc" for name in ("expedition_challenge", "archive_open")
}


def core_version(path):
    with zipfile.ZipFile(path) as jar:
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    return next(mod["version"] for mod in metadata["mods"] if mod["modId"] == "world_progression_core")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance path")

    core = ROOT / f"modules/world-progression-core/build/libs/world-progression-core-{VERSION}.jar"
    installed = instance / CORE_TARGET
    if core_version(core) != VERSION or core_version(installed) not in {"0.3.1-heritage", VERSION}:
        raise RuntimeError("Unexpected core version in the source or development instance")

    chapter = (QUESTS / "chapters" / CHAPTER).read_bytes()
    prefixes = language_prefixes(json.loads(chapter))
    payloads = {CORE_TARGET: core.read_bytes(), BOOK_PATH / "chapters" / CHAPTER: chapter}
    chapter_target = instance / BOOK_PATH / "chapters" / CHAPTER
    if chapter_target.exists() and chapter_target.read_bytes() != chapter:
        raise RuntimeError("Review the local edits to the third chapter before overwriting it")

    for locale in ("zh_cn", "en_us"):
        relative = BOOK_PATH / "lang" / f"{locale}.snbt"
        existing = read_language(instance / relative)
        previous = json.loads(subprocess.check_output(
            ["git", "show", f"{BASELINE}:staging/{relative.as_posix()}"], cwd=ROOT, encoding="utf-8"))
        staged = read_language(QUESTS / "lang" / f"{locale}.snbt")
        for key, value in staged.items():
            if key in REVISED_KEYS or key.startswith(prefixes):
                if key in existing and existing[key] not in (previous.get(key), value):
                    raise RuntimeError(f"Review the local translation change in {locale}: {key}")
                existing[key] = value
        payloads[relative] = (json.dumps(existing, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    for relative in payloads:
        if not (instance / relative).resolve().is_relative_to(instance):
            raise RuntimeError(f"Deployment path escapes instance: {relative}")

    now = datetime.now().astimezone()
    backup = ROOT / "backups" / now.strftime("world-turn-%Y%m%d-%H%M%S-%f")
    files = [{"path": relative.as_posix(), "existed": (instance / relative).is_file()}
             for relative in payloads]
    receipt = {
        "date": now.isoformat(), "instance": str(instance), "applied": args.apply,
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "core_version": VERSION, "files": files,
        "runtime_acceptance": "pending user playtest",
    }
    if args.apply:
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
        (ROOT / "evidence/world-turn-deployment.json").write_text(output, encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
