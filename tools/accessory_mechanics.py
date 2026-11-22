"""Numeric and class-bound accessory behavior in the installed 1.21.1 pack."""
from collections import defaultdict
from pathlib import Path
import re
import subprocess
from tempfile import TemporaryDirectory
from zipfile import ZipFile

BUILDER_CLASSES = {
    "org/confluence/terra_curio/common/init/TCItems.class": "terra_curio",
    "org/confluence/mod/common/init/item/AccessoryItems.class": "confluence",
}
ATTRIBUTES = {
    "knockback_resistance":"击退抗性", "armor":"护甲", "dodge_chance":"闪避率", "attack_speed":"攻击速度",
    "attack_damage":"近战伤害", "block_break_speed":"挖掘速度", "critical_chance":"暴击率",
    "ranged_damage":"远程伤害", "magic_damage":"魔法伤害", "movement_speed":"移动速度", "jump_strength":"跳跃力度",
    "submerged_mining_speed":"水下挖掘速度", "attack_knockback":"攻击击退", "entity_interaction_range":"实体交互距离",
    "aggro":"仇恨", "armor_penetration":"护甲穿透", "ranged_velocity":"远程弹速",
    "block_interaction_range":"方块交互距离", "pickup_range":"拾取距离", "safe_fall_distance":"安全掉落距离",
    "luck":"幸运", "fall_damage_multiplier":"掉落伤害倍率", "swim_speed":"游泳速度", "summon_damage":"召唤伤害",
    "sentry_capacity":"哨兵上限", "summon_knockback":"召唤物击退", "minion_capacity":"仆从上限",
    "critical_damage":"暴击伤害", "crit_chance":"暴击率", "step_height":"跨步高度", "generic_armor_penetration":"护甲穿透", "max_mana":"魔力上限",
    "magic_critical_chance":"魔法暴击率", "ranged_critical_chance":"远程暴击率",
}


def translate_attr(name, value, operation):
    name = name.split(":")[-1].split(".")[-1]
    if name.startswith("get") and name[3].isupper():
        name = re.sub(r"(?<!^)(?=[A-Z])", "_", name[3:])
    name = name.lower()
    unit = "%"
    if operation.lower() == "add_value" and name not in ("critical_chance", "crit_chance", "dodge_chance"):
        unit = ""
    number = value * 100 if unit else value
    return f"{ATTRIBUTES.get(name, name)} {number:+g}{unit}"


def registry_effects(classes, overrides, root, decompiler=None):
    """Read the two builder registries, then apply the accessory data map overlays."""
    if not decompiler:
        location = root / "work/build-baseline/gradle-user-home/caches/modules-2/files-2.1/org.vineflower/vineflower"
        decompiler, = location.glob("*/*/vineflower-*.jar")
    result = defaultdict(list)
    modifiers = {}
    with TemporaryDirectory(dir=root / "work") as folder:
        temp = Path(folder)
        with ZipFile(temp / "registry.jar", "w") as jar:
            for name, data in classes.items():
                jar.writestr(name, data)
        java = Path("C:/Gradle/jdk-21-temurin/bin/java.exe")
        subprocess.run([str(java) if java.exists() else "java", "-jar", str(decompiler), "--log-level=ERROR",
                        str(temp / "registry.jar"), str(temp)], check=True)
        for name, namespace in BUILDER_CLASSES.items():
            text = (temp / name.replace(".class", ".java")).read_text("utf8")
            for definition in re.split(r"(?=   public static final )", text):
                header = definition.split(";")[0]
                item = re.search(r'= register\w*\(\s*"([^"]+)"', header)
                if not item:
                    continue
                key = namespace + ":" + item[1]
                pairs = re.findall(r"\.attribute\((?:\w+\.)?(\w+)(?:\(\))?,\s*([-+\d.]+)[DF]?,\s*Operation\.([A-Z_]+)\)", header)
                modifiers[key] = [translate_attr(attr, float(v), op) for attr, v, op in pairs]
                wings = re.search(r'registerWings\(\s*"[^"]+",\s*ModRarity\.\w+,\s*([\d.]+)F,\s*(\d+),\s*(true|false),\s*(true|false)', header)
                if wings:
                    movement = f"飞行速度系数 {wings[1]}，动力飞行 {int(wings[2])/20:g} 秒"
                    if wings[3] == "true": movement += "，可滑翔"
                    if wings[4] == "true": movement += "，支持水平飞行"
                    result[key].append(movement + "。")
    for item, values in overrides.items():
        listed = values.get("terra_curio:attributes")
        if listed is not None:
            modifiers[item] = [translate_attr(attr, x["amount"], x["operation"]) for attr, mods in listed.items()
                               for x in mods]
        if "terra_curio:fishing_power" in values:
            result[item].append(f"渔力 +{values['terra_curio:fishing_power']:g}。")
        if values.get("terra_curio:effect_immunities"):
            result[item].append("免疫" + "、".join(EFFECTS[e.split(":")[-1]] for e in values["terra_curio:effect_immunities"]) + "。")
    for item, lines in modifiers.items():
        if lines:
            result[item].append("、".join(lines) + "。")
    return result


EFFECTS = {"poison":"中毒", "blindness":"失明", "slowness":"缓慢", "weakness":"虚弱",
           "bleeding":"流血", "broken_armor":"破甲", "confused":"困惑", "cursed":"诅咒",
           "silenced":"沉默", "stoned":"石化", "frozen":"冻结"}

# Supplemental mechanics from item classes, Patchouli entries and slot validators
# where the standard item tooltip is absent or receives runtime format arguments.
NOTES = {
    "beyonddimensions": {
        "net_feeder_item": "从绑定的维度网络向玩家提供食物。",
        "net_magnet_item": "将范围内的掉落实体收进绑定网络，收集距离可切换。",
        "net_restocker_item": "按已设定的补货项目从网络补充随身物资。",
        "net_terminal_item": "打开所连接的维度网络，存取其中物品。",
        "xp_exchange_item": "通过绑定的网络在玩家经验和网络存储之间转存。",
    },
    "cataclysm": {
        "aptrgangr_head": "冥行武弁头颅外观，可戴在头部。",
        "draugr_head": "再行魂尸头颅外观，可戴在头部。",
        "kobolediator_skull": "骸龙斗士头骨外观，可戴在头部。",
        "belt_of_beginner": "提供 2 个额外护身符槽。",
        "belt_of_monstrosity": "提供 2 个额外护身符槽。",
        "berserker_soul_amulet": "攻击伤害 +10%，护甲 −25%。",
        "chitin_claw": "实体交互距离 +0.25 格，方块交互距离 +1 格。",
        "ring_of_grudged": "额外暴击伤害属性 +10。",
        "vitality_ankh": "自然治疗属性 +20。",
    },
    "confluence": {
        "guide_voodoo_doll":"佩戴后可伤害向导。长按使用可切换血肉墙与血肉山形态，在地狱把玩偶投入岩浆能引发相应的首领战。",
        "clothier_voodoo_doll":"佩戴后可攻击服装商，在夜间亲手杀死他能再次召出骷髅王。",
        "boredoms_pact_falling_resolve":"移动会累积计数，原地站立后计数每秒减少，归零时会在佩戴者上方召出落石。",
        "mana_flower":"魔力不足时会自动使用携带的魔力药水。",
        "crimson_heart":"装备后召出随行的猩红之心，作为照明宠物。",
        "shadow_orb":"装备后召出随行的暗影珠，作为照明宠物。",
        "magic_lantern":"装备后召出随行的魔法灯笼，作为照明宠物。",
    },
    "goety": {
        "brew_bag":"保存喷溅型药酿，可用药酿包界面或轮盘快速换取。",
        "crone_hat":"普通药剂使用后有 25% 几率保留，药酿为 10%。",
        "dark_robe_fancy":"与黑暗长袍同类，可降低施法消耗的灵魂能量。",
        "eternal_cauldron":"可背负的坩埚，带独立物品栏和操作界面。",
        "feline_amulet":"让附近的苦力怕和幻翼像避开猫一样避开佩戴者。",
        "focus_bag":"可收纳 10 个聚晶，持魔杖时可通过聚晶轮盘换取。",
        "focus_pack":"可收纳 20 个聚晶，适合需要多套法术的施法者。",
        "frost_robe":"霜冻法术的灵魂消耗减半。能穿过细雪并抵御冻结。",
        "geo_robe":"占卜法术的灵魂消耗减半。",
        "grave_glove":"镰刀攻击速度 +50%。",
        "illusion_robe_mirror":"幻术长袍的外观变体，强化幻象法术。",
        "nameless_cape":"死灵长袍的强化形态，允许亡灵仆从用主人灵魂能量疗伤。无名套移除原套装的日晒负面效果。",
        "nameless_crown":"死灵王冠的强化形态，支持亡灵仆从长期存在。无名套移除原套装的日晒负面效果。",
        "nether_robe":"降低火焰伤害，下界法术灵魂消耗减半，允许下界仆从消耗灵魂能量自愈。",
        "ominous_charm":"存下佩戴者的不祥之兆，之后可用饰品按键释放。",
        "pendant_of_hunger":"吸纳物品栏的腐肉，在饥饿值下降时自动喂食。腐肉仍可能引发饥饿。",
        "ring_of_thirst":"自己或自己的仆从杀死生物后，直接取得掉落的经验。",
        "sea_amulet":"在激活的潮涌核心 8 格内充能，潜水时消耗储能维持潮涌能量。",
        "soul_healer":"用储存的灵魂能量治疗自身。给灾厄村民仆从后，它会在低血量时自动使用。",
        "storm_robe":"减缓下落并抵御雷击，风暴法术的灵魂消耗减半。",
        "thrash_glove":"锤类武器的攻击速度 +50%。",
        "totem_of_roots":"收集和储存灵魂能量，可用作诡厄施法的灵魂储备。",
        "totem_of_souls":"储存灵魂能量，容量高于根之图腾。",
        "unholy_hat":"提供火焰保护，使下界仆从能够长期存在。与不洁长袍配合可将部分火焰法术转为狱火。",
        "void_robe":"有几率瞬移闪避，免疫虚空侵蚀。虚空法术的灵魂消耗减半。",
        "warlock_robe":"提高魔法与爆炸防护，配合术士腰带可提升蜘蛛的卵掉落。",
        "warlock_sash":"配合术士长袍，提高蜘蛛掉落蜘蛛卵的机会。",
        "witch_hat_hedge":"女巫帽的外观变体，可加快女巫长袍内部的药酿制作。",
        "witch_robe":"提高魔法抗性，带可操作的内部酿造台。",
    },
    "irons_spellbooks": {
        "affinity_ring":"储存特定法术的亲和信息，装备后强化戒指所标示的法术。空戒尚未绑定法术。",
        "cursed_doll_spell_book":"10 个法术槽，最大魔力 +200，鲜血法强和法术抗性各 +10%。",
        "necronomicon_spell_book":"合计 10 个法术槽，最大魔力 +200。预置猩红斩击、血步、汲取射线和烈焰风暴，并提升唤起亡灵的亲和。",
        "lurker_ring":"佩戴者隐身时的攻击可额外造成 50% 伤害，触发冷却 15 秒。",
    },
    "l2complements": {
        "eternal_totem_of_dream":"幻梦图腾的可重用版本，触发后进入冷却。",
        "totem_of_dream":"致命伤害触发时恢复满血并回到出生点，同时获得指向原地点的易碎传送石。",
        "totem_of_the_sea":"在水中、雨中或气泡柱内受到通常的致命伤害时发挥图腾效果。",
    },
    "l2hostility":{
        "detector_glasses":"配合恶意探测器查看已净化的区段。",
        "oddeyes_glasses":"帮助发现隐身生物，也能在失明或黑暗中识别怪物。",
        "infinity_glove":"提供 5 个额外戒指槽和 1 个额外护符槽。",
        "triple_strip_cape":"提供 3 个额外背部槽。",
    },
    "pandora":{"pandora_bracelet":"可串上 9 个潘多拉串饰，持有时可右键编辑。",
               "pandora_necklace":"可串上 18 个潘多拉串饰，持有时可右键编辑。"},
    "terra_curio":{"step_stool":"佩戴步凳后可借助临时垫脚高度触及高处。"},
    "terra_entity":{"chester_staff":"召唤宠物切斯特，可随身带着它行动。",
                   "wallet":"召唤随行的宠物扑满。","slimy_saddle":"从坐骑栏召唤史莱姆坐骑。",
                   "honeyed_goggles":"从坐骑栏召唤蜜蜂坐骑。"},
    "touhou_little_maid":{"ultramarine_orb_elixir":"女仆携带时在致命伤害后提供额外生命，消耗饰品耐久。"},
}
for source, alt in (("frost_robe","frost_robe_cryo"), ("nether_robe","nether_robe_warped"),
                    ("unholy_hat","unholy_hat_halo"), ("witch_robe","witch_robe_hedge"),
                    ("warlock_robe","warlock_robe_dark")):
    NOTES["goety"][alt] = NOTES["goety"][source]
for index in range(13):
    key = f"plushie_{index}" if index else "plushie"
    NOTES["goety"][key] = "可放置，也可作为头部外观展示的毛绒玩偶。"
for index in range(1, 5):
    NOTES["l2hostility"][f"charm_of_looting_{index}"] = f"第 {index} 阶掠夺串饰。击败符合对应词条与等级条件的敌人时，可取得指定额外掉落。"


def configured_effects(instance):
    import tomllib
    config = instance / "config"
    get = lambda relative: tomllib.loads((config / relative).read_text("utf-8-sig"))
    tarot = get("tarotcards/tarotcards.toml")
    t = tarot["cards"]
    # The card tooltips use effect amplifier + 1 and display percentage factors ×100.
    def pct(value): return f"{100 * value:g}%"

    cards = {
        "death": f"对生物造成的伤害 +{pct(t['death_damagebonus'])}。",
        "judgement": f"有 {pct(t['judgement_damagechance'])} 几率打出双倍伤害。",
        "justice": f"将所受伤害的 {pct(t['justice_damagemultiplier'])} 反弹给攻击者。",
        "strength": f"获得力量 {t['strength_amplifier'] + 1} 级。",
        "temperance": f"饥饿消耗减少 {pct(t['temperance_reduction'])}。",
        "the_chariot": f"移动速度 +{pct(t['the_chariot_speedboost'])}。",
        "the_emperor": f"获得村庄英雄 {t['the_emperpor_heroofvillagebonus'] + 1} 级。",
        "the_empress": f"作用范围 {t['the_empress_range']:g} 格，在附近照料动物与幼崽。",
        "the_fool": f"获得跳跃提升 {t['the_fool_jumpboost'] + 1} 级。",
        "the_hanged_man": f"将所受伤害的 {pct(t['the_hanged_man_xpratio'])} 转成经验，每次上限 {t['the_hanged_man_xpcap']}。",
        "the_hermit": f"在 {t['the_hermit_allyrange']:g} 格内没有盟友时，护甲 +{t['the_hermit_armorbonus']:g}。",
        "the_hierophant": f"获得经验值时额外增加 {pct(t['the_hierophant_xpboost'])}。",
        "the_lovers": f"为 {t['the_lovers_range']:g} 格内的盟友提供生命恢复 {t['the_lovers_regenamplifier']+1} 级。",
        "the_star": f"基础方块触及距离 +{pct(t['the_star_reachboost'])}。",
        "the_sun": f"基础最大生命值 +{pct(t['the_sun_healthboost'])}。",
        "the_tower": f"摔落伤害减少 {pct(t['the_tower_damagenegation'])}。",
        "the_world": f"对 {t['the_world_range']:g} 格内的生物施加缓慢 {t['the_world_slownessamplifier']+1} 级。",
        "wheel_of_fortune": f"幸运 +{t['wheel_of_fortune_luckbonus']:g}。",
        "tarot_deck": f"容纳 {tarot['tarot_deck_size']} 张牌。" + ("牌组内卡牌效果也会生效。" if tarot["tarot_deck_applies_effects"] else ""),
    }
    h = get("l2configs/l2hostility-server.toml")["items"]
    l2 = {
        "curse_of_envy": f"击杀带词条的怪物时有机会取得其词条，每阶概率 {pct(h['envyDropRate'])}。",
        "curse_of_gluttony": f"击杀怪物时，每级提供 {pct(h['gluttonyBottleDropRate'])} 瓶装恶意掉率。",
        "curse_of_greed": f"击杀怪物的词条物品掉落率 ×{h['greedDropFactor']:g}。",
        "curse_of_pride": f"每级玩家难度使生命上限 +{pct(h['prideHealthBonus'])}、伤害 +{pct(h['prideDamageBonus'])}。",
        "curse_of_wrath": f"攻击等级高于自己的怪物时，每级等级差增加 {pct(h['wrathDamageBonus'])} 伤害。",
        "flaming_thorn": f"击中怪物时施加 {h['flameThornTime']/20:g} 秒魂火，等级取决于目标当前效果种数。",
        "greed_of_nidhoggur": f"击杀怪物时，每级怪物等级为其掉落物数量增加 {pct(h['nidhoggurDropFactor'])}。",
        "ring_of_corrosion": f"攻击时消耗目标装备 {pct(h['ringOfCorrosionFactor'])} 最大耐久，受击时自己装备损失 {pct(h['ringOfCorrosionPenalty'])} 最大耐久。",
        "ring_of_healing": f"每秒回复 {pct(h['ringOfHealingRate'])} 最大生命值。",
        "ring_of_life": f"单次损失生命上限为自身最大生命的 {pct(h['ringOfLifeMaxDamage'])}。",
    }
    b = get("touhou_little_maid_spell-common.toml")["baubles"]
    healing, defense, util, special = b["healing"], b["defense"], b["utility"], b["special"]
    maid = {
        "bleeding_heart": f"女仆造成伤害时按 {pct(healing['bleedingHeartHealRatio'])} 伤害量回复女仆和主人。",
        "chaos_book": f"将伤害拆为 {special['chaosBookDamageSplitCount']} 段，每段至少 {special['chaosBookMinSplitDamage']:g} 点。命中额外造成目标最大生命 {pct(special['chaosBookTrueDamagePercent'])} 的真实伤害，至少 {special['chaosBookTrueDamageMin']:g} 点。",
        "double_heart_chain": f"相距 {defense['doubleHeartChainMaxDistance']:g} 格内，主人与女仆分担伤害，分担比例 {pct(defense['doubleHeartChainShareRatio'])}。",
        "flow_core": f"每 {util['flowCoreTickInterval']/20:g} 秒按每级好感回复 {pct(healing['flowCoreHealthRegenRate'])} 最大生命；每级好感的最终减伤为 {pct(defense['flowCoreDamageReduction'])}。",
        "quick_chant_ring": f"女仆每级好感减少 {pct(util['quickChantRingCooldownReduction'])} 法术冷却。",
        "rock_crystal": f"女仆击退抗性 +{util['rockCrystalKnockbackResistance']:g}。",
        "spring_ring": f"随女仆损失的生命比例增加伤害，最多 +{pct(b['damage']['springRingMaxDamageBonus'])}。",
        "spring_bloom_return": f"施法获得花返，最多 {b['springBloomReturnMaxStacks']} 层，持续 {b['springBloomReturnStackDurationTicks']/20:g} 秒。受击触发后消耗一层，回复 {pct(b['springBloomReturnHealRatio'])} 最大生命，返还 {pct(b['springBloomReturnCooldownRefundRatio'])} 剩余法术冷却。",
    }
    return {f"{ns}:{key}": value for ns, group in (("tarotcards", cards),("l2hostility", l2),("touhou_little_maid_spell", maid))
            for key, value in group.items()}
