"""Install only the equipment reference chapters, groups and their translations."""
import argparse
import json
from pathlib import Path
import subprocess

from build_equipment_atlas import PREFIX
from build_gear_guide import FILENAME
from campaign_navigation import ACCESSORY_GROUP, ARMOR_GROUP, BOOK_GROUPS
from deploy_campaign_refresh import chapter_groups, previous
from deploy_world_turn import BOOK_PATH, INSTANCE, QUESTS, ROOT, install_payloads, language_prefixes, read_language

BASELINE = "12901c2"


def atlas_updates(instance):
    payloads = {}
    prefixes = []
    last = {}
    for release in ("equipment-atlas", "gear-expansion", "equipment-collection"):
        receipt = ROOT / "evidence" / f"{release}-deployment.json"
        if receipt.is_file():
            record = json.loads(receipt.read_bytes())
            if record.get("instance") == str(instance) and record["date"] > last.get("date", ""):
                last = record
    for src in sorted((QUESTS / "chapters").glob(PREFIX + "*.snbt")):
        file = BOOK_PATH / "chapters" / src.name
        content = src.read_bytes()
        chapter = json.loads(content)
        if chapter["group"] not in (ARMOR_GROUP, ACCESSORY_GROUP):
            raise ValueError(f"Unknown catalog chapter {file}")
        prefixes.extend(language_prefixes(chapter))
        dest = instance / file
        if dest.exists():
            if dest.read_bytes() == content: continue
            # FTB saves chapters back in its own SNBT formatting. When the source
            # for this release is identical, keep the instance's own serialization.
            if last.get("instance") == str(instance):
                prior = subprocess.run(["git", "show", last["source_commit"] + ":staging/" + file.as_posix()],
                                       cwd=ROOT, capture_output=True)
                if prior.returncode == 0:
                    previous_chapter = json.loads(prior.stdout)
                    if previous_chapter == chapter: continue
                    try:
                        live = json.loads(dest.read_bytes())
                    except ValueError:
                        live = None
                    if live == previous_chapter:
                        payloads[file] = content
                        continue
            raise ValueError(f"Review the installed equipment chapter before replacing it: {file}")
        payloads[file] = content
    if not prefixes: raise ValueError("Build the equipment chapters before deploying")
    guide = BOOK_PATH / "chapters" / (FILENAME + ".snbt")
    if not (instance / guide).is_file():
        payloads[guide] = (QUESTS / "chapters" / guide.name).read_bytes()
    prefixes.extend(language_prefixes(json.loads((QUESTS / "chapters" / guide.name).read_bytes())))
    groups = BOOK_PATH / "chapter_groups.snbt"
    original = chapter_groups(instance / groups)
    installed = {v["id"] for v in original["chapter_groups"]}
    missing = [{"id": key} for key, _ in BOOK_GROUPS if key not in installed]
    if missing:
        original["chapter_groups"].extend(missing)
        payloads[groups] = (json.dumps(original, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    prefixes.extend(f"chapter_group.{key}." for key in (ACCESSORY_GROUP, ARMOR_GROUP))
    prefixes = tuple(prefixes)
    for locale in ("zh_cn", "en_us"):
        rel = Path("lang", locale + ".snbt")
        file = BOOK_PATH / rel
        base = previous(file, last["source_commit"] if last.get("instance") == str(instance) else BASELINE)
        staged = read_language(QUESTS / rel)
        current = read_language(instance / file)
        changed = False
        for key, value in staged.items():
            if not key.startswith(prefixes) or current.get(key) == value: continue
            if key in current and current[key] != base.get(key):
                if base.get(key) == value: continue
                raise ValueError(f"Review local wording for {locale} {key}")
            current[key] = value
            changed = True
        if changed:
            payloads[file] = (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    return payloads


def main(apply):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE: raise ValueError("Unexpected development instance")
    updates = atlas_updates(instance)
    if updates: install_payloads(instance, updates, "equipment-atlas", None, apply)
    else: print("Equipment reference already installed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    main(parser.parse_args().apply)
