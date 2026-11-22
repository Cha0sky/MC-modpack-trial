"""Back up and install the single-player encounter balance, crystal HUD and hostility progression."""
import argparse
import json
from pathlib import Path
import re
import tomllib
from zipfile import ZipFile

from deploy_campaign_refresh import INSTANCE, ROOT, mod_version
from deploy_world_turn import install_payloads
from hostility_growth_balance import payloads as hostility_payloads

VERSION = "0.9.0-balance"
TARGET = Path("mods/world-progression-core-0.1.0.jar")
WITCH_CONFIG = Path("config/touhou_little_maid_spell-common.toml")
TABLE = "data/world_progression_core/balance/encounters.json"


def witch_config(instance, solo_hp):
    raw = (instance / WITCH_CONFIG).read_bytes()
    old = tomllib.loads(raw.decode("utf-8-sig"))["winefox"]["winefoxMaxHealth"]
    if old == solo_hp:
        return raw
    if old != 600:
        raise ValueError(f"Unexpected default challenge health: {old}")
    text, count = re.subn(r"(?m)^([ \t]*winefoxMaxHealth[ \t]*=[ \t]*)[\d.]+(?=[ \t]*(?:#[^\r\n]*)?\r?$)",
                          lambda m: m[1] + str(float(solo_hp)), raw.decode("utf8"))
    if count != 1:
        raise ValueError("Expected one default challenge setting")
    return text.encode("utf8")


def main(apply):
    instance = INSTANCE.resolve(strict=True)
    if instance != INSTANCE: raise ValueError("Unexpected development instance")
    jar = ROOT / "modules/world-progression-core/build/libs" / f"world-progression-core-{VERSION}.jar"
    if mod_version(jar, "world_progression_core") != VERSION or mod_version(
            instance / TARGET, "world_progression_core") not in ("0.8.2-playtest", VERSION):
        raise ValueError("Unexpected progression core")
    with ZipFile(jar) as built:
        tiers = json.loads(built.read(TABLE))["tiers"]
    witch, = [row for tier in tiers for row in tier if row.get("nativeHealthConfig")]
    if witch["id"] != "touhou_little_maid_spell:stellar_witch":
        raise ValueError("Unexpected custom health owner")
    files = {TARGET: jar.read_bytes(), **hostility_payloads(instance),
             WITCH_CONFIG: witch_config(instance, witch["soloHp"])}
    install_payloads(instance, files, "encounter-balance", VERSION, apply,
                     module_versions={"world_progression_core": VERSION})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    main(parser.parse_args().apply)
