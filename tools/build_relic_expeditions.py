"""Author the two expeditions following the first hardmode loadout."""

from build_field_equipment_quests import uid as equipment_id
from build_heritage_quests import DATA, QUESTS, merge, uid as stable_id, write
from campaign_navigation import finish_chapter

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

    def add(name, title, lines, icon, position, dependencies, rewards, *, optional=False, any_one=False,
            subtitle=None, advancement=None):
        qid = uid(name)
        if name == "provisions":
            task = {"type": "item", "item": {"id": "world_progression_core:sealed_ration", "count": 1},
                    "count": 2, "consume_items": False}
        else:
            task = {"type": "advancement", "advancement": advancement or PREFIX + name}
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
        if subtitle:
            lang[f"quest.{qid}.quest_subtitle"] = subtitle

    add("provisions", "工坊配给 · 整装出发", [
        "带上 2 份热封行军餐来领图纸、笔和照明。地下墓穴或林地府邸选一处走；这趟的要紧事，是让后来的人也能找到入口。",
        "一处就可交差：写手记、拍照片或挑战当地首领。喜欢找路也有第四种办法：踏勘两处，到档案室展图。"],
        "world_progression_core:sealed_ration", (0, 0), [equipment_id("trial")],
        [("minecraft:map", 4), ("minecraft:soul_torch", 48), ("minecraft:feather", 1), ("minecraft:paper", 4)],
        subtitle="单地取证，或双地踏勘")
    add("catacombs", "墓穴线 · 找到地下墓穴", [
        "用归途指南针找铁魔法地下墓穴，或用探险者指南针搜 irons_spellbooks:catacombs。进门稍作停留，档案台便会记下坐标。",
        "先踩出一条回地面的路。在入口写手记或拍照就可回程，想探内室再去找死者之王。"],
        "irons_spellbooks:wayward_compass", (2.5, -2), [uid("provisions")],
        [("minecraft:ladder", 48), ("minecraft:golden_carrot", 24)], optional=True)
    add("mansion", "府邸线 · 查明林地据点", [
        "循林地探险家地图出发，或用探险者指南针搜 minecraft:mansion。进入府邸稍作停留，档案台会记住首个地点。",
        "把靠近入口的房间记在手记里，或趁光线好留一帧。带了黑暗卷轴的队伍还可在这里挑战灾厄宰相。"],
        "minecraft:dark_oak_log", (2.5, 2), [uid("provisions")],
        [("minecraft:scaffolding", 64), ("minecraft:cooked_beef", 32)], optional=True)
    add("dead_king", "自选讨伐 · 死者之王", [
        "先留好回程路线，再深入墓穴与沉睡的遗骸互动。击败现身的死者之王，这份战报可以用来归档，第六章驻地也会认领。"],
        "irons_spellbooks:necronomicon_spell_book", (5, -3), [uid("catacombs")],
        [("irons_spellbooks:epic_ink", 1), ("irons_spellbooks:arcane_essence", 32),
         ("minecraft:experience_bottle", 32)], optional=True, subtitle="墓穴讨伐 · 战果可代替勘察凭据")
    add("vizier", "自选讨伐 · 灾厄宰相", [
        "把黑色染料、魔法布料、不死图腾从上到下竖排，制成黑暗卷轴。在府邸内长按使用，迎战灾厄宰相。本人或仆从留下的击败记录都能交差。"],
        "goety:dark_scroll", (5, 3), [uid("mansion")],
        [("goety:magic_fabric", 8), ("minecraft:diamond", 4), ("minecraft:golden_apple", 6)],
        optional=True, subtitle="府邸讨伐 · 战果可代替勘察凭据")

    add("servant_bounty", "府邸悬赏 · 仆从收尾", [
        "把第二章的奥术实践带到府邸。这回由你牵制宰相，留给自己的诡厄仆从完成最后一击。",
        "取得仆从击败宰相的成就可交这份额外战报，领回一个不死图腾和经验瓶。"],
        "minecraft:totem_of_undying", (8, 4), [uid("vizier"), stable_id("arcane_practice")],
        [("minecraft:totem_of_undying", 1), ("minecraft:experience_bottle", 32)], optional=True,
        subtitle="奥术自选 · 仆从击败宰相本体", advancement="goety:goety/servant_kill_vizier")
    add("notebook", "测绘员 · 写一页现场手记", [
        "主手拿羽毛，副手放纸。走进地下墓穴或林地府邸，潜行右键遗址中的方块，留下一张带实地坐标的手记。"],
        "minecraft:writable_book", (5, -1), [uid("catacombs"), uid("mansion")],
        [("minecraft:paper", 24), ("minecraft:glow_ink_sac", 8)], optional=True, any_one=True)
    add("field_photo", "摄影师 · 遗址里的第一帧", [
        "带装好胶片的拾光定影相机，站在地下墓穴或林地府邸内部按下快门。带一帧照片回来，也算调查凭据。"],
        "exposure:camera", (5, 1), [uid("catacombs"), uid("mansion")],
        [("minecraft:glow_item_frame", 4), ("minecraft:amethyst_shard", 16)], optional=True, any_one=True)
    add("map_exhibit", "双地踏勘 · 给后来者挂一张图", [
        "两处遗址都走一遍。先在档案台绘一张归档图，把它挂进展示框；第二章已挂过图的也算。若展出的正是这趟绘的遗址图，一张图就够交差。",
        "领回磁石和指南针。归档图标的是遗址，下次给补给站埋一块磁石，针就可以专指你选的站口。"],
        "minecraft:cartography_table", (5, 5), [uid("catacombs"), uid("mansion")],
        [("minecraft:lodestone", 1), ("minecraft:compass", 2), ("minecraft:glow_item_frame", 4)], optional=True,
        subtitle="联合委托 · 调查两个遗址，沿用档案室的地图展出记录",
        advancement="world_progression_core:heritage/map_wall")
    add("chart", "交回远征地图", [
        "回档案台，空手潜行右键选中已发现的墓穴或府邸。松开潜行，手持空地图右键，换一张标好坐标的归档图。",
        "手记、照片、当地战报或双地踏勘，交任一份。收起归档图，下一趟要给远征队安一只补给桶。"],
        "minecraft:filled_map", (8, 0),
        [uid("notebook"), uid("field_photo"), uid("dead_king"), uid("vizier"), uid("map_exhibit")],
        [("minecraft:compass", 2), ("minecraft:iron_block", 8), ("minecraft:experience_bottle", 24)],
        any_one=True, subtitle="取到一种凭据后，把归档图带回前线")
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
    finish_chapter(chapter, lang)
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("en_us", "zh_cn"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
