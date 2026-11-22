"""Inputs shared by the accessory atlas and the per-mod guide readers."""
from pathlib import Path

import equipment_enigmatic_hearts
import equipment_relic_expansion

READERS = (equipment_enigmatic_hearts, equipment_relic_expansion)
SHELVES = {
    "enigmaticlegacyplus": "神秘遗物 · 咒戒与术石",
    "relics": "Relics · 探索与养成",
    "celestial_artifacts": "星月遗物 · 材料与进阶",
    "bhc": "生命护符 · 心罐与装填",
}


SHELF_TEXT = {
    SHELVES["enigmaticlegacyplus"]: "想追非欧立方，先找术石台，留好途中拿到的术石和残片。七咒之戒另有佩戴代价，打开戒指页后再决定是否戴上。",
    SHELVES["relics"]: "先按单件页指引去对应群系或维度开箱。把得到的遗物放进自己的背包，悬停其上，按住「研究光标下的遗物」，连起星座解锁能力。能力页记录本件遗物如何取得经验，主动能力菜单可在按键设置中查找。",
    SHELVES["celestial_artifacts"]: "从传家项链查起，想做哪件进阶装备，就打开成品页向左追原料。走刻蚀板路线前先查触发条件，有的需要佩戴灾厄之册，以特定方式击败敌人。",
    SHELVES["bhc"]: "先做空心罐和红色心罐，再逐级升级。手持生命护符右击，把心罐装进同色槽，再戴好护符。佩戴提供的生命与已经使用的生命水晶分别计算。",
}


def mod_jars(instance, extra_mods=None):
    files = {p.name: p for p in (instance / "mods").glob("*.jar")}
    if extra_mods:
        files.update((p.name, p) for p in Path(extra_mods).glob("*.jar"))
    return [files[name] for name in sorted(files)]


def records(jars, kind):
    return [row for reader in READERS if (fetch := getattr(reader, kind, None)) for row in fetch(jars)]


def virtual_recipes(jars):
    recipes = {}
    for reader in READERS:
        if fetch := getattr(reader, "virtual_recipes", None):
            recipes.update(fetch(jars))
    return recipes
