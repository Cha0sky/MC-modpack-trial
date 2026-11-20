"""Author the three-route heritage chapter without changing previous quest IDs.

The core owns route facts and the two-of-three milestone. FTB shows the same
dependency threshold and keeps every route optional for chapter completion.
"""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
QUESTS = ROOT / "staging/config/ftbquests/quests"
RES = ROOT / "modules/world-progression-core/src/main/resources"
DATA = RES / "data/world_progression_core"
ASSETS = RES / "assets/world_progression_core"
MOD = "world_progression_core:"
ADVANCEMENTS = (
    "welcome", "engineering_heat", "engineering_assembly", "engineering_record",
    "arcane_foundation", "arcane_practice", "arcane_record",
    "expedition_sites", "expedition_challenge", "expedition_record",
    "convergence", "archive_craft", "archive_open", "all_three", "map_wall",
)
ROUTE_RECORDS = ("engineering_record", "arcane_record", "expedition_record")


def uid(name, namespace="lostworld.heritage.v1/"):
    value = int(hashlib.sha256((namespace + name).encode()).hexdigest()[:16], 16)
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def merge(path, additions):
    data = json.loads(path.read_text("utf-8-sig")) if path.exists() else {}
    data.update(additions)
    write(path, data)


def item_reward(name, item, count, components=None):
    stack={"id": item, "count": 1}
    if components:
        stack["components"]=components
    return {"id": uid(name), "type": "item", "item": stack, "count": count}


def milestone_supplies(name):
    # Keep the original reward prefix and its IDs/counts intact for existing saves.
    packs = {
        "welcome": [("minecraft:cooked_beef", 32), ("minecraft:torch", 64), ("minecraft:scaffolding", 128)],
        "engineering_heat": [("create:zinc_ingot", 16), ("minecraft:copper_ingot", 32), ("minecraft:redstone", 32), ("minecraft:quartz", 8)],
        "engineering_record": [("create:mechanical_arm", 1), ("create:brass_funnel", 8), ("create:belt_connector", 16), ("create:iron_sheet", 32)],
        "arcane_record": [("minecraft:enchanting_table", 1), ("minecraft:bookshelf", 16), ("minecraft:lapis_lazuli", 32), ("minecraft:experience_bottle", 24)],
        "expedition_sites": [("minecraft:golden_apple", 4), ("minecraft:cooked_beef", 64), ("minecraft:arrow", 64)],
        "expedition_record": [("minecraft:diamond_boots", 1), ("minecraft:golden_carrot", 64), ("minecraft:arrow", 64)],
        "convergence": [("minecraft:stone_bricks", 128), ("minecraft:smooth_stone", 128), ("minecraft:oak_log", 128)],
        "archive_open": [("create:item_vault", 8), ("minecraft:diamond_axe", 1), ("minecraft:golden_carrot", 64)],
        "all_three": [("minecraft:enchanted_book", 1), ("minecraft:diamond", 8), ("minecraft:experience_bottle", 32)],
    }
    components = {
        "minecraft:diamond_boots": {"minecraft:enchantments": {"levels": {
            "minecraft:protection": 3, "minecraft:feather_falling": 4, "minecraft:unbreaking": 2}}},
        "minecraft:diamond_axe": {"minecraft:enchantments": {"levels": {
            "minecraft:efficiency": 4, "minecraft:unbreaking": 3}}},
        "minecraft:enchanted_book": {"minecraft:stored_enchantments": {"levels": {"minecraft:mending": 1}}},
    }
    return [item_reward(name + "/supplies_v1/" + item, item, count, components.get(item))
            for item, count in packs.get(name, [])]


def chapter():
    previous = uid("restore", "lostworld.workshop.v1/")
    doc = {"id": uid("chapter"), "filename": "02_heritage_survey", "order_index": 1,
           "icon": {"id": MOD + "heritage_archive"}, "progression_mode": "linear", "quests": []}
    lang = {"chapter." + uid("chapter") + ".title": "第二章 · 遗产调查"}

    def quest(name, title, desc, x, y, icon, needs, rewards=(), optional=False, minimum=0):
        q = {"id": uid(name), "x": x, "y": y, "icon": {"id": icon}, "min_width": 340,
             "dependencies": [previous] if name == "welcome" else [uid(n) for n in needs],
             "dependency_requirement": "all_completed", "progression_mode": "linear", "optional": optional,
             "hide_dependency_lines": False, "hide_until_deps_visible": False,
             "hide_until_deps_complete": False, "hide_lock_icon": False,
             "tasks": [{"id": uid(name + "/task"), "type": "advancement",
                        "advancement": MOD + "heritage/" + name, "criterion": "done"}],
             "rewards": [*rewards, *milestone_supplies(name)]}
        if minimum:
            q["min_required_dependencies"] = minimum
        doc["quests"].append(q)
        lang["quest." + q["id"] + ".title"] = title
        lang["quest." + q["id"] + ".quest_desc"] = desc

    quest("welcome", "维修簿之外", [
        "工坊的维修簿里有三条线索。工程、奥术、远征任选两条完成，就能在档案台汇总发现。队友可分头调查。"],
        0, 0, "minecraft:written_book", ())

    quest("engineering_heat", "工程 · 让火进入工艺", [
        "用空燃烧室在下界捕获烈焰人。给燃烧室添燃料，在上方的工作盆搅拌铜锭和锌锭；每对出 2 黄铜锭，备齐 8 锭。"],
        2, -3, "create:blaze_burner", ("welcome",), optional=True)
    quest("engineering_assembly", "工程 · 可重复的精密工艺", [
        "让机械手运行一次、用注液器加工一次，再取得 1 个精密构件。注液可试着给空玻璃瓶灌 250 mB 蜂蜜。",
        "以金板起步，依次部署小齿轮、大齿轮、铁粒，重复 5 轮。构件成功率为 80%，给流水线留一点余料。"],
        4, -3, "create:precision_mechanism", ("engineering_heat",), optional=True)
    quest("engineering_record", "整理工程记录", [
        "热工和装配记录齐了。把奖励里的机械臂和黄铜漏斗接到生产线，下一批零件就能自动上料。"],
        6, -3, "create:brass_ingot", ("engineering_assembly",), optional=True, rewards=[
            item_reward("engineering_record/brass", "create:brass_ingot", 8),
            item_reward("engineering_record/shaft", "create:shaft", 16),
        ])

    quest("arcane_foundation", "奥术 · 选择研究方法", [
        "选一种研究方法。铁魔法用铭文台准备铁质法术书，再装备到法术书栏；诡厄巫法从黑暗魔杖入手。"],
        2, 0, "minecraft:enchanting_table", ("welcome",), optional=True)
    quest("arcane_practice", "奥术 · 让知识发挥作用", [
        "继续同一种方法。铁魔法从装备好的法术书施放一次法术；诡厄巫法召出僵尸仆从，让它击败一个敌对生物。"],
        4, 0, "minecraft:enchanted_book", ("arcane_foundation",), optional=True)
    quest("arcane_record", "整理奥术记录", [
        "术式已经验证。用领到的附魔台和书架，在基地给法具留一间研究室。"],
        6, 0, "minecraft:book", ("arcane_practice",), optional=True, rewards=[
            item_reward("arcane_record/experience", "minecraft:experience_bottle", 8),
            item_reward("arcane_record/paper", "minecraft:paper", 16),
            item_reward("arcane_record/lapis", "minecraft:lapis_lazuli", 8),
        ])

    quest("expedition_sites", "远征 · 走入两处遗产", [
        "走进两类遗迹，位置就会进入档案。可以选择钢铁守护者室、附魔剑冢、沙漠遗址或古代工厂。",
        "指南针可搜 wrought_chamber、enchanted_sword_shrine、desert_site、ancient_factory。"],
        2, 3, "minecraft:compass", ("welcome",), optional=True)
    quest("expedition_challenge", "远征 · 一次自选挑战", [
        "任选一位对手：钢铁守护者、霜冻巨兽、克苏鲁之眼、世界吞噬怪、克苏鲁之脑或骷髅领主。",
        "想找骷髅领主，可用指南针搜 goety:crypt，到诡厄的大坟墓查看罐子。"],
        4, 3, "minecraft:iron_axe", ("expedition_sites",), optional=True)
    quest("expedition_record", "整理远征记录", [
        "两类遗迹、一场首领战，足够整理第一份远征记录。领到的摔落保护靴适合接着下墓。"],
        6, 3, "minecraft:map", ("expedition_challenge",), optional=True, rewards=[
            item_reward("expedition_record/pearls", "minecraft:ender_pearl", 4),
            item_reward("expedition_record/experience", "minecraft:experience_bottle", 12),
        ])

    quest("convergence", "让两条线索相遇", [
        "任意两份调查齐备，就能建立共同档案。挑个回家经常路过的位置，给档案台留一格。"],
        8, 0, "minecraft:written_book", ROUTE_RECORDS, minimum=2)
    quest("archive_craft", "建一处保存知识的地方", [
        "工作台上排摆书、玻璃、书；中排摆铜板、安山机壳、铜板；下排摆木板、共振胶、木板。做好遗产档案台并放下。"],
        10, 0, MOD + "heritage_archive", ("convergence",))
    quest("archive_open", "我们的遗产档案", [
        "空手右键档案台领取档案书。潜行右键轮换已发现地点，松开潜行，主手持空地图右键制图。档案书带回来还能更新。"],
        12, 0, MOD + "heritage_archive", ("archive_craft",), rewards=[
            item_reward("archive_open/frames", "minecraft:item_frame", 8),
            item_reward("archive_open/lanterns", "minecraft:lantern", 4),
        ])
    quest("all_three", "可选 · 完整的三路调查", [
        "把第三条线索也调查完，补齐工程、奥术和远征三份记录。"],
        10, 4, "minecraft:globe_banner_pattern", ROUTE_RECORDS, optional=True, rewards=[
            {"id": uid("all_three/choice"), "type": "choice", "table_id": 0, "table_data": {
                "loot_size": 1, "rewards": [
                    {**item_reward("all_three/vault", "create:item_vault", 4), "weight": 1.0},
                    {**item_reward("all_three/apples", "minecraft:golden_apple", 4), "weight": 1.0},
                    {**item_reward("all_three/experience", "minecraft:experience_bottle", 24), "weight": 1.0},
                ],
            }},
        ])
    quest("map_wall", "远征档案室 · 地图上墙", [
        "在档案台选一处已发现的遗迹，持空地图绘图。把带目标标记的归档地图放进普通或荧光展示框，给下一队人留下路线。"],
        12, 3, "minecraft:glow_item_frame", ("archive_open",), optional=True,
        rewards=[item_reward("map_wall/frames", "minecraft:glow_item_frame", 6),
                 item_reward("map_wall/maps", "minecraft:map", 16)])
    return doc, lang, previous


def resources():
    for name in ADVANCEMENTS:
        write(DATA / ("advancement/heritage/" + name + ".json"), {
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
    frame_map = '{Item:{id:"minecraft:filled_map",components:{"minecraft:map_decorations":{heritage:{}}}}}'
    write(DATA / "advancement/heritage/map_wall.json", {"criteria": {"done": {
        "trigger": "minecraft:player_interacted_with_entity", "conditions": {
            "item": {"items": "minecraft:filled_map"}, "entity": {
                "type": ["minecraft:item_frame", "minecraft:glow_item_frame"], "nbt": frame_map}}}}})
    write(DATA / "recipe/heritage_archive.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BGB", "CAC", "PRP"],
        "key": {"B": {"item": "minecraft:book"}, "G": {"item": "minecraft:glass"},
                "C": {"item": "create:copper_sheet"}, "A": {"item": "create:andesite_casing"},
                "P": {"tag": "minecraft:planks"}, "R": {"item": MOD + "resonant_binder"}},
        "result": {"id": MOD + "heritage_archive", "count": 1},
    })
    write(DATA / "loot_table/blocks/heritage_archive.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": MOD + "heritage_archive"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }], "random_sequence": MOD + "blocks/heritage_archive",
    })
    axe = RES / "data/minecraft/tags/block/mineable/axe.json"
    tag = json.loads(axe.read_text("utf-8-sig")) if axe.exists() else {"replace": False, "values": []}
    if MOD + "heritage_archive" not in tag["values"]:
        tag["values"].append(MOD + "heritage_archive")
    write(axe, tag)
    write(ASSETS / "models/block/heritage_archive.json", {
        "parent": "minecraft:block/orientable", "textures": {
            "top": "minecraft:block/oak_planks", "front": "minecraft:block/bookshelf",
            "side": "minecraft:block/polished_andesite",
        },
    })
    write(ASSETS / "models/item/heritage_archive.json", {"parent": MOD + "block/heritage_archive"})
    write(ASSETS / "blockstates/heritage_archive.json", {"variants": {
        "facing=" + facing: {"model": MOD + "block/heritage_archive", "y": rotation}
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
    }})
    merge(ASSETS / "lang/zh_cn.json", {"block.world_progression_core.heritage_archive": "遗产档案台"})
    merge(ASSETS / "lang/en_us.json", {"block.world_progression_core.heritage_archive": "Heritage Archive"})


def verify(doc, previous):
    objects = [doc]
    for quest in doc["quests"]:
        objects.extend([quest, *quest["tasks"], *quest["rewards"]])
        for reward in quest["rewards"]:
            objects.extend(reward.get("table_data", {}).get("rewards", []))
    ids = [o["id"] for o in objects]
    assert len(ids) == len(set(ids))
    assert all(0 < int(identifier, 16) <= 0x7FFFFFFFFFFFFFFF for identifier in ids)
    assert len(doc["quests"]) == 15
    nodes = {q["id"]: q for q in doc["quests"]}
    reached = {previous}
    for quest in doc["quests"]:
        assert set(quest["dependencies"]) <= reached, "Missing or cyclic dependency"
        reached.add(quest["id"])
    for name in ADVANCEMENTS[1:10]:
        assert nodes[uid(name)]["optional"], "The third route must remain optional"
    convergence = nodes[uid("convergence")]
    assert convergence["dependencies"] == [uid(n) for n in ROUTE_RECORDS]
    assert convergence["min_required_dependencies"] == 2
    assert nodes[uid("all_three")]["optional"]
    assert nodes[uid("archive_open")]["dependencies"] == [uid("archive_craft")]
    assert set(ADVANCEMENTS) == {q["tasks"][0]["advancement"].split("/")[-1] for q in doc["quests"]}
    old_ids = set()
    for path in (QUESTS / "chapters").glob("*.snbt"):
        if path.name == "02_heritage_survey.snbt":
            continue
        old = json.loads(path.read_text("utf-8-sig"))
        old_ids.add(old["id"])
        for q in old.get("quests", []):
            old_ids.add(q["id"])
            old_ids.update(t["id"] for key in ("tasks", "rewards") for t in q.get(key, []))
    assert previous in old_ids, "Workshop completion dependency must exist"
    assert not old_ids.intersection(ids), "IDs collide with an existing chapter"


def main():
    doc, lang, previous = chapter()
    verify(doc, previous)
    write(QUESTS / "chapters/02_heritage_survey.snbt", doc)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / ("lang/" + locale + ".snbt"), lang)
    resources()
    print(f"Heritage: {len(doc['quests'])} quests; two-of-three convergence")


if __name__ == "__main__":
    main()
