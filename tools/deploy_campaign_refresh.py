"""Update the existing campaign, growth sword and RPG guide in the development instance."""
import argparse
import json
from pathlib import Path
import subprocess
import tomllib
import zipfile

from deploy_world_turn import BOOK_PATH, INSTANCE, QUESTS, ROOT, install_payloads, language_prefixes, read_language

BASELINE = "dc010a9"
CHAPTERS = ("00_lostworld_chapter1", "01_workshop_rekindled", "02_heritage_survey", "03_world_turn",
            "04_field_equipment", "05_relic_expeditions", "06_frontline_depot")
BUILDS = (
    ("world-progression-core", "world_progression_core", "0.1.0", "0.7.0-frontline", "0.7.1-campaign"),
    ("evolvingblade", "evolvingblade", "1.1.0", "1.1.0", "1.1.3-longsword"),
    ("rpgleveling", "rpgleveling", "1.0.0", "1.0.0", "1.0.1-fieldguide"),
)


def previous(relative):
    return json.loads(subprocess.check_output(["git", "show", f"{BASELINE}:staging/{relative.as_posix()}"],
                                              cwd=ROOT, encoding="utf8"))


def mod_version(path, mod_id):
    with zipfile.ZipFile(path) as jar:
        meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
    return next(mod["version"] for mod in meta["mods"] if mod["modId"] == mod_id)


def main(apply, blade_only=False):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise RuntimeError("Unexpected development instance")
    payloads = {}
    module_versions = {}
    for module, mod_id, filename_version, old, new in BUILDS:
        if blade_only and mod_id != "evolvingblade":
            continue
        path = Path("mods") / f"{module}-{filename_version}.jar"
        jar = ROOT / "modules" / module / "build/libs" / f"{module}-{new}.jar"
        compatible = {old, new}
        if mod_id == "evolvingblade":
            compatible.update(("1.1.1-folio", "1.1.2-pixels"))
        if mod_version(jar, mod_id) != new or mod_version(instance / path, mod_id) not in compatible:
            raise RuntimeError(f"Review the version of {module}")
        payloads[path] = jar.read_bytes()
        module_versions[mod_id] = new

    if blade_only:
        core = instance / "mods/world-progression-core-0.1.0.jar"
        install_payloads(instance, payloads, "blade-pixels", mod_version(core, "world_progression_core"), apply,
                         module_versions=module_versions)
        return

    prefixes = ()
    for name in CHAPTERS:
        file = f"chapters/{name}.snbt"
        path = BOOK_PATH / file
        revised = json.loads((QUESTS / file).read_bytes())
        current = json.loads((instance / path).read_bytes())
        if current != revised:
            if current != previous(path):
                raise RuntimeError(f"Review the local changes to {name}")
            payloads[path] = (QUESTS / file).read_bytes()
        prefixes += language_prefixes(revised)

    for locale in ("en_us", "zh_cn"):
        file = f"lang/{locale}.snbt"
        path = BOOK_PATH / file
        original = previous(path)
        current = read_language(instance / path)
        for key, value in read_language(QUESTS / file).items():
            if not key.startswith(prefixes) or original.get(key) == value:
                continue
            if key in current and current[key] not in (value, original.get(key)):
                raise RuntimeError(f"Review the local translation in {locale}: {key}")
            current[key] = value
        payloads[path] = (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    install_payloads(instance, payloads, "campaign-refresh", BUILDS[0][-1], apply, module_versions=module_versions)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--blade-only", action="store_true", help="Only replace the growth sword module")
    args = parser.parse_args()
    main(args.apply, args.blade_only)
