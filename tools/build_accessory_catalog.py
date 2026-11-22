"""Inventory the installed Curios item tags and the pack's item-specific bauble registrations."""
from collections import defaultdict
from io import BytesIO
import argparse
import json
from pathlib import Path
import re
from zipfile import ZipFile
from accessory_mechanics import BUILDER_CLASSES, NOTES, configured_effects, registry_effects

ROOT = Path(__file__).resolve().parents[1]
INSTANCE = ROOT.parent.parent / ".minecraft/versions/星庭远征-开发版"
OUTPUT = ROOT / "docs/data/equipment/accessories.json"

SLOTS = {
    "accessory": "泰拉饰品", "back": "背部", "belt": "腰带", "body": "躯干",
    "bracelet": "手镯", "charm": "护符", "curio": "通用饰品", "feet": "足部",
    "hands": "手部", "head": "头部", "hostility_curse": "恶意诅咒", "light_pet": "照明宠物",
    "mount": "坐骑", "necklace": "项链", "pandora": "潘多拉", "pet": "宠物",
    "ring": "戒指", "rings": "指环", "spellbook": "法术书", "talisman": "护身符",
    "tarot": "塔罗牌", "waist": "腰部", "maid_bauble": "女仆饰品",
}

# BaubleManager.init and MaidBaubleRegistry.bindMaidBauble register these directly
# rather than defining item tags. Spell White List is registered as blue_note.
MAID_BINDINGS = {
    "touhou_little_maid": """
        drown_protect_bauble explosion_protect_bauble ultramarine_orb_elixir fall_protect_bauble
        fire_protect_bauble item_magnet_bauble magic_protect_bauble nimble_fabric
        projectile_protect_bauble mute_bauble wireless_io
    """,
    "minecraft": "totem_of_undying",
    "touhou_little_maid_spell": """
        spell_enhancement_core arc_cross bleeding_heart flow_core quick_chant_ring spring_ring
        double_heart_chain rock_crystal sliver_cercis blue_note hairpin transmog_necklace chaos_book
        soul_book ender_pocket floating_fox_leaf molten_fox_leaf wound_rime_blade anchor_core
        spell_overlimit_core spring_bloom_return fragrant_ingenuity dream_cat_crystal staranchor_pearl
    """,
    "goety": "unholy_hat",
}

# Read from Iron's 3.16.3 ItemRegistry; spell-school, mana and cooldown values are
# equipment modifiers. Their effects apply alongside the book's stored spells.
IRON_FACTS = {
    "wimpy_spell_book": "原始法术槽为 0。",
    "legendary_spell_book": "12 个法术槽。",
    "netherite_spell_book": "12 个法术槽，最大魔力 +200，冷却缩减 +20%。",
    "diamond_spell_book": "10 个法术槽，最大魔力 +100。",
    "gold_spell_book": "8 个法术槽，最大魔力 +50，施法耗时缩减 +15%。",
    "iron_spell_book": "6 个法术槽。",
    "copper_spell_book": "5 个法术槽。",
    "rotten_spell_book": "8 个法术槽，最大魔力 +100，法术抗性 −15%。",
    "blaze_spell_book": "10 个法术槽，最大魔力 +200，火焰法强 +10%。",
    "dragonskin_spell_book": "12 个法术槽，最大魔力 +200，末影法强 +10%。",
    "druidic_spell_book": "10 个法术槽，最大魔力 +200，自然法强 +10%。",
    "villager_spell_book": "10 个法术槽，最大魔力 +200，神圣法强 +10%，施法耗时缩减 +10%。",
    "ice_spell_book": "12 个法术槽，最大魔力 +200，冰霜法强 +10%。",
    "evoker_spell_book": "最大魔力 +200，唤魔法强 +10%。",
    "mana_ring": "最大魔力 +100。", "silver_ring": "最大魔力 +25。",
    "cooldown_ring": "法术冷却缩减 +15%。", "cast_time_ring": "施法耗时缩减 +15%。",
    "heavy_chain_necklace": "法术抗性 +15%。", "conjurers_talisman": "召唤物伤害 +15%。",
    "greater_conjurers_talisman": "召唤物伤害 +15%。", "amethyst_resonance_charm": "魔力再生 +15%。",
    "betrayer_signet": "邪术法强 +10%。",
}

SPECIAL = {
    "minecraft:totem_of_undying": "女仆饰品格也能装不死图腾，在致命伤害时触发。",
    "create:goggles": "查看 Create 设备的转速、应力和流体等仪表信息。",
    "goetydelight:doll_item": "佩戴在头部展示玩偶外观；切石机中的羊毛可用于取得玩偶。",
}

# Code-driven drops and trades with no normal recipe or resource loot entry.
# See ShadowOrbBlock / CrimsonHeartBlock, Crone.dropCustomDeathLoot,
# PriestEntity.BibleTrade, and the doll's cutting instructions.
SOURCES = {
    "confluence:shadow_orb": "在腐化地下击碎天然暗影珠，每次有 20% 概率掉落同名照明宠物饰品。",
    "confluence:crimson_heart": "在猩红地下击碎天然猩红之心，每次有 20% 概率掉落同名照明宠物饰品。",
    "goety:crone_hat": "击败诡厄巫婆可取得她的帽子。可在朽坏木屋用蓄能的水晶球邀战。",
    "irons_spellbooks:villager_spell_book": "向铁魔法牧师交易，交出已翻译的高位唤魔者日志。",
    "goetydelight:doll_item": "把羊毛放入切石机，选择玩偶。",
    "confluence:guide_voodoo_doll": "下界巫毒恶魔携带向导巫毒玩偶，击败后可拾取。",
}

DISPLAY_NAMES = {"minecraft:totem_of_undying": "不死图腾", "cataclysm:belt_of_monstrosity": "巨兽腰带",
                 "irons_spellbooks:legendary_spell_book": "传奇法术书"}

# Iron's 3.16.3 retains the model and an optional tag entry for this old
# item, but has no item registration for it.
UNREGISTERED = {"irons_spellbooks:enchanted_ward_amulet"}

def walk_jars(jar):
    # Bundled dependencies are loaded first so the parent pack's text and
    # data-map overrides take precedence over their library defaults.
    for file in jar.namelist():
        if file.startswith("META-INF/jarjar/") and file.endswith(".jar"):
            with ZipFile(BytesIO(jar.read(file))) as child:
                yield from walk_jars(child)
    yield jar


def clean(text):
    return re.sub(r"§[a-z0-9]", "", text, flags=re.IGNORECASE).strip()


def scan(instance, jars):
    tags, language, fallback, models, slot_ids = defaultdict(list), {}, {}, set(), set()
    classes, overlays = {}, defaultdict(dict)
    for path in jars:
        with ZipFile(path) as root:
            for jar in walk_jars(root):
                for file in jar.namelist():
                    if file in BUILDER_CLASSES:
                        classes[file] = jar.read(file)
                    if file == "data/terra_curio/data_maps/item/accessories.json":
                        for item, data in json.loads(jar.read(file))["values"].items():
                            if data.get("replace"): overlays[item].clear()
                            overlays[item].update(data.get("value", data))
                    tag = re.fullmatch(r"data/([^/]+)/tags/item/(.+)\.json", file)
                    if tag:
                        key = f"{tag[1]}:{tag[2]}"
                        data = json.loads(jar.read(file))
                        if data.get("replace"):
                            tags[key].clear()
                        tags[key].extend(data["values"])
                    slot = re.fullmatch(r"data/[^/]+/curios/slots/([^/]+)\.json", file)
                    if slot:
                        slot_ids.add(slot[1])
                    lang = re.fullmatch(r"assets/[^/]+/lang/(zh_cn|en_us)\.json", file)
                    if lang:
                        raw = json.loads(jar.read(file).decode("utf-8-sig"), strict=False)
                        (language if lang[1] == "zh_cn" else fallback).update(raw)
                    model = re.fullmatch(r"assets/([^/]+)/models/item/(.+)\.json", file)
                    if model:
                        models.add(f"{model[1]}:{model[2]}")
    return tags, language, fallback, models, slot_ids, classes, overlays


def tagged_items(key, tags, models, ancestors=()):
    if key in ancestors:
        raise ValueError(f"Circular item tag: {key}")
    for entry in tags.get(key, []):
        value = entry if isinstance(entry, str) else entry["id"]
        if value.startswith("#"):
            yield from tagged_items(value[1:], tags, models, (*ancestors, key))
        elif value not in UNREGISTERED and (isinstance(entry, str) or entry.get("required", True) or value in models):
            yield value


def effect_index(language):
    index = defaultdict(list)
    patterns = (r"tooltip\.item\.([\w]+)\.([\w]+)(?:\.\d+)?", r"info\.([\w]+)\.([\w]+)(?:\.\d+)?",
                r"(?:item|block)\.([\w]+)\.([\w]+?)[._](?:tip|desc|description)\d*(?:\.\d+)?",
                r"([\w]+)\.item\.equipment\.([\w]+)(?:\.\d+)?")
    for name in sorted(language):
        for pattern in patterns:
            match = re.fullmatch(pattern, name)
            if match:
                index[match[1] + ":" + match[2]].append(clean(language[name]))
                break
    return index


def effects_for(item, index, class_effects, configured):
    namespace, path = item.split(":")
    effects = []
    known = SPECIAL.get(item) or (IRON_FACTS.get(path) if namespace == "irons_spellbooks" else None)
    if known:
        effects.append(known)
    for text in (NOTES.get(namespace, {}).get(path), configured.get(item)):
        if text:
            effects.append(text)
    effects.extend(class_effects.get(item, ()))
    for value in index[item] + (index["maidspell:" + path][1:] if namespace == "touhou_little_maid_spell" else []):
        if value and not re.search(r"%(?:\d+\$)?[sdf]", value) and value not in effects:
            effects.append(value.replace("%%", "%"))
    return effects


def build(instance, decompiler=None, extra_mods=None):
    from equipment_expansions import mod_jars, records
    jars = mod_jars(instance, extra_mods)
    additions = {v["id"]: v for v in records(jars, "accessory_records")}
    tags, language, fallback, models, slot_ids, classes, overlays = scan(instance, jars)
    class_effects = registry_effects(classes, overlays, ROOT, decompiler)
    items = defaultdict(set)
    for slot in sorted(slot_ids):
        for item in tagged_items("curios:" + slot, tags, models):
            items[item].add(slot)
    for namespace, paths in MAID_BINDINGS.items():
        for path in paths.split():
            items[namespace + ":" + path].add("maid_bauble")
    # Custom Curios validator goetydelight:doll_item checks DollEntityItem.
    items["goetydelight:doll_item"].add("head")
    out = []
    index = effect_index(language)
    configured = configured_effects(instance)
    for item in sorted(items.keys() | additions.keys()):
        if item in additions:
            out.append(additions[item])
            continue
        slots = items[item]
        namespace, path = item.split(":")
        langkeys = ["item." + namespace + "." + path, "block." + namespace + "." + path]
        name = next((source[key] for source in (language, fallback) for key in langkeys if key in source), None)
        name = DISPLAY_NAMES.get(item, name)
        if not name:
            raise ValueError("Missing registered item name: " + item)
        record = {"id": item, "name": clean(name), "slots": [SLOTS[s] for s in sorted(slots)],
                  "effects": list(dict.fromkeys(effects_for(item, index, class_effects, configured)))}
        if item in SOURCES:
            record["source"] = [SOURCES[item]]
        out.append(record)
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", type=Path, default=INSTANCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--decompiler", type=Path, help="Vineflower JAR for the installed Terra accessory registries")
    parser.add_argument("--extra-mods", type=Path)
    args = parser.parse_args()
    rows = build(args.instance, args.decompiler, args.extra_mods)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(f"{len(rows)} accessories")


if __name__ == "__main__":
    main()
