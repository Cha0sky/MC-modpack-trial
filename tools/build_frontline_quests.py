"""Build the field depot quest chapter and its stock, use, and photography records."""

from build_heritage_quests import DATA, MOD, QUESTS, merge, uid as stable_id, write
from build_relic_expeditions import player_record, uid as expedition_id

FILENAME = "06_frontline_depot"
RECORD = MOD + "frontline/"
TRAIL = MOD + "trail_supply_crate"
RESCUE = MOD + "rescue_supply_crate"


def uid(key):
    return stable_id(key, "lostworld.frontline.v1/")


def records():
    done = {"trigger": "minecraft:impossible"}
    entries = {key: {"done": done} for key in ("station", "trail_delivery", "rescue_delivery", "mixed_reserve")}
    opened = {}
    for lane, crate in (("trail", TRAIL), ("rescue", RESCUE)):
        entries[lane + "_prepared"] = {"done": done, "carried": {
            "trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": crate}]}}}
        opened[lane] = player_record({"stats": [
            {"type": "minecraft:used", "stat": crate, "value": {"min": 1}}]})
    entries["opened"] = opened
    entries["field_use"] = {"done": done}
    entries["night_watch"] = {mob: {
        "trigger": "exposure:frame_exposed",
        "conditions": {"frame": {"day_time": {"min": 13000, "max": 23000},
                                  "entities_in_frame": [{"type": "minecraft:" + mob}]}}}
        for mob in ("zombie", "skeleton", "pillager")}
    for name, criteria in entries.items():
        write(DATA / f"advancement/frontline/{name}.json", {"criteria": criteria, "requirements": [list(criteria)]})


def item(item_id, count=1):
    return {"type": "item", "item": {"id": item_id, "count": 1}, "count": count, "consume_items": False}


def adv(record):
    return {"type": "advancement", "advancement": record}


def reward(name, item_id, count):
    return {"id": uid(name + "/reward/" + item_id), "type": "item", "item": {"id": item_id, "count": 1}, "count": count}


def field_manual():
    choices = []
    for enchantment, level in (("feather_falling", 4), ("silk_touch", 1), ("looting", 3)):
        book = reward("handover/" + enchantment, "minecraft:enchanted_book", 1)
        book["item"]["components"] = {"minecraft:stored_enchantments": {"levels": {"minecraft:" + enchantment: level}}}
        choices.append({**book, "weight": 1.0})
    return {"id": uid("handover/manual"), "type": "choice", "table_id": 0,
            "table_data": {"loot_size": 1, "rewards": choices}}


def main():
    records()
    chapter = {"id": uid("chapter"), "filename": FILENAME, "order_index": 5, "icon": {"id": "minecraft:barrel"},
               "progression_mode": "linear", "quests": []}
    lang = {f"chapter.{chapter['id']}.title": "第六章 · 前线补给站"}

    def add(name, title, position, icon, deps, tasks, lines, rewards, *, mainline=False, minimum=0):
        qid = uid(name)
        dependencies = [expedition_id("chart")] if name == "dispatch" else [uid(key) for key in deps]
        node = {"id": qid, "x": position[0], "y": position[1], "icon": {"id": icon}, "min_width": 350,
                "dependencies": dependencies, "dependency_requirement": "all_completed", "optional": not mainline,
                "tasks": [{"id": uid(name + "/task/" + task.get("advancement", task["type"])), **task}
                          for task in tasks],
                "rewards": [reward(name, key, amount) for key, amount in rewards]}
        if minimum:
            node["min_required_dependencies"] = minimum
        chapter["quests"].append(node)
        lang[f"quest.{qid}.title"] = title
        lang[f"quest.{qid}.quest_desc"] = lines
        return node

    add("dispatch", "地图的另一端", (0, 0), "minecraft:compass", (), [item("minecraft:compass")], [
        "带普通指南针领木桶和营火，顺第五章的地图去设站。行粮、救援、蛙港货运，任选两项交班。"],
        [("minecraft:barrel", 2), ("minecraft:campfire", 2), ("minecraft:spruce_log", 48)], mainline=True)
    add("station", "把木桶留在遗址旁", (2, 0), "minecraft:campfire", ("dispatch",), [adv(RECORD + "station")], [
        "在归档标记水平半径 128 格内放木桶。任选一个侧面，在同高度紧贴放一堆点燃的营火。主手持普通指南针，潜行右键木桶盘点。"],
        [("minecraft:lantern", 12), ("minecraft:stone_bricks", 64)], mainline=True)
    add("trail", "厨房排班 · 装一包行粮", (2, -3), TRAIL, ("dispatch",), [adv(RECORD + "trail_prepared")], [
        "把 2 份热封行军餐、2 煤炭、2 木棍、1 纸放入压机下的工作盆。冷压出野外补给包；两包够一单。"],
        [("minecraft:paper", 32), ("minecraft:copper_ingot", 24)])
    add("rescue", "救援排班 · 扎紧吊绳", (2, 3), RESCUE, ("dispatch",), [adv(RECORD + "rescue_prepared")], [
        "用压机和工作盆把 1 金苹果、6 竹子、1 线、1 纸包成一份救援物资。两包够一单。"],
        [("minecraft:bamboo", 48), ("minecraft:string", 24), ("minecraft:gold_ingot", 8)])
    add("pack", "货运试车 · 封上第一箱", (2, 6), "create:packager", ("dispatch",), [adv("create:packager")], [
        "把打包机贴住库存，给它红石信号封出第一箱。先用一箱圆石试车，再把正式货物送上运输线。"],
        [("create:andesite_alloy", 24), ("minecraft:chain", 24), ("minecraft:redstone", 32)])
    add("trail_delivery", "行粮到站", (5, -2), TRAIL, ("station", "trail"), [adv(RECORD + "trail_delivery")], [
        "在营地同一只木桶存 2 个野外补给包，用指南针潜行右键盘点。包留在桶里，每包可拆出 2 份行军餐和 8 支火把。"],
        [("farmersdelight:cooking_pot", 1), ("minecraft:golden_carrot", 48)])
    add("rescue_delivery", "救援物资到站", (5, 2), RESCUE, ("station", "rescue"), [adv(RECORD + "rescue_delivery")], [
        "在营地同一只木桶存 2 个救援补给包，用指南针盘点。每包能拆出 1 个金苹果和 6 个脚手架。"],
        [("minecraft:gold_block", 2), ("minecraft:scaffolding", 64)])
    add("freight", "货运通线 · 蛙港签收", (5, 6), "create:package_frogport", ("pack",), [adv("create:frogport")], [
        "用锁链连接两个传动轮并供能。手持蛙港右键锁链，再放到链旁。给发件打包机贴地址牌，收件蛙港填同名地址。",
        "亲自放置收件蛙港，等它从链上取货。手持收到的纸箱潜行右键，取出内层补给包。"],
        [("create:item_vault", 8), ("create:brass_funnel", 4), ("minecraft:chain", 48)])
    end = add("handover", "交班 · 打开第一份储备", (8, 0), "minecraft:written_book",
        ("trail_delivery", "rescue_delivery", "freight"), [adv(RECORD + "opened"), adv(RECORD + "field_use")], [
        "行粮到站、救援到站、蛙港收货，完成任意两项。带一包补给回到归档遗址附近，对空气右键拆开，供下一班队员取用。"],
        [("minecraft:iron_block", 8), ("minecraft:experience_bottle", 48)], mainline=True, minimum=2)
    end["rewards"].append(field_manual())
    add("banquet", "驻地餐桌 · 摆一桌盛宴", (4, -5), "farmersdelight:roast_chicken_block", ("trail",),
        [adv("farmersdelight:main/place_feast")], [
        "做一道能上桌的农夫乐事盛宴，摆出来让同伴分食。烤鸡和酿南瓜都合适。"],
        [("minecraft:emerald", 16), ("minecraft:honey_bottle", 16)])
    add("pharmacy", "药房备货 · 强效治疗", (4, 4), "confluence:greater_healing_potion", ("rescue",),
        [item("confluence:greater_healing_potion", 3)], [
        "在汇流来世炼药桌加入 3 瓶瓶装水、3 妖精尘、1 水晶碎块。带上产出的 3 瓶强效治疗药水。"],
        [("minecraft:gold_ingot", 16), ("minecraft:ender_pearl", 12)])
    add("photo", "夜哨底片", (5, -7), "exposure:camera", ("station",),
        [adv(RECORD + "night_watch"), adv("exposure:adventure/moment_in_time")], [
        "游戏时间 13000 至 23000，用相机拍到僵尸、骷髅或掠夺者。再亲手冲印一张自选照片，挂进值班室。"],
        [("minecraft:glow_item_frame", 8), ("minecraft:glow_ink_sac", 24)])
    add("patrol", "驻地委托 · 收回通行路线", (8, -3), "minecraft:shield", ("station",),
        [adv(RECORD + "patrol")], [
        "死者之王和灾厄宰相任选一位讨伐，带回通行路线。第五章已经赢过的战斗会计入。"],
        [("minecraft:diamond", 8), ("minecraft:golden_apple", 8)])
    add("blade_burst", "守夜人的破围剑", (8, -6), "evolvingblade:star_eater", ("station",),
        [adv(MOD + "prologue_training/burst")], [
        "噬星之刃达到四阶后，主手持剑，潜行右键施放爆发。守夜时留着这招，等敌人聚拢再出手。"],
        [("minecraft:diamond", 4), ("minecraft:golden_carrot", 32)])
    add("reserve", "扩建验收 · 两套常备库存", (8, 4), "create:item_vault",
        ("handover", "trail_delivery", "rescue_delivery", "freight"), [adv(RECORD + "mixed_reserve")], [
        "开通全部三项业务后，把两种补给各 4 包同时存进一只营地木桶，持指南针盘点。"],
        [("create:item_vault", 16), ("create:brass_funnel", 8), ("minecraft:powered_rail", 48)])
    previous_bosses = {boss: player_record({"advancements": {MOD + "relic_expeditions/" + boss: True}})
                       for boss in ("dead_king", "vizier")}
    write(DATA / "advancement/frontline/patrol.json", {"criteria": previous_bosses, "requirements": [list(previous_bosses)]})
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
