"""Install the approved starter kit, sword and compass fixes in the development instance."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib

from build_chapter1_quests import uid
from deploy_campaign_refresh import BOOK_PATH, INSTANCE, ROOT, mod_version, previous, read_language
from deploy_world_turn import install_payloads

BASELINE = "c912e00"
CORE = "0.8.2-playtest"
BLADE = "1.1.5-playtest"
ABILITIES = {
    "wave_range": (24.0, 48.0),
    "wave_cooldown_ticks": (160, 80),
    "wave_final_cooldown_ticks": (80, 40),
    "burst_radius": (6.0, 12.0),
    "burst_final_radius": (8.0, 16.0),
    "burst_cooldown_ticks": (400, 200),
    "burst_final_cooldown_ticks": (200, 100),
}


def blade_settings(path):
    original = path.read_bytes()
    config = tomllib.loads(original.decode("utf-8-sig"))["abilities"]
    text = original.decode("utf8")
    for key, (before, after) in ABILITIES.items():
        text = re.sub(rf"(?m)^([ \t]*# Default: ){re.escape(str(before))}(?=\r?\n[ \t]*# Range:[^\r\n]*\r?\n[ \t]*{key}[ \t]*=)",
                      lambda match: match[1] + str(after), text)
        if config[key] == after:
            continue
        if config[key] != before:
            raise ValueError(f"Locally modified ability setting: {key}={config[key]}")
        text, count = re.subn(rf"(?m)^([ \t]*{key}[ \t]*=[ \t]*)[\d.]+",
                              lambda match: match[1] + str(after), text)
        if count != 1:
            raise ValueError(f"Expected a single setting for {key}")
    for key in ("wave_final_pierce", "burst_max_targets"):
        text = re.sub(rf"(?m)^(?:[ \t]*# (?:Default|Range):[^\r\n]*\r?\n){{0,2}}[ \t]*{key}[ \t]*=[^\r\n]*\r?\n", "", text)
    return text.encode("utf8")


def prologue(path):
    # FTB rewrites JSON as comma-optional SNBT, omits defaults and numbers its
    # reward tables. Apply the two structural edits using its installed parser.
    moddev = ROOT / "modules/world-progression-core/build/moddev"
    libraries = (moddev / "serverLegacyClasspath.txt").read_text("utf8").splitlines()
    ftb, = (INSTANCE / "mods").glob("ftb-library-*.jar")
    sdk = moddev / "artifacts/neoforge-21.1.248.jar"
    java = Path("C:/Gradle/jdk-21-temurin/bin/java.exe")
    with tempfile.TemporaryDirectory() as tmp:
        result = Path(tmp) / "chapter.snbt"
        subprocess.run([str(java), "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(map(str, [ftb, sdk, *libraries])),
                        str(ROOT / "tools/UpdateStarterReward.java"), str(INSTANCE / path),
                        str(ROOT / "staging" / path), str(result), uid("arrival"), uid("workshop"), uid("workshop/network")],
                       check=True)
        return result.read_bytes()


def language(path):
    old = previous(path, BASELINE)
    revised = read_language(ROOT / "staging" / path)
    current = read_language(INSTANCE / path)
    for key, value in revised.items():
        if old.get(key) == value or current.get(key) == value:
            continue
        if key in current and current[key] != old.get(key):
            raise ValueError(f"Locally modified translation in {path}: {key}")
        current[key] = value
    return (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")


def main(apply):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE:
        raise ValueError("Unexpected development instance")
    payloads = {}
    for module, mod, filename, old, new in (
        ("world-progression-core", "world_progression_core", "world-progression-core-0.1.0.jar", "0.8.1-terrain", CORE),
        ("evolvingblade", "evolvingblade", "evolvingblade-1.1.0.jar", "1.1.4-longsword", BLADE),
    ):
        path = Path("mods") / filename
        built = ROOT / "modules" / module / "build/libs" / f"{module}-{new}.jar"
        if mod_version(instance / path, mod) not in (old, new) or mod_version(built, mod) != new:
            raise ValueError(f"Unexpected version for {module}")
        payloads[path] = built.read_bytes()
    config = Path("config/evolvingblade-server.toml")
    payloads[config] = blade_settings(instance / config)
    chapter = BOOK_PATH / "chapters/00_lostworld_chapter1.snbt"
    payloads[chapter] = prologue(chapter)
    for locale in ("en_us", "zh_cn"):
        path = BOOK_PATH / f"lang/{locale}.snbt"
        payloads[path] = language(path)
    install_payloads(instance, payloads, "starter-sword-compass", CORE, apply,
                     module_versions={"world_progression_core": CORE, "evolvingblade": BLADE})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    main(parser.parse_args().apply)
