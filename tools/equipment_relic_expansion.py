"""Player-facing catalogue for Relics 0.10.7.8 and Celestial Artifacts 2.0.4.

Slot membership, translated names, and celestial chest drops are read from the
actual distributions. Notes describe the runtime item abilities and loot rules
that do not appear in standard recipe / item data.
"""

import json
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile

from equipment_sources import mod_files


SLOTS = {
    "back": "背部", "belt": "腰带", "body": "身体", "bracelet": "手镯",
    "catastrophe": "灾厄书槽", "charm": "护符", "etching": "刻蚀板",
    "feet": "足部", "hands": "手部", "head": "头部", "heart": "心脏",
    "necklace": "项链", "pendant": "吊坠", "ring": "戒指", "scroll": "卷轴",
}

# RelicData/LootEntries chooses a chest by dimension, biome or table-name
# patterns. These labels give examples matched by the actual patterns.
CHEST_GROUPS = {
    "end": "末地结构箱，或主世界要塞等名称含 stronghold 的结构箱",
    "nether": "下界结构箱，也可寻找废弃传送门宝箱",
    "aquatic": "海洋、海岸或河流群系中的结构箱",
    "overworld": "主世界的结构箱",
    "tropic": "丛林、竹林或热带雨林群系中的结构箱",
    "mountain": "山峰、高地或丘陵群系中的结构箱",
    "snow": "冰原、雪地或冰川群系中的结构箱",
    "mountain_snow": "针叶林、冰雪或山地群系中的结构箱",
    "desert": "沙漠或恶地群系中的结构箱",
    "bastion": "堡垒遗迹等猪灵主题的结构箱",
    "village": "村庄或名称含 pillage 的结构箱",
    "cave": "废弃矿井等矿道箱，或洞穴群系中的结构箱",
}

# Registered RelicItem instances, including the four hand-operated relics.
RELICS = {
    "midnight_robe": ("end", "暗处隐身并加速。隐身中的首次攻击额外增伤。离开目标脚下的圆圈后才能再次潜伏。"),
    "elytra_booster": ("end", "在背包中用燃料给推进器充能，鞘翅飞行时发动能力便可消耗充能向前加速。"),
    "drowned_belt": ("aquatic", "扩展遗物槽位，增加下沉速度与水下输出。解锁激流能力后，晴天陆地上也能发动激流三叉戟。"),
    "hunter_belt": ("overworld", "扩展遗物槽位。驯养的宠物随训练能力提高伤害。"),
    "leather_belt": ("overworld", "增加可装备遗物的槽位数，具体增量见这件腰带的能力页。"),
    "spore_sack": ("tropic", "生命跌破一半会释放追踪孢子，按已损失生命造成伤害并阻止目标治疗 5 秒。回到半血以上可重新触发。"),
    "shadow_glaive": ("end", "造成伤害时有机会放出弹射飞刃，在 16 格内的目标间跳转。解锁分裂能力后，弹射还有机会复制飞刃。"),
    "magma_walker": ("nether", "可短时行走在熔岩表面，超时过热会持续受伤。耐热能力还能抵消岩浆块伤害。"),
    "aqua_walker": ("aquatic", "允许短时踩水过河。鞋底渗水后会拖着你下沉，长距离涉水要给自己留好落脚点。"),
    "ice_skates": ("snow", "在冰面连续滑行会逐步加速。撞击目标时，伤害随滑行时间提高。"),
    "ice_breaker": ("mountain_snow", "提高下落速度和击退抗性。下坠时发动能力，着地冲击波将击退周围目标，威力取决于落差。"),
    "roller_skates": ("overworld", "持续朝前移动就会加速，普通地面也会像冰面一样打滑。"),
    "amphibian_boot": ("aquatic", "连续游泳或淋雨奔跑会逐渐提速。鳃能力解锁后，水下不再消耗氧气。"),
    "phantom_boot": ("end", "脚下生成临时幻影桥，其他持有这种遗物的人也能通行。在桥上停留过久会坠落，落地后才能再架桥。"),
    "springy_boot": ("mountain", "落地时从方块表面向上反弹，主动发动还可将自己弹向空中。"),
    "rage_glove": ("nether", "连续攻击叠怒，超过 3 秒断连就会清空。怒气同时放大输出与承伤，冲刺可耗尽怒气攻击沿途目标。"),
    "enders_hand": ("end", "让末影人保持中立，发动换位能力可以与视线中的目标交换位置。"),
    "wool_mitten": ("mountain_snow", "戴着手套，用空手对雪按住使用键制出冰雪球。命中会击晕目标并冻结附近敌人。"),
    "reflection_necklace": ("nether", "承伤积蓄能量，蓄满或停受伤 5 秒后向四周迸射黑曜石碎片，伤害并击晕目标。"),
    "jellyfish_necklace": ("aquatic", "入水后不再下沉，碰撞敌人可放电。解锁麻痹能力后，电击还会限制目标行动。"),
    "holy_locket": ("desert", "圣洁姿态可将部分治疗化为伤害，不洁姿态可汲取周围治疗。消耗储存的充能可以短时免伤。"),
    "chorus_inhibitor": ("end", "食用紫颂果时可以选定视线中的方块作为传送落点。"),
    "bastion_ring": ("bastion", "让普通猪灵保持中立，最近的猪灵会指引堡垒方向。交易能力也可能额外结算一次以物易物。"),
    "leafy_ring": (None, "潜行并将头部藏入草丛等适用植物方块时，获得隐匿效果。"),
    "blazing_flask": ("nether", "对方块使用后设立禁飞区。中心附近的火焰越多，区域越大。"),
    "infinity_ham": ("village", "自动再生并储存最多六块肉，一口按饥饿值取用。解锁腌制后能注入药水，解锁肉棒后可消耗存肉击晕敌人。"),
    "magic_mirror": ("cave", "手持使用可返回自己的重生点。"),
    "space_dissector": ("nether", "按住使用键选定传送的一端，松开设置另一端。生物接触端点可双向通行，两点间须有畅通的直线。"),
}

CELESTIAL = {
    "magic_arrow_bag": "提高弓伤害 10%，箭矢额外获得 1 点击退。",
    "flame_arrow_bag": "提高弓伤害与箭矢击退，命中的箭矢还会点燃目标。",
    "spirit_arrow_bag": "弓伤害提高 20%，箭矢飞得更快并额外击退敌人。",
    "iron_scabbard": "每 10 秒短暂获得强化刀刃，提高攻击伤害和暴击率。",
    "leech_scabbard": "定时获得强化刀刃。效果生效时，造成伤害的 25% 用来治疗自己。",
    "titan_scabbard": "周期性获得强化刀刃。在此期间近战攻击最大生命高于自己的目标时，额外增伤 75%。",
    "twisted_scabbard": "击杀生物可积攒扭曲附着，提高近战伤害。层数随时间流失，背负终焉诅咒时每层更强，装备也会削弱治疗。",
    "forest_cloak": "增加跳跃高度，遭遇弹射物时有 20% 概率闪避。",
    "emerald_bracelet": "提高暴击率。幸运达到 2 点时，受击有机会获得伤害吸收。",
    "life_bracelet": "提高生命恢复属性，并持续提供生命恢复效果。",
    "precious_bracelet": "魔法伤害降低 40%，方块交互距离增加 1 格。",
    "red_ruby_bracelet": "火焰伤害降低 90%，受到火焰伤害时短暂获得力量。",
    "hidden_bracelet": "隐身消失后过一段时间可再获隐身。隐身期间攻击伤害提高 25%。",
    "scarlet_bracelet": "攻击时能消耗自身生命，将其换成按目标最大生命计算的额外伤害。默认最多消耗到自身半血。",
    "charming_bracelet": "增加 2 点护甲。击杀僵尸能改善附近村民对你的好感，受击还能让周围生物转向攻击者。",
    "spirit_bracelet": "加快拉弓与弩上弦，箭矢命中后短暂加速移动。",
    "catastrophe_scroll": "开启灾厄诅咒、专属掉落和饰品。默认增加 3 个护符槽和 7 个刻蚀槽，戴上后无法用常规方式摘下。刻蚀板可反转对应诅咒。",
    "war_dead_badge": "失血时逐步提高护甲、攻击和移速。背负混沌诅咒且低于半血时，周围怪物越多，攻击回复越多。",
    "undead_charm": "规避一次致命伤害，默认冷却 180 秒。",
    "destroyer_badge": "攻击伤害提高 40%，同时承受更多伤害，适合能控制交战距离的配装。",
    "twisted_brain": "受击时有 17% 概率规避本次伤害，同时获得短时攻击增益。",
    "corrupt_badge": "每种负面效果都能提高攻击、攻速和挖掘速度。主动释放可以给自己施加负面效果。",
    "cursed_talisman": "武器与防具上的诅咒附魔为你增加暴击率和暴击伤害。",
    "cursed_protector": "降低正面承伤，大额伤害另有减免。副手盾牌不再进入冷却。",
    "cursed_totem": "承受非致命伤害积攒怨念，叠满 5 层可消耗层数抵消一次致命伤。触发不死图腾时还会诅咒近处的攻击者。",
    "holy_talisman": "周期性削弱附近怪物，对亡灵有更高减伤。遭遇致命攻击时保留 1 点生命并获得吸收护盾，默认冷却 60 秒。",
    "holy_sword": "提升暴击率，受击会反伤，失血时提高攻击。攻击亡灵造成的伤害还会等量治疗自己。",
    "angel_heart": "每 2 秒回复 1 点生命，每 30 秒清除负面效果，低血量时还能减伤。",
    "angel_pearl": "持续治疗附近玩家。自己身上每种正面效果都会增加护甲与生命恢复速度。",
    "demon_curse": "禁止自身回血，将额外治疗力转成攻击力与移速。",
    "knight_shelter": "提高护甲和伤害减免。持盾会定时回血，主手持盾回复更快，成功格挡还可反伤。",
    "soul_box": "规避致命一击并向攻击者反击，造成按自身最大生命计算的深渊伤害。保命默认冷却 240 秒。",
    "solar_magnet": "吸引周围掉落物，白天攻击伤害提高 25%。",
    "gluttony_badge": "每点饱食度提供 1% 减伤。进食后短暂获得力量与生命恢复，平时需留意饥饿效果。",
    "magic_horseshoe": "提高移动速度和幸运，摔落伤害降低 95%。",
    "bearing_stamen": "增加 20 点最大生命，强化生命恢复，并提供再生效果。",
    "abyss_will_badge": "每隔一段时间叠加深渊呼唤，同时提高输出与承伤。主动爆发会临时叠高层数，结束后清空层数并损失生命。",
    "sands_talisman": "增加 50% 经验获取，在炎热地区增加 30% 攻击伤害。",
    "sacrificial_object": "减免 5% 伤害。死亡时有机会留下金锭，并带走附近最大生命低于自己的敌对生物。",
    "deers_mercy_amulet": "增加 10 点最大生命和 2 点攻击伤害，也是悯鹿护符的前置饰品。",
    "deer_inscribed_amulet": "提高最大生命、护甲、韧性和攻击，周期性强化下一击。装备灾厄之册时，反转的诅咒越多，增伤越高。",
    "chaotic_etching": "反转混沌诅咒，失血时提高伤害减免。",
    "origin_etching": "反转始源诅咒，提升挖掘速度与攻击伤害。",
    "life_etching": "反转生命诅咒，提升最大生命和治疗力。",
    "truth_etching": "反转真理诅咒，限制一次生物攻击最多能打掉的生命比例。",
    "desire_etching": "反转欲望诅咒，提高时运与抢夺等级。",
    "nihility_etching": "反转虚无诅咒，降低虚空伤害，受击时对攻击者施加负面效果。",
    "end_etching": "反转终焉诅咒，攻击后按自身已损生命回血。",
    "sea_god_crown": "提供水下呼吸和夜视。主动发动可将天气转为下雨，默认冷却 30 秒。",
    "prayer_crown": "延长受伤后的无敌时间，潜行承伤降低 25%。受击还有机会按本次伤害治疗自己。",
    "abyss_core": "单次伤害超过 12 点时可将其压至 12 点，默认冷却 60 秒。",
    "guardian_eye": "提高水中机动性，水下不会耗氧。位于水中或雨中时，所受伤害降低 20%。",
    "evil_eye": "免疫黑暗与失明，适合探索幽匿区域。",
    "spirit_crown": "周围实体少时提高箭矢伤害。与目标距离每增加 1 格，还有一份距离增伤。",
    "sakura_hairpin": "提高最大生命和护甲，幸运越高，暴击加成越多。",
    "yellow_duck": "允许直接在水面行走。",
    "angel_desire": "飞行时提高伤害、免疫弹射物，并可为鞘翅飞行提供动力。",
    "heart_of_revenge": "提高弓伤害，受到伤害后有短暂的复仇增伤窗口。",
    "twisted_heart": "装备时削弱护甲、韧性和攻击。背负虚无诅咒后，这些代价会反转为加成。",
    "greedy_heart": "提高经验收益，附带时运与抢夺各 1 级。",
    "demon_heart": "每条灾厄诅咒都加强战斗属性，达到 3 条后防止燃烧，5 条后免疫缓慢与虚弱。",
    "star_necklace": "从背后攻击提高 40% 伤害，夜间还能获得抗性提升。",
    "cross_necklace": "受伤后额外增加 10 游戏刻的无敌时间。",
    "gallop_necklace": "增加移速，移动中攻击时还能把速度转为额外伤害。",
    "fang_necklace": "提高攻速，对穿甲目标增伤。命中有 50% 概率施加中毒。",
    "precious_necklace": "提高 20% 暴击伤害。",
    "holy_necklace": "增加 4 点最大生命，延长受伤后的无敌时间，接受治疗还可短时获得抗性提升。",
    "heirloom_necklace": "提供 2 点护甲、5% 移速与 10% 经验加成。",
    "emerald_necklace": "击杀生物时有机会额外获得绿宝石。",
    "ender_protector": "提供 4 点盔甲韧性。成功格挡时有 50% 概率将攻击者传送走。",
    "red_heart_necklace": "最大生命提高 5%。",
    "lock_of_abyss": "攻击施加缓慢，重复命中逐级叠高。达到触发条件时消耗缓慢，造成额外深渊伤害。",
    "spirit_necklace": "增强远程输出，受击时可短暂加速移动。",
    "treasure_hunter_necklace": "在特定生物群系的开阔水域钓鱼，有机会钓出相应结构的战利品箱。",
    "unowned_pendant": "古城出土的吊坠底坯，可再制成混沌吊坠、怨影吊坠或疾行项链。",
    "chaotic_pendant": "用附魔台附魔时，所得附魔的等级提高 3 级。",
    "shadow_pendant": "造成伤害时吸血，阻止失眠招来幻翼。在低于 7 级的光照中，越暗越能获得攻防加成。",
    "gold_ring": "佩戴后持续获得幸运效果。",
    "amethyst_ring": "获得夜视并提高攻击伤害。",
    "diamond_ring": "佩戴后持续获得力量效果。",
    "emerald_ring": "增加 1 点幸运，可参与绿宝石饰品套装。",
    "flight_ring": "提供飞行能力。",
    "netherite_ring": "提供抗火效果，在下界还可减免 10% 伤害。",
    "ring_of_life": "每 5 秒随机催熟附近的一株作物。",
    "thunder_ring": "免疫雷击伤害。",
    "nether_fire": "免疫火焰伤害，受到攻击后会点燃攻击者。",
    "freeze_ring": "免疫冰冻伤害。",
    "nostalgic_butterfly_ring": "提高暴击率，同时降低暴击伤害。可与鹿系护符组成套装。",
    "traveler_scroll": "提高地面、飞行与水中机动性，每次进入新维度还可获得短时速度与生命恢复。",
    "sea_god_scroll": "消除水下挖掘速度惩罚。在雨中或水中可获得力量与抗性。",
    "skywalker_scroll": "潜行发动记录坐标，正常发动返回记录处。传送限于同一维度。",
    "twisted_scroll": "增加 1 级抢夺。遭遇的怪物有几率分裂，战利品收益与围攻风险随之增加。",
}

REQUIRES_SCROLL = set("""
    twisted_scabbard hidden_bracelet scarlet_bracelet war_dead_badge undead_charm
    twisted_brain corrupt_badge cursed_talisman cursed_protector cursed_totem
    demon_curse soul_box abyss_will_badge sacrificial_object abyss_core twisted_heart
    greedy_heart demon_heart ender_protector lock_of_abyss treasure_hunter_necklace
    chaotic_pendant shadow_pendant twisted_scroll
""".split())

CHESTS = {
    "abandoned_mineshaft": "废弃矿井宝箱", "ancient_city": "古城宝箱",
    "bastion_treasure": "堡垒遗迹的宝藏箱", "desert_pyramid": "沙漠神殿宝箱",
    "end_city_treasure": "末地城宝箱", "jungle_temple": "丛林神庙宝箱",
    "nether_bridge": "下界要塞桥梁宝箱", "underwater_ruin_big": "大型海底废墟宝箱",
    "village_plains_house": "平原村庄的民居宝箱", "fishing_treasure": "在 Y ≥ 300 的开阔水域钓鱼",
}

# Compiled add_item modifiers, with the default server values. The final five
# etchings below need the matching player curse flag; the first two do not.
MOB_DROPS = {
    "chaotic_etching": "让爆炸伤害击杀凋灵，默认有 30% 概率掉落。",
    "truth_etching": "让袭击阵营的灾厄村民击杀凋灵，默认有 50% 概率掉落。",
    "origin_etching": "佩戴灾厄之册并触发始源诅咒后，在末影龙高度 Y ≥ 200 时击杀它，默认有 50% 概率掉落。",
    "life_etching": "佩戴灾厄之册并触发生命诅咒后，击杀最大生命至少 500 点的生物，默认有 15% 概率掉落。",
    "desire_etching": "佩戴灾厄之册并触发欲望诅咒后，以至少 7 级抢夺击杀凋灵，默认有 25% 概率掉落。",
    "nihility_etching": "佩戴灾厄之册并触发虚无诅咒后，用绕过附魔防护的伤害击杀监守者，默认有 50% 概率掉落。",
    "end_etching": "佩戴灾厄之册并触发终焉诅咒后，身带至少 9 种负面效果击杀监守者，默认有 75% 概率掉落。",
    "charming_bracelet": "对村民的声誉达到 100 时击杀村民，默认有 50% 概率掉落。",
    "demon_curse": "亲手击败凋灵后再击杀恼鬼，默认有 3% 概率掉落。",
    "guardian_eye": "击杀远古守卫者，默认有 50% 概率掉落。",
    "twisted_brain": "让凋灵击杀袭击阵营的灾厄村民，默认有 10% 概率掉落。",
    "twisted_heart": "让凋灵击杀最大生命至少 20 点的生物，默认有 10% 概率掉落。",
    "heirloom_necklace": "默认在玩家首次触发模组的开局赠礼时放入背包。",
    "catastrophe_scroll": "默认随家传项链一起发给玩家，可自行决定何时佩戴。",
}

HELD = {"magic_mirror", "space_dissector", "blazing_flask", "infinity_ham"}
MATERIALS = {
    "the_end_dust": ("用于星月遗物的进阶制作。", "佩戴灾厄之册，击杀正以玩家为目标的生物，默认有 2% 概率掉落。"),
    "nebula_cube": ("多种进阶饰品使用的材料。", "满足一种刻蚀板的特殊掉落条件后，若该次未抽到刻蚀板，模组会掉落星云立方。"),
}

# Ingredients consumed directly by a wearable recipe and the guardian spike,
# which is the missing intermediate between an ocean ingot and its upgrade.
CORE_MATERIALS = {
    "treasure_fragment": "废弃矿井宝箱中约有三分之一的概率找到。用于多条手环、项链和戒指路线。",
    "void_essence": "末地城宝箱中有五分之一的概率找到。",
    "death_essence": "让最大生命至少 100 点的生物死于凋零类伤害，默认掉率为 60%。",
    "fire_essence": "玩家击杀烈焰人时，默认有 8% 概率掉落。",
    "ocean_essence": "玩家击杀普通守卫者时，默认有 8% 概率掉落。",
    "cursed_essence": "亲手击败凋灵后，用带诅咒附魔的主手武器击杀生物，默认有 5% 概率掉落。",
    "light_fragment": "亲手击败凋灵后，击杀沙漠中的尸壳，默认有 5% 概率掉落。",
    "midnight_fragment": "亲手击败凋灵后，击杀流浪者，默认有 5% 概率掉落。",
    "shulker_scrap": "使潜影贝死于爆炸伤害，默认有 50% 概率掉落。",
    "soaring_wings": "玩家击杀幻翼时，幻翼本身必须在 Y ≥ 619。可先诱导幻翼向高空追击。",
    "warden_sclerite": "玩家击杀监守者时，默认有 50% 概率掉落。",
    "pure_nether_star": "玩家身上同时带有至少 12 种正面效果时击杀凋灵。",
    "heart_fragment": "让高压苦力怕击杀掠夺者，由掠夺者掉落。",
    "sakura_fragment": "破坏樱花树叶，默认有 10% 概率掉落。",
    "broken_totem": "不死图腾实际触发救命效果时，默认有 50% 概率获得残件。",
    "celestial_fragment": "主世界夜间每 6000 游戏刻进行一次判定，默认有 50% 概率在随机一名主世界玩家周围 50 至 100 格的空中生成碎片。",
    "guardian_spike": "守卫者和远古守卫者均能掉落，默认概率为 20%。可将海洋锭加工成强化海洋锭。",
    "ocean_ingot": "用铁锭配合海晶砂粒与海晶碎片制作，可直接用于海神王冠，也可继续强化。",
    "reinforced_ocean_ingot": "用四根守护者尖刺加工一块海洋锭，可制作海神卷轴或锻造海洋盔甲。",
    "sakura_steel": "用两枚樱花碎片、铜锭和金锭制作，可用于樱花发簪。",
}

SETS = (
    ({"spirit_bracelet", "spirit_crown", "spirit_necklace", "spirit_arrow_bag"},
     "精灵四件套含手环、花冠、项链和箭袋。持续拉弓可强化箭矢，背射增伤，速度效果下射出的箭还有机会使敌人缓慢。"),
    ({"emerald_ring", "emerald_necklace", "emerald_bracelet"},
     "戴齐绿宝石戒指、项链和手环，额外提高幸运和暴击率。"),
    ({"sea_god_crown", "sea_god_scroll"},
     "搭配海神王冠与海神卷轴可减少水生敌人的伤害，手持三叉戟还会加强近战与投掷攻击。"),
    ({"deer_inscribed_amulet", "nostalgic_butterfly_ring"},
     "戴齐悯鹿护符与眷蝶之戒，普通攻击可追加一次魔法或深渊伤害。"),
)


def _archive(jars, mod_id):
    path = mod_files(tuple(jars)).get(mod_id)
    return ZipFile(path) if path is not None else None


def _read(archive, path):
    return json.loads(archive.read(path))


def _slots(archive, namespace):
    slots = defaultdict(list)
    for path in archive.namelist():
        if path.startswith("data/curios/tags/item/") and path.endswith(".json"):
            slot = SLOTS[Path(path).stem]
            for item in _read(archive, path)["values"]:
                if isinstance(item, str) and item.startswith(namespace + ":"):
                    slots[item.split(":")[1]].append(slot)
    return slots


def _record(ns, item, language, slots, effects, sources):
    return {"id": ns + ":" + item, "name": language["item." + ns + "." + item],
            "slots": slots, "effects": effects, "source": sources}


def _relic_records(archive, held):
    lang = _read(archive, "assets/relics/lang/zh_cn.json")
    slots = _slots(archive, "relics")
    for item in sorted(HELD if held else slots):
        group, effect = RELICS[item]
        source = ("探索获取，可在" + CHEST_GROUPS[group] + "中随机找到。") if group else (
            "作者将叶环标为开发中。本版默认遗物战利品表没有为它指定箱子，也没有生存配方。"
        )
        row = _record("relics", item, lang, ["手持"] if held else slots[item], [effect], [source])
        if held:
            row["label"] = "手持遗物"
        yield row


def _celestial_records(archive):
    ns = "celestial_artifacts"
    lang = _read(archive, "assets/celestial_artifacts/lang/zh_cn.json")
    chest_sources = defaultdict(list)
    for table, place in CHESTS.items():
        data = _read(archive, f"data/{ns}/loot_table/chests/{table}.json")
        for pool in data["pools"]:
            for entry in pool["entries"]:
                item = entry.get("name", "")
                if item.startswith(ns + ":"):
                    how = (place if table == "fishing_treasure" else "搜寻" + place)
                    chest_sources[item.split(":")[1]].append(how + "时有机会获得。")
    for item, slots in sorted(_slots(archive, ns).items()):
        effects = [CELESTIAL[item]]
        if item in REQUIRES_SCROLL:
            effects.append("需要同时装备灾厄之册。")
        effects.extend(note for members, note in SETS if item in members)
        source = chest_sources[item]
        if item in MOB_DROPS:
            source.append(MOB_DROPS[item])
        yield _record(ns, item, lang, slots, effects, source)


def accessory_records(jars):
    records = []
    relics = _archive(jars, "relics")
    if relics:
        with relics:
            records.extend(_relic_records(relics, held=False))
    celestial = _archive(jars, "celestial_artifacts")
    if celestial:
        with celestial:
            records.extend(_celestial_records(celestial))
    return records


def progression_records(jars):
    records = []
    relics = _archive(jars, "relics")
    if relics:
        with relics:
            records.extend(_relic_records(relics, held=True))
    celestial = _archive(jars, "celestial_artifacts")
    if celestial:
        with celestial:
            lang = _read(celestial, "assets/celestial_artifacts/lang/zh_cn.json")
            for item, (effect, source) in MATERIALS.items():
                row = _record("celestial_artifacts", item, lang, [], [effect], [source])
                row["label"] = "制作材料"
                records.append(row)
    core = _archive(jars, "celestial_core")
    if core:
        with core:
            lang = _read(core, "assets/celestial_core/lang/zh_cn.json")
            for item, source in CORE_MATERIALS.items():
                row = _record("celestial_core", item, lang, [], [], [source])
                row.update(label="进阶材料", shelf="celestial_artifacts")
                records.append(row)
    return records


def armor_records(jars):
    core = _archive(jars, "celestial_core")
    if not core:
        return []
    materials = {
        "ocean": ((3, 8, 6, 3), "每件提供 2 点盔甲韧性。四件套持续提供水下呼吸。"),
        "sakura": ((2, 6, 5, 2), "穿在盔甲栏时，每 5 秒恢复 1 点耐久。"),
        "virtual_gold": ((3, 8, 6, 3), "每件提供 2 点盔甲韧性，附魔能力为 30。四件套持续提供抗火。"),
    }
    records = []
    with core:
        lang = _read(core, "assets/celestial_core/lang/zh_cn.json")
        for prefix, (defences, effect) in materials.items():
            for slot, suffix, armor in zip(("head", "chest", "legs", "feet"),
                                           ("helmet", "chestplate", "leggings", "boots"), defences):
                item = prefix + "_" + suffix
                row = {"id": "celestial_core:" + item, "name": lang["item.celestial_core." + item],
                       "slot": slot, "armor": armor, "effects": [effect]}
                if prefix == "virtual_gold":
                    row["source"] = ["击杀着火且穿有附魔金甲的生物可取得虚金残骸，九块合成虚金锭，再按对应盔甲的形状制作。"]
                records.append(row)
    return records
