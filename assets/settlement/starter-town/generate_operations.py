"""Rebuild the Minecraft Builder source, plot anchors and separate street preview."""
from collections import deque
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PREVIOUS = ROOT.parent / "emberstead"
WORK = ROOT.parents[2] / "work" / "starter-town"
OPS, PLOTS = [], []
NAME = "starter-town"
SIDES = ("north", "east", "south", "west")
LOG = "stripped_spruce_log"
STONE = "stone_bricks"
SLATE = ("deepslate_tile_stairs", "deepslate_tiles", "deepslate_tile_slab")
COPPER = ("waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper", "waxed_weathered_cut_copper_slab")
RED = ("brick_stairs", "bricks", "brick_slab")
WOOD = ("dark_oak_stairs", "dark_oak_planks", "dark_oak_slab")


def box(ops, x, y, z, xx, yy, zz, block):
    if x <= xx and y <= yy and z <= zz:
        ops.append(dict(op="cuboid", start=[x, y, z], end=[xx, yy, zz], block=block))


def put(ops, x, y, z, block):
    ops.append(dict(op="block", pos=[x, y, z], block=block))


def lantern(ops, x, y, z, hanging=False):
    put(ops, x, y, z, f"lantern[hanging={str(hanging).lower()},waterlogged=false]")


def panel(ops, x0, y, z, x1, color):
    box(ops, x0, y, z, x1, y + 1, z, color + "_stained_glass")


def bed(ops, x, z, color):
    for dz, part in ((0, "foot"), (1, "head")):
        put(ops, x, 1, z + dz, f"{color}_bed[facing=south,part={part},occupied=false]")


def lamp_post(ops, x, z):
    put(ops, x, 1, z, "chiseled_stone_bricks")
    box(ops, x, 2, z, x, 3, z, "spruce_fence")
    lantern(ops, x, 4, z)


def gable(ops, x0, z0, x1, z1, y, rise, roof, fill="spruce_planks"):
    stair, cap, slab = roof
    for i in range(rise):
        box(ops, x0 + i, y + i, z0, x0 + i, y + i, z1, f"{stair}[facing=east]")
        box(ops, x1 - i, y + i, z0, x1 - i, y + i, z1, f"{stair}[facing=west]")
        for z in (z0 + 1, z1 - 1):
            box(ops, x0 + i + 1, y + i, z, x1 - i - 1, y + i, z, fill)
    box(ops, x0 + rise, y + rise, z0, x1 - rise, y + rise, z1, cap)
    box(ops, x0 + rise, y + rise + 1, z0, x1 - rise, y + rise + 1, z1, f"{slab}[type=bottom]")


def room(ops, x0, z0, x1, z1, top, wall, roof, entry, panes="light_gray", open_entry=False):
    box(ops, x0 - 1, 0, z0 - 1, x1 + 1, 0, z1 + 1, STONE)
    box(ops, x0 + 1, 0, z0 + 1, x1 - 1, 0, z1 - 1, "spruce_planks")
    box(ops, x0, 1, z0, x1, top, z1, wall)
    box(ops, x0 + 1, 1, z0 + 1, x1 - 1, top, z1 - 1, "air")
    for x in (x0, x1):
        for z in range(z0, z1 + 1, 4):
            box(ops, x, 1, z, x, top, z, f"{LOG}[axis=y]")
            if z + 2 < z1:
                box(ops, x, 2, z + 1, x, 3, z + 2, f"{panes}_stained_glass")
        box(ops, x, 1, z1, x, top, z1, f"{LOG}[axis=y]")
        box(ops, x, top, z0, x, top, z1, f"{LOG}[axis=z]")
    for z in (z0, z1):
        box(ops, x0, top, z, x1, top, z, f"{LOG}[axis=x]")
        for x in range(x0 + 3, x1 - 1, 4):
            box(ops, x - 1, 1, z, x - 1, top - 1, z, f"{LOG}[axis=y]")
            if abs(x - entry) > 1 or z == z1:
                panel(ops, x, 2, z, x + 1, panes)
                box(ops, x, 1, z - (1 if z == z0 else -1), x + 1, 1,
                    z - (1 if z == z0 else -1), "spruce_slab[type=top]")
    if open_entry:
        box(ops, entry - 1, 1, z0, entry + 1, 3, z0, "air")
    else:
        for y, half in ((1, "lower"), (2, "upper")):
            put(ops, entry, y, z0, f"spruce_door[facing=north,half={half},hinge=left]")
    gable(ops, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top + 1, (x1 - x0 + 2) // 2, roof)
    for x in range(x0 + 2, x1, 5):
        lantern(ops, x, 1, z0 - 1)
        box(ops, x, top, z0 + 1, x, top, z1 - 1, f"{LOG}[axis=z]")
        lantern(ops, x, top - 1, (z0 + z1) // 2, True)


def yard(ops, w, d):
    box(ops, 0, 0, 0, w - 1, 0, d - 1, "grass_block")


def walk(ops, x, z, end, width=1):
    box(ops, x - width, 0, z, x + width, 0, end, "andesite")


def awning(ops, x0, x1, z0, z1, base, accent):
    for x in (x0, x1):
        box(ops, x, 1, z0, x, base - 1, z0, "spruce_fence")
    for x in range(x0, x1 + 1):
        block = accent if x % 3 == 0 else "white_wool"
        box(ops, x, base, z0, x, base, z1, block)
    lantern(ops, x0 + 1, base - 1, z0, True)
    lantern(ops, x1 - 1, base - 1, z0, True)


def bench(ops, x, z, length, facing):
    box(ops, x, 1, z, x + length - 1, 1, z, f"spruce_stairs[facing={facing}]")


def hall(ops):
    yard(ops, 21, 19)
    walk(ops, 10, 0, 5, 3)
    room(ops, 4, 6, 16, 16, 7, "calcite", SLATE, 10, "yellow", True)
    # Raised public entrance, raised bell gable and broad timber landing.
    box(ops, 6, 0, 1, 14, 0, 5, STONE)
    for x in (6, 14):
        box(ops, x, 1, 2, x, 6, 2, f"{LOG}[axis=y]")
        box(ops, x, 6, 2, x, 6, 5, f"{LOG}[axis=z]")
    gable(ops, 5, 1, 15, 7, 7, 4, COPPER)
    box(ops, 8, 13, 8, 12, 15, 12, STONE)
    box(ops, 9, 14, 8, 11, 14, 12, "air")
    box(ops, 8, 14, 9, 12, 14, 11, "air")
    gable(ops, 7, 7, 13, 13, 16, 3, COPPER)
    put(ops, 10, 14, 10, "bell[attachment=ceiling,facing=north,powered=false]")
    put(ops, 10, 1, 14, "lectern[facing=north,has_book=false,powered=false]")
    box(ops, 7, 0, 12, 13, 0, 15, "polished_andesite")
    for z in (9, 11, 13):
        bench(ops, 6, z, 3, "south")
        bench(ops, 12, z, 3, "south")
    for x in (5, 15):
        lantern(ops, x, 1, 4)
    box(ops, 8, 1, 15, 12, 1, 15, "spruce_slab[type=top]")


def clinic(ops):
    yard(ops, 15, 12)
    walk(ops, 7, 0, 4)
    room(ops, 2, 4, 12, 10, 4, "calcite", COPPER, 7, "white")
    awning(ops, 4, 10, 1, 3, 3, "green_wool")
    bench(ops, 4, 6, 2, "north")
    bed(ops, 9, 7, "white")
    bed(ops, 10, 7, "white")
    put(ops, 5, 1, 8, "cauldron")
    put(ops, 4, 1, 9, "barrel[facing=north]")
    put(ops, 4, 2, 9, "potted_fern")
    box(ops, 8, 1, 9, 8, 2, 9, "white_wool")
    for x in (2, 12):
        put(ops, x, 1, 1, "flowering_azalea")


def store(ops):
    yard(ops, 15, 13)
    walk(ops, 7, 0, 5, 2)
    room(ops, 2, 5, 12, 11, 4, "packed_mud", SLATE, 7, "yellow", True)
    awning(ops, 2, 12, 1, 4, 4, "orange_wool")
    for x in (4, 5, 9, 10):
        put(ops, x, 1, 10, "barrel[facing=north]")
    box(ops, 3, 1, 7, 5, 1, 7, "spruce_planks")
    box(ops, 9, 1, 7, 11, 1, 7, "spruce_planks")
    put(ops, 5, 2, 7, "flower_pot")
    put(ops, 10, 2, 10, "potted_azalea_bush")


def restaurant(ops):
    yard(ops, 17, 19)
    walk(ops, 8, 0, 5, 2)
    room(ops, 2, 5, 14, 16, 5, "calcite", RED, 8, "orange", True)
    awning(ops, 5, 11, 1, 4, 4, "red_wool")
    # A lower kitchen wing and its brick stack form a second roof silhouette.
    room(ops, 9, 12, 15, 17, 4, "bricks", WOOD, 12, "orange")
    box(ops, 10, 1, 12, 12, 3, 12, "air")
    box(ops, 14, 4, 16, 14, 13, 16, "bricks")
    box(ops, 13, 13, 15, 15, 13, 17, "brick_slab[type=top]")
    put(ops, 14, 14, 16, "brick_wall")
    for x in (5, 11):
        for z in (7, 10):
            put(ops, x, 1, z, "spruce_fence")
            put(ops, x, 2, z, "spruce_pressure_plate")
            put(ops, x - 1, 1, z, "spruce_stairs[facing=west]")
            put(ops, x + 1, 1, z, "spruce_stairs[facing=east]")
    for x in (10, 13):
        put(ops, x, 1, 16, "furnace[facing=north]")
    put(ops, 12, 1, 16, "barrel[facing=north]")
    put(ops, 4, 1, 14, "barrel[facing=north]")
    put(ops, 4, 2, 14, "potted_fern")
    # A projecting timber buttress breaks the long dining-room wall facing the square.
    box(ops, 1, 1, 10, 1, 5, 10, f"{LOG}[axis=y]")


def workshop(ops):
    yard(ops, 13, 15)
    walk(ops, 6, 0, 5, 2)
    room(ops, 2, 5, 10, 13, 5, "bricks", SLATE, 6, "orange", True)
    box(ops, 3, 0, 1, 9, 0, 4, "andesite")
    for x in (3, 9):
        box(ops, x, 1, 1, x, 3, 1, f"{LOG}[axis=y]")
    gable(ops, 2, 0, 10, 5, 4, 3, COPPER)
    box(ops, 9, 4, 12, 9, 10, 12, "bricks")
    put(ops, 9, 11, 12, "brick_wall")
    put(ops, 4, 1, 11, "crafting_table")
    put(ops, 5, 1, 11, "furnace[facing=north]")
    put(ops, 9, 1, 11, "stonecutter[facing=north]")
    box(ops, 3, 1, 7, 3, 1, 9, "spruce_planks")
    box(ops, 3, 4, 2, 9, 4, 2, f"{LOG}[axis=x]")
    lantern(ops, 6, 3, 2, True)


def warehouse(ops):
    yard(ops, 13, 11)
    walk(ops, 6, 0, 3, 2)
    room(ops, 2, 3, 10, 9, 4, "spruce_planks", WOOD, 6, "yellow", True)
    for x in (3, 4, 8, 9):
        for y in (1, 2):
            put(ops, x, y, 8, "barrel[facing=north]")
    box(ops, 2, 0, 0, 10, 0, 2, "spruce_planks")
    lantern(ops, 6, 3, 3, True)
    put(ops, 1, 1, 2, "hay_block[axis=y]")
    put(ops, 1, 2, 2, "hay_block[axis=x]")
    put(ops, 11, 1, 2, "hay_block[axis=z]")


def plaza(ops):
    yard(ops, 21, 15)
    box(ops, 1, 0, 1, 19, 0, 13, "andesite")
    box(ops, 2, 0, 2, 18, 0, 12, "polished_andesite")
    walk(ops, 10, 0, 14, 2)
    old_ops(ops, "well", 7, 4)
    for x, z in ((2, 2), (18, 2), (2, 12), (18, 12)):
        lamp_post(ops, x, z)
    for x, accent in ((3, "red_wool"), (14, "yellow_wool")):
        awning(ops, x, x + 3, 5, 8, 4, accent)
        box(ops, x, 1, 8, x + 3, 1, 8, "spruce_planks")
        put(ops, x + 1, 2, 8, "potted_poppy")
    bench(ops, 8, 12, 5, "south")


def gate(ops):
    yard(ops, 11, 10)
    walk(ops, 5, 0, 9, 1)
    for x in (1, 8):
        box(ops, x, 0, 4, x + 1, 3, 6, STONE)
        box(ops, x, 4, 4, x + 1, 5, 6, f"{LOG}[axis=y]")
        box(ops, x, 1, 7, x + 1, 1, 9, "stone_brick_wall")
        lantern(ops, x, 1, 3)
    box(ops, 2, 5, 5, 8, 5, 5, f"{LOG}[axis=x]")
    gable(ops, 0, 3, 10, 7, 6, 4, SLATE)
    lantern(ops, 5, 4, 5, True)
    put(ops, 8, 1, 1, "spruce_fence")
    put(ops, 8, 2, 1, "spruce_sign[rotation=8,waterlogged=false]")


def old_ops(target, name, x=0, z=0):
    """Translate the original structure operations, clipped to its authored plot."""
    ox, oz, w, d = OLD_LAYOUT[name]["bounds"]
    for op in OLD_OPERATIONS:
        if op["op"] == "block":
            ax, ay, az = op["pos"]
            if ox <= ax < ox + w and oz <= az < oz + d:
                put(target, x + ax - ox, ay, z + az - oz, op["block"])
        elif op["op"] == "cuboid":
            ax, ay, az = op["start"]
            bx, by, bz = op["end"]
            box(target, x + max(ax, ox) - ox, ay, z + max(az, oz) - oz,
                x + min(bx, ox + w - 1) - ox, by, z + min(bz, oz + d - 1) - oz, op["block"])


def position(p, w, d, turns):
    x, y, z = p
    for _ in range(turns):
        x, z, w, d = d - 1 - z, x, d, w
    return [x, y, z]


def state(block, turns):
    name, sep, props = block.partition("[")
    if not sep or turns == 0:
        return block
    compass = dict(zip(SIDES, SIDES[turns:] + SIDES[:turns]))
    out = []
    for prop in props[:-1].split(","):
        key, value = prop.split("=")
        if key == "axis" and value in ("x", "z") and turns % 2:
            value = "z" if value == "x" else "x"
        elif key == "rotation":
            value = str((int(value) + 4 * turns) % 16)
        elif key == "facing":
            value = compass.get(value, value)
        out.append(compass.get(key, key) + "=" + value)
    return name + "[" + ",".join(out) + "]"


def module(name, x, z, w, d, ex, fn, turns=0, anchors=None):
    local = []
    fn(local)
    global_pos = lambda p: [q + shift for q, shift in zip(position(p, w, d, turns), (x, 0, z))]
    for op in local:
        converted = dict(op, block=state(op["block"], turns))
        if op["op"] == "block":
            converted["pos"] = global_pos(op["pos"])
        else:
            a, b = global_pos(op["start"]), global_pos(op["end"])
            converted.update(start=list(map(min, a, b)), end=list(map(max, a, b)))
        OPS.append(converted)
    width, depth = (d, w) if turns % 2 else (w, d)
    desc = dict(id=name, bounds=[x, z, width, depth], entry=position([ex, 0, -1], w, d, turns),
                desired=[x - 32, z - 48], villagers=[])
    if anchors:
        for key in ("home", "bed"):
            if key in anchors:
                desc[key] = position(anchors[key], w, d, turns)
        desc["villagers"] = [position(pt, w, d, turns) for pt in anchors.get("villagers", [])]
    PLOTS.append(desc)


def retained(name, x, z, turns=0):
    desc = OLD_LAYOUT[name]
    module(name, x, z, *desc["bounds"][2:], desc["entry"][0],
           lambda ops: old_ops(ops, name), turns, desc)


def write(path, doc):
    path.write_text(json.dumps(doc, ensure_ascii=True, separators=(",", ":")) + "\n", encoding="utf-8")


def street_preview():
    # This pavement is only for the flat design view. Runtime grades each connector.
    blocked = {(x, z) for p in PLOTS for px, pz, w, d in [p["bounds"]]
               for x in range(px, px + w) for z in range(pz, pz + d)}
    preview = []
    street = {(x, z) for x in range(2, 77) for z in (21, 22, 43, 44)} - blocked
    street |= {(42, z) for z in range(22, 45)} - blocked
    street |= {(67, z) for z in range(22, 50)} - blocked
    for p in PLOTS:
        px, pz, _, _ = p["bounds"]
        dx, _, dz = p["entry"]
        start = (px + dx, pz + dz)
        queue, parent = deque([start]), {start: None}
        while queue:
            tile = queue.popleft()
            if tile in street:
                while tile:
                    street.add(tile)
                    tile = parent[tile]
                break
            x, z = tile
            for nxt in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                a, b = nxt
                if 0 <= a < 80 and 0 <= b < 65 and nxt not in parent and nxt not in blocked:
                    parent[nxt] = tile
                    queue.append(nxt)
        else:
            raise ValueError(f"No preview road to {p['id']}")
    for x, z in sorted(street):
        put(preview, x, 0, z, "cobblestone" if (x + z) % 5 == 0 else "andesite")
    return preview


def main():
    global OLD_OPERATIONS, OLD_LAYOUT
    OLD_OPERATIONS = json.loads((PREVIOUS / "operations.json").read_text(encoding="utf-8"))["operations"]
    OLD_LAYOUT = {p["id"]: p for p in json.loads((PREVIOUS / "layout.json").read_text(encoding="utf-8"))["modules"]}
    retained("player_home", 32, 48)
    retained("villager_west", 17, 49)
    retained("villager_east", 51, 51)
    retained("farm", 0, 26, 1)
    retained("expansion_plot", 69, 28)
    module("well", 20, 25, 21, 15, 10, plaza)
    module("town_hall", 20, 0, 21, 19, 10, hall, 2)
    module("clinic", 1, 6, 15, 12, 7, clinic, 2)
    module("general_store", 46, 5, 15, 13, 7, store, 2)
    module("restaurant", 46, 25, 17, 19, 8, restaurant, 3)
    module("workshop", 67, 2, 13, 15, 6, workshop, 2)
    module("granary", 0, 48, 13, 11, 6, warehouse)
    module("town_gate", 69, 49, 11, 10, 5, gate)
    doc = dict(name=NAME, description="Three streets, a well square and thirteen terraced plots.", operations=OPS)
    write(ROOT / "operations.json", doc)
    write(ROOT / "layout.json", dict(schema=2, schematic=NAME + ".schem", modules=PLOTS))
    WORK.mkdir(parents=True, exist_ok=True)
    write(WORK / "preview.json", dict(doc, operations=[
        dict(op="cuboid", start=[0, -1, 0], end=[79, -1, 64], block="grass_block"),
        *OPS, *street_preview()]))
    print(f"{len(OPS)} operations across {len(PLOTS)} plots; preview on an 80 x 65 site")


if __name__ == "__main__":
    main()
