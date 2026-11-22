"""Index the installed 1.21.1 recipe, loot, shop and item-tag resources for the field guide."""
from collections import Counter, defaultdict
from functools import lru_cache
from io import BytesIO
import json
from pathlib import Path
import re
import tomllib
from zipfile import ZipFile

from deploy_workshop import INSTANCE, QUESTS


STATIONS = {
    "cursed_shaped": "工作台", "shapeless_no_remain": "工作台",
    "spellstone_table": "术石台", "amulet_shapeless": "工作台升级护符",
    "fluid_transformation": "流体转化",
    "crafting_shaped": "工作台", "crafting_shapeless": "工作台", "smithing_transform": "锻造台",
    "workshop": "工匠作坊", "hardmode_anvil": "困难模式砧", "heavy_work_bench": "重型工作台",
    "loom": "织布机", "sawmill": "锯木机", "sky_mill": "天磨", "ritual": "黑暗祭坛",
    "altar_recipe_serializers": "女仆祭坛", "extreme_shaped": "终极工作台", "extreme_shapeless": "终极工作台",
    "weapon_fusion": "机械融合砧", "mechanical_crafting": "动力合成器", "smelting": "熔炉",
    "item_transmutation": "微光转化", "stonecutting": "切石机", "crafting_color": "工作台染色",
    "cursed_infuser_recipes": "诅咒注入器", "burnt": "燃烧转化", "diffusion": "扩散",
    "mixing": "机械搅拌器", "pressing": "机械压机", "brazier": "火盆", "sewing": "缝纫",
    "sequenced_assembly": "机械动力序列装配", "hellforge": "地狱熔炉", "extra_step_stool": "脚凳升级",
}

# RecipeManagerMixin in Confluence 1.2.4 removes these standalone Terra Curio
# recipe IDs and all terra_entity recipe IDs before the recipes are decoded.
TC_REMOVED = set("""blizzard_in_a_bottle life_form_analyzer obsidian_skull workshop flurry_boots
ice_skates rocket_boots stopwatch avenger_emblem architect_gizmo_pack anklet_of_the_wind ankh_charm
magic_mirror_from_gold_ingot magic_mirror_from_platinum_ingot""".split())
LOOT_NAMES = {
    "simple_dungeon": "地牢", "abandoned_mineshaft": "废弃矿井", "end_city_treasure": "末地城",
    "ancient_city": "远古城市", "nether_bridge": "下界要塞", "woodland_mansion": "林地府邸",
    "buried_treasure": "埋藏的宝藏", "shipwreck_treasure": "沉船宝藏", "desert_pyramid": "沙漠神殿",
    "cave_chests": "洞穴宝箱", "underground_chests": "地下宝箱", "surface_chests": "地表宝箱",
    "ivy_chests": "常春藤箱", "living_ivy_chests": "生命常春藤箱", "ice_chests": "冰冻箱",
    "skyware_chests": "天域箱", "water_chests": "水中箱", "living_wood_chests": "生命木箱",
}


def jar_layers(jar):
    for file in jar.namelist():
        if file.startswith("META-INF/jarjar/") and file.endswith(".jar"):
            with ZipFile(BytesIO(jar.read(file))) as child:
                yield from jar_layers(child)
    yield jar


def read_json(jar, name):
    raw = jar.read(name).decode("utf-8-sig")
    try:
        return json.loads(raw, strict=False)
    except ValueError:
        # Some distributed resources use Gson's line comments.
        return json.loads(re.sub(r'(?m)^\s*//[^\n]*', '', raw), strict=False)


@lru_cache(maxsize=8)
def mod_files(paths):
    found = {}
    for path in paths:
        with ZipFile(path) as jar:
            name = "META-INF/neoforge.mods.toml"
            if name not in jar.namelist(): continue
            for mod in tomllib.loads(jar.read(name).decode("utf-8-sig")).get("mods", []):
                found[mod["modId"]] = path
    return found


def rid(value):
    return value if ":" in value else "minecraft:" + value


class EquipmentSources:
    def __init__(self, instance=INSTANCE, extra_mods=None):
        from equipment_expansions import mod_jars
        self.jars = mod_jars(instance, extra_mods)
        self.instance = instance
        self.lang = {}
        self.fallback = {}
        self.mods = {"minecraft", "neoforge"}
        self.tags = defaultdict(set)
        self.recipes = {}
        self.loot = {}
        self.modifiers = {}
        self.enabled_loot_modifiers = set()
        self.shops = {}
        self.outputs = defaultdict(list)
        self.finds = defaultdict(set)
        self.buy = defaultdict(set)
        self.quests = defaultdict(set)
        vanilla = instance / (instance.name + ".jar")
        for file in [vanilla, *self.jars]:
            with ZipFile(file) as base:
                for layer in jar_layers(base):
                    self._read_layer(layer)
        version = json.loads(vanilla.with_suffix(".json").read_bytes())
        assets = instance.parents[1] / "assets"
        index = assets / "indexes" / (version["assetIndex"]["id"] + ".json")
        key = json.loads(index.read_bytes())["objects"]["minecraft/lang/zh_cn.json"]["hash"]
        self.lang.update(json.loads((assets / "objects" / key[:2] / key).read_bytes()))
        self._index()
        self._quest_rewards()

    def _quest_rewards(self):
        lang = json.loads((QUESTS / "lang/zh_cn.snbt").read_bytes())
        for file in (QUESTS / "chapters").glob("*.snbt"):
            if file.stem.startswith("equipment_"): continue
            for quest in json.loads(file.read_bytes())["quests"]:
                label = lang.get("quest." + quest["id"] + ".title", "")
                for reward in quest.get("rewards", []):
                    if reward.get("type") == "item" and isinstance(reward.get("item"), dict):
                        self.quests[reward["item"]["id"]].add("任务奖励 · " + label)
                    if reward.get("type") == "choice":
                        for part in reward.get("table_data", {}).get("rewards", []):
                            if isinstance(part.get("item"), dict):
                                self.quests[part["item"]["id"]].add("任务自选奖励 · " + label)

    def _read_layer(self, jar):
        keys = jar.namelist()
        if "META-INF/neoforge.mods.toml" in keys:
            meta = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode("utf-8-sig"))
            self.mods.update(m["modId"] for m in meta.get("mods", []))
        for n in keys:
            if not n.endswith(".json"): continue
            if re.fullmatch(r"assets/[^/]+/lang/(zh_cn|en_us)\.json", n):
                dest = self.lang if n.endswith("zh_cn.json") else self.fallback
                dest.update(read_json(jar, n))
                continue
            match = re.fullmatch(r"data/([^/]+)/(recipe|loot_table|loot_modifiers|npc/shop|tags/item)/(.+)\.json", n)
            if not match: continue
            ns, folder, name = match.groups()
            key = ns + ":" + name
            value = read_json(jar, n)
            if folder == "tags/item":
                if value.get("replace"): self.tags[key].clear()
                self.tags[key].update(x if isinstance(x, str) else x["id"] for x in value.get("values", []))
            elif folder == "recipe": self.recipes[key] = value
            elif folder == "loot_table": self.loot[key] = value
            elif folder == "loot_modifiers":
                if key == "neoforge:global_loot_modifiers":
                    if value.get("replace"): self.enabled_loot_modifiers.clear()
                    self.enabled_loot_modifiers.update(value.get("entries", []))
                else: self.modifiers[key] = value
            else: self.shops[key] = value

    def condition(self, c):
        kind = c.get("type")
        if kind == "neoforge:mod_loaded": return c.get("modid") in self.mods
        if kind == "neoforge:false": return False
        if kind == "neoforge:not": return not self.condition(c["value"])
        if kind == "neoforge:and": return all(map(self.condition, c["values"]))
        if kind == "neoforge:or": return any(map(self.condition, c["values"]))
        if kind == "l2core:bool_config":
            path = self.instance / "config" / c["path"]
            if path.is_file():
                value = self.config(path)
                for key in c["line"]: value = value[key]
                return value == c["expected"]
        return True

    @staticmethod
    @lru_cache(maxsize=None)
    def config(path):
        return tomllib.loads(path.read_text("utf-8-sig"))

    def add_pack_labels(self, rows):
        for row in rows:
            self.lang["item." + row["id"].replace(":", ".")] = row["name"]
        for tag, name in {
            "c:strings": "线", "c:glass_blocks/colorless": "无色玻璃方块", "c:bones": "骨头",
            "c:hearts": "任意颜色的微型心", "c:wither_bones": "凋零骨", "c:leathers": "皮革",
            "c:rods/wooden": "木棒", "c:nether_stars": "下界之星", "c:gems/prismarine": "海晶碎片",
            "c:gems/amethyst": "紫水晶碎片", "c:gems/diamond": "钻石", "c:gems/emerald": "绿宝石",
            "c:ingots/iron": "铁锭", "c:storage_blocks/gold": "金块", "c:storage_blocks/netherite": "下界合金块",
            "c:seeds": "任意种子", "minecraft:wool": "任意羊毛", "minecraft:leaves": "任意树叶",
            "enigmaticlegacyplus:enigmatic_amulets": "任意已显现的护身符",
        }.items():
            self.lang["tag.item." + tag.replace(":", ".").replace("/", ".")] = name

    def name(self, key):
        if key.startswith("#"):
            tag = key[1:]
            tr = self.lang.get("tag.item." + tag.replace(":", ".").replace("/", "."))
            if tr: return tr
            values = sorted(self.tag_items(tag))
            if len(values) == 1: return self.name(values[0])
            if 1 < len(values) <= 4: return "（" + " 或 ".join(self.name(v) for v in values) + "）"
            return "#" + tag
        key = rid(key)
        if key == "minecraft:netherite_upgrade_smithing_template": return "下界合金升级锻造模板"
        tail = key.replace(":", ".")
        for catalog in (self.lang, self.fallback):
            for prefix in ("item.", "block.", "entity."):
                if prefix + tail in catalog: return catalog[prefix + tail]
        return key

    @lru_cache(maxsize=None)
    def tag_items(self, key):
        found, seen, todo = set(), set(), [key]
        while todo:
            tag = todo.pop()
            if tag in seen: continue
            seen.add(tag)
            for member in self.tags.get(tag, ()):
                if member.startswith("#"): todo.append(rid(member[1:]))
                else: found.add(rid(member))
        return found

    @staticmethod
    def ingredients(raw):
        # A row is (alternative IDs, number required). Shaped patterns repeat
        # a slot per symbol, while NeoForge amount ingredients count per slot.
        if isinstance(raw, str): return (raw,), 1
        if isinstance(raw, list):
            opts = [EquipmentSources.ingredients(x) for x in raw]
            return tuple(dict.fromkeys(i for items, _ in opts for i in items)), (opts[0][1] if opts else 1)
        if not isinstance(raw, dict): return (), 1
        count = raw.get("count", 1)
        if "ingredient" in raw:
            opts, base = EquipmentSources.ingredients(raw["ingredient"])
            return opts, count * base
        if "items" in raw:
            return EquipmentSources.ingredients(raw["items"])
        item = raw.get("item", raw.get("id"))
        if isinstance(item, dict): return EquipmentSources.ingredients(item)
        if "tag" in raw: return ("#" + raw["tag"],), count
        return ((item,) if isinstance(item, str) else ()), count

    def recipe_inputs(self, d):
        result = Counter()
        def add(raw, multiplier=1):
            if isinstance(raw, dict) and raw.get("type") == "touhou_little_maid_spell:optional_mod_item" and raw.get("modid") not in self.mods:
                return  # AltarRecipeSerializerMixin filters empty optional ingredients.
            opts, count = self.ingredients(raw)
            if opts: result[opts] += count * multiplier
        if "pattern" in d and "key" in d:
            for symbol, count in Counter("".join(d["pattern"]).replace(" ", "")).items(): add(d["key"][symbol], count)
        else:
            for key in ("template", "base", "addition", "source", "activation_item", "ingredient", "input"):
                if key in d: add(d[key])
            for inp in d.get("ingredients", []): add(inp)
            if d["type"] == "celestial_core:fluid_transformation":
                for inp in d.get("inputs", []): add(inp)
        if d["type"] == "enigmaticlegacyplus:spellstone_table":
            # The menu requires and consumes one core outside the seven recipe slots.
            add("enigmaticlegacyplus:spellcore")
            if d.get("debris_count"): add("enigmaticlegacyplus:spellstone_debris", d["debris_count"])
        return result

    def _index(self):
        for recipe, d in self.recipes.items():
            ns, slug = recipe.split(":", 1)
            if "confluence" in self.mods and (ns == "terra_entity" or ns == "terra_curio" and slug in TC_REMOVED): continue
            if not all(map(self.condition, d.get("neoforge:conditions", []))): continue
            result = d.get("result", d.get("results", d.get("target", [])))
            if d.get("type") == "celestial_core:fluid_transformation": result = d.get("output", [])
            for stack in result if isinstance(result, list) else [result]:
                output = stack if isinstance(stack, str) else stack.get("id", stack.get("item")) if isinstance(stack, dict) else None
                if isinstance(output, str):
                    self.outputs[rid(output)].append((recipe, d, stack.get("count", 1) if isinstance(stack, dict) else 1))
        direct, refs = defaultdict(set), defaultdict(set)
        def entries(nodes, items, tables):
            for e in nodes:
                if e.get("type") in ("item", "minecraft:item") and e.get("name"): items.add(rid(e["name"]))
                if e.get("type") in ("loot_table", "minecraft:loot_table"):
                    ref = e.get("value", e.get("name"))
                    if isinstance(ref, str): tables.add(rid(ref))
                entries(e.get("children", []), items, tables)
        for key, d in self.loot.items():
            for pool in d.get("pools", []): entries(pool.get("entries", []), direct[key], refs[key])
        def targets(c):
            if c.get("condition") == "neoforge:loot_table_id": yield c["loot_table_id"]
            for sub in c.get("terms", []): yield from targets(sub)
        for modifier, d in self.modifiers.items():
            if modifier not in self.enabled_loot_modifiers: continue
            if "confluence" in self.mods and modifier.startswith("terra_curio:"): continue
            if not all(map(self.condition, d.get("neoforge:conditions", []))): continue
            if d.get("type") == "l2hostility:trait_scaled" and "id" in d.get("result", {}):
                rules = d.get("conditions", [])
                trait = next((c for c in rules if c.get("condition") == "l2hostility:trait_and_level"), {})
                charm = next((c.get("item") for c in rules if c.get("condition") == "l2hostility:player_has_item"), None)
                if trait and charm:
                    title = self.lang.get("trait." + trait["trait"].replace(":", "."), trait["trait"])
                    self.quests[d["result"]["id"]].add(
                        f"特殊掉落 · 持有{self.name(charm)}，亲手击败具有{title} {trait['minLevel']}–{trait['maxLevel']} 级词条的怪物。")
            for parent in (k for c in d.get("conditions", []) for k in targets(c)):
                if d.get("type") == "neoforge:add_table": refs[parent].add(d["table"])
                if d.get("type") == "irons_spellbooks:append_loot": refs[parent].add(d["key"])
                if d.get("type", "").startswith("tarotcards:") and self.config(
                        self.instance / "config/tarotcards/tarotcards.toml")["loot"]["do_loot_generation"]:
                    direct[parent].update(d["items"])
        # Follow sub-tables from named loot sources so a generic internal pool
        # can still be traced back to an actual chest or mob.
        for key in self.loot.keys() | direct.keys() | refs.keys():
            if "confluence" in self.mods and key.startswith("terra_entity:entities/"): continue
            path = key.split(":")[1]
            if not path.startswith(("chests/", "entities/", "blocks/", "gameplay/", "treasure_bag/", "archaeology/", "advancement/")): continue
            if path.startswith("chests/additional_"): continue
            if key.startswith("enigmaticlegacyplus:chests/") and path.endswith("_addon"): continue
            if key.startswith("celestial_artifacts:chests/"): continue  # Injected tables, resolved to actual locations by the mod reader.
            todo, visited = [key], set()
            while todo:
                sub = todo.pop()
                if sub in visited: continue
                visited.add(sub)
                for item in direct[sub]:
                    self.finds[item].add(key)
                todo.extend(refs[sub] - visited)
        for npc, d in self.shops.items():
            for trade in d.get("trades", []):
                task = trade.get("trade_task", {})
                for tier, rewards in task.get("result_pool", {}).items():
                    for stack in rewards:
                        if "id" in stack: self.quests[stack["id"]].add(f"完成渔夫任务 · 累计 {tier} 次")
                stock = trade.get("result", trade.get("deferred_result"))
                if isinstance(stock, dict): stock = stock.get("id")
                if isinstance(stock, str): self.buy[rid(stock)].add(npc)

    def recipe_info(self, item):
        routes = []
        for key, d, count in self.outputs.get(item, []):
            parts = self.recipe_inputs(d)
            if not parts: continue
            ty = d["type"].split(":")[-1]
            station = STATIONS.get(ty, d["type"])
            extra = []
            if d.get("craftType"): extra.append("仪式 " + d["craftType"])
            if d.get("research"): extra.append("需解锁研究 " + d["research"])
            for raw in [*d.get("ingredients", []), *d.get("key", {}).values()]:
                if isinstance(raw, dict) and (potion := raw.get("components", {}).get("minecraft:potion_contents", {}).get("potion")):
                    key_potion = "item.minecraft.potion.effect." + potion.split(":")[-1]
                    extra.append("药水须用" + self.lang.get(key_potion, potion))
            if ty == "cursed_shaped": extra.append("若成品属于诅咒或祝福物品，能被浸染的材料须先浸染")
            if ty == "spellstone_table": extra.append("术质核心放入专槽，成品取出时消耗")
            if ty == "fluid_transformation": extra.append("投入流体：" + self.name(d["fluid"]))
            if d.get("power"): extra.append("P 点 " + str(d["power"]))
            if count > 1: extra.append(f"产出 {count} 件")
            if ty == "sequenced_assembly":
                steps = []
                for step in d["sequence"]:
                    name = step["type"].split(":")[-1]
                    tools = {"deploying": "机械手", "pressing": "压机", "filling": "注液器", "cutting": "动力锯"}
                    supply = [self.name(i["item"]) if "item" in i else self.name("#" + i["tag"]) if "tag" in i
                              else str(i.get("amount", "")) + " mB " + i.get("fluid", "") for i in step.get("ingredients", [])[1:]]
                    steps.append(tools.get(name, name) + ("装入" + "、".join(supply) if supply else ""))
                extra.append(" → ".join(steps) + f"，循环 {d.get('loops', 1)} 轮")
                outputs = d["results"]
                if len(outputs) > 1:
                    weight = sum(o.get("chance", 1) for o in outputs if o.get("id") == item)
                    extra.append(f"产出概率 {100 * weight / sum(o.get('chance', 1) for o in outputs):.2g}%")
            details = []
            for options, amount in parts.items():
                s = " 或 ".join(self.name(i) for i in options)
                if d.get("all_different") and options == ("#enigmaticlegacyplus:materials_of_the_cube",):
                    choices = sorted(self.tag_items(options[0][1:]))
                    details.append(f"从 {len(choices)} 种术石中任选 {amount} 种，各 1 个")
                    continue
                details.append((f"（{s}）" if len(options) > 1 else s) + (f" ×{amount}" if amount != 1 else ""))
            line = station + " · " + " ＋ ".join(details) + ("（" + "，".join(extra) + "）" if extra else "")
            ingredients = set()
            for options in parts:
                for opt in options:
                    if opt.startswith("#"): ingredients.update(self.tag_items(opt[1:]))
                    else: ingredients.add(rid(opt))
            routes.append((line, ingredients, ty))
        return routes

    def loot_name(self, key, expansion=False):
        ns, path = key.split(":", 1)
        category, _, suffix = path.partition("/")
        slug = suffix.split("/")[-1]
        if category == "entities":
            entity_ns = suffix.split("/")[0] if "/" in suffix else ns
            tr = self.name(entity_ns + ":" + slug)
            return "击败 · " + tr
        if category == "treasure_bag":
            boss, _, mode = suffix.partition("/")
            tier = "大师" if mode.startswith("master") else "专家" if mode.startswith("expert") else mode
            return "打开" + self.name("terra_entity:" + boss) + "的宝藏袋（" + tier + "）"
        label = LOOT_NAMES.get(slug)
        if expansion and not label:
            label = {
                "ancient_city_ice_box": "远古城市冰窖", "bastion_bridge": "堡垒遗迹桥梁", "bastion_other": "堡垒遗迹杂物箱",
                "igloo_chest": "雪屋", "jungle_temple": "丛林神庙", "jungle_temple_dispenser": "丛林神庙发射器",
                "ruined_portal": "废弃传送门", "stronghold_corridor": "要塞走廊", "stronghold_crossing": "要塞十字路口",
                "underwater_ruin_big": "大型海底废墟", "village_armorer": "村庄盔甲匠铺",
                "stronghold_library": "要塞图书馆", "shipwreck_supply": "沉船补给箱", "shipwreck_map": "沉船地图箱",
                "bastion_treasure": "堡垒遗迹藏宝室", "bastion_hoglin_stable": "堡垒遗迹疣猪兽棚",
            }.get(slug)
            if key.startswith("enigmaticlegacyplus:chests/spellstone_hut/"): label = "术石小屋宝箱"
        if not label:
            variant = slug.removesuffix("_chests") + "_chest" if slug.endswith("_chests") else slug
            translated = self.name(ns + ":" + variant)
            label = translated if ":" not in translated else key
        if category == "chests": return "搜寻宝箱 · " + label
        if category == "blocks": return "采集 · " + label
        if "crate/" in suffix or "bag/" in suffix: return "开匣 · " + label
        if "fishing" in suffix: return "钓鱼 · " + label
        return "战利品 · " + label

    def other_sources(self, item):
        ways = sorted(self.quests[item])
        if item == "minecraft:elytra": ways.append("在末地城寻找末地船，取下船舱物品展示框中的鞘翅。")
        if item == "confluence:boredoms_pact_falling_resolve":
            ways.append("巨石彩蛋世界会把这件饰品装入玩家的第一个泰拉饰品槽。")
        for npc in sorted(self.buy[item]):
            name = npc.split(":")[1]
            ways.append("购买 · " + self.name("terra_entity:" + name))
        kinds = defaultdict(list)
        expansion = item.split(":")[0] in {"enigmaticlegacyplus", "bhc", "celestial_artifacts", "celestial_core", "relics"}
        for label in dict.fromkeys(self.loot_name(t, expansion) for t in sorted(self.finds[item])):
            kind, sep, value = label.partition(" · ")
            if sep: kinds[kind].append(value)
            else: ways.append(label)
        for kind, labels in kinds.items():
            for i in range(0, len(labels), 4): ways.append(kind + " · " + "、".join(labels[i:i + 4]))
        return ways
