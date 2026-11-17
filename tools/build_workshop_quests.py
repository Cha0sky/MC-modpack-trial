"""Build the approved workshop chapter and its small authored resource set.

Only this chapter's quest/lang keys and named resources are written. Existing
prologue IDs, rewards and translations are kept. This is functional quest text,
not an expansion of the author's still-unwritten full story or outer-god lore.
"""
from pathlib import Path
import hashlib
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
QUESTS = ROOT / "staging/config/ftbquests/quests"
RES = ROOT / "modules/world-progression-core/src/main/resources"
DATA = RES / "data/world_progression_core"
ASSETS = RES / "assets/world_progression_core"
MOD = "world_progression_core:"
ADVANCEMENTS = (
    "commission", "survey", "power", "plates", "fiber", "binder", "tuner",
    "arrival", "calibrate", "restore", "both_regions", "batch_copper",
    "field_notes", "photography", "wroughtnaut",
)


def uid(name, namespace="lostworld.workshop.v1/"):
    # Stable positive signed-long IDs are required by this FTB Quests version.
    value = int(hashlib.sha256((namespace + name).encode()).hexdigest()[:16], 16)
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def merge(path, additions):
    data = json.loads(path.read_text("utf-8-sig")) if path.exists() else {}
    data.update(additions)
    write(path, data)


def item_reward(name, item, count=1, components=None):
    stack = {"id": item, "count": 1}
    if components:
        stack["components"] = components
    return {"id": uid(name), "type": "item", "item": stack, "count": count}


def named_reward(name, item, title, lore):
    return item_reward(name, item, components={
        "minecraft:custom_name": json.dumps({"text": title, "italic": False}, ensure_ascii=False),
        "minecraft:lore": [json.dumps({"text": line, "italic": False, "color": "gray"}, ensure_ascii=False)
                           for line in lore],
    })


def milestone_supplies(name):
    # New rewards are always appended after legacy entries, including choice tables.
    packs = {
        "commission": [("minecraft:cooked_beef", 32), ("minecraft:torch", 64), ("minecraft:cobblestone", 128)],
        "power": [("minecraft:copper_ingot", 32), ("minecraft:iron_ingot", 32), ("minecraft:redstone", 32), ("minecraft:coal", 64)],
        "plates": [("create:goggles", 1), ("create:andesite_alloy", 16)],
        "tuner": [("create:wrench", 1), ("minecraft:water_bucket", 2), ("create:water_wheel", 3),
                  ("create:shaft", 32), ("create:large_cogwheel", 8), ("create:cogwheel", 16), ("minecraft:cobblestone", 128)],
        "restore": [("minecraft:diamond_pickaxe", 1), ("minecraft:oak_log", 128), ("minecraft:glass", 64),
                    ("minecraft:iron_ingot", 64), ("minecraft:cooked_beef", 32), (MOD + "resonant_binder", 2)],
        "both_regions": [("minecraft:oak_log", 64), ("minecraft:coal", 32)],
        "batch_copper": [("create:andesite_tunnel", 8), ("minecraft:hopper", 8), ("create:chute", 16), ("create:shaft", 64)],
        "field_notes": [("minecraft:bow", 1), ("minecraft:arrow", 64), ("minecraft:cooked_beef", 32)],
        "photography": [("minecraft:writable_book", 1), ("minecraft:item_frame", 16), ("minecraft:paper", 64)],
        "wroughtnaut": [("minecraft:diamond", 4), ("minecraft:golden_apple", 4), ("minecraft:experience_bottle", 16)],
    }
    enchantments = {
        "minecraft:diamond_pickaxe": {"minecraft:efficiency": 3, "minecraft:unbreaking": 2},
        "minecraft:bow": {"minecraft:power": 3, "minecraft:unbreaking": 2},
    }
    return [item_reward(name + "/supplies_v1/" + item, item, count,
                        {"minecraft:enchantments": {"levels": enchantments[item]}} if item in enchantments else None)
            for item, count in packs.get(name, [])]


def chapter():
    prologue_next = uid("next", "lostworld.chapter1.v1/")
    doc = {"id": uid("chapter"), "filename": "01_workshop_rekindled", "order_index": 0,
           "icon": {"id": MOD + "rough_tuner"}, "progression_mode": "linear", "quests": []}
    lang = {"chapter." + uid("chapter") + ".title": "第一章 · 重启第一座工坊"}

    def quest(name, title, desc, x, y, icon, needs, rewards=(), optional=False):
        q = {"id": uid(name), "x": x, "y": y, "icon": {"id": icon}, "min_width": 320,
             "dependencies": [uid(n) for n in needs] if name != "commission" else [prologue_next],
             "dependency_requirement": "all_completed", "progression_mode": "linear", "optional": optional,
             "hide_dependency_lines": False, "hide_until_deps_visible": False,
             "hide_until_deps_complete": False, "hide_lock_icon": False,
             "tasks": [{"id": uid(name + "/task"), "type": "advancement",
                        "advancement": MOD + "workshop/" + name, "criterion": "done"}],
             "rewards": [*rewards, *milestone_supplies(name)]}
        doc["quests"].append(q)
        lang["quest." + q["id"] + ".title"] = ("支线 · " if optional else "") + title
        lang["quest." + q["id"] + ".quest_desc"] = desc

    quest("commission", "旧工坊的委托", [
        "一份残缺的维修记录提到了边境旧工坊：那里曾为附近的居民维修工具、制造零件。",
        "击败史莱姆王后，腐化与猩红已经可以进入。取得当地原木，尝试恢复记录中的复合材料工艺。",
        "采样与建设可以自由安排先后。自然罗盘帮助找群系，探险者指南针帮助找遗迹；不必重新完成已经做过的序章。",
        "这一章的核心是制造工具并亲手修复旧设施，五条支线均可自由选择，完成主线即可进入第二章。",
        "先领取熟牛排、火把和搭路石料。采样出发前检查工具、背包空位与回家的路线。"],
        0, 0, "minecraft:book", ())
    quest("survey", "越过边境", [
        "到腐化或猩红地区调查，准备共 4 个乌木原木或暗影原木。两种可以混合使用。",
        "乌木原木来自腐化树，暗影原木来自猩红树；不要求先挖黑檀石、猩红石或击败另一个首领。",
        "带在身上的样本会被记录，任务不会收走材料。之后把这 4 个原木送进磨石。"],
        2, -1, "confluence:ebony_log", ("commission",))
    quest("power", "让动力持续运转", [
        "让机械压机、磨石或搅拌器接上实际运转的动力，并走到设备旁累计观察约 10 秒。",
        "教学推荐小水车。压机与磨石可走 8 RPM 低速支路；搅拌器通过两级齿轮增速到 32 RPM。",
        "可采用其他动力方案。若系统过载，增加动力容量或分开运行设备；不需要把所有设备都加速。",
        "领取铁铜锭、红石和燃料，接下来由你亲自把原料加工成板材与共振胶。"],
        2, 1, "create:water_wheel", ("commission",))
    quest("plates", "第一批精密零件", [
        "准备 7 个铁板和 2 个铜板：原生压机把 1 个金属锭压成 1 个对应板材。",
        "5 个铁板与 2 个安山合金先做搅拌头，再按 JEI 制作搅拌器和工作盆；另外 2 个铁板与 2 个铜板留给调谐器。",
        "取得的成果允许提前准备和同伴分工。先放进背包让成果记录，再投入后续工艺；已用于搅拌头和调谐器的板材也会计入。普通熔炉照常可以冶炼原料。",
        "领取工程师护目镜与安山合金，用护目镜观察设备的转速和应力，继续扩展你的工坊。"],
        4, 1, "create:mechanical_press", ("power",))
    quest("fiber", "异常材料的用途", [
        "将乌木原木或暗影原木放进磨石：每个原木稳定产出 2 份浸染纤维。",
        "共需 8 份纤维，4 个原木正好够用。可以放进背包记录，也可以直接送入自动混合；已经制成的共振胶会计入其消耗的纤维。"],
        4, -1, MOD + "tainted_fiber", ("survey", "power"))
    quest("binder", "稳定的共振胶", [
        "在工作盆内投入 2 份浸染纤维、1 份红石粉与 1 个黏液球或汇流凝胶，由机械搅拌器产出 1 份共振胶。",
        "本配方不加热、不需要液体管线，也不需要黄铜、烈焰人或下界材料。搅拌支路推荐 32 RPM。",
        "准备并记录 4 份共振胶：1 份用于制造调谐器，另外 3 份留给工坊三处设备。"],
        6, 0, MOD + "resonant_binder", ("plates", "fiber"))
    quest("tuner", "粗制调谐器", [
        "工作台组装：上排 铜板／玻璃／铜板；中排 铁板／共振胶／铁板；下排 空／传动杆／空。",
        "调谐器可以诊断古代设备的动力、方向和校准状态。它不消耗自身、不需要燃料；成功修复一处设备时消耗 1 份共振胶。",
        "在主世界手持调谐器对空气右键，领取指向废弃工坊的勘测地图；地图丢失后可再次操作补领，有短暂冷却。任务解锁顺序不影响已经取得的材料记录。",
        "领取扳手、水桶、3 台小水车、传动件和垫脚石料作为现场维修包。水车可给三处节点分别供能；出发前确认背包里还留有 3 份共振胶。"],
        8, 0, MOD + "rough_tuner", ("binder",))
    quest("arrival", "找到旧址", [
        "沿工坊地图抵达旧址。在已有存档中请向未生成过的区块探索；更新后重用调谐器即可领取勘测图，探险者指南针也可以辅助寻找。",
        "正门、侧面、自己搭出的桥或挖出的通路都可以。先查看动力室、传动装置与屋顶信号装置。",
        "推荐带水车、水桶、传动杆、大小齿轮、扳手和 3 份共振胶，现场也可采用自己的传动方案。"],
        10, 0, "minecraft:filled_map", ("tuner",))
    quest("calibrate", "三处故障", [
        "手持调谐器对设备使用，阅读故障提示。三处节点都需要至少 8 RPM；方向节点还要求正向旋转，诊断会显示实际正负转速。",
        "动力接口是有圆孔或符号的彩色正面。把 Create 轴或齿轮放在正面相邻一格，轴向朝向设备，再接上动力。",
        "到达屋顶信号节点并恢复其条件。每处条件满足后，对设备长按右键约 3 秒完成校准，消耗 1 份共振胶。失败操作不扣材料。",
        "在同一座工坊内完成三处设备，修复状态由这个世界共享。"],
        12, 0, MOD + "workshop_relay", ("arrival",))
    quest("restore", "工坊重新亮起", [
        "三处设备就绪后，使用中央控制台，让旧工坊重新启动。档案和章节成果在启动成功后开放。",
        "这里留下的工艺记录介绍普通工匠如何为周边居民服务，并指向机械、奥术与远征三类遗产。下一章将继续这些调查。",
        "这座工坊可以成为继续改造的野外据点。领取工程补给和效率Ⅲ、耐久Ⅱ钻石镐，带着新能力回家。",
        "第二章“遗产调查”会从这里接续。奖励中的 2 份共振胶可留给档案台，五条支线可以随时回来完成。"],
        14, 0, MOD + "workshop_console", ("calibrate",), rewards=[
            item_reward("restore/alloy", "create:andesite_alloy", 8),
            item_reward("restore/shaft", "create:shaft", 16),
            item_reward("restore/cogwheel", "create:cogwheel", 8),
        ])
    quest("both_regions", "另一种异常", [
        "分别进入腐化与猩红，并让背包记录到乌木原木和暗影原木各 4 个。主线只要求任一路线，这里是自选的第二份调查。",
        "奖励两份备用共振胶、两件可放入展示框的区域标本纪念牌，以及木材与燃料补给。任务不收走你的原木。"],
        4, -3, "confluence:shadow_log", ("survey",), optional=True, rewards=[
            item_reward("both_regions/binder", MOD + "resonant_binder", 2),
            named_reward("both_regions/ebony", "minecraft:purple_dye", "腐化原木标本牌", ["边境调查纪念", "可放入展示框陈列。"]),
            named_reward("both_regions/shadow", "minecraft:red_dye", "猩红原木标本牌", ["边境调查纪念", "可放入展示框陈列。"]),
        ])
    quest("batch_copper", "从手作到量产", [
        "准备并记录 16 个铜板，已经用于调谐器的铜板也会计入。推荐尝试自动输入与收取，也允许手动完成。",
        "任务不收走铜板。奖励安山漏斗、传送带，再加安山隧道、漏斗、溜槽和一组传动杆，足够把几台机器真正连成一条线。"],
        6, 3, "create:andesite_funnel", ("plates",), optional=True, rewards=[
            item_reward("batch_copper/funnel", "create:andesite_funnel", 4),
            item_reward("batch_copper/belt", "create:belt_connector", 4),
        ])
    quest("field_notes", "野外见闻录", [
        "在本章开启后走入三种不同的 Explorify 小型遗迹或营地。相同类型重复发现不会代替新的见闻。",
        "探索方向由你选择，不要求主线清空这些遗迹。已记录的发现会保留，之后回来交任务即可。",
        "领取铁砧、经验补给，以及力量Ⅲ、耐久Ⅱ弓和箭矢、熟食，为更远的冒险做准备。"],
        8, -3, "minecraft:spyglass", ("commission",), optional=True, rewards=[
            item_reward("field_notes/anvil", "minecraft:anvil"),
            item_reward("field_notes/experience", "minecraft:experience_bottle", 8),
        ])
    quest("photography", "第一次远征纪念", [
        "用拾光定影的相机记录自己的远征，完成胶片冲洗和打印，保存三张不同的照片。",
        "风景、同伴、遗迹或自己的工坊都可以，不对照片内容打分。自己拍摄的三张不同照片需要完成打印，复制同一张不会重复计数。",
        "领取展示框、横幅、书与笔和纸，把记录带回家，做成自己的照片墙或远征日志。"],
        10, 3, "minecraft:glow_item_frame", ("commission",), optional=True, rewards=[
            item_reward("photography/frame", "minecraft:glow_item_frame", 4),
            item_reward("photography/banner", "minecraft:white_banner", 2),
        ])
    options = []
    for enchantment in ("unbreaking", "efficiency", "feather_falling"):
        reward = item_reward("wroughtnaut/choice/" + enchantment, "minecraft:enchanted_book", components={
            "minecraft:stored_enchantments": {"levels": {"minecraft:" + enchantment: 3}},
        })
        reward["weight"] = 1.0
        options.append(reward)
    quest("wroughtnaut", "沉睡的铁甲", [
        "准备充分后，自愿挑战 Mowzie 的钢铁守护者。它位于地下的守护者室，是本章较难的技巧型支线。",
        "保留模组原生掉落。额外领取一枚纪念牌，并从耐久Ⅲ、效率Ⅲ、摔落保护Ⅲ附魔书中选择一本。另有钻石、金苹果和经验瓶作为挑战补给。",
        "这里的模组称号不代表它已经被设定为主线封印守护者；不完成此支线也能重启工坊。"],
        12, -3, "minecraft:iron_axe", ("commission",), optional=True, rewards=[
            {"id": uid("wroughtnaut/book_choice"), "type": "choice", "table_id": 0,
             "table_data": {"loot_size": 1, "rewards": options}},
            named_reward("wroughtnaut/plaque", "minecraft:iron_nugget", "沉睡铁甲纪念牌", ["一次自愿的遗迹挑战", "可放入展示框陈列。"]),
        ])
    return doc, lang, prologue_next


def build_recipes():
    write(DATA / "tags/item/workshop_samples.json", {"replace": False, "values": [
        {"id": "confluence:ebony_log", "required": False},
        {"id": "confluence:shadow_log", "required": False},
    ]})
    write(DATA / "tags/item/workshop_binders.json", {"replace": False, "values": [
        "minecraft:slime_ball", {"id": "confluence:gel", "required": False},
    ]})
    write(DATA / "recipe/workshop_tainted_fiber.json", {
        "type": "create:milling", "ingredients": [{"tag": MOD + "workshop_samples"}],
        "processing_time": 150, "results": [{"id": MOD + "tainted_fiber", "count": 2}],
    })
    write(DATA / "recipe/workshop_resonant_binder.json", {
        "type": "create:mixing", "ingredients": [
            {"item": MOD + "tainted_fiber"}, {"item": MOD + "tainted_fiber"},
            {"item": "minecraft:redstone"}, {"tag": MOD + "workshop_binders"},
        ], "results": [{"id": MOD + "resonant_binder", "count": 1}],
    })
    write(DATA / "recipe/workshop_rough_tuner.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["CGC", "IRI", " S "],
        "key": {"C": {"item": "create:copper_sheet"}, "G": {"item": "minecraft:glass"},
                "I": {"item": "create:iron_sheet"}, "R": {"item": MOD + "resonant_binder"},
                "S": {"item": "create:shaft"}},
        "result": {"id": MOD + "rough_tuner", "count": 1},
    })
    write(DATA / "advancement/recipes/workshop_rough_tuner.json", {
        "parent": "minecraft:recipes/root", "criteria": {"has_binder": {
            "trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [MOD + "resonant_binder"]}]}}},
        "requirements": [["has_binder"]], "rewards": {"recipes": [MOD + "workshop_rough_tuner"]},
    })
    for name in ADVANCEMENTS:
        write(DATA / ("advancement/workshop/" + name + ".json"), {"criteria": {"done": {"trigger": "minecraft:impossible"}}})


def png(path, pixels):
    """Write small hand-authored pixel textures with no imaging dependency."""
    height, width = len(pixels), len(pixels[0])
    data = b"".join(b"\0" + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    def chunk(kind, payload):
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff)
    output = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    output += chunk(b"IDAT", zlib.compress(data)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(output)


def sprite(name, rows, palette):
    colors = {key: tuple(int(value[i:i+2], 16) for i in (0, 2, 4)) + (255,) for key, value in palette.items()}
    colors["."] = (0, 0, 0, 0)
    assert len(rows) == 16 and all(len(row) == 16 for row in rows)
    png(ASSETS / ("textures/item/" + name + ".png"), [[colors[p] for p in row] for row in rows])


def build_assets():
    sprite("tainted_fiber", [
        "................", "................", "......a.........", "....aabb....a...",
        "...abbcc...abb..", "..abccbb..abca..", "..bcbba..abcba..", "...abb..abcba...",
        "....a..abcba....", "...a..abcba.....", "..ab.abcba..a...", "..abbcbba..ab...",
        "...bbba...aba...", "....aa.....a....", "................", "................",
    ], {"a": "392743", "b": "76506D", "c": "C88C93"})
    sprite("resonant_binder", [
        "................", "................", ".....aaaaaa.....", "....abbbbbba....",
        "....acccccca....", ".....acccca.....", ".....adddda.....", "....adeeedda....",
        "...adeeffedda...", "...adeeefedda...", "...adeeeeedda...", "...addeeeedda...",
        "....adddddda....", ".....aaaaaa.....", "................", "................",
    ], {"a": "352B3A", "b": "A17750", "c": "D3AC6D", "d": "67436C", "e": "A85E8C", "f": "F3BC9C"})
    sprite("rough_tuner", [
        "................", "...aa......aa...", "..abba....abba..", "..abca....acba..",
        "..abca....acba..", "..abca....acba..", "...abaaaaaaba...", "....abbbbba.....",
        ".....accca......", "......ada.......", ".....aeeea......", ".....aefea......",
        ".....aefea......", ".....aeeea......", "......aaa.......", "................",
    ], {"a": "34343B", "b": "A96543", "c": "E4AB6D", "d": "83C9C4", "e": "5D4146", "f": "96646B"})
    for name in ("tainted_fiber", "resonant_binder", "rough_tuner"):
        write(ASSETS / ("models/item/" + name + ".json"), {
            "parent": "minecraft:item/handheld" if name == "rough_tuner" else "minecraft:item/generated",
            "textures": {"layer0": MOD + "item/" + name},
        })
    shell = []
    for y in range(16):
        row = []
        for x in range(16):
            border = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            rivet = x in (2, 13) and y in (2, 13)
            color = (177, 139, 91) if rivet else ((53, 65, 65) if border else (91 + (x+y)%3*3, 105 + (x+y)%3*3, 100))
            row.append(color + (255,))
        shell.append(row)
    png(ASSETS / "textures/block/workshop_shell.png", shell)
    colors = {"power": (220, 155, 67), "direction": (73, 174, 190), "signal": (169, 119, 198)}
    for kind, color in colors.items():
        for calibrated in (False, True):
            pixels = [list(row) for row in shell]
            for y in range(3, 13):
                for x in range(3, 13):
                    pixels[y][x] = (30, 38, 42, 255)
            for y in range(5, 11):
                for x in range(5, 11):
                    if kind == "power":
                        mark = x in (5, 10) or y in (5, 10)
                    elif kind == "direction":
                        mark = y == 8 or (x >= 8 and abs(y-8) == 10-x)
                    else:
                        mark = y == 10-abs(x-7) or y == 11-abs(x-7)
                    if mark:
                        pixels[y][x] = color + (255,)
            pixels[3][11] = pixels[3][12] = ((92, 224, 125) if calibrated else (155, 71, 54)) + (255,)
            name = "workshop_relay_" + kind + ("_on" if calibrated else "_off")
            png(ASSETS / ("textures/block/" + name + ".png"), pixels)
            write(ASSETS / ("models/block/" + name + ".json"), {
                "parent": "minecraft:block/orientable", "textures": {
                    "top": MOD + "block/workshop_shell", "front": MOD + "block/" + name,
                    "side": MOD + "block/workshop_shell",
                },
            })
    variants = {}
    for kind in colors:
        for calibrated in (False, True):
            for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                variants[f"calibrated={str(calibrated).lower()},facing={facing},kind={kind}"] = {
                    "model": MOD + "block/workshop_relay_" + kind + ("_on" if calibrated else "_off"), "y": rotation,
                }
    write(ASSETS / "blockstates/workshop_relay.json", {"variants": variants})
    write(ASSETS / "models/item/workshop_relay.json", {"parent": MOD + "block/workshop_relay_power_off"})
    console = {}
    for active in (False, True):
        pixels = [list(row) for row in shell]
        for y in range(3, 12):
            for x in range(3, 13):
                pixels[y][x] = ((29, 66, 58) if active else (30, 38, 42)) + (255,)
        for row in (5, 7, 9):
            for x in range(5, 11):
                pixels[row][x] = ((122, 223, 168) if active else (94, 98, 97)) + (255,)
        for x in (5, 8, 11):
            pixels[13][x] = (202, 155, 87, 255)
        name = "workshop_console_" + ("on" if active else "off")
        png(ASSETS / ("textures/block/" + name + ".png"), pixels)
        write(ASSETS / ("models/block/" + name + ".json"), {
            "parent": "minecraft:block/orientable", "textures": {
                "top": MOD + "block/workshop_shell", "front": MOD + "block/" + name, "side": MOD + "block/workshop_shell",
            },
        })
        for facing, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            console[f"active={str(active).lower()},facing={facing}"] = {"model": MOD + "block/" + name, "y": rotation}
    write(ASSETS / "blockstates/workshop_console.json", {"variants": console})
    write(ASSETS / "models/item/workshop_console.json", {"parent": MOD + "block/workshop_console_off"})
    merge(ASSETS / "lang/zh_cn.json", {
        "item.world_progression_core.tainted_fiber": "浸染纤维",
        "item.world_progression_core.resonant_binder": "共振胶",
        "item.world_progression_core.rough_tuner": "粗制调谐器",
        "block.world_progression_core.workshop_relay": "旧工坊继电器",
        "block.world_progression_core.workshop_console": "旧工坊控制台",
        "itemGroup.world_progression_core": "失落世界 · 工坊遗产",
    })
    merge(ASSETS / "lang/en_us.json", {
        "item.world_progression_core.tainted_fiber": "Tainted Fiber",
        "item.world_progression_core.resonant_binder": "Resonant Binder",
        "item.world_progression_core.rough_tuner": "Rough Tuner",
        "block.world_progression_core.workshop_relay": "Workshop Relay",
        "block.world_progression_core.workshop_console": "Workshop Console",
        "itemGroup.world_progression_core": "Lost World: Workshop Heritage",
    })


def verify(doc, external_dependency):
    objects = [doc]
    for q in doc["quests"]:
        objects.extend([q, *q["tasks"], *q["rewards"]])
        for reward in q["rewards"]:
            objects.extend(reward.get("table_data", {}).get("rewards", []))
    ids = [o["id"] for o in objects]
    assert len(ids) == len(set(ids))
    assert all(0 < int(identifier, 16) <= 0x7FFFFFFFFFFFFFFF for identifier in ids)
    assert len(doc["quests"]) == 15
    nodes = {q["id"]: q for q in doc["quests"]}
    visited = {external_dependency}
    for q in doc["quests"]:
        assert set(q["dependencies"]) <= visited, "Missing or cyclic quest dependency"
        visited.add(q["id"])
    assert nodes[uid("binder")]["dependencies"] == [uid("plates"), uid("fiber")]
    assert nodes[uid("restore")]["dependencies"] == [uid("calibrate")]
    assert all(nodes[uid(name)]["optional"] for name in ("both_regions", "batch_copper", "field_notes", "photography", "wroughtnaut"))
    choices = nodes[uid("wroughtnaut")]["rewards"][0]["table_data"]["rewards"]
    assert len(choices) == 3
    assert set(ADVANCEMENTS) == {q["tasks"][0]["advancement"].split("/")[-1] for q in doc["quests"]}
    for path in (QUESTS / "chapters").glob("*.snbt"):
        if path.name == "01_workshop_rekindled.snbt":
            continue
        previous = json.loads(path.read_text("utf-8-sig"))
        old_ids = {previous["id"]}
        for q in previous.get("quests", []):
            old_ids.add(q["id"])
            old_ids.update(t["id"] for key in ("tasks", "rewards") for t in q.get(key, []))
        assert not old_ids.intersection(ids), "IDs collide with an existing chapter"


def main():
    doc, lang, external = chapter()
    verify(doc, external)
    write(QUESTS / "chapters/01_workshop_rekindled.snbt", doc)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / ("lang/" + locale + ".snbt"), lang)
    build_recipes()
    build_assets()
    print("Workshop: 10 main quests + 5 optional quests, 3 recipes, 15 runtime advancements, native choice reward, authored item/block assets")


if __name__ == "__main__":
    main()
