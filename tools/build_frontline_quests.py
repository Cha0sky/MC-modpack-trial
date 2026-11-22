"""Build the field depot quest chapter and its stock, use, and photography records."""

from build_heritage_quests import DATA, MOD, QUESTS, merge, uid as stable_id, write
from build_relic_expeditions import player_record, uid as expedition_id
from build_world_turn_quests import uid as world_turn_id
from campaign_navigation import finish_chapter

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

    def add(name, title, position, icon, deps, tasks, lines, rewards, *, mainline=False, minimum=0,
            other_chapters=(), subtitle=None):
        qid = uid(name)
        dependencies = [expedition_id("chart")] if name == "dispatch" else [uid(key) for key in deps]
        node = {"id": qid, "x": position[0], "y": position[1], "icon": {"id": icon}, "min_width": 350,
                "dependencies": [*dependencies, *other_chapters], "dependency_requirement": "all_completed", "optional": not mainline,
                "tasks": [{"id": uid(name + "/task/" + task.get("advancement", task["type"])), **task}
                          for task in tasks],
                "rewards": [reward(name, key, amount) for key, amount in rewards]}
        if minimum:
            node["min_required_dependencies"] = minimum
        chapter["quests"].append(node)
        lang[f"quest.{qid}.title"] = title
        lang[f"quest.{qid}.quest_desc"] = lines
        if subtitle:
            lang[f"quest.{qid}.quest_subtitle"] = subtitle
        return node

    add("dispatch", "地图的另一端", (0, 0), "minecraft:compass", (), [item("minecraft:compass")], [
        "归档图上多了一个你亲自走过的地方。下一班人可没走过这趟。拿普通指南针领木桶和营火，沿图再走一次。",
        "营地缺三样：能吃的、能救急的、能接下一箱货的。挑两项办妥，在遗址旁拆一包补给就能交班。"],
        [("minecraft:barrel", 2), ("minecraft:campfire", 2), ("minecraft:spruce_log", 48)], mainline=True,
        subtitle="沿归档图重访遗址，行粮、救援、货运任选两项")
    add("station", "把木桶留在遗址旁", (2, 0), "minecraft:campfire", ("dispatch",), [adv(RECORD + "station")], [
        "靠近归档标记找一块平地，水平半径 128 格内都可设站。墓穴深处难扎营，就到它正上方的地表。",
        "在木桶任意一侧同高紧贴点燃营火，主手持普通指南针，潜行右键木桶盘点。看到库存数字，站址就定了。"],
        [("minecraft:lantern", 12), ("minecraft:stone_bricks", 64)], mainline=True)
    add("trail", "厨房排班 · 装一包行粮", (2, -3), TRAIL, ("dispatch",), [adv(RECORD + "trail_prepared")], [
        "用第三章的生产线接第一单。压机下的工作盆放 2 份热封行军餐、2 煤炭、2 木棍、1 纸，冷压一包。备齐两包就可送到营地。"],
        [("minecraft:paper", 32), ("minecraft:copper_ingot", 24)])
    add("rescue", "救援排班 · 扎紧吊绳", (2, 3), RESCUE, ("dispatch",), [adv(RECORD + "rescue_prepared")], [
        "墓穴里要能止住伤势，也要有办法爬上来。在压机下用工作盆把 1 金苹果、6 竹子、1 线、1 纸压成一包。备齐两包交给营地。"],
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
        "三项业务有两项运转，补给站就能换班。带一包行粮或救援物资，到归档遗址水平 128 格内，对空气右键亲手拆包。",
        "锅还可以接着煮，桶里的东西也留给下一趟。收下交班配给，再选一本适合自己的附魔书。"],
        [("minecraft:iron_block", 8), ("minecraft:experience_bottle", 48)], mainline=True, minimum=2,
        subtitle="三单择二 · 回到前线拆包交班")
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
        "回到墓穴挑战死者之王，或在府邸邀战灾厄宰相。第五章已带回一份战报，驻地就可以直接结算这笔赏金。"],
        [("minecraft:diamond", 8), ("minecraft:golden_apple", 8)], subtitle="自选赏金 · 沿用第五章首领战果")
    add("locker", "前后方联勤 · 随身库房", (2, -6), "minecraft:ender_chest", ("station",),
        [{"type": "stat", "stat": "minecraft:open_enderchest", "value": 2}], [
        "第三章后队的两只末影箱派上用场了。一只放工坊，一只放营地，把自己常换的工具存进去，两头都能取。",
        "累计打开末影箱两次即可交差。营地木桶照常用来盘点全队的货。"],
        [("minecraft:obsidian", 32), ("minecraft:ender_eye", 4)], other_chapters=(world_turn_id("rearguard"),),
        subtitle="下界后队委托的后续 · 查两次个人库存")
    add("return_bearing", "测绘联勤 · 标出站口", (2, -8), "minecraft:lodestone", ("station",),
        [adv("minecraft:nether/use_lodestone")], [
        "把双地踏勘领到的磁石安在站口，手持普通指南针对它右键。留一枚普通指南针盘货，把定过向的那枚带出门。",
        "在同一维度，磁石指针会带你回到埋石的位置。已经学会校准磁石指南针的测绘员可以沿用原有进度。"],
        [("minecraft:map", 16), ("minecraft:lantern", 16)], other_chapters=(expedition_id("map_exhibit"),),
        subtitle="双地踏勘的后续 · 给返程留一枚磁石指针")
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
    finish_chapter(chapter, lang)
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
