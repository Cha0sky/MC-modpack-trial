"""Generate World Turn quests, field supplies and bulk hellforge recipes."""

import json
from build_heritage_quests import ASSETS, DATA, QUESTS, merge, uid as quest_id, write
from build_workshop_quests import sprite

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

    def add(name, title, description, icon, dependency, advancement=None, rewards=(), pos=None, optional=False, either=False):
        qid = uid(name)
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

    add("prepare", "去往下界之前", [
        "带粗制调谐器和 1 份共振胶进入下界，领好远征石料。血肉首领倒下后，世界将进入困难模式，部分地形会随之转化。"], "confluence:guide_voodoo_doll", previous, rewards=[
        ("food", {"id": "minecraft:golden_carrot", "count": 1}, 32),
        ("fire_resistance", {"id": "minecraft:potion", "count": 1,
                             "components": {"minecraft:potion_contents": {"potion": "minecraft:long_fire_resistance"}}}, 2),
        ("path_blocks", {"id": "minecraft:cobblestone", "count": 1}, 64),
    ])
    add("survey", "下界 · 从岩浆块取样", [
        "在下界主手持调谐器、副手放共振胶，右键岩浆块。1 份胶能取到 2 份热样胶。"], "world_progression_core:thermal_binder", uid("prepare"), "thermal_survey", pos=(2.5, -1))
    add("cook", "后方 · 备一锅饭", [
        "在热源上架厨锅，煮蔬菜汤、鸡汤、炖牛肉或炒饭，盛出一份。炖牛肉可用生牛肉、胡萝卜和马铃薯。"], "farmersdelight:cooking_pot", uid("prepare"), "field_kitchen", pos=(2.5, 1))
    add("pack", "工坊 · 压封行军餐", [
        "给压机下的工作盆放入 1 份料理、1 张铜板和 1 份热样胶。把压好的行军餐带到前线；吃下恢复 8 点饥饿值，并获得 90 秒抗火。"], "world_progression_core:sealed_ration", [uid("survey"), uid("cook")], "pack_ration",
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
        "看向导巫毒玩偶的提示选择形态，把玩偶投入下界岩浆。击败血肉墙或血肉山，收好掉落的神锤。"], "confluence:wall_of_flesh_treasure_bag", [uid("beacons"), uid("vanguard")], "flesh_defeated", pos=(10, 0), either=True)
    add("transition", "世界已进入困难模式", [
        "世界进入困难模式后，带档案书回档案台查看地形转化进度。下一趟带神锤去找祭坛。"], "confluence:pwnhammer", uid("flesh"), "hardmode_open", pos=(12.5, 0))
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
