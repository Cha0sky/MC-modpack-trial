"""Install the collection tasks and the pack's FTB/Enigmatic integration in one transaction."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

from deploy_campaign_refresh import BOOK_PATH, INSTANCE, ROOT, mod_version, previous, read_language
from deploy_world_turn import install_payloads

BASELINE = "84a2cf3"
JAR = "stargarden-pack-compat-1.0.0.jar"


def chapter_changes():
    sdk = ROOT / "modules/world-progression-core/build/moddev"
    libraries = (sdk / "serverLegacyClasspath.txt").read_text("utf8").splitlines()
    ftb, = (INSTANCE / "mods").glob("ftb-library-*.jar")
    cp = os.pathsep.join(map(str, [ftb, sdk / "artifacts/neoforge-21.1.248.jar", *libraries]))
    chapters = BOOK_PATH / "chapters"
    with TemporaryDirectory() as tmp:
        subprocess.run(["C:/Gradle/jdk-21-temurin/bin/java.exe", "-Dfile.encoding=UTF-8", "-cp", cp,
                        str(ROOT / "tools/UpdateEquipmentTasks.java"), str(INSTANCE / chapters),
                        str(ROOT / "staging" / chapters), tmp], check=True)
        return {chapters / f.name: f.read_bytes() for f in Path(tmp).glob("*.snbt")}


def main(apply):
    updates = chapter_changes()
    for locale in ("zh_cn", "en_us"):
        rel = BOOK_PATH / "lang" / (locale + ".snbt")
        old, revised, current = previous(rel, BASELINE), read_language(ROOT / "staging" / rel), read_language(INSTANCE / rel)
        changed = False
        for key, value in revised.items():
            if value == old.get(key) or current.get(key) == value: continue
            if key in current and current[key] != old.get(key):
                raise ValueError(f"Review local translation {locale} {key}")
            current[key] = value
            changed = True
        if changed: updates[rel] = (json.dumps(current, ensure_ascii=False, indent=2) + "\n").encode("utf8")
    relative = Path("mods") / JAR
    built = ROOT / "modules/pack-compat/build/libs" / JAR
    if mod_version(built, "stargarden_pack_compat") != "1.0.0":
        raise ValueError(f"Unexpected mod metadata in {built}")
    content = built.read_bytes()
    if not (INSTANCE / relative).exists() or (INSTANCE / relative).read_bytes() != content:
        updates[relative] = content
    if updates:
        install_payloads(INSTANCE, updates, "equipment-collection", None, apply,
                         module_versions={"stargarden_pack_compat": "1.0.0"})
    else:
        print("Equipment collection tasks and compat mod already installed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    main(parser.parse_args().apply)
