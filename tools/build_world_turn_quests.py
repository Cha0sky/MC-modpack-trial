"""Generate the four World Turn quests and their advancement definitions."""

import json
from build_heritage_quests import DATA, QUESTS, merge, uid as quest_id, write

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

    def add(name, title, description, icon, dependency, advancement=None, rewards=()):
        qid = uid(name)
        task = {"id": uid(name + "/task"), "type": "checkmark"}
        if advancement:
            task.update(type="advancement", advancement="world_progression_core:world_turn/" + advancement,
                        criterion="done")
        chapter["quests"].append({
            "id": qid, "x": len(chapter["quests"]) * 2.5, "y": 0,
            "icon": {"id": icon}, "min_width": 340,
            "dependencies": [dependency], "dependency_requirement": "all_completed",
            "progression_mode": "linear", "tasks": [task],
            "rewards": [{"id": uid(name + "/reward/" + key), "type": "item", "item": stack, "count": count}
                        for key, stack, count in rewards],
        })
        lang["quest." + qid + ".title"] = title
        lang["quest." + qid + ".quest_desc"] = description

    add("prepare", "去往下界之前", [
        "从遗产档案继续远征，准备好装备、照明和可返回的下界通道。勾选后可以领取金胡萝卜、抗火药水和搭路石料。",
        "击败血肉首领会开启汇流来世的困难模式，随后部分主世界地形会发生变化。想先试打时，请使用备份存档。",
        "带上向导巫毒玩偶。召唤方式见下一节点。",
    ], "confluence:guide_voodoo_doll", previous, rewards=[
        ("food", {"id": "minecraft:golden_carrot", "count": 1}, 32),
        ("fire_resistance", {"id": "minecraft:potion", "count": 1,
                             "components": {"minecraft:potion_contents": {"potion": "minecraft:long_fire_resistance"}}}, 2),
        ("path_blocks", {"id": "minecraft:cobblestone", "count": 1}, 64),
    ])
    add("flesh", "击败血肉首领", [
        "按向导巫毒玩偶的提示选择形态，再把玩偶投入下界岩浆。击败血肉墙或血肉山中的任意一位。",
        "任务读取汇流来世的世界通关记录，你可以和同伴一起打。过去已经完成的挑战也会计入。",
        "查看原生战利品并收好神锤，下一步可以用它显现新矿。",
    ], "confluence:wall_of_flesh_treasure_bag", uid("prepare"), "flesh_defeated")
    add("transition", "世界已进入困难模式", [
        "当血肉首领的战果和困难模式状态都已记录，本节点会自动完成。",
        "世界可能还在分批转换地形。带着遗产档案书使用档案台，可以分别查看首领战果、困难模式状态和地形转换进度。",
        "准备神锤和采矿装备，就可以开始寻找新材料。",
    ], "confluence:pwnhammer", uid("flesh"), "hardmode_open")
    add("first_ingot", "第一炉新矿", [
        "用神锤破坏恶魔祭坛或猩红祭坛，按汇流来世的提示显现矿物，再下矿寻找粗钴或粗钯金。",
        "在地狱熔炉里，每 3 份同类粗矿可熔成 1 份钴锭或钯金锭。背包中取得任一种锭即可记录。",
        "把新材料带回基地，开始准备下一轮完整配装。可领取经验修补书、经验瓶和地狱砖。",
    ], "confluence:cobalt_ingot", uid("transition"), "first_ingot", rewards=[
        ("mending", {"id": "minecraft:enchanted_book", "count": 1,
                     "components": {"minecraft:stored_enchantments": {"levels": {"minecraft:mending": 1}}}}, 1),
        ("experience", {"id": "minecraft:experience_bottle", "count": 1}, 32),
        ("nether_bricks", {"id": "minecraft:nether_bricks", "count": 1}, 128),
    ])
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
    for name in ("flesh_defeated", "hardmode_open", "first_ingot"):
        write(DATA / f"advancement/world_turn/{name}.json",
              {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    write(DATA / "tags/item/world_turn_ingots.json", {"replace": False, "values": [
        {"id": "confluence:cobalt_ingot", "required": False},
        {"id": "confluence:palladium_ingot", "required": False},
    ]})
    print("World Turn: 4 quests, archive prerequisite and reward IDs verified")


if __name__ == "__main__":
    main()
