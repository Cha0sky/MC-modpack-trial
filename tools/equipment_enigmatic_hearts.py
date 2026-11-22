"""Guide data for the 1.21.1 releases of Enigmatic Legacy+ and Baubley Heart Canisters."""
import json
from zipfile import ZipFile

from equipment_sources import mod_files


EL = "enigmaticlegacyplus"
BHC = "bhc"
SLOTS = {"amulet": "护身符", "back": "背部", "charm": "护符", "ring": "戒指",
         "scroll": "卷轴", "spellstone": "术石", "belt": "腰带", "heart_amulet": "生命护符"}
COLORS = {"red": "红", "yellow": "黄", "green": "绿", "blue": "蓝"}
AMULETS = {
    "red": ("红", "攻击伤害 +2。"), "aqua": ("青", "疾跑速度 +15%。"),
    "violet": ("紫", "有 15% 几率偏转弹射物。"), "magenta": ("品红", "重力降低 20%，安全下落距离增加 1 格。"),
    "green": ("绿", "挖掘效率 +2。"), "black": ("黑", "获得 10% 生命窃取。"), "blue": ("蓝", "游泳速度 +25%。"),
}

# Effects are condensed from the 1.1.2 tooltip, Curios callbacks and item interactions.
EFFECTS = {
    "angel_blessing": "在空中发动定向冲刺；可偏转弹射物，免疫摔落和挤压。承受虚空和凋零的代价更高。",
    "ascension_amulet": "同时获得七色神秘护身符的加成，并增加 1 个护符槽。",
    "avarice_scroll": "增加时运，村民交易打六五折，让猪灵保持中立。击杀生物有 15% 几率额外掉落一颗绿宝石。",
    "berserk_emblem": "已损失的生命比例越高，攻击、行动速度和抗性加成越强。",
    "blazing_core": "免疫火焰，可短时间抵挡熔岩伤害，受击时点燃攻击者。水生生物对你造成更多伤害。",
    "chaos_elytra": "滑翔时按住跳跃加速，消耗耐久。高速冲撞可以攻击敌人。",
    "cosmic_scroll": "致命伤害触发时恢复至半血并取得增益，此后进入冷却。对未被选召的玩家有额外近战威胁。",
    "creation_heart": "装备或放在物品栏中即可获得不朽。装备时能飞行并发动凋灵骷髅头弹幕。",
    "cursed_ring": "接受七项诅咒以换取抢夺、时运、特殊掉落等奖励。常规操作无法摘下，戴前请查看完整诅咒。",
    "cursed_scroll": "身上装备的诅咒附魔会强化卷轴的战斗加成。",
    "cursed_xp_scroll": "可存取经验，等级越高能力越强。经验球收益降低，死亡会失去全部经验。",
    "desolation_ring": "敌对生物不再主动锁定你，附近未被激怒的生物可以代死。击杀生物也会失去掉落物。",
    "dimness_charm": "需承受七咒达到一定时间比例才能佩戴。它放宽深渊物品的七咒时间要求，并减轻七咒对护甲与输出的惩罚。",
    "dislocation_ring": "立即收集范围内的掉落物，可用潜行切换拾取状态。",
    "earth_promise": "当一次伤害达到当前生命值的一定比例时，完全挡下这次伤害，随后进入冷却。",
    "eldritch_amulet": "持续注视的生物会虚弱、减速。死亡时保留物品栏，经验和灵魂水晶仍会丢失。",
    "enchanter_pearl": "附魔台无需青金石，附魔更容易出现高品质结果；额外增加一个护符槽。",
    "ender_ring": "按随身末影箱键打开自己的末影箱，便于远征途中存取物资。",
    "enigmatic_eye": "手持休眠之眼右击，将它唤醒为莫测之眼。佩戴后增加一个护符槽，还能识破迷人徽章，显示玩家真名。",
    "escape_scroll": "死亡时把物品送至床或重生锚旁；未设置重生位置时送至世界出生点。卷轴自身留在原地。",
    "ethereal_forging_charm": "提高携带物品保住耐久的机会，改善锻造产物属性。",
    "etherium_core": "受击时以以太护盾抵挡伤害，为后续反击蓄势，并提供爆炸防护。",
    "explorer_scroll": "探测附近的藏宝位置，探索新结构时可以先查看提示。",
    "eye_of_nebula": "可瞬移至敌人身后，有机会闪避伤害，提升魔法战斗能力。接触水会受伤。",
    "fabulous_scroll": "消耗经验可自由飞行，信标范围内免除飞行的经验开销。",
    "forger_crystal": "锻造时铁砧不会损坏，经验花费减半。两件相同且耐久全满的工具可打造成不可破坏的工具，用作不死图腾。",
    "forger_gem": "铁砧经验花费减半，铁砧与锻造产物都更耐用。",
    "forgotten_ice": "冰上行走、抵抗冻结，并冻结攻击者。主动能力能冻结周围，火焰是其弱点。",
    "golden_ring": "猪灵会把佩戴者当作穿戴金器的旅人，维持中立。",
    "golem_heart": "提高护甲与抗击退能力；空着护甲栏时另获护甲、韧性与爆炸抗性，魔法伤害仍是弱点。",
    "heaven_scroll": "在信标范围内消耗经验飞行，建设高处时也能维持挖掘速度。",
    "hell_blade_charm": "当一次攻击足以削去目标一定比例的血量时，追加伤害并回复自身生命。",
    "hunter_scroll": "根据猎杀记录获得抗性。面对不同猎物时，留意卷轴上的记录。",
    "ichor_charm": "免疫灵液侵蚀，并能利用敌人已有的状态效果施加灵液。",
    "illusion_lantern": "召出灵魂火焰攻击敌人并在邻近目标间分摊伤害；强光会带来额外危险。",
    "infernal_ring": "帮助保留烈焰巨力的力量，在满血时提供减伤。",
    "insignia": "佩戴后可用别名隐藏真名。已觉醒的神秘之眼能识破伪装。",
    "iron_ring": "佩戴时增加 1 点护甲。",
    "lost_engine": "物品冷却缩短 25%，免疫摔落和爆炸；佩戴时要躲避雷击。",
    "magnet_ring": "吸引附近的掉落物，在需要保持掉落物原位时可按住潜行。",
    "majestic_elytra": "滑翔中按住跳跃可以加速，飞行加速消耗耐久。",
    "miner_ring": "消耗燃料把采掘掉落物直接熔炼，适合长时间下矿。",
    "mining_charm": "可切换地下夜视，在没有照明的巷道里辨认矿脉。",
    "monster_charm": "提高对亡灵生物的伤害，击杀怪物获得双倍经验。",
    "night_scroll": "阻止幻翼自然生成，部分战斗属性随周围的明暗改变。",
    "ocean_stone": "赋予水下呼吸并强化游泳，主动能力可消耗经验改变天气。",
    "quartz_ring": "护甲 +2、幸运 +1.5，受到的魔法伤害默认减少 25%。",
    "redemption_amulet": "攻击蓄力能提供减伤，未被你攻击的目标无法伤你；另获幸运、韧性与一个护符槽。",
    "redemption_ring": "持续回复生命，增强输出与抗性，并保留额外抢夺、时运和独特掉落。",
    "revival_leaf": "主动能力消耗经验治疗周围生物。植被能为佩戴者提供空中机动。",
    "scorched_charm": "免疫火焰灼伤，可在熔岩上行走，攻击燃烧目标时汲取生命。",
    "spelltuner": "在物品栏内对术石右击进行调律，得到对应效果并提高寻找术石的机会。",
    "starlight_ring": "提高流星出现几率，并可使相关装备将当前位置视为信标范围内。",
    "survivor_scroll": "在亮处获得恢复，生命较低时还有额外的生存属性。",
    "the_cube": "疾跑、游泳、攻击速度、挖掘、时运和幸运均获提升。生命高于 1 点时可挡住致命一击并留 1 点。主动能力会将你送到三大维度之一的随机地点。",
    "thunder_scroll": "免疫闪电，改善横扫攻击，多次命中后可以引雷。",
    "unwitnessed_amulet": "手持右击会显现七种颜色中的一种，并刻下使用者名字。颜色决定附加属性。",
    "violence_scroll": "吸收诅咒附魔的力量，缩短目标受击后的无敌时间。",
    "void_pearl": "免去呼吸需求，攻击附加凋零。靠近你的生物在黑暗中会受到伤害，并有机会抵挡致命伤害。",
    "xp_scroll": "无限量存取经验。手持时潜行右击启停，右击在吸收与释放模式间切换。",
}

PLACEHOLDER = {"extra_scroll": "扩展卷轴（占位）", "blessing_scroll": "祝福卷轴（占位）"}
SPECIAL_NAMES = {"enigmatic_eye": "休眠之眼／莫测之眼", **PLACEHOLDER}
# EnigmaticHandler.canUse checks the cursed, blessed and eldritch item components.
CURSED_ONLY = {"avarice_scroll", "berserk_emblem", "cursed_scroll", "cursed_xp_scroll", "enchanter_pearl", "night_scroll"}
BLESSED_ALTERNATIVE = {"redemption_amulet", "forger_crystal", "scorched_charm", "earth_promise", "thunder_scroll"}
ELDRITCH = {"chaos_elytra", "desolation_ring", "eldritch_amulet", "violence_scroll"}
EL_SOURCE = {
    "unwitnessed_amulet": "模组开启初始装备时，首次登录可取得无主护身符。",
    "cursed_ring": "模组开启初始装备时，首次登录会收到七咒之戒。",
    "creation_heart": "创造模式物品，可在创造物品栏取得。",
    "enigmatic_eye": "每名玩家第一次打开尚未生成战利品的非空藏宝箱时，可在箱中找到休眠之眼。",
    "hell_blade_charm": "在下界保持半血以下攻击敌人，积累隐藏的战斗分数。超过 100 分后打开尚未生成战利品的下界藏宝箱，可获得一枚。",
    "illusion_lantern": "下界灵魂沙峡谷中，玩家挖掘灵魂土也有 0.6% 几率发现它。",
}


def jar_for(jars, namespace):
    return mod_files(tuple(jars)).get(namespace)


def expand(jar, item):
    if not item.startswith("#"):
        return [item]
    namespace, key = item[1:].split(":", 1)
    values = json.loads(jar.read(f"data/{namespace}/tags/item/{key}.json"))["values"]
    return [item_id for value in values for item_id in expand(jar, value)]


def curio_slots(jar):
    slots = {}
    for path in jar.namelist():
        if not path.startswith("data/curios/tags/item/") or not path.endswith(".json"):
            continue
        slot = SLOTS[path.rsplit("/", 1)[1][:-5]]
        for value in json.loads(jar.read(path))["values"]:
            for item in expand(jar, value):
                slots.setdefault(item, []).append(slot)
    return slots


def bhc_wearable(key):
    if key.endswith("_heartpulse_belt"):
        color = key.split("_", 1)[0]
        level = list(COLORS).index(color) + 1
        return COLORS[color] + "色心跳腰带", [
            f"移动速度 +{12 * level}%，跳跃强度 +{0.08 * level:.2f}，跨步高度 +0.4 格。跌落距离不超过 6 格时免受摔落伤害。"]
    common = "主手持护符右击打开，按颜色装入心罐，再放入专用生命护符槽。每罐增加 2 点最大生命。受伤后摘下护符会保留已损失的血量，重伤时先治疗。"
    if key == "heart_amulet":
        return "生命护符", [common, "红、黄、绿、蓝各可装 10 罐，默认最多增加 80 点生命。"]
    if key == "soul_heart_amulet":
        return "灵魂生命护符", [common, "第 5 格可装灵魂心罐，五种各装 10 罐共增加 100 点生命。致死时消耗一罐灵魂心罐，回满生命、清除原有效果，并获得再生、伤害吸收和抗火。默认返还一罐蓝色心罐。", "本版升级配方会把原护符中已装入的心罐转移到成品。"]
    raise ValueError(key)


def accessory_records(jars):
    result = []
    for namespace in (EL, BHC):
        if not (path := jar_for(jars, namespace)):
            continue
        with ZipFile(path) as jar:
            lang = json.loads(jar.read(f"assets/{EL}/lang/zh_cn.json")) if namespace == EL else {}
            for item, slots in sorted(curio_slots(jar).items()):
                key = item.split(":")[1]
                source = EL_SOURCE.get(key)
                if namespace == BHC:
                    name, effects = bhc_wearable(key)
                elif key.startswith("enigmatic_amulet_"):
                    color, effect = AMULETS[key.removeprefix("enigmatic_amulet_")]
                    name = f"{lang[f'item.{EL}.enigmatic_amulet']}（{color}）"
                    effects = [effect, "死亡时以超维容器保存物品、经验与灵魂水晶，返回容器旁即可取回。"]
                elif key in PLACEHOLDER:
                    name = PLACEHOLDER[key]
                    effects = ["此版本注册为占位物品，尚无卷轴能力。"]
                    source = "本版未提供可确认的普通生存获取途径。"
                else:
                    name = SPECIAL_NAMES[key] if key in SPECIAL_NAMES else lang[f"item.{EL}.{key}"]
                    effects = [EFFECTS[key]]
                    if key in CURSED_ONLY:
                        effects.append("需承受七咒之戒的诅咒方可使用。")
                    elif key in BLESSED_ALTERNATIVE:
                        effects.append("需承受七咒，或佩戴至少 5 级的救赎之戒方可使用。")
                    elif key in ELDRITCH:
                        effects.append("深渊遗物要求佩戴七咒之戒，且承受诅咒的时间占游玩时间默认至少 99.5%。")
                row = {"id": item, "name": name, "slots": slots, "effects": effects}
                if source:
                    row["source"] = [source]
                result.append(row)
    return result


def armor_records(jars):
    path = jar_for(jars, EL)
    if not path:
        return []
    with ZipFile(path) as jar:
        lang = json.loads(jar.read(f"assets/{EL}/lang/zh_cn.json"))
    result = []
    for key, slot, defense in (("etherium_helmet", "head", 4), ("etherium_chestplate", "chest", 9),
                               ("etherium_leggings", "legs", 7), ("etherium_boots", "feet", 4),
                               ("majestic_elytra", "chest", 0), ("chaos_elytra", "chest", 0)):
        result.append({"id": f"{EL}:{key}", "name": lang[f"item.{EL}.{key}"], "slot": slot, "armor": defense,
                       "effect": EFFECTS.get(key, "每件额外增加 8% 以太护盾属性。")})
    return result


def progression_records(jars):
    result = []
    if jar_for(jars, BHC):
        def heart(key, name, effect, source=None):
            row = {"id": f"bhc:{key}", "name": name, "effects": [effect]}
            if source:
                row["source"] = [source]
            result.append(row)

        heart("canister", "空心罐", "四级心罐的基础容器。先制成红色心罐，再逐级升级。")
        heart("relic_apple", "遗物苹果", "用于装配红色心罐。")
        heart("wither_bone", "凋零骨", "用于装配红色心罐。", "凋灵骷髅默认有 18% 几率掉落。")
        heart("soul_heart_crystal", "灵魂之心水晶", "用于把生命护符升级到灵魂级，也是生命系剑和弓的材料。")
        heart("soul_heart_canister", "灵魂心罐", "装入灵魂生命护符的第 5 格。每罐增加 2 点生命，触发保命时消耗一罐。")
        sources = ("普通敌对怪物死亡时默认有 15% 几率掉落，监守者也可触发。", "带 NeoForge 首领标签的生物（末影龙除外）死亡时默认掉落。",
                   "末影龙死亡时默认掉落。", "监守者死亡时默认掉落。")
        for i, (color, title) in enumerate(COLORS.items()):
            heart(f"{color}_heart", f"微型{title}心", f"用于制作{title}色心罐及同色治疗用品。", sources[i])
            heart(f"{color}_heart_melted", f"熔融{title}心", "熔炼微型心可得。八份同色熔心围住一枚微型心，可合成该颜色的微型心；相邻颜色之间还能按九比一换算。")
            heart(f"{color}_heart_canister", f"{title}色心罐", "把它装进生命护符内的同色格。每罐提供 2 点生命，默认每色可装 10 罐。")
            heart(f"{color}_heart_patch", f"{title}色治疗贴", f"使用后恢复 {(2, 6, 10, 20)[i]} 点生命，默认冷却 {(5, 10, 20, 30)[i]} 秒。")
        for key, name, effect in (
                ("blade_of_vitality", "生命之刃", "主手持剑，潜行右击打开装填界面，每装入一个心罐增加 1 点主手攻击伤害。"),
                ("vigor_bow", "活力弓", "主手持弓，潜行左击打开装填界面，每装入两罐增加 1 点箭矢基础伤害，并能加快拉弓。")):
            result.append({"id": f"bhc:{key}", "name": name, "label": "手持武器",
                           "effects": [effect, "武器使用普通工作台配方，制作前先取出灵魂生命护符里存放的心罐。"]})
    if path := jar_for(jars, EL):
        with ZipFile(path) as jar:
            lang = json.loads(jar.read(f"assets/{EL}/lang/zh_cn.json"))
        for key, description in {
                "spellstone_table": "编织术石时，在核心槽放一枚术质核心，碎片槽放足术石残片，另七格放配方物品。取出成品会消耗核心。",
                "spellstone_debris": "编织术石时单独消耗的材料，不同术石所需数量不同。",
                "cosmic_heart": "与六种各不相同的候选术石一同用于编织非欧立方。",
                "spellcore": "每次在术石台编织一件术石都要消耗一枚，放入独立的核心槽。",
                "blank_scroll": "多种功能卷轴共用的底材。", "darkest_scroll": "用于制作深渊卷轴。",
                "bless_stone": "用于将佩戴的七咒之戒转化为救赎之戒。",
                "starlight_pearl": "在物品栏中拿起宝珠，对以太核心或飘渺锻符右击，消耗一颗宝珠升级为星光核心或星光锻符。",
            }.items():
            name = lang.get(f"item.{EL}.{key}") or lang[f"block.{EL}.{key}"]
            row = {"id": f"{EL}:{key}", "name": name, "effects": [description]}
            if key == "spellstone_debris":
                row["source"] = ["沙漠神殿、沙漠水井、海底废墟或古迹废墟中的考古也可能刷出术石残片。"]
            result.append(row)
    return result


def virtual_recipes(jars):
    result = {}
    if jar_for(jars, EL):
        for color in AMULETS:
            result[f"{EL}:enigmatic_amulet_{color}"] = [
                ("显现 · 手持无主护身符右击，随机得到七色之一。", {f"{EL}:unwitnessed_amulet"}, "显现")]
        result[f"{EL}:redemption_ring"] = [
            ("救赎 · 佩戴七咒之戒，手持神圣之石经历死亡，戒指随之转化。极限模式手持神圣之石右击即可。",
             {f"{EL}:cursed_ring", f"{EL}:bless_stone"}, "救赎")]
        for item in ("etherium_core", "ethereal_forging_charm"):
            result[f"{EL}:{item}"] = [
                ("强化 · 在物品栏拿起星辰宝珠，对已经制成的本件饰品右击，消耗宝珠强化原物。", {f"{EL}:starlight_pearl"}, "强化")]
    if jar_for(jars, BHC):
        for amulet, colors in (("heart_amulet", list(COLORS)), ("soul_heart_amulet", [*COLORS, "soul"])):
            parts = {f"bhc:{color}_heart_canister" for color in colors}
            result[f"bhc:{amulet}"] = [("装填 · 打开护符，将已制成的心罐放入同色槽。各种颜色可独立装入，也能取回。", parts, "装填")]
    return result
