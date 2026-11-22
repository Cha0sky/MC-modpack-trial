"""Update the existing campaign, growth sword and RPG guide in the development instance."""
import argparse
import json
import re
from pathlib import Path
import subprocess
import tomllib
import zipfile

from build_gear_guide import FILENAME as GEAR_GUIDE
from campaign_navigation import BESTIARY, GUIDE_GROUP, JOURNAL, LEGS, MAIN_GROUP
from deploy_world_turn import BOOK_PATH, INSTANCE, QUESTS, ROOT, install_payloads, language_prefixes, read_language

BASELINE = "dc010a9"
GUIDE_BASELINE = "cab8a9a"
GUIDE_RELEASE = "a0805e1"
CHAPTERS = tuple(leg.filename for leg in LEGS) + (GEAR_GUIDE, JOURNAL, BESTIARY)
BUILDS = (
    ("world-progression-core", "world_progression_core", "0.1.0", "0.7.0-frontline", "0.7.1-campaign"),
    ("evolvingblade", "evolvingblade", "1.1.0", "1.1.0", "1.1.4-longsword"),
    ("rpgleveling", "rpgleveling", "1.0.0", "1.0.0", "1.0.1-fieldguide"),
)


def previous(relative, baseline=BASELINE):
    return json.loads(subprocess.check_output(["git", "show", f"{baseline}:staging/{relative.as_posix()}"],
                                              cwd=ROOT, encoding="utf8"))


def mod_version(path, mod_id):
    with zipfile.ZipFile(path) as jar:
        meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    return next(mod["version"] for mod in meta["mods"] if mod["modId"] == mod_id)


def chapter_groups(path):
    text = path.read_text(encoding="utf-8-sig")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        # The FTB client writes chapter groups as a comma-optional list of ID compounds.
        entry = r'\{\s*id\s*:\s*"([0-9A-Fa-f]{16})"\s*\}'
        found = re.fullmatch(r'\s*\{\s*chapter_groups\s*:\s*\[([\s\S]*)\]\s*\}\s*', text)
        if found and re.fullmatch(r'(?:[\s,]*' + entry + r')*[\s,]*', found[1]):
            return {"chapter_groups": [{"id": key} for key in re.findall(entry, found[1])]}
        raise RuntimeError(f"Review the locally edited chapter groups: {path}")


def book_updates(instance, *, guide_only):
    payloads = {}
    prefixes = ()
    # The earlier three-file guide installer can also be used against the previous campaign.
    old_guide_only = guide_only and not (instance / BOOK_PATH / f"chapters/{JOURNAL}.snbt").exists()
    names = ("00_lostworld_chapter1", "04_field_equipment", GEAR_GUIDE) if guide_only else CHAPTERS
    for name in names:
        file = f"chapters/{name}.snbt"
        path = BOOK_PATH / file
        staged = (QUESTS / file).read_bytes()
        revised = json.loads(staged)
        prefixes += language_prefixes(revised)
        if guide_only and name != GEAR_GUIDE:
            continue
        if old_guide_only:
            revised = previous(path, GUIDE_RELEASE)
            staged = (json.dumps(revised, ensure_ascii=False, indent=2) + "\n").encode("utf8")
        dest = instance / path
        if name in (GEAR_GUIDE, JOURNAL, BESTIARY) and not dest.exists():
            payloads[path] = staged
            continue
        current = json.loads(dest.read_bytes())
        if current == revised:
            continue
        if name in (JOURNAL, BESTIARY) or (
                current != previous(path, GUIDE_RELEASE) and (name == GEAR_GUIDE or current != previous(path))):
            raise RuntimeError(f"Review the local changes to {name}")
        payloads[path] = staged

    if not guide_only:
        file = "chapter_groups.snbt"
        path = BOOK_PATH / file
        groups = chapter_groups(instance / path) if (instance / path).exists() else {"chapter_groups": []}
        existing = {g["id"] for g in groups["chapter_groups"]}
        additions = [g for g in chapter_groups(QUESTS / file)["chapter_groups"] if g["id"] not in existing]
        if additions:
            groups["chapter_groups"].extend(additions)
            payloads[path] = (json.dumps(groups, ensure_ascii=False, indent=2) + "\n").encode("utf8")
        prefixes += tuple(f"chapter_group.{key}." for key in (MAIN_GROUP, GUIDE_GROUP))

    for locale in ("en_us", "zh_cn"):
        file = f"lang/{locale}.snbt"
        path = BOOK_PATH / file
        first = previous(path, BASELINE)
        before_guide = previous(path, GUIDE_BASELINE)
        after_guide = previous(path, GUIDE_RELEASE)
        revised = after_guide if old_guide_only else read_language(QUESTS / file)
        current = read_language(instance / path)
        changed = False
        for key, value in revised.items():
            if not key.startswith(prefixes) or current.get(key) == value:
                continue
            if guide_only and value == (before_guide if old_guide_only else after_guide).get(key):
                continue
            if key in current and current[key] not in (first.get(key), before_guide.get(key), after_guide.get(key)):
                if value == after_guide.get(key):
                    continue  # Preserve local wording for entries untouched by this revision.
                raise RuntimeError(f"Review the local translation in {locale}: {key}")
            current[key] = value
            changed = True
        if changed:
            payloads[path] = (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    return payloads


def main(apply, blade_only=False, guide_only=False, book_only=False):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance")
    if guide_only or book_only:
        updates = book_updates(instance, guide_only=guide_only)
        if updates:
            install_payloads(instance, updates, "equipment-guide" if guide_only else "journey-campaign", None, apply)
        else:
            print("Requested quest book data is already installed")
        return
    payloads = {}
    module_versions = {}
    for module, mod_id, filename_version, old, new in BUILDS:
        if blade_only and mod_id != "evolvingblade":
            continue
        path = Path("mods") / f"{module}-{filename_version}.jar"
        jar = ROOT / "modules" / module / "build/libs" / f"{module}-{new}.jar"
        compatible = {old, new}
        if mod_id == "evolvingblade":
            compatible.update(("1.1.1-folio", "1.1.2-pixels", "1.1.3-longsword"))
        if mod_version(jar, mod_id) != new or mod_version(instance / path, mod_id) not in compatible:
            raise RuntimeError(f"Review the version of {module}")
        payloads[path] = jar.read_bytes()
        module_versions[mod_id] = new

    if blade_only:
        core = instance / "mods/world-progression-core-0.1.0.jar"
        install_payloads(instance, payloads, "blade-pixels", mod_version(core, "world_progression_core"), apply,
                         module_versions=module_versions)
        return

    payloads.update(book_updates(instance, guide_only=False))
    install_payloads(instance, payloads, "campaign-refresh", BUILDS[0][-1], apply, module_versions=module_versions)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument("--book-only", action="store_true", help="Update only campaign chapters, groups and language")
    scope.add_argument("--guide-only", action="store_true", help="Install the equipment guide and its mainline links")
    scope.add_argument("--blade-only", action="store_true", help="Only replace the growth sword module")
    args = parser.parse_args()
    main(args.apply, args.blade_only, args.guide_only, args.book_only)
