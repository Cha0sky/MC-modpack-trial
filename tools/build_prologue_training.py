"""Extend the prologue with equipment drills and its shared-world catch-up route."""

from build_heritage_quests import DATA, write
from build_gear_guide import guide_link

PREFIX = "world_progression_core:prologue_training/"
ACCESSORIES = ("aglet", "cloud_in_a_bottle", "blizzard_in_a_bottle", "hermes_boots",
               "flurry_boots", "band_of_regeneration", "shackle")
DRILLS = {
    "blade": ["evolvingblade:star_eater"],
    "melee": ["confluence:" + metal + "_broadsword" for metal in
              ("copper", "tin", "lead", "silver", "tungsten", "golden", "platinum")],
    "archery": ["confluence:" + metal + style for metal in
                ("copper", "tin", "iron", "lead", "silver", "tungsten", "golden", "platinum")
                for style in ("_bow", "_short_bow")] + ["confluence:wooden_short_bow"],
    "magic": ["confluence:" + gem + "_staff" for gem in ("amethyst", "topaz", "sapphire", "ruby", "diamond")],
}


def build_records():
    for name in ("allocate", "wave", "burst"):
        write(DATA / f"advancement/prologue_training/{name}.json", {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    write(DATA / "advancement/prologue_training/veteran.json", {"criteria": {
        "done": {"trigger": "minecraft:impossible"},
        "three_styles": {"trigger": "minecraft:location", "conditions": {"player": {"type_specific": {
            "type": "minecraft:player", "advancements": {
                **{PREFIX + style: True for style in ("melee", "archery", "magic")},
                "world_progression_core:unlock_evil": True}}}}}}, "requirements": [["done","three_styles"]]})
    write(DATA / "advancement/prologue_training/accessory.json", {"criteria": {"equipped": {
        "trigger": "curios:equip_curio", "conditions": {"item": {"items": ["terra_curio:" + n for n in ACCESSORIES]}}}}})
    targets = ["minecraft:" + mob for mob in ("zombie", "husk", "drowned", "skeleton", "stray",
               "bogged", "spider", "cave_spider", "creeper", "slime", "pillager")]
    for name, weapons in DRILLS.items():
        damage = {"dealt": {"min": 1}, "blocked": False}
        if name in ("blade", "melee"):
            damage["type"] = {"direct_entity": {"type": "minecraft:player"}}
        if name == "archery":
            # Confluence converts even ordinary arrows into its own projectile when shot from a Terra bow.
            damage["type"] = {"direct_entity": {"type": ["minecraft:arrow", "minecraft:spectral_arrow",
                "confluence:arrow_projectile", "confluence:bee_arrow_projectile", "confluence:hell_bat_arrow_projectile"]}}
        if name == "magic":
            damage["type"] = {"direct_entity": {"type": "confluence:base_mana_staff_projectile"}}
        write(DATA / f"advancement/prologue_training/{name}.json", {"criteria": {"hit": {
            "trigger": "minecraft:player_hurt_entity",
            "conditions": {"player": {"equipment": {"mainhand": {"items": weapons}}},
                           "entity": {"type": targets}, "damage": damage}}}})


def add_training(chapter, language, uid):
    nodes = {q["id"]: q for q in chapter["quests"]}
    training = []

    def item_reward(name, item, count):
        return {"id": uid("training/" + name + "/" + item), "type": "item",
                "item": {"id": item, "count": 1}, "count": count}

    def adv(name):
        return {"type": "advancement", "advancement": PREFIX + name}

    def add(name, title, coords, icon, deps, task, lines, rewards=(), minimum=0):
        qid = uid(name)
        node = {"id": qid, "x": coords[0], "y": coords[1], "icon": {"id": icon}, "min_width": 320,
                "optional": True, "dependencies": [uid(dep) for dep in deps], "progression_mode": "linear",
                "dependency_requirement": "all_completed", "tasks": [{"id": uid(name + "/task"), **task}],
                "rewards": [item_reward(name, item, count) for item, count in rewards]}
        if minimum:
            node["min_required_dependencies"] = minimum
        training.append(node)
        language[f"quest.{qid}.title"] = title
        language[f"quest.{qid}.quest_desc"] = lines
        return node

    handbook = item_reward("arrival", "patchouli:guide_book", 1)
    handbook["item"]["components"] = {"patchouli:book": "rpgleveling:guide"}
    nodes[uid("arrival")]["rewards"].extend([item_reward("arrival", "rpgleveling:skill_scroll", 1), handbook])
    add("allocate", "投下第一点", (3, -3), "rpgleveling:skill_scroll", ["arrival"], adv("allocate"), [
        "领取小屋任务的加点卷轴，右键得到 1 点。打开「加点界面」，在一条数值属性上按 +1。生命上限适合第一次出门，也可以投资移速或伤害。",
        "开发版按 N，模组默认 K。挖矿、首次合成和打怪都能积攒角色经验；想比较属性，翻开与卷轴一起领到的加点手册。"
    ], [("minecraft:string", 6), ("minecraft:arrow", 32)])
    blade = add("blade", "噬星之刃 · 第一剑", (3, -5), "evolvingblade:star_eater", ["arrival"], adv("blade"), [
        "你还记不起这把剑的来历，先试试它的分量。主手持剑，近身命中僵尸、骷髅、蜘蛛或普通史莱姆。",
        "按 G 或输入 /bladefolio 翻开剑谱，看离下一阶还差多少成长点。",
        "云朵瓶、赫尔墨斯靴、再生手环任选一件。领奖前可以翻开手册比较。", guide_link("查手册 · 开荒饰品怎么选", "starter")
    ])
    offers = [item_reward("blade/choice", "terra_curio:" + name, 1)
              for name in ("cloud_in_a_bottle", "hermes_boots", "band_of_regeneration")]
    blade["rewards"].append({"id": uid("blade/accessory_choice"), "type": "choice", "table_id": 0,
                             "table_data": {"loot_size": 1, "rewards": [dict(offer, weight=1.0) for offer in offers]}})

    add("melee", "兵器架 · 铜阔剑", (7, 4), "confluence:copper_broadsword", ["mine"], adv("melee"), [
        "主手用铜阔剑命中一只敌怪，打完一剑就退到落点外。锡、铅、银、钨、金、铂金阔剑也可练习。",
        guide_link("查手册 · 铜阔剑的属性和配方", "copper_broadsword")
    ], [("minecraft:cooked_beef", 16)])
    add("archery", "兵器架 · 金属弓", (7, 6), "confluence:iron_bow", ["mine"], adv("archery"), [
        "主手持泰拉弓射中僵尸、骷髅、蜘蛛或史莱姆，等箭命中再换手。木短弓和铜至铂金的金属长短弓均可。",
        guide_link("查手册 · 铁弓的属性和配方", "iron_bow")
    ], [("minecraft:arrow", 64)])
    add("magic", "兵器架 · 宝石法杖", (7, 8), "confluence:amethyst_staff", ["mine"], adv("magic"), [
        "主手施法，让宝石弹命中僵尸、骷髅、蜘蛛或史莱姆。紫晶、黄玉、蓝玉、红玉和钻石法杖均可。",
        guide_link("查手册 · 紫晶法杖与魔力准备", "amethyst_staff")
    ], [("minecraft:amethyst_shard", 12)])
    add("loadout", "把饰品戴上", (10, 5), "terra_curio:cloud_in_a_bottle", ["melee", "archery", "magic"],
        adv("accessory"), [
            "练熟一门兵器，再选一件搭配它的饰品。想越过落点就带云朵瓶，想在平地放风筝就穿跑鞋。把开荒分册里任意一件戴进背包的饰品栏。",
            guide_link("看七件开荒饰品 · 属性与获取", "starter"), guide_link("翻开整本装备手册", "index")
        ], [("minecraft:golden_carrot", 12)], minimum=1)
    add("crown", "王冠与退路", (14, 3), "confluence:slime_crown",
        ["workshop", "allocate", "blade", "loadout"], {"type": "item", "item": {"id": "confluence:slime_crown", "count": 1},
        "count": 1, "consume_items": False}, [
            "备一顶金冠或铂金冠，在工作台用 8 个凝胶围住它，得到史莱姆皇冠。先把床和备用食物安顿在战场边。",
            "清出一段可横移的平地，放两层错开的平台。看到史莱姆王起跳，先离开落点再还击。",
            guide_link("战前翻阅 · 装备手册", "index")
        ], [("minecraft:scaffolding", 32), ("minecraft:cooked_beef", 12)])
    add("campaign", "老兵委托 · 三门兵器", (14, -3), "minecraft:map", ["generator", "create"],
        adv("veteran"), [
            "熟手可以用三种泰拉武器各命中一次敌人，再击败史莱姆王，直接提交战报。更新前已打通边境的世界可沿用原战果。"
        ])
    king = nodes[uid("king")]
    king["dependencies"] = [uid("crown"), uid("campaign")]
    king["min_required_dependencies"] = 1
    # The pre-existing quest verifier expects prerequisites to occur before their children in file order.
    quests = chapter["quests"]
    before_king = quests.index(king)
    quests[before_king:before_king] = training
    training.clear()
    add("wave", "剑谱 · 第一束剑气", (21, 3), "evolvingblade:star_eater", ["next"], adv("wave"), [
        "噬星之刃到二阶后，主手持剑，对准前方按右键放出剑气。标准路线累计 150 成长点可到二阶，击败史莱姆王也是一笔成长。",
        "四阶时可潜行右键释放爆发。剑谱的「形态」页列出各阶所需的成长点和首领战果。"
    ], [("minecraft:experience_bottle", 16), ("minecraft:golden_apple", 2)])
    quests.extend(training)
    build_records()
