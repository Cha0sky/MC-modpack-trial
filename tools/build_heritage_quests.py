"""Author the three-route heritage chapter without changing previous quest IDs.

The core owns route facts and the two-of-three milestone. FTB shows the same
dependency threshold and keeps every route optional for chapter completion.
"""
from pathlib import Path
from campaign_navigation import finish_chapter
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
    "pump_station", "source_intake", "recovery_intake", "spout_station", "controller_built", "controller_regulated",
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

    def quest(name, title, desc, x, y, icon, needs, rewards=(), optional=False, minimum=0, native=None, subtitle=None):
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
        if native:
            q["tasks"][0]["advancement"] = native
            del q["tasks"][0]["criterion"]
        doc["quests"].append(q)
        lang["quest." + q["id"] + ".title"] = title
        lang["quest." + q["id"] + ".quest_desc"] = desc
        if subtitle:
            lang["quest." + q["id"] + ".quest_subtitle"] = subtitle
        return q

    quest("welcome", "维修簿之外", [
        "旧工坊复工了，维修簿里的做法还有多少能用？工匠可以重新装配精密构件，术士可以重做一次施法，探路的人去查遗迹与守敌。",
        "工程、奥术、远征任选两份成果即可建档。工程队可分成两班：一班烧黄铜、备构件，一班架泵、铺管道，最后合装一台真正能用的调速器。"],
        0, 0, "minecraft:written_book", (), subtitle="工程、奥术、远征三路任选两路")

    quest("engineering_heat", "工程 · 让火进入工艺", [
        "用空燃烧室在下界捕获烈焰人。给燃烧室添燃料，在上方的工作盆搅拌铜锭和锌锭；每对出 2 黄铜锭，备齐 8 锭。"],
        2, -2, "create:blaze_burner", ("welcome",), optional=True)
    quest("pump_station", "工程 · 水从哪儿来", [
        "两张铜板夹一枚铜锭，做出四根流体管道。用管道和齿轮做机械泵，接上动力让它转起来。用扳手调好泵的方向，将水推往注液器。",
        "管网可以固定在河边，也可以用桶供料。下两格任选一种，整座工坊只需交一份供水记录。"],
        1, -4, "create:mechanical_pump", ("welcome",), optional=True)
    quest("source_intake", "取水方案 · 河岸泵房", [
        "把开口管正对水源方块，另一端经泵接注液器或储罐。让系统确实抽到水。留出能补水的水源，下一批就可以连续开工。"],
        3, -5, "create:fluid_pipe", ("pump_station",), optional=True,
        rewards=[item_reward("source_intake/pipes", "create:fluid_pipe", 8)])
    quest("recovery_intake", "取水方案 · 厂内倒桶", [
        "铁栏杆放在铜机壳上方合成分液池。往池面放一桶水，等空桶被送出。给分液池接管，将水泵到注液器，厂房从此不必紧邻河岸。"],
        3, -3.5, "create:item_drain", ("pump_station",), optional=True,
        rewards=[item_reward("recovery_intake/buckets", "minecraft:bucket", 3)])
    quest("spout_station", "工程 · 开阀试灌", [
        "用铜机壳和干海带做注液器，接在泵的出水管上。下方放置物台，放一块泥土，实际注入 500 mB 水得到草方块。也可以直接用下一步的黄铜机壳试灌。"],
        5, -4, "create:spout", ("source_intake", "recovery_intake"), minimum=1, optional=True)
    quest("engineering_assembly", "工程 · 可重复的精密工艺", [
        "让机械手真正加工一次，注液器也完成一次灌装。两个岗位可以交给不同的人，取得的精密构件留给调速器。",
        "以金板起步，依次部署小齿轮、大齿轮、铁粒，重复 5 轮。构件成功率为 80%，给流水线留一点余料。"],
        4, -2, "create:precision_mechanism", ("engineering_heat",), optional=True)
    quest("controller_built", "工程委托 · 试装一台调速器", [
        "干净的黄铜机壳先在注液器下注入 250 mB 水。把湿润的试装件移到机械手下，给机械手一个精密构件；再交给压机封壳。一遍就能得到工坊试装调速器。",
        "用置物台逐站转移，或把三台机器沿传送带排开都行。拿起成品登记，再把它装回传动轴。"],
        6.5, -3, "create:rotation_speed_controller", ("engineering_assembly", "spout_station"), optional=True)
    quest("controller_regulated", "工程委托 · 带载调速", [
        "把调速器接上动力，顶面安一枚竖直的大齿轮，让齿轮轴与调速器的轴交叉。准星对着调速器，用滚轮改变设定，让大齿轮带动输出轴。",
        "可把这段轴接回工作盆，以 32 RPM 带动搅拌器。试车读的是调速器在转、齿轮已接好且改过设定。"],
        8, -3, "create:large_cogwheel", ("controller_built",), optional=True)
    quest("engineering_copy", "工程档案 · 领取已签的记录", [
        "世界共用一本工程总档。有同伴签过调速器试车单，或旧版工坊已登记工程调查，就能在这里抄一份记录。刚开工的队伍沿上方两班施工。"],
        7.5, -6, "minecraft:paper", ("welcome",), optional=True,
        native=MOD + "heritage/engineering_record")
    quest("engineering_record", "整理工程记录", [
        "工程总档收下了试车记录。试装的调速器留给基地，用机械臂和黄铜漏斗给归来的样本做一条分拣线。"],
        9.5, -2, "create:brass_ingot", ("controller_regulated", "engineering_copy"), minimum=1, optional=True, rewards=[
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
        "远征队要交两样东西：两类遗迹的坐标，以及一场首领战果。两件事可按行程先后完成。钢铁守护者室、附魔剑冢、沙漠遗址、古代工厂任选两类走入。",
        "指南针可搜 wrought_chamber、enchanted_sword_shrine、desert_site、ancient_factory。"],
        3, 2, "minecraft:compass", ("welcome",), optional=True)
    quest("expedition_challenge", "远征 · 一次自选挑战", [
        "任选一位对手：钢铁守护者、霜冻巨兽、克苏鲁之眼、世界吞噬怪、克苏鲁之脑或骷髅领主。",
        "想找骷髅领主，可用指南针搜 goety:crypt，到诡厄的大坟墓查看罐子。"],
        3, 4, "minecraft:iron_axe", ("welcome",), optional=True)
    quest("expedition_record", "整理远征记录", [
        "地点和战果都到手了，把这次远征写进总档。下一队就能从档案台绘图，循着你的坐标再来。"],
        6, 3, "minecraft:map", ("expedition_sites", "expedition_challenge"), optional=True, subtitle="两类遗址与一场自选讨伐", rewards=[
            item_reward("expedition_record/pearls", "minecraft:ender_pearl", 4),
            item_reward("expedition_record/experience", "minecraft:experience_bottle", 12),
        ])

    frost = quest("ice_recovery", "雪地悬赏 · 带回冰霜结晶", [
        "雪地队要带回一件能继续研究的战利品。找到仍握着冰霜结晶的霜冻巨兽，亲自击败它，把结晶带在身上。",
        "结晶留给出队的人，长按使用可吹出冰霜旋风。结账时另领取一双摔落保护靴和雪地铺路用的蓝冰。"],
        5, 6, "mowziesmobs:ice_crystal", ("welcome",), optional=True, native="mowziesmobs:kill_frostmaw",
        subtitle="雪地自选 · 本人击败记录与一枚结晶",
        rewards=[item_reward("ice_recovery/ice", "minecraft:blue_ice", 24),
                 item_reward("ice_recovery/boots", "minecraft:leather_boots", 1,
                             {"minecraft:enchantments": {"levels": {"minecraft:feather_falling": 4}}})])
    frost["tasks"].append({"id": uid("ice_recovery/sample"), "type": "item", "item": {
        "id": "mowziesmobs:ice_crystal", "count": 1}, "count": 1, "consume_items": False})
    quest("convergence", "让两条线索相遇", [
        "旧日的工程与术式曾在同一座文明里发展。如今散落的工艺、术式、遗址坐标，又有两份能摆回同一张桌上。找个队友都方便取用的位置建档。"],
        8, 0, "minecraft:written_book", ROUTE_RECORDS, minimum=2)
    quest("archive_craft", "建一处保存知识的地方", [
        "工作台上排摆书、玻璃、书；中排摆铜板、安山机壳、铜板；下排摆木板、共振胶、木板。做好遗产档案台并放下。"],
        10, 0, MOD + "heritage_archive", ("convergence",))
    quest("archive_open", "我们的遗产档案", [
        "空手右键档案台领取档案书，带回旧书可更新。潜行右键选地点，再松开潜行，持空地图右键绘图。",
        "下次出队要往下界走。把调谐器、共振胶和档案书收在一起，出发时直接取走。"],
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
    quest("shelf_reader", "档案室 · 给书架接一盏灯", [
        "摆一个雕纹书架，让红石比较器的输入端紧贴书架、输出端朝外。装入或取出不同格子的书，比较器强度会跟着变化。可在输出端接红石灯当借阅提示。"],
        14, 2, "minecraft:chiseled_bookshelf", ("archive_open",), optional=True,
        native="minecraft:adventure/read_power_of_chiseled_bookshelf",
        rewards=[item_reward("shelf_reader/lamps", "minecraft:redstone_lamp", 4),
                 item_reward("shelf_reader/shelves", "minecraft:chiseled_bookshelf", 4)])
    quest("sample_sorting", "档案室 · 机械臂接班", [
        "带上工程调查发给你的机械臂。用置物台作取件点、漏斗作交件点，手持机械臂标好输入和输出，再放下接轴供能。让它搬过一件样品。"],
        14, -2, "create:mechanical_arm", ("engineering_record", "archive_open"), optional=True,
        native="create:mechanical_arm",
        rewards=[item_reward("sample_sorting/vault", "create:item_vault", 4),
                 item_reward("sample_sorting/filters", "create:filter", 4)])
    return doc, lang, previous


def resources():
    for name in ADVANCEMENTS:
        write(DATA / ("advancement/heritage/" + name + ".json"), {
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
    prototype = {"id": "create:rotation_speed_controller", "components": {
        "minecraft:custom_data": "{lostworld_trial_controller:1b}",
        "minecraft:item_name": json.dumps({"text": "工坊试装调速器"}, ensure_ascii=False)}}
    transitional = {"id": MOD + "incomplete_trial_controller"}
    steps = [("filling", [{"type": "neoforge:single", "fluid": "minecraft:water", "amount": 250}]),
             ("deploying", [{"item": "create:precision_mechanism"}]), ("pressing", [])]
    write(DATA / "recipe/heritage_trial_controller.json", {
        "type": "create:sequenced_assembly", "ingredient": {"item": "create:brass_casing"},
        "transitional_item": transitional, "loops": 1, "results": [prototype],
        "sequence": [{"type": "create:" + kind, "ingredients": [{"item": transitional["id"]}, *supplies],
                      "results": [transitional]} for kind, supplies in steps],
    })
    for name, item in {
        "brass_stock": {"items": "create:brass_ingot", "count": {"min": 8}},
        "controller_prototype": {"items": prototype["id"], "predicates": {
            "minecraft:custom_data": "{lostworld_trial_controller:1b}"}},
    }.items():
        write(DATA / ("advancement/heritage/" + name + ".json"), {"criteria": {"done": {
            "trigger": "minecraft:inventory_changed", "conditions": {"items": [item]}}}})
    write(ASSETS / "models/item/incomplete_trial_controller.json", {
        "parent": "create:block/brass_casing",
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
    merge(ASSETS / "lang/zh_cn.json", {"block.world_progression_core.heritage_archive": "遗产档案台",
                                        "item.world_progression_core.incomplete_trial_controller": "待封装的调速器"})
    merge(ASSETS / "lang/en_us.json", {"block.world_progression_core.heritage_archive": "Heritage Archive",
                                        "item.world_progression_core.incomplete_trial_controller": "Unsealed Speed Controller"})


def verify(doc, previous):
    objects = [doc]
    for quest in doc["quests"]:
        objects.extend([quest, *quest["tasks"], *quest["rewards"]])
        for reward in quest["rewards"]:
            objects.extend(reward.get("table_data", {}).get("rewards", []))
    ids = [o["id"] for o in objects]
    assert len(ids) == len(set(ids))
    assert all(0 < int(identifier, 16) <= 0x7FFFFFFFFFFFFFFF for identifier in ids)
    assert len(doc["quests"]) == 25
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
    assert nodes[uid("spout_station")]["min_required_dependencies"] == 1
    assert nodes[uid("engineering_record")]["min_required_dependencies"] == 1
    assert nodes[uid("archive_open")]["dependencies"] == [uid("archive_craft")]
    assert nodes[uid("expedition_challenge")]["dependencies"] == [uid("welcome")]
    assert nodes[uid("expedition_record")]["dependencies"] == [uid("expedition_sites"), uid("expedition_challenge")]
    records = {q["tasks"][0]["advancement"] for q in doc["quests"]}
    assert records == ({MOD + "heritage/" + name for name in ADVANCEMENTS} |
                       {"minecraft:adventure/read_power_of_chiseled_bookshelf", "create:mechanical_arm",
                        "mowziesmobs:kill_frostmaw"})
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
    finish_chapter(doc, lang)
    write(QUESTS / "chapters/02_heritage_survey.snbt", doc)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / ("lang/" + locale + ".snbt"), lang)
    resources()
    print(f"Heritage: {len(doc['quests'])} quests; two-of-three convergence")


if __name__ == "__main__":
    main()
