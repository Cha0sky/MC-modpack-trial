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

    def add(name, title, description, icon, dependency, advancement=None, rewards=(), pos=None):
        qid = uid(name)
        task = {"id": uid(name + "/task"), "type": "checkmark"}
        if advancement:
            task.update(type="advancement", advancement="world_progression_core:world_turn/" + advancement,
                        criterion="done")
        x, y = pos or (len(chapter["quests"]) * 2.5, 0)
        chapter["quests"].append({
            "id": qid, "x": x, "y": y,
            "icon": {"id": icon}, "min_width": 340,
            "dependencies": [dependency] if isinstance(dependency, str) else dependency,
            "dependency_requirement": "all_completed",
            "progression_mode": "linear", "tasks": [task],
            "rewards": [{"id": uid(name + "/reward/" + key), "type": "item", "item": stack, "count": count}
                        for key, stack, count in rewards],
        })
        lang["quest." + qid + ".title"] = title
        lang["quest." + qid + ".quest_desc"] = description

    add("prepare", "去往下界之前", [
        "从遗产档案继续远征，准备好装备、照明和可返回的下界通道。勾选后可以领取金胡萝卜、抗火药水和搭路石料。",
        "击败血肉首领会开启汇流来世的困难模式，随后部分主世界地形会发生变化。想先试打时，请使用备份存档。",
        "先分头勘察下界热源、在厨房备餐，再回工坊压封补给。勘察时带上粗制调谐器和至少 1 份共振胶。",
    ], "confluence:guide_voodoo_doll", previous, rewards=[
        ("food", {"id": "minecraft:golden_carrot", "count": 1}, 32),
        ("fire_resistance", {"id": "minecraft:potion", "count": 1,
                             "components": {"minecraft:potion_contents": {"potion": "minecraft:long_fire_resistance"}}}, 2),
        ("path_blocks", {"id": "minecraft:cobblestone", "count": 1}, 64),
    ])
    add("survey", "下界 · 从岩浆块取样", [
        "先去下界走一趟，选好传送门和返回路线。主手拿粗制调谐器，副手放共振胶，右键下界的岩浆块。",
        "1 份共振胶可采到 2 份热样胶，足够压封两餐。岩浆块保留，可以再次取样。",
    ], "world_progression_core:thermal_binder", uid("prepare"), "thermal_survey", pos=(2.5, -1))
    add("cook", "后方 · 备一锅饭", [
        "在热源上架起农夫乐事的厨锅，任选蔬菜汤、鸡汤、炖牛肉或炒饭，盛碗后带到压机旁。",
        "炖牛肉可用生牛肉、胡萝卜和马铃薯；也可以按 JEI 用自家农产做蔬菜汤。取得其中一份即可记入，队友可以负责厨房。",
    ], "farmersdelight:cooking_pot", uid("prepare"), "field_kitchen", pos=(2.5, 1))
    add("pack", "工坊 · 压封行军餐", [
        "在机械压机下方放工作盆。每次投入 1 份热样胶、1 张铜板和 1 份备好的料理，压出 1 份热封行军餐。可以手动投料，也可以用漏斗或传送带接入。",
        "将成品拿到背包里。吃一餐恢复 8 点饥饿值并获得 90 秒抗火，饱食时也能使用。另一份热样胶可以留给下次远征。",
    ], "world_progression_core:sealed_ration", [uid("survey"), uid("cook")], "pack_ration",
        rewards=[("copper", {"id": "minecraft:copper_ingot", "count": 1}, 16),
                 ("chute", {"id": "create:chute", "count": 1}, 4)], pos=(5, 0))
    add("flesh", "击败血肉首领", [
        "按向导巫毒玩偶的提示选择形态，再把玩偶投入下界岩浆。击败血肉墙或血肉山中的任意一位。",
        "任务读取汇流来世的世界通关记录，你可以和同伴一起打。过去已经完成的挑战也会计入。",
        "查看原生战利品并收好神锤，下一步可以用它显现新矿。",
    ], "confluence:wall_of_flesh_treasure_bag", uid("pack"), "flesh_defeated", pos=(7.5, 0))
    add("transition", "世界已进入困难模式", [
        "当血肉首领的战果和困难模式状态都已记录，本节点会自动完成。",
        "世界可能还在分批转换地形。带着遗产档案书使用档案台，可以分别查看首领战果、困难模式状态和地形转换进度。",
        "准备神锤和采矿装备，就可以开始寻找新材料。",
    ], "confluence:pwnhammer", uid("flesh"), "hardmode_open", pos=(10, 0))
    add("charge", "回程 · 给地狱熔炉配料", [
        "拿神锤敲祭坛显现新矿，带回 6 份粗钴或 6 份粗钯金。在基地把这批矿交给机械搅拌器与工作盆。",
        "6 份同种粗矿一次混成 1 块对应炉料，无需加热或注液。首批先拿到背包里登记，再送进地狱熔炉；以后可以直接连续供料。",
    ], "world_progression_core:cobalt_furnace_charge", uid("transition"), "batch_charge", pos=(12.5, 0))
    add("first_ingot", "第一炉新矿", [
        "把搅拌好的炉料和燃料送入地狱熔炉。一块炉料一次熔出 2 个对应金属锭。原生散矿配方仍可用，3 粗矿出 1 锭。",
        "把钴锭或钯金锭收进背包，下一章从重型工作台继续。已有原生战果和新锭的玩家可以使用原有物资补做前面的工序。",
        "把新材料带回基地，开始准备下一轮完整配装。可领取经验修补书、经验瓶和地狱砖。",
    ], "confluence:cobalt_ingot", uid("charge"), "first_ingot", rewards=[
        ("mending", {"id": "minecraft:enchanted_book", "count": 1,
                     "components": {"minecraft:stored_enchantments": {"levels": {"minecraft:mending": 1}}}}, 1),
        ("experience", {"id": "minecraft:experience_bottle", "count": 1}, 32),
        ("nether_bricks", {"id": "minecraft:nether_bricks", "count": 1}, 128),
    ], pos=(15, 0))
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
    for name in ("thermal_survey", "flesh_defeated", "hardmode_open", "first_ingot"):
        write(DATA / f"advancement/world_turn/{name}.json",
              {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    write(DATA / "tags/item/world_turn_ingots.json", {"replace": False, "values": [
        {"id": "confluence:cobalt_ingot", "required": False},
        {"id": "confluence:palladium_ingot", "required": False},
    ]})
    supply_resources()
    print("World Turn: 8 quests, archive prerequisite and reward IDs verified")


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
