"""Deploy the three formal chapters and retire the five known prototype chapters."""

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import tomllib
import zipfile

from deploy_workshop import ROOT, INSTANCE, QUESTS, closed
from quest_language import read_language

BASELINE = "40d4f3c"
BOOK_PATH = Path("config/ftbquests/quests")
FORMAL_CHAPTERS = (
    "00_lostworld_chapter1.snbt",
    "01_workshop_rekindled.snbt",
    "02_heritage_survey.snbt",
)
PROTOTYPE_CHAPTERS = (
    "01_home.snbt", "02_workshop.snbt", "03_arcana.snbt",
    "04_challenges.snbt", "05_homestead.snbt",
)
ARCHIVE = ROOT / "docs/archive/legacy-quests"
CORE_VERSION = "0.3.1-heritage"
CORE_TARGET = Path("mods/world-progression-core-0.1.0.jar")


def language_prefixes(chapter):
    return ("chapter." + chapter["id"] + ".", *(
        "quest." + quest["id"] + "." for quest in chapter["quests"]
    ))


def retired_prefixes():
    prefixes = tuple(prefix for name in PROTOTYPE_CHAPTERS
                     for prefix in language_prefixes(json.loads((ARCHIVE / name).read_text("utf-8-sig"))))
    # The earliest prologue used IDs with the high bit set. The active prologue uses
    # the corresponding positive IDs, but the old translations survived the migration.
    prologue = json.loads((QUESTS / "chapters" / FORMAL_CHAPTERS[0]).read_text("utf-8-sig"))
    old = tuple(f"{kind}.{int(identifier, 16) | (1 << 63):016X}." for kind, identifier, _
                in (prefix.split(".") for prefix in language_prefixes(prologue)))
    return prefixes + old


def baseline(relative):
    return json.loads(subprocess.check_output(
        ["git", "show", BASELINE + ":staging/" + relative.as_posix()], cwd=ROOT, encoding="utf-8"))


def chapter_id(path):
    content = path.read_text("utf-8-sig")
    try:
        return json.loads(content)["id"]
    except json.JSONDecodeError:
        match = re.search(r'^\t(?:id|"id"): *"([0-9A-Fa-f]+)"', content, re.MULTILINE)
        if not match:
            raise ValueError(f"Cannot identify the chapter in {path}")
        return match[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance path")

    def target(relative):
        path = instance / relative
        if not path.resolve().is_relative_to(instance):
            raise RuntimeError(f"Deployment path escapes instance: {relative}")
        return path

    payloads = {}
    removals = []
    formal_prefixes = ()
    for name in FORMAL_CHAPTERS:
        staged = QUESTS / "chapters" / name
        relative = BOOK_PATH / "chapters" / name
        proposed = json.loads(staged.read_text("utf-8-sig"))
        existing = json.loads(target(relative).read_text("utf-8-sig"))
        if existing != proposed and existing != baseline(relative):
            raise RuntimeError(f"Review local edits to {name} before overwriting")
        formal_prefixes += language_prefixes(proposed)
        payloads[relative] = staged.read_bytes()

    for name in PROTOTYPE_CHAPTERS:
        relative = BOOK_PATH / "chapters" / name
        path = target(relative)
        if not path.exists():
            continue
        expected_id = json.loads((ARCHIVE / name).read_text("utf-8-sig"))["id"]
        if chapter_id(path) != expected_id:
            raise RuntimeError(f"Unexpected chapter identity in {name}")
        removals.append(relative)

    retired = retired_prefixes()
    for locale in ("zh_cn", "en_us"):
        relative = BOOK_PATH / "lang" / f"{locale}.snbt"
        current = read_language(target(relative))
        original = baseline(relative)
        staged = read_language(QUESTS / "lang" / f"{locale}.snbt")
        merged = {key: value for key, value in current.items() if not key.startswith(retired)}
        for key, value in staged.items():
            if not key.startswith(formal_prefixes):
                continue
            if key not in merged or merged[key] == original.get(key):
                merged[key] = value
            elif value != original.get(key) and value != merged[key]:
                raise RuntimeError(f"Review conflicting language edit {locale}: {key}")
        payloads[relative] = (json.dumps(merged, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    core = ROOT / f"modules/world-progression-core/build/libs/world-progression-core-{CORE_VERSION}.jar"
    with zipfile.ZipFile(core) as jar:
        meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    if not any(mod["modId"] == "world_progression_core" and mod["version"] == CORE_VERSION for mod in meta["mods"]):
        raise RuntimeError("Unexpected core artifact")
    if set(target(CORE_TARGET).parent.glob("world-progression-core*.jar")) != {target(CORE_TARGET)}:
        raise RuntimeError("Expected exactly one installed world progression core")
    payloads[CORE_TARGET] = core.read_bytes()

    time = datetime.now(timezone(timedelta(hours=8)))
    backup = ROOT / "backups" / ("quest-refresh-" + time.strftime("%Y%m%d-%H%M%S-%f"))
    receipt = {
        "date": time.isoformat(),
        "instance": str(instance),
        "applied": args.apply,
        "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "core_version": CORE_VERSION,
        "updated": [path.as_posix() for path in payloads],
        "removed": [path.as_posix() for path in removals],
        "runtime_acceptance": "pending user playtest",
    }
    if args.apply:
        closed()
        for relative in (*payloads, *removals):
            saved = backup / relative
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target(relative), saved)
        closed()
        try:
            for relative, content in payloads.items():
                target(relative).write_bytes(content)
            for relative in removals:
                target(relative).unlink()
        except OSError:
            for relative in (*payloads, *removals):
                shutil.copy2(backup / relative, target(relative))
            raise
        receipt["backup"] = str(backup)
        payload = json.dumps(receipt, ensure_ascii=False, indent=2) + "\n"
        (backup / "receipt.json").write_text(payload, encoding="utf-8")
        (ROOT / "evidence/quest-refresh-deployment.json").write_text(payload, encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
