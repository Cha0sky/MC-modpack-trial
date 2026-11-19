"""Author the first hardmode loadout and the advancement predicates that verify it."""

from build_heritage_quests import DATA, QUESTS, merge, uid as quest_id, write
from build_world_turn_quests import uid as world_turn_id

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
        "取得第三章的第一份新锭后，准备重型工作台。普通工作台中竖排放入工作台、铁砧或铅砧、工作台即可制作。",
        "拿到重型工作台或空手右键打开已放好的工作台，都能留下记录。",
        "接下来任选采掘或战斗路线。只有最后的下一阶锻造任务会涉及秘银或山铜。"
    ], BENCH, 0, 0, [world_turn_id("first_ingot")], [("minecraft:coal", 32), ("minecraft:oak_log", 64)])
    quest("tool", "采掘 · 换上新工具", [
        "在重型工作台制作钴镐、钴钻头、钯金镐或钯金钻头。把其中一件拿在主手，稍等片刻即可记录。",
        "钴镐需要 15 个钴锭、3 根珍珠木棍；钴钻头用 12 个钴锭。钯金镐需要 18 个钯金锭、3 根珍珠木棍，钯金钻头用 18 个钯金锭。",
        "镐柄用的珍珠木棍可在重型工作台用 3 块珍珠木板制成 6 根。钻头路线可以直接使用金属锭。"
    ], "confluence:cobalt_drill", 2, -1, [uid("bench")],
          [("minecraft:torch", 64), ("minecraft:rail", 48)], optional=True)
    quest("suit", "战斗 · 选一套合身的盔甲", [
        "在重型工作台制作钴或钯金护甲。穿上同一材质的头部、胸甲、护腿、靴子四件，稍等片刻即可登记。",
        "头部按流派选。钴可用头盔、钴帽或面具，钯金可用头盔、头饰或面具。一整套钴甲共 45 锭，钯金甲共 54 锭。",
        "按你原有的战斗习惯搭配成长剑、弓或法术。想继续使用其他护甲也可以走采掘路线。"
    ], "confluence:palladium_chestplate", 2, 1, [uid("bench")],
          [("minecraft:golden_carrot", 32), ("minecraft:arrow", 64)], optional=True)
    quest("trial", "把整备用起来", [
        "任选一种实际行动。采掘路线在生存模式用上述镐或钻头挖一块有矿物掉落的矿石，例如铁矿。",
        "战斗路线穿齐上述任一套装，击败一只僵尸、尸壳、骷髅、流浪者、蜘蛛或掠夺者。可自由选择武器。",
        "完成一条路线即可领取扩建与远征补给。其他路线和下一阶锻造可以随后继续。"
    ], "minecraft:golden_apple", 4, 0, [uid("tool"), uid("suit")],
          [("minecraft:iron_block", 8), ("minecraft:experience_bottle", 32), ("minecraft:golden_apple", 6)], either=True)
    quest("anvil", "选修 · 下一阶锻造", [
        "继续按原生祭坛线索寻找下一层矿物，准备秘银砧或山铜砧。取得或放置其中一座即可记录。",
        "重型工作台配方分别需要 10 个秘银锭或 12 个山铜锭，另加 3 块珍珠木板。查看 JEI 安排采集与熔炼。"
    ], "confluence:mythril_anvil", 6, -1, [uid("trial")],
          [("minecraft:anvil", 2), ("minecraft:obsidian", 32)], optional=True)

    resources = advancements()
    for name, criteria in resources.items():
        write(DATA / f"advancement/field_equipment/{name}.json", {
            "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "confluence"}],
            "criteria": criteria, "requirements": [list(criteria)],
        })
    write(DATA / "tags/item/field_equipment/mining_tools.json", {"replace": False, "values": [
        {"id": f"confluence:{metal}_{tool}", "required": False}
        for metal in ("cobalt", "palladium") for tool in ("pickaxe", "drill")]})
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
