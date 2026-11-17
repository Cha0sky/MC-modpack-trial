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
    "convergence", "archive_craft", "archive_open", "all_three",
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
        "旧工坊已经重新运转。维修簿中提到的工业设施、奥术记录与偏远遗址，现在可以分别展开调查。",
        "工程、奥术、远征三条路线同时开放，先后顺序由你选择。完成任意两条即可汇合；第三条保留为可选的继续探索。",
        "路线完成记录与发现的遗迹坐标由世界共享。材料与设备可以提前准备，也允许同伴协作。",
        "领取远征熟食、照明和脚手架再出发；三条路线分别提供工厂扩建、研究设施和探索装备奖励。"],
        0, 0, "minecraft:written_book", ())

    quest("engineering_heat", "工程 · 让火进入工艺", [
        "准备一个已经捕获烈焰人的燃烧室，以及至少 8 个黄铜锭。取得物品即可记录，允许同伴分工。",
        "空燃烧室由 4 铁板围绕 1 下界岩制作；在下界捕获烈焰人，带回后为其提供普通燃料。",
        "在加热工作盆中搅拌 1 铜锭与 1 锌锭，得到 2 黄铜锭。普通加热足够，不需要超级加热。",
        "本章原生进度采用持有记录；它不要求你丢弃队友送来的材料。领取锌铜、红石和石英后，可继续制作电子管和机械手。"],
        2, -3, "create:blaze_burner", ("welcome",), optional=True)
    quest("engineering_assembly", "工程 · 可重复的精密工艺", [
        "实际运行一次机械手、完成一次注液器加工，并取得 1 个精密构件。三项可以分开完成，不要求一口气建成大型流水线。",
        "机械手由电子管、安山机壳和黄铜手制作。黄铜手需要 4 黄铜板与 1 安山合金；电子管由磨好的玫瑰石英与铁板制作，玫瑰石英需要 1 石英和 8 红石。",
        "注液器用于学习流体加工，例如向玻璃瓶注入 250 mB 蜂蜜。任意一次有效的注液器加工都可记录，精密构件则按下一条的原生工序装配。",
        "精密构件以金板起步，依次部署小齿轮、大齿轮和铁粒，重复 5 轮。原生成功率为 80%，请按 JEI 准备余量。",
        "机械手和注液器使用其原生运行进度，精密构件采用取得记录；已完成的原生进度可以被承认。"],
        4, -3, "create:precision_mechanism", ("engineering_heat",), optional=True)
    quest("engineering_record", "整理工程记录", [
        "热工与精密加工的成果已经齐备，工程路线自动记入世界档案。不会收走设备或精密构件。",
        "领取机械臂、黄铜漏斗、传送带和铁板，把刚掌握的工艺扩展成实际的自动供料与加工线。原有黄铜和传动杆奖励也照常保留。",
        "把这份能力带回家园：继续扩展产线，或先去调查其他遗产，都由你决定。"],
        6, -3, "create:brass_ingot", ("engineering_assembly",), optional=True, rewards=[
            item_reward("engineering_record/brass", "create:brass_ingot", 8),
            item_reward("engineering_record/shaft", "create:shaft", 16),
        ])

    quest("arcane_foundation", "奥术 · 选择研究方法", [
        "奥术路线有两种完整的研究方法，选择其中一种继续即可，完成两种也只算一条奥术路线。",
        "铁魔法方法：准备铭文台和铁质法术书，将法术书实际装备到对应位置，为亲自施法做准备。",
        "诡厄方法：准备黑暗魔杖，按模组手册学习召唤僵尸仆从。",
        "两套方法保留各自的装备与操作方式，不要求先把另一套体系也做完。"],
        2, 0, "minecraft:enchanting_table", ("welcome",), optional=True)
    quest("arcane_practice", "奥术 · 让知识发挥作用", [
        "沿你准备好的研究方法完成一次实际运用。",
        "铁魔法方法：实际装备铁质法术书后，从法术书释放一次法术。",
        "诡厄方法：用黑暗魔杖召唤僵尸仆从，再取得一次自己的仆从击败敌对生物的原生成就。",
        "基础和实践须完整满足同一体系。铁魔法与诡厄任一体系完成即可登记奥术记录。"],
        4, 0, "minecraft:enchanted_book", ("arcane_foundation",), optional=True)
    quest("arcane_record", "整理奥术记录", [
        "实际的奥术实践已经完成，研究记录自动加入世界档案。法术书、魔杖与其他装备由你保留。",
        "领取附魔台、16 个书架和研究耗材，在家里布置能长期使用的研究与附魔角。它不要求你补做另一套奥术方法。"],
        6, 0, "minecraft:book", ("arcane_practice",), optional=True, rewards=[
            item_reward("arcane_record/experience", "minecraft:experience_bottle", 8),
            item_reward("arcane_record/paper", "minecraft:paper", 16),
            item_reward("arcane_record/lapis", "minecraft:lapis_lazuli", 8),
        ])

    quest("expedition_sites", "远征 · 走入两处遗产", [
        "调查以下四类遗迹中的任意两类：Mowzie 钢铁守护者室、汇流来世附魔剑冢、灾变沙漠遗址、灾变古代工厂。",
        "探险者指南针可使用结构 ID 搜索：mowziesmobs:wrought_chamber、confluence:enchanted_sword_shrine、cataclysm:desert_site、cataclysm:ancient_factory。",
        "走入结构范围即可留下调查记录和该类首个地点的坐标，不需要在调查时击杀遗迹中的 Boss。同一类型的不同地点只算一类。",
        "这些坐标稍后可以在遗产档案台中查询、制图。领取金苹果、熟食与箭矢，为下一次自选挑战补足后勤。"],
        2, 3, "minecraft:compass", ("welcome",), optional=True)
    quest("expedition_challenge", "远征 · 一次自选挑战", [
        "从以下挑战中任选一个完成：钢铁守护者、霜冻巨兽、克苏鲁之眼、世界吞噬怪或克苏鲁之脑。",
        "只记录首领本体或对应的完整挑战，普通怪物与首领仆从不算。前章已经完成的钢铁守护者挑战会被承认。",
        "先调查再挑战、先挑战再旅行都可以；不要求把这些 Boss 逐个清完。原生掉落照常保留。"],
        4, 3, "minecraft:iron_axe", ("expedition_sites",), optional=True)
    quest("expedition_record", "整理远征记录", [
        "两类遗迹与一次自选挑战的记录已经齐备，远征路线自动加入世界档案。",
        "保留找到的装备与战利品。领取保护Ⅲ、摔落保护Ⅳ、耐久Ⅱ钻石靴，以及金胡萝卜和箭矢，继续选择自己的路线。"],
        6, 3, "minecraft:map", ("expedition_challenge",), optional=True, rewards=[
            item_reward("expedition_record/pearls", "minecraft:ender_pearl", 4),
            item_reward("expedition_record/experience", "minecraft:experience_bottle", 12),
        ])

    quest("convergence", "让两条线索相遇", [
        "世界档案中已经有任意两条路线的完整记录，调查成果可以汇合。工程、奥术、远征三选二，第三条继续可选。",
        "接下来制作遗产档案台，把共同发现的地点和已经掌握的能力保存成可以带走的记录。",
        "档案台使用普通材料，不要求黄铜或精密构件；选择奥术与远征的玩家同样可以完成本章。",
        "领取成批石砖、平滑石和原木，为档案室、工厂或新的远征据点准备建筑材料。"],
        8, 0, "minecraft:written_book", ROUTE_RECORDS, minimum=2)
    quest("archive_craft", "建一处保存知识的地方", [
        "制作并放置遗产档案台。工作台上排：书／玻璃／书；中排：铜板／安山机壳／铜板；下排：木板／共振胶／木板。",
        "可以放在自己的家、已修复的工坊或其他喜欢的位置。普通木板均可，档案台可以拆下搬走。",
        "制作它不收走任何路线装备，也不要求工程路线的黄铜与装配设备。"],
        10, 0, MOD + "heritage_archive", ("convergence",))
    quest("archive_open", "我们的遗产档案", [
        "右键使用遗产档案台，取得或更新实体档案书，查看世界共享的三线进度与已经发现的遗迹地点。",
        "潜行右键切换已记录的地点；主手拿空地图右键，将所选地点绘制成地图。每类遗迹保存最先发现的位置。",
        "任意两条路线足以完成本章。领取物品保险库和效率Ⅳ、耐久Ⅲ钻石斧，连同展示与照明奖励一起，把调查成果安置进自己的基地。",
        "档案台会继续保存后来的发现，你可以回到第三条路线，也可以建设、整理收藏或准备下一次远征。"],
        12, 0, MOD + "heritage_archive", ("archive_craft",), rewards=[
            item_reward("archive_open/frames", "minecraft:item_frame", 8),
            item_reward("archive_open/lanterns", "minecraft:lantern", 4),
        ])
    quest("all_three", "可选 · 完整的三路调查", [
        "工程、奥术和远征全部留下完整记录。这个额外目标不影响两线汇合与主线完成。",
        "从 4 个物品保险库、4 个金苹果或 24 个经验瓶中选择一份纪念补给。",
        "另可领取经验修补附魔书、8 个钻石与 32 个经验瓶，作为完成全部调查的额外奖励。"],
        10, 4, "minecraft:globe_banner_pattern", ROUTE_RECORDS, optional=True, rewards=[
            {"id": uid("all_three/choice"), "type": "choice", "table_id": 0, "table_data": {
                "loot_size": 1, "rewards": [
                    {**item_reward("all_three/vault", "create:item_vault", 4), "weight": 1.0},
                    {**item_reward("all_three/apples", "minecraft:golden_apple", 4), "weight": 1.0},
                    {**item_reward("all_three/experience", "minecraft:experience_bottle", 24), "weight": 1.0},
                ],
            }},
        ])
    return doc, lang, previous


def resources():
    for name in ADVANCEMENTS:
        write(DATA / ("advancement/heritage/" + name + ".json"), {
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
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
    assert len(doc["quests"]) == 14
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
    print("Heritage: 14 quests; 3 optional routes; two-of-three convergence; 14 core advancements; portable archive recipe/loot/model")


if __name__ == "__main__":
    main()
