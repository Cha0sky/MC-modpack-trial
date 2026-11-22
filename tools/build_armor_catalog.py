"""Build the 1.21.1 armour handbook snapshot from the installed pack, without starting Minecraft.

The two largest registries use generated names and multiple independent helmet materials.
Read their actual register calls. For the smaller families the values below are transcribed
from the named version's material/type tables. Require all tagged armour items to resolve.
"""
import argparse
import io
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile
import tomllib
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INSTANCE = ROOT.parent.parent / ".minecraft/versions/星庭远征-开发版"
OUTPUT = ROOT / "docs/data/equipment/armor.json"
SLOTS = {"HELMET": "head", "CHESTPLATE": "chest", "LEGGINGS": "legs", "BOOTS": "feet"}
PIECES = ("helmet", "chestplate", "leggings", "boots")
CONFLUENCE = "org/confluence/mod/common/init"
IRON = "io/redspace/ironsspellbooks"
CLASSES = {
    f"{CONFLUENCE}/armor/ModArmorMaterials", f"{CONFLUENCE}/item/ArmorItems",
    f"{CONFLUENCE}/item/VanityArmorItems", f"{IRON}/registries/ItemRegistry",
    "com/Vivideru/Goety/common/items/VivideruItems", "com/Polarice3/Goety/common/items/ModItems",
}


def read_resources(instance, jars):
    labels, tags, classes = {}, {}, {}
    def read_jar(jar):
        for path in jar.namelist():
            if path.startswith("META-INF/jarjar/") and path.endswith(".jar"):
                with ZipFile(io.BytesIO(jar.read(path))) as child:
                    read_jar(child)
            elif path.endswith("/lang/zh_cn.json"):
                labels.update(json.loads(jar.read(path).decode("utf-8-sig"), strict=False))
            elif match := re.fullmatch(r"data/([^/]+)/tags/item/(.+)\.json", path):
                key = ":".join(match.groups())
                # Only equipment-slot tags and their armour-family children are used below.
                if any(fragment in key for fragment in ("armor", "helmets", "boots", "leggings", "chestplates")):
                    try:
                        data = json.loads(jar.read(path))
                    except json.JSONDecodeError:
                        continue
                    tags.setdefault(key, []).extend(data["values"])
            elif path.removesuffix(".class") in CLASSES and path.endswith(".class"):
                classes[path] = jar.read(path)
    for path in jars:
        with ZipFile(path) as jar:
            read_jar(jar)
    # Vanilla translations are in the launcher's asset index.
    minecraft = instance.parent.parent
    manifest = json.loads((instance / (instance.name + ".json")).read_text("utf-8-sig"))
    index = json.loads((minecraft / f"assets/indexes/{manifest['assetIndex']['id']}.json").read_bytes())
    blob = index["objects"]["minecraft/lang/zh_cn.json"]["hash"]
    labels.update(json.loads((minecraft / "assets/objects" / blob[:2] / blob).read_bytes()))
    return labels, tags, classes


def decode_registries(classes, destination, java, vineflower):
    archive = destination / "registries.jar"
    with ZipFile(archive, "w") as jar:
        for name, code in classes.items():
            jar.writestr(name, code)
    subprocess.run([str(java), "-Xmx1G", "-jar", str(vineflower), "--log-level=ERROR",
                    "--thread-count=2", str(archive), str(destination)], check=True)


def records(source, labels, tags, config, extra=()):
    result = {}
    translations = {
        "irons_spellbooks:wizard_hat": "巫师宽檐帽",
        "goety:cursed_metal_wolf_armor": "诅咒金属狼铠", "goety:dark_wolf_armor": "黑暗狼铠",
        "goety:black_beast_cursed_armor": "暗兽诅咒铠甲", "goety:black_beast_dark_armor": "暗兽黑暗铠甲",
        "goety:warg_cursed_armor": "座狼诅咒铠甲", "goety:warg_dark_armor": "座狼黑暗铠甲",
    }
    for stem, title in (("possessed_armor", "附魔盔甲"), ("wraith_armor", "幻灵盔甲")):
        for piece, suffix in zip(PIECES, ("头盔", "胸甲", "护腿", "靴子")):
            translations[f"terra_entity:{stem}_{piece}"] = title + "·" + suffix

    def add(item, slot, armor, note=None):
        key = item.replace(":", ".")
        name = labels.get("item." + key) or labels.get("block." + key) or translations.get(item)
        if not name:
            raise ValueError(f"Missing name for {item}")
        if item in result:
            raise ValueError(f"Duplicate item {item}")
        result[item] = {"id": item, "name": name, "slot": slot, "armor": armor}
        if note:
            result[item]["effect"] = note

    def family(prefix, values, note=None):
        for piece, armor in zip(PIECES, values, strict=True):
            add(f"{prefix}_{piece}", SLOTS[piece.upper()], armor, note)

    def code(location):
        return (source / (location + ".java")).read_text("utf8")

    # Numbers follow head / chest / legs / feet, the order of Minecraft's ArmourItem.Type.
    vanilla = {"leather": (1, 3, 2, 1), "chainmail": (2, 5, 4, 1), "iron": (2, 6, 5, 2),
               "golden": (2, 5, 3, 1), "diamond": (3, 8, 6, 3), "netherite": (3, 8, 6, 3)}
    for family_name, values in vanilla.items():
        family("minecraft:" + family_name, values)
    add("minecraft:turtle_helmet", "head", 2, "离开水面可获得水下呼吸。")
    add("minecraft:elytra", "chest", 0, "从高处下落时展开滑翔。")
    for name, value in (("leather", 3), ("iron", 5), ("golden", 7), ("diamond", 11)):
        add(f"minecraft:{name}_horse_armor", "body", value, "装备给驯服的马。")
    add("minecraft:wolf_armor", "body", 11, "供驯服的狼穿戴；多数伤害优先消耗狼铠耐久。")

    material = code(CONFLUENCE + "/armor/ModArmorMaterials")
    full_pattern = r"(\w+) = registerArmorMaterial\(\s*\"[^\"]+\",\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)"
    mats = {key: dict(zip(SLOTS, map(int, values))) for key, *values in re.findall(full_pattern, material)}
    for key, kind, value in re.findall(r'(\w+) = registerSingletonMaterial\(\s*"[^"]+",\s*Type\.(\w+),\s*(\d+)', material):
        mats[key] = {kind: int(value)}
    armor_items = code(CONFLUENCE + "/item/ArmorItems")
    pattern = r'\bregister\(\s*"(\w+)"\s*,\s*ModArmorMaterials\.(\w+),\s*Type\.(\w+),(.*?)\);'
    bonuses = {"fourClassesDamage": "四职业伤害", "meleeDamage": "近战伤害", "magicDamage": "魔法伤害",
               "rangedDamage": "远程伤害", "summonDamage": "召唤伤害", "criticalChance": "暴击率"}
    attributes = {"ATTACK_SPEED": "攻击速度", "BLOCK_BREAK_SPEED": "挖掘速度", "MOVEMENT_SPEED": "移动速度",
                  "SWIM_SPEED": "游泳速度", "MINION_CAPACITY": "仆从容量", "WHIP_RANGE": "鞭子范围"}
    for name, mat, kind, builder in re.findall(pattern, armor_items, re.S):
        details = []
        for method, number in re.findall(r'\.(\w+)\((-?[\d.]+)\)', builder):
            if method in bonuses:
                details.append(f"{bonuses[method]} {float(number) * 100:+g}%")
            elif method == "additionalMana":
                details.append(f"魔力上限 +{number}")
        for attribute, value, operation in re.findall(r'\.attribute\(\w+\.(\w+),\s*(-?[\d.]+),\s*Operation\.(\w+)\)', builder):
            unit = f"{float(value):+g}" if operation == "ADD_VALUE" else f"{float(value) * 100:+g}%"
            details.append(f"{attributes[attribute]} {unit}")
        for i in range(int(match[1]) if (match := re.search(r"\.tooltips\((\d+)\)", builder)) else 0):
            line = labels.get(f"tooltip.item.confluence.{name}.{i}")
            if line and not re.search(r"%\d*\$?s|§", line):
                details.append(line.rstrip("。"))
        if '.requiresModLoaded("sodiumdynamiclights")' in builder:
            details.append("照明需要 Sodium Dynamic Lights")
        add("confluence:" + name, SLOTS[kind], mats[mat][kind], "。".join(dict.fromkeys(details)) or None)
    vanity = code(CONFLUENCE + "/item/VanityArmorItems")
    for method, name, kind in re.findall(r'= (register(?:Attribute)?VanityArmor)\(\s*"(\w+)"[^;]*?Type\.(\w+)', vanity):
        if method == "registerAttributeVanityArmor":
            assert name == "dead_mans_seater" and re.search(r"Attributes\.ARMOR,\s*4.0,\s*Operation.ADD_VALUE", vanity)
            add("confluence:" + name, SLOTS[kind], 4, "可作时装，实际穿入护甲槽时提供 4 点护甲。")
        else:
            add("confluence:" + name, SLOTS[kind], 0, "汇流时装，单件基础护甲为 0。")
    # TEArmors and ArmorSuit explicitly register two extra four-piece sets.
    family("terra_entity:possessed_armor", (3, 6, 6, 3))
    family("terra_entity:wraith_armor", (3, 5, 5, 3))
    add("terra_curio:diving_helmet", "head", 2, "溺水伤害降为原来的六分之一。")

    iron_items = code(IRON + "/registries/ItemRegistry")
    iron_pattern = r'registerItem\(\s*"(\w+)",\s*properties -> new (\w+ArmorItem)\(\s*Type\.(\w+)'
    # Per-piece spells/bonuses come from the constructors and ExtendedArmorItem.schoolAttributes.
    schools = {"Archevoker": "唤魔", "Cultist": "鲜血", "Cryomancer": "冰霜", "Electromancer": "雷电",
               "Plagued": "自然", "Priest": "神圣", "Pyromancer": "火焰", "Shadowwalker": "末影"}
    mage_notes = {
        "WanderingMagician": "魔力上限 +25。", "Pumpkin": "魔力上限 +75。",
        "NetheriteMage": "魔力上限 +125，法术强度 +5%。", "Wizard": "魔力上限 +125，法术强度 +5%。",
        "Paladin": "魔力上限 +150，法术强度 +10%。", "InfernalSorcerer": "魔力上限 +150，法术强度 +10%。",
        "BootsOfSpeed": "魔力上限 +150，法术强度 +10%，施法移动速度 +60%，移动速度 +25%。",
        "TarnishedCrown": "魔力上限 +150，魔力恢复 +25%，攻击伤害 −15%。",
        "GoldCrown": "魔力上限 +9900，冷却缩减 +90%。",
    }
    # ArmorMaterialRegistry.schoolArmorMap() and its named specializations all use 3/8/6/3.
    for name, clazz, kind in re.findall(iron_pattern, iron_items):
        values = (3, 8, 6, 3)
        if clazz == "WanderingMagicianArmorItem":
            values = (2, 6, 5, 2)
        elif clazz == "TarnishedCrownArmorItem":
            values = (0, 0, 0, 0)
        elif clazz == "GoldCrownArmorItem":
            values = (20, 20, 20, 20)
        family_name = clazz.removesuffix("ArmorItem")
        note = (f"魔力上限 +125，法术强度 +5%，{schools[family_name]}法术强度 +10%。"
                if family_name in schools else mage_notes[family_name])
        add("irons_spellbooks:" + name, SLOTS[kind], dict(zip(SLOTS, values))[kind], note)

    cat = tomllib.loads((config / "cataclysm-common.toml").read_text("utf-8-sig"))["tools_and_abilities"]
    def scaled(group, value):
        return math.floor(value * cat[group]["armor_config"]["armor_multiplier"] + 0.5)
    # Armortier and the default component modifiers installed by ModItems.
    for stem, values in (("ignitium", (6, 11, 9, 6)), ("cursium", (5, 10, 8, 5))):
        family("cataclysm:" + stem, tuple(scaled(stem + "_armor", v) for v in values))
    add("cataclysm:ignitium_elytra_chestplate", "chest", scaled("ignitium_armor", 11), "结合鞘翅与焰魔胸甲。")
    add("cataclysm:monstrous_helm", "head", 3)
    add("cataclysm:bone_reptile_helmet", "head", scaled("bone_reptile_armor", 6))
    add("cataclysm:bone_reptile_chestplate", "chest", scaled("bone_reptile_armor", 11))
    add("cataclysm:bloom_stone_pauldrons", "chest", scaled("bloom_stone_armor", 9))

    # Goety 3.2.0 constructs these holders from ModArmorMaterials. Some similarly
    # named config fields (e.g. CursedPaladinChest=8) are unused by the 1.21.1 item.
    for family_name, values in (("cursed_knight", (2, 6, 5, 2)), ("cursed_paladin", (3, 7, 6, 3)),
                                ("black_iron", (2, 6, 5, 2)), ("dark", (3, 8, 6, 3))):
        family("goety:" + family_name, values)
    add("goety:malefic_helm", "head", 2)
    goety = code("com/Polarice3/Goety/common/items/ModItems")
    for name, beast, value in re.findall(r'ITEMS\.register\(\s*"(\w+)",\s*\(\) -> new (Ravager|Trampler)ArmorItem\((\d+)', goety):
        add("goety:" + name, "body", int(value), {"Ravager": "劫掠兽用兽甲。", "Trampler": "践踏者用兽甲。"}[beast])
    pets = code("com/Vivideru/Goety/common/items/VivideruItems")
    pet_armor = {"CursedMetalWolf": (13, "狼"), "DarkWolf": (20, "狼"), "CursedBlackBeast": (15, "暗兽"),
                 "DarkBlackBeast": (20, "暗兽"), "CursedWarg": (15, "座狼"), "DarkWarg": (20, "座狼")}
    for name, clazz in re.findall(r'ITEMS\.register\(\s*"(\w+)",\s*(\w+)ArmorItem::new', pets):
        value, wearer = pet_armor[clazz]
        add("goety:" + name, "body", value, f"供{wearer}穿戴。")

    # MaterialHandler takes the first three head pieces from vanilla gold, leather and iron.
    mowzie = tomllib.loads((config / "mowziesmobs-common.toml").read_text("utf-8-sig"))["tools_and_abilities"]
    def mow(name, slot, value, family_name):
        mul = mowzie[family_name]["armor_config"]["damage_reduction_multiplier"]
        add("mowziesmobs:" + name, slot, int(value * mul), None)
    mow("wrought_helmet", "head", 2, "wrought_helm")
    mow("sol_visage", "head", 2, "sol_visage")
    for mask in ("fury", "fear", "rage", "bliss", "misery", "faith"):
        mow("umvuthana_mask_" + mask, "head", 1, "umvuthana_mask")
    for part, slot, value in zip(("beads", "robe", "belt", "sandals"), SLOTS.values(), (2, 7, 6, 2)):
        mow("geomancer_" + part, slot, value, "geomancerArmor")

    for stem, values in (("totemic_gold", (2, 6, 5, 2)), ("poseidite", (3, 8, 6, 3)),
                         ("shulkerate", (3, 8, 6, 3)), ("sculkium", (6, 10, 9, 5)), ("eternium", (3, 8, 6, 3))):
        family("l2complements:" + stem, values)
    family("avaritia:infinity", (6, 16, 12, 6))
    family("create:cardboard", (1, 1, 1, 1))
    for stem, values in (("copper", (2, 4, 1)), ("netherite", (3, 8, 3))):
        for part, slot, value in zip(("diving_helmet", "backtank", "diving_boots"), ("head", "chest", "feet"), values):
            add("create:" + stem + "_" + part, slot, value)
    add("create:goggles", "head", 0, "工程师护目镜可在头部装备槽读取机器数据。")
    add("aeronautics:aviators_goggles", "head", 1)
    for part, slot, value in (("straw_hat", "head", 3), ("straw_hat_flower", "head", 3),
                              ("farmer_chest_plate", "chest", 4), ("farmer_leggings", "legs", 5), ("farmer_boots", "feet", 2)):
        add("kaleidoscope_cookery:" + part, slot, value)
    add("touhou_little_maid_spell:star_witch_hat", "head", 3, "魔力上限 +200，法术强度 +15%，冷却缩减 +10%。")

    # These introductions are implemented in code rather than a Recipe or LootTable.
    # Source classes: LivingEntityEvents, DeathChestBlock.BEntity, ModVillagers,
    # AbstractHauntedArmor, Heresiarch, NecromancerEntity, InitialArmors and WinefoxTrades.
    for piece in PIECES:
        result["goety:cursed_knight_" + piece]["source"] = "击败穿这套甲的缠魂甲胄，每个护甲槽的基础掉落率为 5%。"
        for family_name, mob in (("possessed_armor", "装甲幻影魔"), ("wraith_armor", "幻灵")):
            result[f"terra_entity:{family_name}_{piece}"]["source"] = f"讨伐穿戴此套装备的{mob}，从其装备掉落取得。"
    result["goety:malefic_helm"]["source"] = "击败异教牧首，其专属死亡掉落会生成恶骸骨盔。"
    result["irons_spellbooks:tarnished_helmet"]["source"] = "击败头戴这顶王冠的亡灵术士，头盔槽基础掉落率为 15%。"
    result["irons_spellbooks:gold_crown"]["source"] = "铁魔法的开发用王冠，本版本未查到常规生存配方或掉落。"
    result["touhou_little_maid_spell:star_witch_hat"]["source"] = "在星途终岸赢下可解锁装备交易的星之魔女切磋。随后用 32 颗绿宝石向她购买，每日限 1 件。"
    result["confluence:dead_mans_seater"]["source"] = "开启自然生成、尚未结算战利品的死人木箱或死人金箱，留意周围机关。"
    for piece in ("rain_cap", "raincoat"):
        result["confluence:" + piece]["source"] = "在非冰雪群系的雨中寻找雨衣僵尸，击败后有机会取得它穿着的这件装备。"
    for dark_pink in ("", "pink_"):
        for piece in ("snow_caps", "snow_suits", "insulated_pants", "insulated_shoes"):
            data = result["confluence:" + dark_pink + piece]
            chance = "冰冻僵尸有 1% 机会穿粉色款。" if dark_pink else ""
            trade = "3 级交易，基价 4" if data["slot"] in ("head", "chest") else "4 级交易，基价 5"
            data["source"] = f"去冰雪群系击败穿着这件装备的冰冻僵尸。{chance}也可向天空型皮匠购买（{trade} 颗绿宝石）。"
    for piece in PIECES:
        result["confluence:mining_" + piece]["source"] = "在看不见天空的地下寻找不死矿工。普通骷髅在这类位置生成时有 1% 机会装备全套矿工装。"

    result.update((row["id"], row) for row in extra)

    def resolve(tag, visited):
        if tag in visited:
            return set()
        values = set()
        for value in tags.get(tag, []):
            item = value if isinstance(value, str) else value["id"]
            if item.startswith("#"):
                values.update(resolve(item[1:], visited | {tag}))
            else:
                values.add(item)
        return values
    for tag, slot in (("head", "head"), ("chest", "chest"), ("leg", "legs"), ("foot", "feet")):
        for item in resolve(f"minecraft:{tag}_armor", set()):
            if item not in result or result[item]["slot"] != slot:
                raise ValueError(f"Unresolved armour tag member {item} ({slot})")
    return sorted(result.values(), key=lambda item: item["id"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--java", type=Path, default=Path("C:/Gradle/jdk-21-temurin/bin/java.exe"))
    parser.add_argument("--vineflower", type=Path)
    parser.add_argument("--extra-mods", type=Path)
    args = parser.parse_args()
    vf = args.vineflower or next((ROOT / "work/build-baseline/gradle-user-home/caches/modules-2/files-2.1/org.vineflower/vineflower")
                                  .glob("*/*/vineflower-*.jar"))
    from equipment_expansions import mod_jars, records as expansion_records
    jars = mod_jars(args.instance, args.extra_mods)
    labels, tags, classes = read_resources(args.instance, jars)
    with tempfile.TemporaryDirectory() as temporary:
        source = Path(temporary)
        decode_registries(classes, source, args.java, vf)
        armor = records(source, labels, tags, args.instance / "config", expansion_records(jars, "armor_records"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(armor, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(f"{len(armor)} armor items written to {args.output}")


if __name__ == "__main__":
    main()
