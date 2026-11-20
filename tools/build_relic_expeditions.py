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
        "装上 2 份热封行军餐，领取空地图和照明。选地下墓穴或林地府邸出发，带一份现场记录回来。"],
        "world_progression_core:sealed_ration", (0, 0), [equipment_id("trial")],
        [("minecraft:map", 4), ("minecraft:soul_torch", 48)])
    add("catacombs", "墓穴线 · 找到地下墓穴", [
        "用归途指南针找铁魔法地下墓穴，或搜 irons_spellbooks:catacombs。走进结构稍作停留，档案台会记下坐标。"],
        "irons_spellbooks:wayward_compass", (2.5, -2), [uid("provisions")],
        [("minecraft:ladder", 48), ("minecraft:golden_carrot", 24)], optional=True)
    add("mansion", "府邸线 · 查明林地据点", [
        "跟随林地探险家地图，或用指南针搜 minecraft:mansion。进入府邸稍作停留，记下第一个发现地点。"],
        "minecraft:dark_oak_log", (2.5, 2), [uid("provisions")],
        [("minecraft:scaffolding", 64), ("minecraft:cooked_beef", 32)], optional=True)
    add("dead_king", "自选讨伐 · 死者之王", [
        "深入墓穴，与沉睡的遗骸互动，唤醒并击败死者之王。先记住来路，再进内室。"],
        "irons_spellbooks:necronomicon_spell_book", (5, -3), [uid("catacombs")],
        [("irons_spellbooks:epic_ink", 1), ("irons_spellbooks:arcane_essence", 32),
         ("minecraft:experience_bottle", 32)], optional=True)
    add("vizier", "自选讨伐 · 灾厄宰相", [
        "把黑色染料、魔法布料、不死图腾从上到下竖排，做成黑暗卷轴。在府邸内长按使用，击败现身的灾厄宰相。"],
        "goety:dark_scroll", (5, 3), [uid("mansion")],
        [("goety:magic_fabric", 8), ("minecraft:diamond", 4), ("minecraft:golden_apple", 6)], optional=True)

    add("notebook", "测绘员 · 写一页现场手记", [
        "主手拿羽毛，副手放纸。走进地下墓穴或林地府邸，潜行右键遗址中的方块，留下一张带实地坐标的手记。"],
        "minecraft:writable_book", (5, -1), [uid("catacombs"), uid("mansion")],
        [("minecraft:paper", 24), ("minecraft:glow_ink_sac", 8)], optional=True, any_one=True)
    add("field_photo", "摄影师 · 遗址里的第一帧", [
        "带装好胶片的拾光定影相机，站在地下墓穴或林地府邸内部按下快门。带一帧照片回来，也算调查凭据。"],
        "exposure:camera", (5, 1), [uid("catacombs"), uid("mansion")],
        [("minecraft:glow_item_frame", 4), ("minecraft:amethyst_shard", 16)], optional=True, any_one=True)
    add("chart", "交回远征地图", [
        "现场手记、遗址照片或当地首领战果，带回任意一种。回档案台，空手潜行右键选中墓穴或府邸。松开潜行，用空地图右键绘出返程图，下一章就靠它选址。"],
        "minecraft:filled_map", (8, 0), [uid("notebook"), uid("field_photo"), uid("dead_king"), uid("vizier")],
        [("minecraft:compass", 2), ("minecraft:iron_block", 8), ("minecraft:experience_bottle", 24)], any_one=True)
    ruins = "world_progression_core:survey_ruins"
    write(DATA / "tags/worldgen/structure/survey_ruins.json", {"replace": False,
          "values": ["minecraft:mansion", "irons_spellbooks:catacombs"]})
    write(DATA / "advancement/relic_expeditions/field_photo.json", {"criteria": {"shutter": {
        "trigger": "exposure:frame_exposed", "conditions": {"player": {"location": {"structures": "#" + ruins}}}
    }}})
    for name in ("catacombs", "mansion", "chart", "notebook"):
        write(DATA / f"advancement/relic_expeditions/{name}.json", {"criteria": {"done": {"trigger": "minecraft:impossible"}}})
    boss_advancement("dead_king", "irons_spellbooks:dead_king")
    boss_advancement("vizier", "goety:vizier", ("goety:goety/kill_a_vizier", "goety:goety/servant_kill_vizier"))
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("en_us", "zh_cn"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
