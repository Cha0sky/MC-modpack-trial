"""Author the two expeditions following the first hardmode loadout."""

from build_field_equipment_quests import uid as equipment_id
from build_heritage_quests import DATA, QUESTS, merge, uid as stable_id, write

FILENAME = "05_relic_expeditions"
PREFIX = "world_progression_core:relic_expeditions/"


def uid(name):
    return stable_id(name, "lostworld.relic_expeditions.v1/")


def player_record(fields):
    return {"trigger": "minecraft:location", "conditions": {"player": {
        "type_specific": {"type": "minecraft:player", **fields}}}}


def boss_advancement(name, entity, native_advancements=()):
    # The runtime awards 'done' for a confirmed death, including nearby co-op players.
    # Native kill statistics and Goety's own completion records also work for old saves.
    criteria = {
        "done": {"trigger": "minecraft:impossible"},
        "earlier_kill": player_record({"stats": [
            {"type": "minecraft:killed", "stat": entity, "value": {"min": 1}}]}),
    }
    for key in native_advancements:
        criteria[key.split("/")[-1]] = player_record({"advancements": {key: True}})
    write(DATA / f"advancement/relic_expeditions/{name}.json", {
        "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": entity.split(":")[0]}],
        "criteria": criteria, "requirements": [list(criteria)]})


def main():
    chapter = {"id": uid("chapter"), "filename": FILENAME, "order_index": 4,
               "icon": {"id": "minecraft:filled_map"}, "progression_mode": "linear", "quests": []}
    lang = {f"chapter.{chapter['id']}.title": "第五章 · 遗址远征"}

    def add(name, title, lines, icon, position, dependencies, rewards, *, optional=False, any_one=False):
        qid = uid(name)
        if name == "provisions":
            task = {"type": "item", "item": {"id": "world_progression_core:sealed_ration", "count": 1},
                    "count": 2, "consume_items": False}
        else:
            task = {"type": "advancement", "advancement": PREFIX + name}
        node = {"id": qid, "x": position[0], "y": position[1], "min_width": 330,
                "icon": {"id": icon}, "optional": optional, "progression_mode": "linear",
                "dependencies": dependencies, "dependency_requirement": "all_completed",
                "tasks": [{"id": uid(name + "/task"), **task}],
                "rewards": [{"id": uid(name + "/reward/" + item), "type": "item",
                             "item": {"id": item, "count": 1}, "count": count} for item, count in rewards]}
        if any_one:
            node["min_required_dependencies"] = 1
        chapter["quests"].append(node)
        lang[f"quest.{qid}.title"] = title
        lang[f"quest.{qid}.quest_desc"] = lines

    add("provisions", "工坊配给 · 整装出发", [
        "完成第四章的实地检验后，从工坊带上两份热封行军餐。热样胶、铜板和厨锅料理在压机下的工作盆里装成一份餐。",
        "领取空地图和照明，选择地下墓穴或林地府邸。只勘察一个地点并把地图带回家，也能完成本章。"],
        "world_progression_core:sealed_ration", (0, 0), [equipment_id("trial")],
        [("minecraft:map", 4), ("minecraft:soul_torch", 48)])
    add("catacombs", "墓穴线 · 找到地下墓穴", [
        "铁魔法的归途指南针可以寻找地下墓穴，也可用探险者指南针搜索 irons_spellbooks:catacombs。",
        "进入结构范围稍作停留，世界档案会记下此类遗迹的首个坐标。可以先撤出制图，日后再进深处挑战。"],
        "irons_spellbooks:wayward_compass", (2.5, -2), [uid("provisions")],
        [("minecraft:ladder", 48), ("minecraft:golden_carrot", 24)], optional=True)
    add("mansion", "府邸线 · 查明林地据点", [
        "用林地探险家地图，或用探险者指南针搜索 minecraft:mansion，进入原版林地府邸的建筑范围并稍作停留。",
        "府邸地点会写入档案，空手调查即可。要邀战灾厄宰相，再准备诡厄的黑暗卷轴。"],
        "minecraft:dark_oak_log", (2.5, 2), [uid("provisions")],
        [("minecraft:scaffolding", 64), ("minecraft:cooked_beef", 32)], optional=True)
    add("chart", "交回远征地图", [
        "任意一条勘察线完成后，返回遗产档案台。空手潜行右键选中地下墓穴或林地府邸，再主手持空地图、松开潜行右键制图。",
        "一张空地图换一张带有目标标记与坐标的地图。这份地图可以交给队友，也可以留给下一次讨伐使用。"],
        "minecraft:filled_map", (5, 0), [uid("catacombs"), uid("mansion")],
        [("minecraft:compass", 2), ("minecraft:iron_block", 8), ("minecraft:experience_bottle", 24)], any_one=True)
    add("dead_king", "自选讨伐 · 死者之王", [
        "回到地下墓穴深处，与沉睡的遗骸互动可唤醒死者之王。先找到退路，再决定是否开战。",
        "击败现身的死者之王本体。原生掉落照常取得。曾有玩家击杀统计的旧战果同样可以登记。"],
        "irons_spellbooks:necronomicon_spell_book", (5, -3), [uid("catacombs")],
        [("irons_spellbooks:epic_ink", 1), ("irons_spellbooks:arcane_essence", 32),
         ("minecraft:experience_bottle", 32)], optional=True)
    add("vizier", "自选讨伐 · 灾厄宰相", [
        "在工作台上竖排放黑色染料、魔法布料和不死图腾，制作诡厄的黑暗卷轴。在林地府邸内长按使用卷轴召唤宰相。",
        "留意剑击、尖刺与怒鬼。击败宰相本体即可完成，分身不计。诡厄原生的亲自击败或仆从击败成就都可接续。"],
        "goety:dark_scroll", (5, 3), [uid("mansion")],
        [("goety:magic_fabric", 8), ("minecraft:diamond", 4), ("minecraft:golden_apple", 6)], optional=True)

    for name in ("catacombs", "mansion", "chart"):
        write(DATA / f"advancement/relic_expeditions/{name}.json", {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    boss_advancement("dead_king", "irons_spellbooks:dead_king")
    boss_advancement("vizier", "goety:vizier", ("goety:goety/kill_a_vizier", "goety:goety/servant_kill_vizier"))
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("en_us", "zh_cn"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
