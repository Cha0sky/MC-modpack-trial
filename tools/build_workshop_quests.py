"""Build the approved workshop chapter and its small authored resource set.

Only this chapter's quest/lang keys and named resources are written. Existing
prologue IDs, rewards and translations are kept. This is functional quest text,
not an expansion of the author's still-unwritten full story or outer-god lore.
"""
from pathlib import Path
from campaign_navigation import finish_chapter
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
    "arrival", "diagnose", "site_photo", "calibrate", "restore", "both_regions", "batch_copper",
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

    def quest(name, title, desc, x, y, icon, needs, rewards=(), optional=False, alternative=False, minimum=0, native=None, subtitle=None, operation=None):
        q = {"id": uid(name), "x": x, "y": y, "icon": {"id": icon}, "min_width": 320,
             "dependencies": [uid(n) for n in needs] if name != "commission" else [prologue_next],
             "dependency_requirement": "all_completed", "progression_mode": "linear", "optional": optional or alternative,
             "hide_dependency_lines": False, "hide_until_deps_visible": False,
             "hide_until_deps_complete": False, "hide_lock_icon": False,
             "tasks": [{"id": uid(name + "/task"), "type": "advancement",
                        "advancement": MOD + "workshop/" + name, "criterion": "done"}],
             "rewards": [*rewards, *milestone_supplies(name)]}
        if minimum:
            q["min_required_dependencies"] = minimum
        if native:
            q["tasks"][0]["advancement"] = native
            del q["tasks"][0]["criterion"]
        elif name == "diagnose":
            # A previously completed calibration also proves the relay was inspected.
            del q["tasks"][0]["criterion"]
        if operation:
            q["tasks"].append({"id": uid(name + "/operation"), "type": "advancement",
                               "advancement": "create:" + operation})
        doc["quests"].append(q)
        lang["quest." + q["id"] + ".title"] = ("支线 · " if optional else "") + title
        lang["quest." + q["id"] + ".quest_desc"] = desc
        if subtitle:
            lang["quest." + q["id"] + ".quest_subtitle"] = subtitle

    quest("commission", "旧工坊的委托", [
        "镇上流传着古代英雄封住受侵蚀土地的故事。边境那座工坊究竟是怎样停下来的？先做一把能读出旧设备状态的工具，再进去看看。",
        "两路可以同时开工。采样的人带斧子取木样，留守的人架水车、压板材。等纤维磨好，选侧送或下料，把原料送上生产线，再开搅拌器。"],
        0, 0, "minecraft:book", (), subtitle="边境取样与家中供能，可分头开工")
    quest("survey", "越过边境", [
        "走进腐化或猩红，收集 4 个乌木原木或暗影原木，两种可以混用。把木样送进磨石。"],
        2, -1, "confluence:ebony_log", ("commission",))
    quest("power", "让动力持续运转", [
        "让压机、磨石或搅拌器转起来，在旁边累计观察 10 秒。小水车可带动 8 RPM 的支路；用大小齿轮分两级增速，把搅拌器提到 32 RPM。"],
        2, 1, "create:water_wheel", ("commission",))
    quest("plates", "第一批精密零件", [
        "接好压机，把锭放到压头正下方的置物台上试压。备 7 张铁板和 2 张铜板，先拿起整批板材再用。5 张铁板和 2 个安山合金做搅拌头，其余留给调谐器。"],
        4, 1, "create:mechanical_press", ("power",), operation="mechanical_press")
    quest("fiber", "异常材料的用途", [
        "剖开边境木样之前，先把磨石转起来。4 个原木磨出 8 份浸染纤维，正好是下一锅共振胶的原料。"],
        4, -1, MOD + "tainted_fiber", ("survey", "power"), operation="millstone")
    quest("side_feed", "送料方案 · 侧面出库", [
        "原料库和搅拌间在同一层？用干海带和安山合金做安山漏斗，贴在原料桶侧面，下面接传送带或置物台。把纤维放进桶，看着漏斗实际送出一份。",
        "两种送料方式任选一种。把试送的纤维取回，连同其余原料一起送进工作盆。"],
        5.5, -1, "create:andesite_funnel", ("plates", "fiber"), alternative=True, native="create:funnel",
        rewards=[item_reward("side_feed/belts", "create:belt_connector", 4)])
    quest("gravity_feed", "送料方案 · 从楼上投料", [
        "原料库在压机上方？另备 2 张铁板和 1 个铁锭做溜槽。上接原料桶，下接收料箱，投入一份纤维，看它落进下层。两种送料方式任选一种。"],
        5.5, 1, "create:chute", ("plates", "fiber"), alternative=True, native="create:chute",
        rewards=[item_reward("gravity_feed/hoppers", "minecraft:hopper", 4)])
    quest("binder", "稳定的共振胶", [
        "把试送过的纤维送进工作盆，每锅下 2 份纤维、1 红石粉和 1 黏液球或凝胶。接上达到 32 RPM 的搅拌器，开机冷拌。取出 4 份装进背包，1 份粘调谐器，3 份带去修继电器。"],
        7, 0, MOD + "resonant_binder", ("side_feed", "gravity_feed"), minimum=1, operation="mechanical_mixer")
    quest("tuner", "粗制调谐器", [
        "工作台上排摆铜板、玻璃、铜板；中排摆铁板、共振胶、铁板；下排中央放传动杆。",
        "在主世界用调谐器对空气右键领地图。带上 3 份胶、水车和传动件再出发。"],
        8, 0, MOD + "rough_tuner", ("binder",))
    quest("arrival", "找到旧址", [
        "顺勘测图进工坊，先找原料经过的动力室，再找换向装置和屋顶信号台。可以读一台继电器，也可以先拍下现场。",
        "旧存档可往新生成的区块找。地图遗失时，在主世界用调谐器对空气右键补领。"],
        10, 0, "minecraft:filled_map", ("tuner",))
    quest("diagnose", "开工勘测 · 读继电器", [
        "拿粗制调谐器右键旧工坊里的一台继电器，读出接口故障。记下需要接轴的那一面，先布置水车和齿轮。",
        "读设备和拍现场两种勘测任选一种即可开工。"],
        11, -1.5, MOD + "rough_tuner", ("arrival",), alternative=True, rewards=[
            item_reward("diagnose/belt", "create:belt_connector", 4)])
    quest("site_photo", "开工勘测 · 拍现场", [
        "给拾光定影的相机装上胶卷，站进工坊，在控制台或屋顶信号台旁拍一帧。按下快门就能回去布置传动。"],
        11, 1.5, "exposure:camera", ("arrival",), alternative=True, rewards=[
            item_reward("site_photo/frame", "minecraft:item_frame", 4)])
    quest("calibrate", "三处故障", [
        "把轴端对准节点正面的彩色接口，三个节点都要达到 8 RPM，换向节点还要正转。用调谐器右键读转速，反向时加一对齿轮换向。",
        "每处达标后长按右键约 3 秒，用掉 1 份共振胶。在同一座工坊修好三处。"],
        12, 0, MOD + "workshop_relay", ("diagnose", "site_photo"), minimum=1, subtitle="读数、实拍任选一项，再修同一座工坊的三处接口")
    quest("restore", "工坊重新亮起", [
        "三处都亮起后，保持供能，再操作中央控制台。曾经有人靠这套传动修工具，如今你也可以把它交给下一个来修理的人。",
        "维修簿通向三项新的调查。把奖励中的共振胶留给档案台。"],
        14, 0, MOD + "workshop_console", ("calibrate",), rewards=[
            item_reward("restore/alloy", "create:andesite_alloy", 8),
            item_reward("restore/shaft", "create:shaft", 16),
            item_reward("restore/cogwheel", "create:cogwheel", 8),
        ])
    quest("both_regions", "另一种异常", [
        "分别到腐化和猩红，各收集 4 个当地原木。两份边境标本可以挂进展示框。"],
        4, -3, "confluence:shadow_log", ("survey",), optional=True, rewards=[
            item_reward("both_regions/binder", MOD + "resonant_binder", 2),
            named_reward("both_regions/ebony", "minecraft:purple_dye", "腐化原木标本牌", ["边境调查纪念", "可放入展示框陈列。"]),
            named_reward("both_regions/shadow", "minecraft:red_dye", "猩红原木标本牌", ["边境调查纪念", "可放入展示框陈列。"]),
        ])
    quest("batch_copper", "从手作到量产", [
        "侧送和下落任选一种完成试车，再把铜板产量做到 16 张。调谐器用掉的铜板也算。领回隧道、漏斗和传送带，给下一批留出进出料的位置。"],
        7, 4, "create:andesite_funnel", ("side_feed", "gravity_feed"), minimum=1, optional=True, rewards=[
            item_reward("batch_copper/funnel", "create:andesite_funnel", 4),
            item_reward("batch_copper/belt", "create:belt_connector", 4),
        ])
    quest("field_notes", "野外见闻录", [
        "走进三种不同的 Explorify 野外遗迹，例如营地、路标和补给藏点。"],
        8, -3, "minecraft:spyglass", ("commission",), optional=True, rewards=[
            item_reward("field_notes/anvil", "minecraft:anvil"),
            item_reward("field_notes/experience", "minecraft:experience_bottle", 8),
        ])
    quest("photography", "第一次远征纪念", [
        "亲自拍摄三张不同的照片，冲洗并打印，收进背包。工坊复工前后的景象就值得留一对。"],
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
        "去钢铁守护者室挑战沉睡的守卫。等它的重击落空，再绕到背后出手。"],
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
        write(DATA / ("advancement/workshop/" + name + ".json"), workshop_record(name))


def workshop_record(name):
    if name == "site_photo":
        return {"criteria": {"done": {"trigger": "exposure:frame_exposed",
                "conditions": {"player": {"location": {"structures": MOD + "abandoned_workshop"}}}}}}
    done = {"trigger": "minecraft:impossible"}
    if name == "diagnose":
        calibrated = {"trigger": "minecraft:location", "conditions": {"player": {"type_specific": {
            "type": "minecraft:player", "advancements": {MOD + "workshop/calibrate": True}}}}}
        return {"criteria": {"done": done, "previous_calibration": calibrated},
                "requirements": [["done", "previous_calibration"]]}
    return {"criteria": {"done": done}}


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
        "structure.world_progression_core.abandoned_workshop": "废弃工坊",
        "structure.world_progression_core.starter_town": "出生集镇",
        "item.world_progression_core.tainted_fiber": "浸染纤维",
        "item.world_progression_core.resonant_binder": "共振胶",
        "item.world_progression_core.rough_tuner": "粗制调谐器",
        "block.world_progression_core.workshop_relay": "旧工坊继电器",
        "block.world_progression_core.workshop_console": "旧工坊控制台",
        "itemGroup.world_progression_core": "失落世界 · 工坊遗产",
    })
    merge(ASSETS / "lang/en_us.json", {
        "structure.world_progression_core.abandoned_workshop": "Abandoned Workshop",
        "structure.world_progression_core.starter_town": "Starter Town",
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
    assert len(doc["quests"]) == 19
    nodes = {q["id"]: q for q in doc["quests"]}
    visited = {external_dependency}
    for q in doc["quests"]:
        assert set(q["dependencies"]) <= visited, "Missing or cyclic quest dependency"
        visited.add(q["id"])
    assert all(nodes[uid(name)]["dependencies"] == [uid("plates"), uid("fiber")]
               for name in ("side_feed", "gravity_feed"))
    assert nodes[uid("binder")]["dependencies"] == [uid("side_feed"), uid("gravity_feed")]
    assert nodes[uid("binder")]["min_required_dependencies"] == 1
    assert nodes[uid("restore")]["dependencies"] == [uid("calibrate")]
    assert all(nodes[uid(name)]["optional"] and nodes[uid(name)]["dependencies"] == [uid("arrival")]
               for name in ("diagnose", "site_photo"))
    assert nodes[uid("calibrate")]["dependencies"] == [uid("diagnose"), uid("site_photo")]
    assert nodes[uid("calibrate")]["min_required_dependencies"] == 1
    assert "criterion" not in nodes[uid("diagnose")]["tasks"][0]
    assert all(nodes[uid(name)]["optional"] for name in ("both_regions", "batch_copper", "field_notes", "photography", "wroughtnaut"))
    choices = nodes[uid("wroughtnaut")]["rewards"][0]["table_data"]["rewards"]
    assert len(choices) == 3
    assert nodes[uid("batch_copper")]["dependencies"] == [uid("side_feed"), uid("gravity_feed")]
    assert nodes[uid("batch_copper")]["min_required_dependencies"] == 1
    records = [q["tasks"][0]["advancement"] for q in doc["quests"]]
    assert {MOD + "workshop/" + name for name in ADVANCEMENTS} | {"create:funnel", "create:chute"} == set(records)
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
    finish_chapter(doc, lang)
    write(QUESTS / "chapters/01_workshop_rekindled.snbt", doc)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / ("lang/" + locale + ".snbt"), lang)
    build_recipes()
    build_assets()
    print("Workshop: 19 quests, two site survey routes and two delivery trials")


if __name__ == "__main__":
    main()
