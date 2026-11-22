"""Generate World Turn quests, field supplies and bulk hellforge recipes."""

import json
from build_heritage_quests import ASSETS, DATA, QUESTS, merge, uid as quest_id, write
from build_workshop_quests import sprite
from campaign_navigation import finish_chapter

FILENAME = "03_world_turn"


def uid(name):
    return quest_id(name, "lostworld.world_turn.v1/")


def generate():
    previous = quest_id("archive_open")
    chapter = {
        "id": uid("chapter"),
        "filename": FILENAME,
        "order_index": 2,
        "icon": {"id": "confluence:pwnhammer"},
        "progression_mode": "linear",
        "quests": [],
    }
    lang = {"chapter." + chapter["id"] + ".title": "第三章 · 世界转折"}

    def add(name, title, description, icon, dependency, advancement=None, rewards=(), pos=None, optional=False,
            either=False, subtitle=None, item_task=None):
        qid = uid(name)
        if item_task:
            item_id, count, consume = item_task
            task = {"id": uid(name + "/task"), "type": "item", "item": {"id": item_id, "count": 1},
                    "count": count, "consume_items": consume}
        else:
            record = "world_progression_core:world_turn/" + advancement if advancement else "minecraft:story/enter_the_nether"
            task = {"id": uid(name + "/task"), "type": "advancement", "advancement": record}
        x, y = pos or (len(chapter["quests"]) * 2.5, 0)
        chapter["quests"].append({
            "id": qid, "x": x, "y": y,
            "icon": {"id": icon}, "min_width": 340,
            "dependencies": [dependency] if isinstance(dependency, str) else dependency,
            "dependency_requirement": "all_completed",
            "progression_mode": "linear", "tasks": [task],
            "optional": optional,
            "rewards": [{"id": uid(name + "/reward/" + key), "type": "item", "item": stack, "count": count}
                        for key, stack, count in rewards],
        })
        if either:
            chapter["quests"][-1]["min_required_dependencies"] = 1
        lang["quest." + qid + ".title"] = title
        lang["quest." + qid + ".quest_desc"] = description
        if subtitle:
            lang["quest." + qid + ".quest_subtitle"] = subtitle

    add("prepare", "去往下界之前", [
        "从档案台收好书，把修过工坊的粗制调谐器和 1 份共振胶带进下界。取样要靠前队，做饭可以留给灶边的人。",
        "击败血肉首领会让世界进入困难模式，部分地形随之转化。准备动手时，先把退路或口粮试好。"],
        "confluence:guide_voodoo_doll", previous, subtitle="从工坊带走工具，让厨房同时开火", rewards=[
        ("food", {"id": "minecraft:golden_carrot", "count": 1}, 32),
        ("fire_resistance", {"id": "minecraft:potion", "count": 1,
                             "components": {"minecraft:potion_contents": {"potion": "minecraft:long_fire_resistance"}}}, 2),
        ("path_blocks", {"id": "minecraft:cobblestone", "count": 1}, 64),
    ])
    add("survey", "下界 · 从岩浆块取样", [
        "主手拿调谐器、副手放共振胶，右键下界的岩浆块。一次带回 2 份热样胶，够在压机下封两餐。"],
        "world_progression_core:thermal_binder", uid("prepare"), "thermal_survey", pos=(2.5, -1))
    add("cook", "后方 · 备一锅饭", [
        "在热源上架起厨锅。蔬菜汤、鸡汤、炖牛肉、炒饭任选一道，盛出送到工坊。家里有生牛肉、胡萝卜和马铃薯，就做炖牛肉。"],
        "farmersdelight:cooking_pot", uid("prepare"), "field_kitchen", pos=(2.5, 1))
    add("pack", "工坊 · 压封行军餐", [
        "把 1 份料理、1 张铜板、1 份热样胶投入压机下的工作盆。压好行军餐，带一份出门；每份补充 8 点饥饿值，还能抗火 90 秒。",
        "接下来任选一职。工兵留三盏引路灯，先锋吃下行军餐试战。两项都做，可以再接一单后队口粮。"],
        "world_progression_core:sealed_ration", [uid("survey"), uid("cook")], "pack_ration", subtitle="两路物资在压机下汇合",
        rewards=[("copper", {"id": "minecraft:copper_ingot", "count": 1}, 16),
                 ("chute", {"id": "create:chute", "count": 1}, 4)], pos=(5, 0))
    add("beacons", "工兵 · 给退路点灯", [
        "沿下界退路放三盏灯笼，可混用灵魂灯笼。手持调谐器依次右键，每两站相隔 12～32 格，终点距起点至少 24 格。三盏都留在原位。"],
        "minecraft:lantern", uid("pack"), "beacons",
        rewards=[("road", {"id": "minecraft:polished_blackstone_bricks", "count": 1}, 64)], pos=(7.5, -1.5), optional=True)
    add("vanguard", "先锋 · 耐火试战", [
        "在下界吃一份热封行军餐，趁这 90 秒抗火时间击败烈焰人或岩浆怪。"],
        "minecraft:blaze_rod", uid("pack"), "vanguard",
        rewards=[("supply", {"id": "minecraft:golden_carrot", "count": 1}, 24)], pos=(7.5, 1.5), optional=True)
    add("flesh", "击败血肉首领", [
        "在下界击败巫毒恶魔可拿到向导巫毒玩偶。看玩偶的提示选好形态，准备就绪再投进岩浆。",
        "血肉墙或血肉山，击败其中一位。拿到宝藏袋就先开袋，收好下一趟敲祭坛要用的神锤。"],
        "confluence:wall_of_flesh_treasure_bag", [uid("beacons"), uid("vanguard")], "flesh_defeated", pos=(10, 0),
        either=True, subtitle="世界转折 · 血肉墙、血肉山任选一战")
    add("doll", "拾回熔岩边的玩偶", [
        "看准巫毒恶魔脚下的地面再交手，把掉落的向导巫毒玩偶收进背包。领走三盏灯笼，正好够替工兵标一条退路。"],
        "confluence:guide_voodoo_doll", uid("prepare"), item_task=("confluence:guide_voodoo_doll", 1, False),
        rewards=[("lanterns", {"id": "minecraft:lantern", "count": 1}, 3)], pos=(4.5, -3), optional=True)
    add("rearguard", "留给后队的一餐", [
        "沿灯走过、在火里试过，这条路才好交给下一班。再封一份行军餐，在任务书中提交给后队。",
        "领到的两只末影箱可一只留在工坊、一只带到前线。同一个人的两只箱子相通，赶路时能随手换装备。"],
        "minecraft:ender_chest", [uid("beacons"), uid("vanguard")], item_task=("world_progression_core:sealed_ration", 1, True),
        rewards=[("field_chests", {"id": "minecraft:ender_chest", "count": 1}, 2)], pos=(10, 3), optional=True,
        subtitle="双线委托 · 提交 1 份热封行军餐")
    add("transition", "世界已进入困难模式", [
        "上次归档的坐标还在，地图外的土地却已经开始变化。带档案书回档案台查看转化进度，再拿神锤去找祭坛。"],
        "confluence:pwnhammer", uid("flesh"), "hardmode_open", pos=(12.5, 0), subtitle="战后归档 · 困难模式开启")
    add("charge", "回程 · 给地狱熔炉配料", [
        "用神锤敲祭坛显出矿物。把 6 份同种粗钴或粗钯金放入工作盆，用机械搅拌器冷拌成一块炉料。"], "world_progression_core:cobalt_furnace_charge", uid("transition"), "batch_charge", pos=(15, 0))
    add("first_ingot", "第一炉新矿", [
        "把炉料送入有燃料的地狱熔炉，一炉出 2 个对应金属锭。拿到成品，就可以铸造第一套困难模式装备。"], "confluence:cobalt_ingot", uid("charge"), "first_ingot", rewards=[
        ("mending", {"id": "minecraft:enchanted_book", "count": 1,
                     "components": {"minecraft:stored_enchantments": {"levels": {"minecraft:mending": 1}}}}, 1),
        ("experience", {"id": "minecraft:experience_bottle", "count": 1}, 32),
        ("nether_bricks", {"id": "minecraft:nether_bricks", "count": 1}, 128),
    ], pos=(17.5, 0))
    return chapter, lang


def main():
    chapter, lang = generate()
    finish_chapter(chapter, lang)
    objects = [chapter]
    for quest in chapter["quests"]:
        objects.extend((quest, *quest["tasks"], *quest["rewards"]))
    ids = {obj["id"] for obj in objects}
    assert len(ids) == len(objects) and all(0 < int(key, 16) < 2**63 for key in ids)

    existing_ids = set()
    for path in (QUESTS / "chapters").glob("*.snbt"):
        if path.stem == FILENAME:
            continue
        existing = json.loads(path.read_text("utf-8-sig"))
        existing_ids.add(existing["id"])
        for quest in existing["quests"]:
            existing_ids.add(quest["id"])
            for reward in quest["rewards"]:
                existing_ids.add(reward["id"])
                existing_ids.update(item["id"] for item in reward.get("table_data", {}).get("rewards", ()))
            existing_ids.update(task["id"] for task in quest["tasks"])
    assert not ids.intersection(existing_ids)
    reachable = set(existing_ids)
    for quest in chapter["quests"]:
        assert set(quest["dependencies"]) <= reachable, "Missing World Turn prerequisite"
        reachable.add(quest["id"])

    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)
    for name in ("thermal_survey", "flesh_defeated", "hardmode_open", "first_ingot", "beacons", "vanguard"):
        write(DATA / f"advancement/world_turn/{name}.json",
              {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    write(DATA / "tags/item/world_turn_ingots.json", {"replace": False, "values": [
        {"id": "confluence:cobalt_ingot", "required": False},
        {"id": "confluence:palladium_ingot", "required": False},
    ]})
    supply_resources()
    print(f"World Turn: {len(chapter['quests'])} quests")


def supply_resources():
    mod = "world_progression_core:"
    meals = ["farmersdelight:" + name for name in ("vegetable_soup", "chicken_soup", "beef_stew", "fried_rice")]
    write(DATA / "tags/item/field_meals.json", {"replace": False, "values": [
        {"id": meal, "required": False} for meal in meals]})
    write(DATA / "recipe/sealed_ration.json", {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "farmersdelight"}],
        "type": "create:compacting", "ingredients": [
            {"tag": mod + "field_meals"}, {"item": mod + "thermal_binder"}, {"item": "create:copper_sheet"}],
        "results": [{"id": mod + "sealed_ration", "count": 1}],
    })
    for metal in ("cobalt", "palladium"):
        charge = mod + metal + "_furnace_charge"
        write(DATA / f"recipe/mix_{metal}_charge.json", {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}],
            "type": "create:mixing", "ingredients": [{"item": "confluence:raw_" + metal}] * 6,
            "results": [{"id": charge, "count": 1}],
        })
        write(DATA / f"recipe/smelt_{metal}_charge.json", {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}],
            "type": "confluence:hellforge", "ingredients": [{"item": charge}],
            "result": {"id": "confluence:" + metal + "_ingot", "count": 2}, "experience": 1.0, "requires_fuel": True,
        })
    for adv, items in (("field_kitchen", "#" + mod + "field_kitchen_proof"), ("pack_ration", [mod + "sealed_ration"]),
                       ("batch_charge", [mod + "cobalt_furnace_charge", mod + "palladium_furnace_charge"])):
        predicate = {"items": items}
        write(DATA / f"advancement/world_turn/{adv}.json", {"criteria": {"done": {
            "trigger": "minecraft:inventory_changed", "conditions": {"items": [predicate]}}}})
    write(DATA / "tags/item/field_kitchen_proof.json", {"replace": False, "values": [
        "#" + mod + "field_meals", mod + "sealed_ration"]})
    supply_assets()


def supply_assets():
    names = {"thermal_binder": ("热样胶", "Thermal Binder"), "sealed_ration": ("热封行军餐", "Sealed Field Ration"),
             "cobalt_furnace_charge": ("钴炉料", "Cobalt Furnace Charge"),
             "palladium_furnace_charge": ("钯金炉料", "Palladium Furnace Charge")}
    shapes = [
        ["................","................","........dd......",".......dhhd.....",".....ddhhlhd....","....dhhhhlhhd...",
         "...dhllhhhhlhd..","...dhhlhhhhhhhd.","..dhhhhhlhhhlhd.","..dhhhlhhhhlhhd.","..dhhhhlhhhhhhd.",
         "...dshhhhhhhhd..","....dsshhhhhd...",".....ddddddd....","................","................"],
        ["................","................","....dddddddd....","...dhhhhhhhhd...","..dlllllllllld..",
         "..dshhhhhhhlhd..","..dhhddddddhhd..","..dhhllllllhhd..","..dhhllhhllhhd..","..dhhllllllhhd..",
         "..dhhddddddhhd..","..dshhhhhhhlhd..","...dssssssssd...","....dddddddd....","................","................"],
        ["................","................","......dddd......","....ddlllhdd....","...dhlhhhlhhd...",
         "..dhhhhhlhhhsd..",".dhhlhhhhhlhhsd.",".dlllllllllllld.",".dssssssssssssd.",".dhhhhlhhhhhhhd.",
         "..dshhhhhlhhhd..","...dshlhhhhsd...","....ddssssdd....","......dddd......","................","................"],
    ]
    colors = [("5f2624","b74724","ffcc60","73302a"), ("52332e","b86941","ffe2a3","824235"),
              ("23394a","527e9f","a8d7f3","384e62"), ("482b32","c1705e","ffd3a1","835253")]
    for index, name in enumerate(names):
        write(ASSETS / f"models/item/{name}.json", {"parent":"minecraft:item/generated",
              "textures":{"layer0":"world_progression_core:item/" + name}})
        sprite(name, shapes[min(index, 2)], dict(zip("dhls", colors[index])))
    for locale, index in (("zh_cn", 0), ("en_us", 1)):
        merge(ASSETS / f"lang/{locale}.json", {"item.world_progression_core." + name: label[index]
              for name, label in names.items()})


if __name__ == "__main__":
    main()
