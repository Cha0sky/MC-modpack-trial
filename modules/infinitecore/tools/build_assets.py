"""Deterministic 16px original artwork and baseline-derived recipe resources.

Writes only this module's src/main/resources. No instance, world or vendor writes.
The binary is a read-only source for retained recipes, tags, languages and legacy icons.
"""
from pathlib import Path
import io
import json
import zipfile
from PIL import Image, ImageDraw

MODULE = Path(__file__).resolve().parents[1]
RESOURCES = MODULE / "src/main/resources"
BASELINE = MODULE.parents[1] / "vendor/infinitecore/infinitecore-1.0.0.jar"
TIERS = ("dirt", "wood", "stone", "copper", "iron", "gold", "diamond", "netherite")
COLORS = ("#876044", "#BA8852", "#727F89", "#C87546", "#C8D2DA", "#D9B447", "#43C8CB", "#65596D")
MATERIALS = ("dirt", "oak_log", "smooth_stone", "copper_block", "iron_block", "gold_block", "diamond_block", "netherite_block")


def write_json(relative, value):
    path = RESOURCES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def mix(color, factor):
    values = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))
    return tuple(max(0, min(255, round(v * factor))) for v in values) + (255,)


def save(image, relative):
    path = RESOURCES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def machine(color, index, face):
    image = Image.new("RGBA", (16, 16), "#1B2026")
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 14, 14), fill="#49545E")
    d.line((1, 1, 14, 1), fill="#78848D")
    d.line((1, 2, 1, 14), fill="#64717B")
    d.line((2, 14, 14, 14), fill="#293239")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill="#A9B4BC")
    if face == "side":
        d.rectangle((4, 3, 11, 11), fill=mix(color, .7))
        d.line((4, 3, 11, 3), fill=mix(color, 1.25))
        d.rectangle((5, 5, 10, 9), fill="#111A22")
        d.rectangle((6, 6, 9, 8), fill=mix(color, 1.3))
        d.line((7, 6, 8, 6), fill="#D9F0F1")
        for y in (5, 7, 9):
            d.line((2, y, 3, y), fill="#162129")
            d.line((12, y, 13, y), fill="#162129")
        # Eight discrete tier lamps, readable independently of the hue.
        for n in range(8):
            d.point((4 + n, 13), fill=mix(color, 1.5) if n <= index else "#242C32")
        if index == 0:
            for p in ((4, 4), (11, 6), (4, 10)): d.point(p, fill="#B08057")
        elif index == 1:
            d.line((4, 10, 11, 10), fill="#D0A06C")
        elif index == 2:
            d.point((4, 8), fill="#A5ADB2")
            d.point((11, 4), fill="#A5ADB2")
        elif index == 6:
            d.point((5, 7), fill="#95EBE9")
            d.point((10, 7), fill="#95EBE9")
    elif face == "top":
        d.rectangle((4, 4, 11, 11), fill="#19242D", outline=mix(color, .9))
        d.polygon(((8, 5), (10, 8), (8, 10), (5, 8)), fill=mix(color, 1.3))
        d.point((8, 7), fill="#E1F3EF")
        for a in (4, 7, 10): d.line((a, 2, a + 1, 2), fill=mix(color, 1.0))
    else:
        d.rectangle((4, 4, 11, 11), fill="#303B45", outline="#171F25")
        for y in (5, 7, 9): d.line((5, y, 10, y), fill="#515E68")
        d.line((4, 12, 11, 12), fill=mix(color, .7))
    return image


def template(color, index):
    image = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(image)
    d.rectangle((2, 1, 13, 14), fill="#17212A", outline="#A4B0B8")
    d.line((3, 2, 12, 2), fill=mix(color, 1.15))
    d.rectangle((3, 4, 12, 10), fill=mix(color, .6))
    # Twin-loop core glyph, keeping the reusable template recognizable as a card.
    d.line(((4, 6), (6, 5), (10, 8), (11, 7), (10, 5), (6, 8), (4, 7), (4, 6)), fill=mix(color, 1.5), width=1)
    for n in range(8): d.point((4 + n, 12), fill=mix(color, 1.5) if n <= index else "#35424D")
    return image


def panel(draw, bounds, fill="#252F38"):
    draw.rectangle(bounds, fill=fill, outline="#111A22")
    x1, y1, x2, y2 = bounds
    draw.line((x1 + 1, y1 + 1, x2 - 1, y1 + 1), fill="#4C5B66")


def gui(io_page=False):
    image = Image.new("RGBA", (256, 256))
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 175, 215), fill="#161E25")
    d.rectangle((1, 1, 174, 214), fill="#3B4853")
    d.rectangle((3, 3, 172, 212), fill="#303B45")
    d.line((3, 3, 172, 3), fill="#74838E")
    panel(d, (5, 4, 170, 20), "#222D36")
    for x, y in ((3, 3), (172, 3), (3, 212), (172, 212)):
        d.point((x, y), fill="#ABBAC4")
    if io_page:
        for row in range(6): panel(d, (7, 31 + row * 23, 169, 52 + row * 23))
        panel(d, (7, 174, 169, 206), "#222D36")
    else:
        panel(d, (7, 23, 169, 48))
        panel(d, (7, 50, 73, 109))
        panel(d, (78, 50, 169, 109))
        panel(d, (7, 111, 169, 122), "#202C33")
        d.rectangle((82, 102, 161, 107), fill="#121B21")
        panel(d, (5, 131, 170, 187), "#27333D")
        panel(d, (5, 189, 170, 212), "#27333D")
        positions = [(12 + col * 18, 54 + row * 18) for row in range(3) for col in range(3)]
        positions += [(12, 26)]
        positions += [(8 + col * 18, 134 + row * 18) for row in range(3) for col in range(9)]
        positions += [(8 + col * 18, 192) for col in range(9)]
        for x, y in positions:
            d.rectangle((x - 1, y - 1, x + 16, y + 16), fill="#121C24")
            d.rectangle((x, y, x + 15, y + 15), fill="#293B48")
            d.line((x, y + 15, x + 15, y + 15), fill="#566975")
        d.rectangle((41, 25, 58, 42), outline="#61757F")
    return image


def main():
    with zipfile.ZipFile(BASELINE) as baseline:
        # Preserve eight machine recipes and both mining tags byte-for-byte.
        paths = [f"data/infinitecore/recipe/{tier}_generator.json" for tier in TIERS]
        paths += [p for p in baseline.namelist() if p.startswith("data/minecraft/tags/") and p.endswith(".json")]
        # Legacy blanks remain valid IDs and icons, but have no crafting recipe.
        paths += [p for p in baseline.namelist() if "/blank_template_" in p and p.startswith("assets/")]
        for relative in paths:
            path = RESOURCES / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(baseline.read(relative))
        languages = {locale: json.loads(baseline.read(f"assets/infinitecore/lang/{locale}.json")) for locale in ("zh_cn", "en_us")}
    for index, (tier, color, material) in enumerate(zip(TIERS, COLORS, MATERIALS)):
        for face, suffix in (("side", ""), ("top", "_top"), ("bottom", "_bottom")):
            save(machine(color, index, face), f"assets/infinitecore/textures/block/{tier}_generator{suffix}.png")
        save(template(color, index), f"assets/infinitecore/textures/item/template_{tier}.png")
        write_json(f"assets/infinitecore/blockstates/{tier}_generator.json", {"variants": {"": {"model": f"infinitecore:block/{tier}_generator"}}})
        write_json(f"assets/infinitecore/models/block/{tier}_generator.json", {
            "parent": "minecraft:block/cube_bottom_top", "textures": {
                "side": f"infinitecore:block/{tier}_generator", "top": f"infinitecore:block/{tier}_generator_top", "bottom": f"infinitecore:block/{tier}_generator_bottom"}})
        write_json(f"assets/infinitecore/models/item/{tier}_generator.json", {"parent": f"infinitecore:block/{tier}_generator"})
        write_json(f"assets/infinitecore/models/item/template_{tier}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"infinitecore:item/template_{tier}"}})
        write_json(f"data/infinitecore/recipe/template_{tier}.json", {
            "type": "minecraft:crafting_shaped", "pattern": ["XXX", "XXX", "XXX"],
            "key": {"X": {"item": f"minecraft:{material}"}}, "result": {"id": f"infinitecore:template_{tier}", "count": 1}})
    write_json("data/infinitecore/recipe/template_customize.json", {"type": "infinitecore:template_customize"})
    write_json("pack.mcmeta", {"pack": {"description": "Infinite Core rebuilt resources", "pack_format": 34}})
    save(gui(), "assets/infinitecore/textures/gui/generator.png")
    save(gui(True), "assets/infinitecore/textures/gui/generator_io.png")
    translations = {
        "gui.infinitecore.io_config": ("IO", "IO"),
        "gui.infinitecore.io_title": ("六面物品接口", "Item IO sides"),
        "gui.infinitecore.back": ("返回", "Back"),
        "gui.infinitecore.syncing": ("同步中", "Syncing"),
        "gui.infinitecore.status.syncing": ("正在同步机器状态", "Syncing machine state"),
        "gui.infinitecore.status.no_template": ("等待生成模板", "Waiting for a template"),
        "gui.infinitecore.status.running": ("运行中 · 每次生成 1 个", "Running - 1 item per cycle"),
        "gui.infinitecore.status.full": ("库存已满 · 暂无可用空间", "Blocked - no room for target"),
        "gui.infinitecore.mode.none": ("关闭", "Off"),
        "gui.infinitecore.mode.input": ("输入", "Input"),
        "gui.infinitecore.mode.output": ("输出", "Output"),
        "gui.infinitecore.mode.both": ("双向", "Both"),
        "gui.infinitecore.can_transfer": ("有物品接口", "Item interface"),
        "gui.infinitecore.no_interface": ("无物品接口", "No item interface"),
        "gui.infinitecore.neighbor.none": ("无邻接方块", "No neighbor"),
        "gui.infinitecore.io_input_hint": ("输入：仅吸入生成模板", "Input: pulls templates only"),
        "gui.infinitecore.io_output_hint": ("输出：仅推出资源库存", "Output: pushes resource slots"),
        "gui.infinitecore.inventory_slots": ("资源库存 %s/9", "Storage %s/9"),
        "gui.infinitecore.interval": ("%s tick/次", "%s ticks/item"),
        "gui.infinitecore.rate": ("理论 %s 个/s", "%s items/s"),
        "gui.infinitecore.multiplier": ("模板倍率 ×%s", "Template x%s"),
        "gui.infinitecore.template_tier": ("%s模板 · 无消耗", "%s template - reusable"),
        "gui.infinitecore.template_slot": ("生成模板槽（可跨阶使用）", "Template slot (any tier)"),
        "gui.infinitecore.rate_hint": ("每次生成 1 个；速率按 20 TPS 计算，实际产量受服务器 TPS 和库存空间影响。", "One item per cycle. Rate assumes 20 TPS; actual output depends on TPS and storage space."),
        "tooltip.infinitecore.no_consumption": ("生成时不消耗模板；定制合成会消耗目标物品", "Generation keeps the template; customization consumes the target item"),
    }
    for direction, chinese, short in zip(("down", "up", "north", "south", "west", "east"), ("下", "上", "北", "南", "西", "东"), ("D", "U", "N", "S", "W", "E")):
        translations[f"gui.infinitecore.direction.{direction}"] = (chinese, short)
        translations[f"gui.infinitecore.direction_full.{direction}"] = (chinese, direction.capitalize())
    for tier, chinese in zip(TIERS, ("泥土", "木头", "石头", "铜", "铁", "金", "钻石", "下界合金")):
        translations[f"gui.infinitecore.tier.{tier}"] = (chinese, tier.capitalize())
    for key, values in translations.items():
        for locale, value in zip(("zh_cn", "en_us"), values): languages[locale][key] = value
    for locale, values in languages.items(): write_json(f"assets/infinitecore/lang/{locale}.json", values)
    print("Generated 24 machine faces, 8 template cards, 2 GUI backgrounds, 17 recipes and retained legacy icons.")


if __name__ == "__main__":
    main()
