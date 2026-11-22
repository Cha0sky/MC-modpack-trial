"""Lay out the armor handbook and accessory recipes as always-readable FTB chapters."""
from collections import defaultdict
from pathlib import Path
import json
import math

import networkx as nx

from build_heritage_quests import QUESTS, write
from campaign_navigation import ACCESSORY_GROUP, ARMOR_GROUP, BOOK_GROUPS, quest_id, turn_page
from equipment_sources import EquipmentSources

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "docs/data/equipment"
PREFIX = "equipment_"
SLOTS = {"head": "头部", "chest": "胸部", "legs": "腿部", "feet": "足部", "body": "坐骑与随从"}
MODS = {
    "terra_curio": "泰拉饰品", "confluence": "汇流来世", "terra_entity": "泰拉生物", "goety": "诡厄巫法",
    "goetydelight": "诡厄乐事", "goety_cataclysm": "诡厄灾变", "goetyiron": "诡厄铁魔法",
    "irons_spellbooks": "铁魔法", "l2hostility": "莱特兰恶意", "l2complements": "莱特兰扩充",
    "cataclysm": "灾变", "touhou_little_maid": "车万女仆", "touhou_little_maid_spell": "万法皆通",
    "tarotcards": "塔罗牌", "beyonddimensions": "超越维度", "mowziesmobs": "Mowzie 的生物",
    "minecraft": "原版", "create": "机械动力", "avaritia": "无尽贪婪", "pandora": "潘多拉",
}
TIERS = ((0, "0"), (2, "1–2"), (4, "3–4"), (6, "5–6"), (8, "7–8"),
         (12, "9–12"), (math.inf, "13 及以上"))


def atlas_id(key):
    return quest_id("equipment_atlas", key)


def item_id(kind, item):
    return atlas_id(kind + "/" + item)


def entry_link(label, kind, item):
    return turn_page(label, item_id(kind, item))


def tier_of(points):
    if not isinstance(points, (int, float)): return "可变"
    return next(label for maximum, label in TIERS if points <= maximum)


def chunks(values, size):
    return [values[i:i + size] for i in range(0, len(values), size)]


class Chapter:
    def __init__(self, key, title, group, order, icon):
        self.key = key
        self.title = title
        self.id = atlas_id("chapter/" + key)
        self.doc = {"id": self.id, "filename": PREFIX + key, "group": group, "order_index": order,
                    "icon": {"id": icon}, "progression_mode": "flexible", "disable_toast": True, "quests": []}
        self.lang = {f"chapter.{self.id}.title": title}

    def page(self, qid, name, icon, x, y, lines, *, subtitle="", parents=()):
        self.doc["quests"].append({"id": qid, "icon": {"id": icon}, "x": x, "y": y, "min_width": 345,
            "optional": True, "disable_toast": True, "tasks": [], "rewards": [], "dependencies": list(parents),
            "progression_mode": "flexible", "hide_until_deps_complete": False, "hide_until_deps_visible": False,
            "hide_details_until_startable": False, "hide_lock_icon": True, "hide_dependency_lines": False})
        self.lang[f"quest.{qid}.title"] = name
        self.lang[f"quest.{qid}.quest_desc"] = lines
        if subtitle: self.lang[f"quest.{qid}.quest_subtitle"] = subtitle


def obtaining(item, sources, recipes, known=()):
    lines = list(dict.fromkeys(row[0] for row in recipes))
    lines.extend([known] if isinstance(known, str) else known)
    lines.extend(sources.other_sources(item))
    if not lines:
        if item in sources.tag_items("confluence_magic_lib:wip"):
            lines.append("本版列为开发中物品，尚未查到生存获取途径。")
        else:
            lines.append("当前资料未确认具体获取途径。")
    return lines


def add_armor(records, sources):
    chapters = []
    tiers = defaultdict(list)
    for row in records: tiers[tier_of(row["armor"])].append(row)
    for order, title in enumerate([v for _, v in TIERS] + ["可变"]):
        rows = tiers[title]
        if not rows: continue
        rows.sort(key=lambda v: (v["armor"] or 0, v["id"].split(":")[0], v["name"], v["id"]))
        book = Chapter("armor_" + str(order), "护甲值 " + title, ARMOR_GROUP, order, rows[0]["id"])
        chapters.append(book)
        groups = defaultdict(list)
        for row in rows: groups[row["slot"]].append(row)
        slots = [s for s in SLOTS if s in groups] + sorted(groups.keys() - SLOTS.keys())
        book.page(atlas_id("contents/" + book.key), "从哪一栏找", rows[0]["id"], 0, -3,
                  ["按单件提供的护甲值排册。套装、附魔和饰品加成可以在穿戴后改变面板。",
                   *[turn_page(f"{SLOTS.get(s, s)} · {len(groups[s])} 件", atlas_id(book.key + "/" + s)) for s in slots]])
        for col, slot in enumerate(slots):
            items = groups[slot]
            label = SLOTS.get(slot, slot)
            x = col * 8
            head = atlas_id(book.key + "/" + slot)
            book.page(head, label, items[0]["id"], x, 0,
                      [entry_link(v["name"], "armor", v["id"]) for v in items])
            for i, v in enumerate(items):
                item = v["id"]
                pts = f'{v["armor"]:g} 点护甲' if isinstance(v["armor"], (int, float)) else "护甲值随状态变化"
                effects = v.get("effect") or v.get("effects", [])
                if isinstance(effects, str): effects = [effects]
                lines = [pts + " · " + label, *effects, *obtaining(item, sources, sources.recipe_info(item), v.get("source", [])),
                         turn_page("查看本档" + label, head), item]
                book.page(item_id("armor", item), v["name"], item, x + i % 4 * 1.6, 2 + i // 4 * 1.6, lines,
                          subtitle=pts + " · " + label)
    return chapters


def branch_plans(graph):
    """For a large family show full root-to-product paths, including shared ingredients."""
    plans = []
    for component in sorted(nx.weakly_connected_components(graph), key=lambda c: (-len(c), min(c))):
        if len(component) == 1: continue
        sub = graph.subgraph(component)
        if len(component) <= 42:
            plans.append(set(component))
            continue
        condensed = nx.condensation(sub)
        paths = []
        for node in condensed:
            if condensed.out_degree(node) == 0:
                route = set()
                for n in nx.ancestors(condensed, node) | {node}: route.update(condensed.nodes[n]["members"])
                paths.append(route)
        groups = []
        for route in sorted(paths, key=lambda c: (-len(c), sorted(c))):
            candidates = [part for part in groups if part & route and len(part | route) <= 34]
            if candidates:
                best = max(candidates, key=lambda part: (len(part & route) / len(route), -len(part)))
                best.update(route)
            else: groups.append(route)
        plans.extend(groups)
    return plans


def layout(sub):
    condensation = nx.condensation(sub)
    ranks = {}
    for n in nx.topological_sort(condensation):
        ranks[n] = 1 + max((ranks[p] for p in condensation.predecessors(n)), default=-1)
    depth = {item: ranks[c] for item, c in condensation.graph["mapping"].items()}
    layers = defaultdict(list)
    for item in sorted(depth): layers[depth[item]].append(item)
    vertical = {}
    for level in sorted(layers):
        def center(item):
            parents = [vertical[p] for p in sub.predecessors(item) if p in vertical]
            return sum(parents) / len(parents) if parents else 0
        items = sorted(layers[level], key=lambda i: (center(i), i))
        for i, item in enumerate(items): vertical[item] = (i - (len(items) - 1) / 2) * 1.6
    return depth, vertical


def add_accessories(records, sources):
    items = {v["id"]: v for v in records}
    recipes = {k: sources.recipe_info(k) for k in items}
    graph = nx.DiGraph()
    graph.add_nodes_from(sorted(items))
    for dest in sorted(items):
        for _, inputs, kind in recipes[dest]:
            if kind == "item_transmutation": continue  # A reversible shimmer conversion is listed as an alternative source.
            graph.add_edges_from((parent, dest) for parent in sorted(inputs & items.keys()) if parent != dest)
    groups = branch_plans(graph)
    large = [g for g in groups if len(g) >= 6]
    small = defaultdict(list)
    for g in groups:
        if len(g) < 6:
            sub = graph.subgraph(g)
            end = min(g, key=lambda k: (-len(nx.ancestors(sub, k)), sub.out_degree(k), k))
            small[end.split(":")[0]].append(g)
    plans = [(g, [g], "") for g in large]
    minor = []
    for ns, families in sorted(small.items()):
        if sum(map(len, families)) < 7:
            minor.extend(families)
            continue
        for batch in chunks(families, 8):
            plans.append((set.union(*batch), batch, MODS.get(ns, ns) + " · 小型合成路线"))
    for batch in chunks(minor, 8):
        plans.append((set.union(*batch), batch, "其他小型合成路线"))
    singles = defaultdict(list)
    for k in sorted(items):
        if not graph.degree(k): singles[k.split(":")[0]].append(k)
    other_singles = []
    for ns, alone in sorted(singles.items()):
        if len(alone) < 8:
            other_singles.extend(alone)
            continue
        for batch in chunks(alone, 48):
            plans.append((set(batch), [], MODS.get(ns, ns) + " · 单件饰品"))
    for batch in chunks(other_singles, 48): plans.append((set(batch), [], "其他独立饰品"))
    owner, books, repeats = {}, [], defaultdict(int)
    for order, (members, families, label) in enumerate(plans):
        sub = graph.subgraph(members)
        tip = min(members, key=lambda k: (-len(nx.ancestors(sub, k)), sub.out_degree(k), k))
        key = "route_" + tip.replace(":", "_")
        if key in (b.key for b in books):
            key += "_" + atlas_id("|".join(sorted(members)))[:7].lower()
        title = label or (items[tip]["name"] + " · 合成路线")
        repeats[title] += 1
        if repeats[title] > 1: title += f"（{repeats[title]}）"
        book = Chapter(key, title, ACCESSORY_GROUP, order, tip)
        books.append(book)
        for item in sorted(members): owner.setdefault(item, key)
        positions = {}
        if families:
            bottom = 0
            for family in families:
                depth, vert = layout(graph.subgraph(family))
                low = min(vert.values())
                for item in family: positions[item] = (depth[item] * 3, bottom + vert[item] - low, depth[item])
                bottom += max(vert.values()) - low + 3.8
        else:
            ordered = sorted(members, key=lambda k: (items[k]["slots"], items[k]["name"], k))
            for i, item in enumerate(ordered): positions[item] = (i % 6 * 1.7, i // 6 * 1.7, 0)
        def local_id(k):
            return item_id("accessory", k) if owner[k] == key else atlas_id(key + "/ref/" + k)
        book.page(atlas_id("contents/" + key), title, tip, 0, -3,
                  (["从左往右看。连线表示前一件可以参加后一件的配方；有替代材料时，打开成品看每一种做法。"]
                   if families else ["这些饰品的配方没有用到本册其他饰品，或可从探索、交易等途径直接取得。"])
                  + [turn_page(items[k]["name"], local_id(k)) for k in sorted(members, key=lambda k: (positions[k], k))])
        for item in sorted(members):
            v = items[item]
            x, y, rank = positions[item]
            parents = sorted(graph.predecessors(item))
            forward = sorted(graph.successors(item))
            effects = v.get("effects", [])
            slots = "、".join(v["slots"])
            lines = ["佩戴位置 · " + slots, *effects,
                     *obtaining(item, sources, recipes[item], v.get("source", []))]
            lines += [entry_link("配方中的饰品 · " + items[k]["name"], "accessory", k) for k in parents]
            lines += [entry_link("可合成 · " + items[k]["name"], "accessory", k) for k in forward]
            lines += [turn_page("返回本册合成图" if families else "返回本册目录", book.id), item]
            deps = [local_id(k) for k in parents if k in members and positions[k][2] < rank]
            book.page(local_id(item), v["name"], item, x, y, list(dict.fromkeys(lines)), subtitle=slots, parents=deps)
    return books, graph


def build(guide_id):
    armor = json.loads((DATA / "armor.json").read_bytes())
    accessories = json.loads((DATA / "accessories.json").read_bytes())
    sources = EquipmentSources()
    armor_books = add_armor(armor, sources)
    routes, graph = add_accessories(accessories, sources)
    dual = {r["id"] for r in armor} & {r["id"] for r in accessories}
    for collection, other, label in ((armor_books, "accessory", "查看饰品槽与合成路线"),
                                      (routes, "armor", "查看护甲位与护甲值")):
        for book in collection:
            for node in book.doc["quests"]:
                if (item := node["icon"]["id"]) in dual:
                    desc = book.lang["quest." + node["id"] + ".quest_desc"]
                    if desc[-1] == item: desc.insert(-1, entry_link(label, other, item))
    armor_index = Chapter("armor_index", "按护甲值查盔甲", ARMOR_GROUP, -1, "minecraft:diamond_chestplate")
    route_index = Chapter("routes_index", "从材料到成品", ACCESSORY_GROUP, -1, "terra_curio:lightning_boots")
    back = turn_page("返回随身装备手册", guide_id("index"))
    armor_index.page(atlas_id("armor/index"), armor_index.title, "minecraft:iron_chestplate", 0, 0,
                     ["选一档护甲值，再按头、胸、腿、足或动物装备查找。",
                      *[turn_page(b.title, b.id) for b in armor_books], back])
    shelves = defaultdict(list)
    for b in routes:
        if "单件" in b.title or "独立" in b.title: shelf = "独立饰品"
        elif "小型" in b.title: shelf = "小型合成路线"
        else: shelf = "多阶合成路线"
        shelves[shelf].append(b)
    route_index.page(atlas_id("routes/index"), route_index.title, "terra_curio:lightning_boots", 0, 0,
                     ["想做一件成品，沿路线向左找材料。点开任一件，也能向右查它可以合成什么。",
                      *[turn_page(name, atlas_id("routes/shelf/" + name)) for name in shelves], back])
    for pos, (name, part) in enumerate(shelves.items()):
        route_index.page(atlas_id("routes/shelf/" + name), name, part[0].doc["icon"]["id"], pos * 2, 2,
                         [turn_page(b.title, b.id) for b in part])
    lang = {f"chapter_group.{k}.title": v for k, v in BOOK_GROUPS}
    books = [armor_index, route_index, *armor_books, *routes]
    chapters = QUESTS / "chapters"
    expected = {b.doc["filename"] for b in books}
    old_ids = set()
    for path in chapters.glob(PREFIX + "*.snbt"):
        old = json.loads(path.read_bytes())
        if old["id"] != atlas_id("chapter/" + path.stem.removeprefix(PREFIX)):
            raise ValueError(f"Unrecognized equipment chapter: {path}")
        old_ids.add(old["id"])
        old_ids.update(v["id"] for v in old["quests"])
        if path.stem not in expected: path.unlink()
    for book in books:
        write(QUESTS / ("chapters/" + book.doc["filename"] + ".snbt"), book.doc)
        lang.update(book.lang)
    write(QUESTS / "chapter_groups.snbt", {"chapter_groups": [{"id": k} for k, _ in BOOK_GROUPS]})
    for locale in ("zh_cn", "en_us"):
        path = QUESTS / f"lang/{locale}.snbt"
        current = json.loads(path.read_bytes())
        for key in list(current):
            bits = key.split(".")
            if len(bits) == 3 and bits[1] in old_ids and key not in lang: del current[key]
        current.update(lang)
        write(path, current)
    print(f"{len(armor)} armor pieces, {len(accessories)} accessories, {graph.number_of_edges()} recipe connections, "
          f"{len(armor_books)} armor tiers, {len(routes)} accessory books")
