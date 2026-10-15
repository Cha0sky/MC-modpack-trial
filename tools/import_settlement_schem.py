"""Import a Sponge .schem into existing starter modules, without WorldEdit at runtime.

All inputs/outputs must stay in this repository. Explicit block-entity contents,
entities and voxels outside the declared module footprints are rejected rather
than silently lost. Run --help for the small offline conversion interface.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "modules/world-progression-core/src/main/resources/data/world_progression_core/settlement/modules.json"
MAIN_IDS = {"player_home", "villager_west", "villager_east", "well", "farm", "expansion_plot"}
FALLBACK_IDS = {"player_compact", "villager_west_compact", "villager_east_compact"}
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
MAX_BYTES = 64 * 1024 * 1024
MAX_CELLS = 8 * 1024 * 1024
STATE_PATTERN = re.compile(r"minecraft:[a-z0-9_]+(?:\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*\])?\Z")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def repo_path(value):
    path = Path(value).resolve()
    require(path.is_relative_to(ROOT), f"Path is outside repository: {path}")
    return path


def relative(path):
    return repo_path(path).relative_to(ROOT).as_posix()


class NbtReader:
    """Bounded big-endian Java NBT reader; no third-party packages required."""

    def __init__(self, data):
        require(len(data) <= MAX_BYTES, "NBT exceeds 64 MiB")
        self.data, self.offset = data, 0

    def take(self, count):
        require(0 <= count <= len(self.data) - self.offset, "Truncated NBT")
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.number("H")).decode("utf-8")

    def count(self):
        count = self.number("i")
        require(0 <= count <= MAX_CELLS * 5, "Invalid NBT collection size")
        return count

    def payload(self, tag, depth=0):
        require(depth < 64, "NBT nesting exceeds 64 levels")
        if tag in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[tag])
        if tag == 7:
            return self.take(self.count())
        if tag == 8:
            return self.string()
        if tag == 9:
            subtype, count = self.number("B"), self.count()
            require(subtype != 0 or count == 0, "Nonempty TAG_End list")
            return [self.payload(subtype, depth + 1) for _ in range(count)]
        if tag == 10:
            result = {}
            while (subtype := self.number("B")) != 0:
                name = self.string()
                require(name not in result, f"Duplicate NBT field: {name}")
                result[name] = self.payload(subtype, depth + 1)
            return result
        if tag in (11, 12):
            count = self.count()
            return [self.number("i" if tag == 11 else "q") for _ in range(count)]
        raise ValueError(f"Unsupported NBT tag: {tag}")

    def root(self):
        require(self.number("B") == 10, "NBT root must be a compound")
        self.string()
        root = self.payload(10)
        require(self.offset == len(self.data), "Unexpected trailing NBT bytes")
        return root


def decode_varints(data, count, palette):
    require(isinstance(data, bytes), "BlockData/Data must be a byte array")
    values, value, shift = [], 0, 0
    for byte in data:
        require(shift < 35, "Block palette VarInt exceeds five bytes")
        value |= (byte & 127) << shift
        if byte & 128:
            shift += 7
        else:
            require(value in palette, f"Unknown block palette index: {value}")
            values.append(value)
            require(len(values) <= count, "Too many block palette indices")
            value, shift = 0, 0
    require(shift == 0, "Truncated block palette VarInt")
    require(len(values) == count, f"Expected {count} block indices, got {len(values)}")
    return values


def check_block_entities(entities):
    """Only empty container/bed identity records can be discarded safely."""
    require(isinstance(entities, list), "BlockEntities must be a list")
    allowed = {"id", "Id", "Pos", "x", "y", "z", "keepPacked"}
    for entity in entities:
        require(isinstance(entity, dict), "Malformed block entity")
        identity = entity.get("Id", entity.get("id", ""))
        require(identity in {"minecraft:chest", "minecraft:bed", "minecraft:barrel"},
                f"Unsupported block entity: {identity!r}; use plain blocks with no NBT")
        for key, value in entity.items():
            if key == "Data":
                require(isinstance(value, dict), "Block entity Data must be a compound")
                for field, content in value.items():
                    require(field in allowed or (field == "Items" and content == []),
                            f"Block entity contents would be lost: {field}")
            else:
                require(key in allowed or (key == "Items" and value == []),
                        f"Block entity contents would be lost: {key}")


def read_schematic(path):
    raw = path.read_bytes()
    require(len(raw) <= MAX_BYTES, "Schematic file exceeds 64 MiB")
    if raw.startswith(b"\x1f\x8b"):
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
            raw = stream.read(MAX_BYTES + 1)
    root = NbtReader(raw).root()
    doc = root.get("Schematic", root)
    version = doc.get("Version")
    require(version in (2, 3), f"Expected Sponge schematic v2 or v3, got {version!r}")
    width, height, length = (doc.get(name) for name in ("Width", "Height", "Length"))
    require(all(type(n) is int and 0 < n <= 512 for n in (width, height, length)),
            "Schematic dimensions must be positive integers no greater than 512")
    count = width * height * length
    require(count <= MAX_CELLS, "Schematic exceeds eight million cells")
    blocks = doc if version == 2 else doc.get("Blocks", {})
    raw_palette = blocks.get("Palette", {})
    require(isinstance(raw_palette, dict) and raw_palette, "Missing block palette")
    palette = {}
    for state, index in raw_palette.items():
        require(STATE_PATTERN.fullmatch(state), f"Only vanilla block states are allowed: {state!r}")
        require(type(index) is int and index >= 0 and index not in palette, "Duplicate/negative palette index")
        if "[" in state:
            props = state.split("[", 1)[1][:-1].split(",")
            require(len({item.split("=")[0] for item in props}) == len(props), f"Duplicate state property: {state}")
        palette[index] = state
    if version == 2:
        require(doc.get("PaletteMax", len(palette)) == len(palette), "PaletteMax does not match palette")
    entities = blocks.get("BlockEntities", doc.get("BlockEntities", doc.get("TileEntities", [])))
    check_block_entities(entities)
    require(not doc.get("Entities", []), "Schematic entities must be empty; villagers come from layout anchors")
    indices = decode_varints(blocks.get("BlockData" if version == 2 else "Data"), count, palette)
    voxels = {}
    for index, palette_index in enumerate(indices):
        state = palette[palette_index]
        if state not in AIR:
            x = index % width
            z = index // width % length
            y = index // (width * length)
            voxels[x, y, z] = state
    metadata = {"format": "Sponge", "version": version, "data_version": doc.get("DataVersion"),
                "size": [width, height, length], "non_air_blocks": len(voxels),
                "empty_block_entities_omitted": len(entities),
                "offset": doc.get("Offset", [0, 0, 0])}
    return voxels, metadata


def state_parts(state):
    name, _, properties = state.partition("[")
    return name, dict(item.split("=") for item in properties.rstrip("]").split(",") if item)


def support_block(state):
    """Conservative full-floor check for spawn anchors, not a registry substitute."""
    name, _ = state_parts(state)
    block = name.removeprefix("minecraft:")
    return (block in {"grass_block", "dirt", "coarse_dirt", "rooted_dirt", "podzol", "stone", "cobblestone",
                      "mossy_cobblestone", "andesite", "polished_andesite", "granite", "diorite", "calcite",
                      "tuff", "deepslate", "cobbled_deepslate", "moss_block", "bricks", "packed_mud", "mud_bricks"}
            or block.endswith(("_planks", "_log", "_wood", "_bricks", "_tiles")))


def validate_module(module):
    width, height, depth = module["size"]
    states = {(x, y, z): module["palette"][p] for x, y, z, p in module["blocks"]}

    def safe_anchor(point, label):
        require(isinstance(point, list) and len(point) == 3 and all(type(n) is int for n in point), f"Invalid {label}")
        x, y, z = point
        require(0 <= x < width and 1 <= y < height - 1 and 0 <= z < depth, f"{label} outside module/head clearance")
        require((x, y, z) not in states and (x, y + 1, z) not in states, f"{label} needs two air blocks")
        require(support_block(states.get((x, y - 1, z), "minecraft:air")), f"{label} has no full floor support")

    for point in module["villagers"]:
        safe_anchor(point, module["id"] + " villager")
    if module["id"] == "player_home":
        safe_anchor(module.get("home"), "Player home")
        bed = module.get("bed")
        require(isinstance(bed, list) and len(bed) == 3 and all(type(n) is int for n in bed), "Missing player bed anchor")
        name, props = state_parts(states.get(tuple(bed), "minecraft:air"))
        require(name == "minecraft:red_bed" and props.get("part") == "foot", "Bed anchor must be red bed foot")
        dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[props["facing"]]
        head = (bed[0] + dx, bed[1], bed[2] + dz)
        head_name, head_props = state_parts(states.get(head, "minecraft:air"))
        require(head_name == name and head_props == {**props, "part": "head"}, "Red bed must have matching head/foot")
        for x, y, z in (tuple(bed), head):
            require(support_block(states.get((x, y - 1, z), "minecraft:air")), "Red bed needs full floor support")
            require((x, y + 1, z) not in states and (x, y + 2, z) not in states, "Red bed needs overhead clearance")
        require(sum(state_parts(s)[0] == "minecraft:red_bed" for s in states.values()) == 2, "Player room needs one red bed")
        require(sum(state_parts(s)[0] == "minecraft:chest" for s in states.values()) == 2, "Player room needs exactly two empty chests")
        require(any(state_parts(s)[0] == "minecraft:crafting_table" for s in states.values()), "Player room needs crafting table")
    entry = module["entry"]
    require(isinstance(entry, list) and len(entry) == 3 and all(type(n) is int for n in entry)
            and 0 <= entry[0] < width and entry[1:] == [0, -1], f"{module['id']}: entry must be [localX,0,-1]")


def convert(schematic, layout_path, base_path):
    layout = json.loads(layout_path.read_text(encoding="utf-8-sig"))
    require(layout.get("schema") == 1, "Layout schema must be 1")
    require(Path(layout.get("schematic", "")).name == schematic.name, "Layout references a different schematic")
    specs = layout.get("modules", [])
    require(len(specs) == len(MAIN_IDS) and {s.get("id") for s in specs} == MAIN_IDS,
            "Layout must contain exactly the six main module IDs")
    voxels, metadata = read_schematic(schematic)
    width, _, depth = metadata["size"]
    bounds = []
    for spec in specs:
        rect = spec.get("bounds")
        require(isinstance(rect, list) and len(rect) == 4 and all(type(n) is int for n in rect), "Invalid module bounds")
        x, z, w, d = rect
        require(x >= 0 and z >= 0 and w > 0 and d > 0 and x + w <= width and z + d <= depth, "Module bounds outside schematic")
        for ox, oz, ow, od in bounds:
            require(x + w <= ox or ox + ow <= x or z + d <= oz or oz + od <= z, "Module bounds overlap")
        bounds.append(rect)
    outside = [point for point in voxels if not any(x0 <= point[0] < x0 + w and z0 <= point[2] < z0 + d
                                                   for x0, z0, w, d in bounds)]
    require(not outside, f"{len(outside)} non-air voxels outside module bounds, first coordinates: {outside[:8]}; "
                         "split roads into runtime connectors instead of silently dropping blocks")
    source = {"kind": "sponge_schematic", "schematic": relative(schematic), "layout": relative(layout_path),
              "schematic_sha256": hashlib.sha256(schematic.read_bytes()).hexdigest(),
              "layout_sha256": hashlib.sha256(layout_path.read_bytes()).hexdigest(), **metadata}
    imported = []
    for spec in specs:
        x0, z0, w, d = spec["bounds"]
        local = {(x - x0, y, z - z0): state for (x, y, z), state in voxels.items()
                 if x0 <= x < x0 + w and z0 <= z < z0 + d}
        require(local, f"Empty module: {spec['id']}")
        height = max(point[1] for point in local) + 1
        # Runtime clears to size height + 3; expose enough vertical room for anchors.
        height = max(height, 4)
        palette = sorted(set(local.values()))
        ids = {state: index for index, state in enumerate(palette)}
        module = {"id": spec["id"], "size": [w, height, d], "entry": spec.get("entry"),
                  "desired": spec.get("desired"), "villagers": spec.get("villagers", []),
                  "palette": palette, "blocks": [[x, y, z, ids[state]] for (x, y, z), state in
                                                    sorted(local.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))]}
        for key in ("home", "bed"):
            if key in spec:
                module[key] = spec[key]
        require(isinstance(module["desired"], list) and len(module["desired"]) == 2
                and all(type(n) is int for n in module["desired"]), "desired must be [dx,dz]")
        validate_module(module)
        imported.append(module)
    base = json.loads(base_path.read_text(encoding="utf-8-sig"))
    require(base.get("schema") == 1, "Existing modules schema must be 1")
    fallback = [module for module in base["modules"] if module["id"] in FALLBACK_IDS]
    require(len(fallback) == 3 and {module["id"] for module in fallback} == FALLBACK_IDS, "Missing compact fallback modules")
    result = {"schema": 1, "source": source, "modules": imported + fallback}
    warnings = []
    if metadata["data_version"] != 3955:
        warnings.append(f"Source DataVersion is {metadata['data_version']}; target Minecraft 1.21.1 is 3955. Runtime registry validation required.")
    report = {"schema": 1, "source": source, "warnings": warnings,
              "modules": [{"id": m["id"], "size": m["size"], "non_air_blocks": len(m["blocks"]),
                           "palette_states": len(m["palette"]), "villagers": len(m["villagers"])} for m in imported],
              "preserved_compact_modules": [m["id"] for m in fallback],
              "validation": "offline NBT, complete voxel coverage, anchors, bed pair, empty containers; game test not run"}
    return result, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("schematic", type=repo_path)
    parser.add_argument("layout", type=repo_path)
    parser.add_argument("--base", type=repo_path, default=RESOURCE, help="Existing JSON containing compact fallback modules")
    parser.add_argument("--output", type=repo_path, default=RESOURCE)
    parser.add_argument("--report", type=repo_path)
    args = parser.parse_args()
    try:
        require(args.output not in {args.schematic, args.layout}, "Output must not overwrite source inputs")
        require(args.report is None or args.report not in {args.schematic, args.layout, args.base, args.output}, "Report path overlaps an input/output")
        result, report = convert(args.schematic, args.layout, args.base)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"output": relative(args.output), "imported_modules": len(MAIN_IDS),
                          "preserved_fallbacks": 3, "warnings": report["warnings"]}, ensure_ascii=False))
    except (ValueError, KeyError, TypeError, OSError, struct.error) as exc:
        parser.exit(1, f"Import rejected: {exc}\n")


if __name__ == "__main__":
    main()
