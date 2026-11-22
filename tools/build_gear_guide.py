"""Build the always-readable equipment section and the quest-book links into it."""
from build_heritage_quests import QUESTS, merge, uid as stable_id, write
from campaign_navigation import GUIDE_GROUP, turn_page
from gear_accessories import ACCESSORIES

FILENAME = "gear_field_guide"


def guide_id(key):
    return stable_id(key, "lostworld.gear_field_guide.v1/")


def guide_link(label, key):
    # FTB Quests parses a serialized text component in each quest_desc line.
    return turn_page(label, guide_id(key))


WEAPONS = (
    ("copper_broadsword", "铜阔剑", "confluence:copper_broadsword", (
        "近战 · 基础攻击伤害 5，攻击速度 1.6。对付跳起的史莱姆，先横移，等它落地再出剑。",
        "制作 · 在普通工作台从上到下放铜锭、铜锭、木棍。序章首趟下矿的铜锭奖励就够用。",
    )),
    ("iron_bow", "铁弓", "confluence:iron_bow", (
        "远程 · 弓的基础伤害为 3.5，耐久 896。拉弓蓄力、松开发射，带上箭，在平台上边退边打。",
        "制作 · 普通工作台用 3 铁锭和 3 根线。铁锭摆成弓背，线竖排作弦。序章的投点任务会给线和箭。",
    )),
    ("amethyst_staff", "紫晶法杖", "confluence:amethyst_staff", (
        "魔法 · 发射直行的宝石弹。基础伤害 13，每发耗 5 魔力，基础施法间隔 13 刻（约 0.65 秒）。",
        "制作 · 在重型工作台用 10 铜锭和 5 紫水晶碎片制作。重型工作台用两个工作台上下夹一个铁砧或铅砧合成。",
        "备战 · 紫水晶晶洞能找到碎片。夜间收集 5 颗坠落之星，可合成一颗魔力水晶来提高魔力上限。",
    )),
    ("star_eater", "噬星之刃", "evolvingblade:star_eater", (
        "成长 · 在新集镇醒来就能拿到。标准路线累计 50 成长点升到初醒，150 点升到灵锋。",
        "用法 · 二阶主手右键发出剑气，四阶潜行右键释放范围爆发。按 G 或输入 /bladefolio 翻看各阶能力、成长点和首领战果。",
    )),
)

ARMOR = (
    ("copper_armor", "铜盔甲", "confluence:copper_chestplate", (
        "开荒 · 四件穿齐额外获得 1 点护甲。饰品和盔甲各占自己的槽位，穿齐铜甲也能戴云朵瓶。",
        "制作 · 普通工作台做头盔、胸甲、护腿、靴子，分别用 5、8、7、4 铜锭，共 24 锭。打算练铜阔剑的话，多留 2 锭。",
    )),
    ("cobalt_armor", "钴盔甲", "confluence:cobalt_chestplate", (
        "困难模式 · 穿钴胸甲、护腿和靴子，按武器选第四件。钴头盔套装提高 15% 近战速度；钴帽套装降低 14% 魔力消耗；钴面具套装有 20% 几率省下弹药。",
        "制作 · 击败血肉首领后，用神锤敲祭坛显矿。采得粗钴后在地狱熔炉熔锭；重型工作台做头、胸、腿、靴分别用 10、20、9、6 锭，共 45 锭。",
    )),
    ("palladium_armor", "钯金盔甲", "confluence:palladium_chestplate", (
        "困难模式 · 穿齐四件后，攻击敌人会提高生命再生速度。三种头部均可组成套装；其中钯金头饰还能增加 60 最大魔力。",
        "制作 · 用神锤敲祭坛显出钯金矿，粗钯金在地狱熔炉熔锭。重型工作台做头、胸、腿、靴分别用 12、24、10、8 锭，共 54 锭。",
    )),
)

STARTER = ("cloud_in_a_bottle", "hermes_boots", "band_of_regeneration",
           "aglet", "blizzard_in_a_bottle", "flurry_boots", "shackle")

# Let players follow a crafting chain without having to search the book again.
RELATED = {
    "cloud_in_a_bottle": ("cloud_in_a_balloon",),
    "hermes_boots": ("spectre_boots",), "flurry_boots": ("spectre_boots",),
    "spectre_boots": ("hermes_boots", "flurry_boots", "lightning_boots"),
    "lightning_boots": ("spectre_boots", "aglet", "anklet_of_the_wind"),
    "natures_gift": ("mana_flower",), "mana_flower": ("natures_gift",),
    "amethyst_staff": ("natures_gift",),
}


def main(extra_mods=None):
    from build_equipment_atlas import atlas_id, build, entry_link
    chapter = {"id": guide_id("chapter"), "filename": FILENAME, "group": GUIDE_GROUP, "order_index": 1,
               "icon": {"id": "terra_curio:cloud_in_a_bottle"}, "quests": []}
    lang = {f"chapter.{chapter['id']}.title": "装备手册 · 武器与饰品"}

    def page(key, title, icon, x, y, lines):
        qid = guide_id(key)
        chapter["quests"].append({"id": qid, "x": x, "y": y, "icon": {"id": icon},
                                  "min_width": 300, "optional": True, "dependencies": [], "tasks": [], "rewards": []})
        lang[f"quest.{qid}.title"] = title
        lang[f"quest.{qid}.quest_desc"] = list(lines)

    groups = (
        ("starter", "开荒饰品", "terra_curio:cloud_in_a_bottle",
         "想越过史莱姆就选云朵瓶，想在平地拉开距离就选赫尔墨斯靴，受伤后需要续航就选再生手环。试剑奖励可从这三件里挑一件。",
         [next(row for row in ACCESSORIES if row[0] == key) for key in STARTER]),
        ("advanced", "进阶饰品", "terra_curio:lightning_boots",
         "想把跑鞋继续升级，先留着鞋带束头。击退哥布林军队后，留意来访的哥布林工匠，他出售工匠作坊和火箭靴。近战可看猛爪手套，法师可看魔力花。",
         [row for row in ACCESSORIES if row[0] not in STARTER]),
        ("weapons", "起手武器", "confluence:copper_broadsword",
         "练习铜阔剑、铁弓、紫晶法杖中的一门即可接上序章。噬星之刃能从开局带到后期，剑谱可随时查看。",
         WEAPONS),
        ("armor", "护甲与套装", "confluence:cobalt_chestplate",
         "史莱姆王前先看铜甲。血肉首领战后，再比较钴甲的三种头部和钯金甲的回复效果。",
         ARMOR),
    )
    page("index", "装备手册 · 目录", "minecraft:book", 0, 0, [
        "点击下列分册查看装备。饰品放进背包的饰品栏，武器拿在主手，套装穿在四个护甲位。",
        turn_page("全盔甲 · 按单件护甲值查阅", atlas_id("armor/index")),
        turn_page("全饰品 · 从材料追到成品", atlas_id("routes/index")),
        *[guide_link(title, key) for key, title, _, _, _ in groups],
        "从主线点进来时，按退格键可以返回上一个条目。",
    ])
    titles = {slug: name for _, _, _, _, entries in groups for slug, name, _, _ in entries}
    for column, (key, title, icon, overview, entries) in enumerate(groups):
        x = -6 + column * 4
        extra = ([turn_page("查所有护甲部件与分级", atlas_id("armor/index"))] if key == "armor" else
                 [turn_page("查所有饰品与合成路线", atlas_id("routes/index"))] if key in ("starter", "advanced") else [])
        page(key, title, icon, x, 2, [overview, *extra, *[guide_link(row[1], row[0]) for row in entries],
                                      guide_link("返回装备目录", "index")])
        for position, (slug, name, item_id, lines) in enumerate(entries):
            page(slug, name, item_id, x - 0.75 + 1.5 * (position % 2), 4 + 1.5 * (position // 2),
                 [*lines, *([entry_link("展开这件饰品的完整路线", "accessory", item_id)] if key in ("starter", "advanced") else
                           [entry_link("查看胸甲护甲值与单件配方", "armor", item_id)] if key == "armor" else []),
                  *[guide_link("相关装备 · " + titles[target], target)
                            for target in RELATED.get(slug, ())], guide_link(f"返回「{title}」", key)])

    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)
    build(guide_id, extra_mods)


if __name__ == "__main__":
    import argparse
    from pathlib import Path
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--extra-mods", type=Path)
    main(parser.parse_args().extra_mods)
