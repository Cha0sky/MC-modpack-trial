"""Update the existing campaign, growth sword and RPG guide in the development instance."""
import argparse
import json
from pathlib import Path
import subprocess
import tomllib
import zipfile

from build_gear_guide import FILENAME as GEAR_GUIDE
from deploy_world_turn import BOOK_PATH, INSTANCE, QUESTS, ROOT, install_payloads, language_prefixes, read_language

BASELINE = "dc010a9"
GUIDE_BASELINE = "cab8a9a"
CHAPTERS = ("00_lostworld_chapter1", "01_workshop_rekindled", "02_heritage_survey", "03_world_turn",
            "04_field_equipment", "05_relic_expeditions", "06_frontline_depot", GEAR_GUIDE)
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


def book_updates(instance, *, guide_only):
    payloads = {}
    prefixes = ()
    names = ("00_lostworld_chapter1", "04_field_equipment", GEAR_GUIDE) if guide_only else CHAPTERS
    for name in names:
        file = f"chapters/{name}.snbt"
        path = BOOK_PATH / file
        staged = (QUESTS / file).read_bytes()
        revised = json.loads(staged)
        prefixes += language_prefixes(revised)
        if guide_only and name != GEAR_GUIDE:
            continue  # Mainline links live in quest_desc; their chapter files are unchanged.
        destination = instance / path
        if name == GEAR_GUIDE and not destination.exists():
            payloads[path] = staged
            continue
        current = json.loads(destination.read_bytes())
        if current != revised:
            if name == GEAR_GUIDE or current != previous(path):
                raise RuntimeError(f"Review the local changes to {name}")
            payloads[path] = staged

    for locale in ("en_us", "zh_cn"):
        file = f"lang/{locale}.snbt"
        path = BOOK_PATH / file
        last_campaign = previous(path, GUIDE_BASELINE)
        original = last_campaign if guide_only else previous(path)
        current = read_language(instance / path)
        changed = False
        for key, value in read_language(QUESTS / file).items():
            if not key.startswith(prefixes) or original.get(key) == value or current.get(key) == value:
                continue
            if key in current and current[key] not in (original.get(key), last_campaign.get(key)):
                raise RuntimeError(f"Review the local translation in {locale}: {key}")
            current[key] = value
            changed = True
        if changed:
            payloads[path] = (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    return payloads


def main(apply, blade_only=False, guide_only=False):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance")
    if guide_only:
        updates = book_updates(instance, guide_only=True)
        if updates:
            install_payloads(instance, updates, "equipment-guide", None, apply)
        else:
            print("Equipment guide is already installed")
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
    scope.add_argument("--guide-only", action="store_true", help="Install the equipment guide and its mainline links")
    scope.add_argument("--blade-only", action="store_true", help="Only replace the growth sword module")
    args = parser.parse_args()
    main(args.apply, args.blade_only, args.guide_only)
