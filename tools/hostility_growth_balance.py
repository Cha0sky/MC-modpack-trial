"""Prepare the L2 Hostility 3.0.18 config changes for the balance release."""
from pathlib import Path
import re
import tomllib


CONFIG = Path("config/l2configs/l2hostility-server.toml")


def payloads(instance):
    original = (instance / CONFIG).read_bytes()
    text = original.decode("utf8")
    config = tomllib.loads(text.lstrip("\ufeff"))
    # Player and chunk-section kill XP use the same native level threshold.
    # Distance is an independent rounded bonus on horizontal distance from (0,0).
    for section, key, old, new in (
        ("difficulty", "killsPerLevel", 30, 120),
        ("scaling", "distanceFactor", 0.003, 0.00075),
    ):
        if config[section][key] == new:
            continue
        if config[section][key] != old:
            raise ValueError(f"Unexpected baseline for {CONFIG}: [{section}] {key}={config[section][key]}")
        text, count = re.subn(rf"(?m)^([ \t]*{key}[ \t]*=[ \t]*)[\d.eE+-]+(?=[ \t]*(?:#[^\r\n]*)?\r?$)",
                              lambda m: m[1] + str(new), text)
        if count != 1:
            raise ValueError(f"Expected one setting for [{section}] {key}")
    return {CONFIG: text.encode("utf8")}
