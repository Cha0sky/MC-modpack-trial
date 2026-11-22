"""Author the first hardmode loadout and the advancement predicates that verify it."""

from build_heritage_quests import DATA, QUESTS, merge, uid as quest_id, write
from build_world_turn_quests import uid as world_turn_id
from build_gear_guide import guide_link

MOD = "world_progression_core"
FILENAME = "04_field_equipment"
TOOL_TAG = f"{MOD}:field_equipment/mining_tools"
BENCH = "confluence:heavy_work_bench"


def uid(name):
    return quest_id(name, "lostworld.field_equipment.v1/")


def equipment(gear):
    return [{"condition": "minecraft:entity_properties", "entity": "this",
             "predicate": {"equipment": {slot: {"items": items} for slot, items in gear.items()}}}]


def armor(metal, headpieces):
    return {
        "head": [f"confluence:{metal}_{head}" for head in headpieces],
        "chest": f"confluence:{metal}_chestplate",
        "legs": f"confluence:{metal}_leggings",
        "feet": f"confluence:{metal}_boots",
    }


def advancements():
    suits = {"cobalt": armor("cobalt", ["helmet", "hat", "mask"]),
             "palladium": armor("palladium", ["helmet", "headgear", "mask"])}
    targets = ["minecraft:" + mob for mob in ("zombie", "husk", "skeleton", "stray", "spider", "pillager")]
    bench_location = [{"condition": "minecraft:block_state_property", "block": BENCH}]
    return {
        "bench": {
            "obtained": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": BENCH}]}},
            "opened": {"trigger": "minecraft:default_block_use", "conditions": {"location": bench_location}},
        },
        "tool": {"done": {"trigger": "minecraft:location", "conditions": {
            "player": equipment({"mainhand": "#" + TOOL_TAG})}}},
        "suit": {metal: {"trigger": "minecraft:location", "conditions": {"player": equipment(slots)}}
                 for metal, slots in suits.items()},
        "prospect": {"done": {"trigger": "minecraft:impossible"}},
        "patrol": {
            group + "_" + metal: {"trigger": "minecraft:player_killed_entity", "conditions": {
                "player": equipment(slots), "entity": {"type": ["minecraft:" + mob for mob in mobs]}}}
            for group, mobs in (("melee", ("zombie", "husk", "spider")), ("ranged", ("skeleton", "stray", "pillager")))
            for metal, slots in suits.items()
        },
        "trial": {
            "done": {"trigger": "minecraft:impossible"},  # A successful ore harvest, awarded by FieldEquipmentEvents.
            **{metal: {"trigger": "minecraft:player_killed_entity",
                       "conditions": {"player": equipment(slots), "entity": {"type": targets}}}
               for metal, slots in suits.items()},
        },
        "anvil": {
            "obtained": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
                {"items": ["confluence:mythril_anvil", "confluence:orichalcum_anvil"]}]}},
            "placed": {"trigger": "minecraft:placed_block", "conditions": {"location": [
                {"condition": "minecraft:location_check", "predicate": {"block": {
                    "blocks": ["confluence:mythril_anvil", "confluence:orichalcum_anvil"]}}}]}},
        },
    }


def main():
    chapter = {"id": uid("chapter"), "filename": FILENAME, "order_index": 3,
               "icon": {"id": BENCH}, "progression_mode": "linear", "quests": []}
    lang = {f"chapter.{chapter['id']}.title": "第四章 · 困难模式整备"}

    def quest(name, title, lines, icon, x, y, dependencies, rewards, *, optional=False, either=False):
        qid = uid(name)
        node = {"id": qid, "x": x, "y": y, "icon": {"id": icon}, "min_width": 330,
                "progression_mode": "linear", "dependency_requirement": "all_completed",
                "dependencies": dependencies, "optional": optional,
                "tasks": [{"id": uid(name + "/task"), "type": "advancement",
                           "advancement": f"{MOD}:field_equipment/{name}"}],
                "rewards": [{"id": uid(name + "/reward/" + item), "type": "item",
                             "item": {"id": item, "count": 1}, "count": count} for item, count in rewards]}
        if either:
            node["min_required_dependencies"] = 1
        chapter["quests"].append(node)
        lang[f"quest.{qid}.title"] = title
        lang[f"quest.{qid}.quest_desc"] = lines

    quest("bench", "把新矿带上工作台", [
        "在工作台里竖排叠放工作台、铁砧或铅砧、工作台，做成重型工作台。家里已经有的话，空手右键打开。"], BENCH, 0, 0, [world_turn_id("first_ingot")], [("minecraft:coal", 32), ("minecraft:oak_log", 64)])
    quest("tool", "采掘 · 换上新工具", [
        "选一件拿到主手。钴镐用 15 锭、钴钻头用 12 锭；钯金镐和钻头各用 18 锭。做镐另备 3 根珍珠木棍。"], "confluence:cobalt_drill", 2, -1, [uid("bench")],
          [("minecraft:torch", 64), ("minecraft:rail", 48)], optional=True)
    quest("suit", "战斗 · 选一套合身的盔甲", [
        "穿齐钴甲或钯金甲的头、胸、腿、靴。想选头部款式，可先比较套装效果。",
        guide_link("查手册 · 钴甲与钯金甲", "armor")], "confluence:palladium_chestplate", 2, 1, [uid("bench")],
          [("minecraft:golden_carrot", 32), ("minecraft:arrow", 64)], optional=True)
    quest("prospect", "采掘队 · 探到第二层矿脉", [
        "主手用钴或钯金镐钻，从深板岩中采出一块秘银矿或山铜矿。神锤敲开更多祭坛，可以显出下一层矿脉。"],
        "confluence:raw_mythril", 4, -2, [uid("tool")], [("minecraft:rail", 64), ("minecraft:diamond", 3)], optional=True)
    quest("patrol", "护卫队 · 两种交锋", [
        "穿齐钴甲或钯金甲。击败一只近敌（僵尸、尸壳、蜘蛛）和一只射手（骷髅、流浪者、掠夺者），两场可换武器。"],
        "minecraft:shield", 4, 2, [uid("suit")], [("minecraft:golden_apple", 3), ("minecraft:experience_bottle", 24)], optional=True)
    quest("trial", "把整备用起来", [
        "采掘队带新镐或钻头下矿，护卫队穿整套新甲巡夜。完成其中一队的实地工作，就能领物资准备遗址远征。"], "minecraft:golden_apple", 6, 0, [uid("prospect"), uid("patrol")],
          [("minecraft:iron_block", 8), ("minecraft:experience_bottle", 32), ("minecraft:golden_apple", 6)], either=True)
    quest("anvil", "选修 · 下一阶锻造", [
        "用重型工作台把 10 秘银锭或 12 山铜锭，搭配 3 块珍珠木板，做成对应的困难砧。"], "confluence:mythril_anvil", 8, -1, [uid("trial")],
          [("minecraft:anvil", 2), ("minecraft:obsidian", 32)], optional=True)

    resources = advancements()
    for name, criteria in resources.items():
        requirements = ([["melee_cobalt", "melee_palladium"], ["ranged_cobalt", "ranged_palladium"]]
                        if name == "patrol" else [list(criteria)])
        write(DATA / f"advancement/field_equipment/{name}.json", {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}],
            "criteria": criteria, "requirements": requirements,
        })
    write(DATA / "tags/item/field_equipment/mining_tools.json", {"replace": False, "values": [
        {"id": f"confluence:{metal}_{tool}", "required": False}
        for metal in ("cobalt", "palladium") for tool in ("pickaxe", "drill")]})
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
