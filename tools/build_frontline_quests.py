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
        "上一次从遗址带回了坐标。这一趟要解决的是：再有人进墓穴或府邸，能去哪里补一顿饭、取一架脚手架？",
        "完成第五章「交回远征地图」，带上普通指南针，领取木桶和营火。按归档地图前往任一已登记的遗址。",
        "行粮、救援、货运三项业务做成任意两项即可交班。小队可以分工，个人也能手送两种货物完成全程。"],
        [("minecraft:barrel", 2), ("minecraft:campfire", 2), ("minecraft:spruce_log", 48)], mainline=True)
    add("station", "把木桶留在遗址旁", (2, 0), "minecraft:campfire", ("dispatch",), [adv(RECORD + "station")], [
        "在地下墓穴或林地府邸的归档标记水平 128 格内放木桶。可以选墓穴正上方的地表，也可以靠着府邸外围扎营。",
        "木桶同高度的四个侧面任选一侧，紧贴放一堆点燃的普通或灵魂营火。主手持普通指南针，潜行右键木桶，看到盘点数字就算设站。空桶也能登记。"],
        [("minecraft:lantern", 12), ("minecraft:stone_bricks", 64)], mainline=True)
    add("trail", "厨房排班 · 装一包行粮", (2, -3), TRAIL, ("dispatch",), [adv(RECORD + "trail_prepared")], [
        "每包需要 2 份热封行军餐、2 块煤炭、2 根木棍和 1 张纸。把它们投入工作盆，接动力的机械压机从上方压合，无需加热。",
        "成品可叠 16 包。取一包到背包便可登记；若货物直接上了传送带，在营地盘点成品也算。领完首批纸张，试着一次做够两包。"],
        [("minecraft:paper", 32), ("minecraft:copper_ingot", 24)])
    add("rescue", "救援排班 · 扎紧吊绳", (2, 3), RESCUE, ("dispatch",), [adv(RECORD + "rescue_prepared")], [
        "工作盆装入 1 个金苹果、6 根竹子、1 根线和 1 张纸，用机械压机打成救援补给包。可以从基地带材料，也可以把小压机搬到前线。",
        "包内的金苹果留给伤员，脚手架用于攀出墓穴。取成品到背包，或让营地仓管直接登记到货。"],
        [("minecraft:bamboo", 48), ("minecraft:string", 24), ("minecraft:gold_ingot", 8)])
    add("pack", "货运试车 · 封上第一箱", (2, 6), "create:packager", ("dispatch",), [adv("create:packager")], [
        "打包机贴住有货的库存，以红石信号让它从库存取货并封箱。纸板块、4 铁锭和 2 红石可在工作台制成打包机，具体摆法见配方。",
        "请亲自放置打包机并运行一次。可先寄一箱圆石试车，再考虑把生产线引到站口。"],
        [("create:andesite_alloy", 24), ("minecraft:chain", 24), ("minecraft:redstone", 32)])
    add("trail_delivery", "行粮到站", (5, -2), TRAIL, ("station", "trail"), [adv(RECORD + "trail_delivery")], [
        "把 2 包未拆的野外补给包存进同一个合格的营地木桶，再用普通指南针潜行右键盘点。可手送，也可以让漏斗送货。",
        "木桶留着所有物资。记录结清后，任何人都可取走一包，对空气右键拆出 2 份热封行军餐和 8 支火把。"],
        [("farmersdelight:cooking_pot", 1), ("minecraft:golden_carrot", 48)])
    add("rescue_delivery", "救援物资到站", (5, 2), RESCUE, ("station", "rescue"), [adv(RECORD + "rescue_delivery")], [
        "在同一个合格的营地木桶里同时备好 2 包未拆的救援补给包，主手用普通指南针潜行右键盘点。两个订单可以同桶一次结清。",
        "每包能拆出 1 个金苹果和 6 个脚手架。队友拿走物资后，已经登记的订单依然有效。"],
        [("minecraft:gold_block", 2), ("minecraft:scaffolding", 64)])
    add("freight", "货运通线 · 蛙港签收", (5, 6), "create:package_frogport", ("pack",), [adv("create:frogport")], [
        "用锁链连起两个锁链传动轮，接上动力。手持货物蛙港先右键锁链，再在链旁放下；收件端设置地址，例如「前线」。",
        "给发件打包机贴同名地址的告示牌，把有地址的纸箱送上锁链。亲自放置的收件蛙港成功取货即完成，可在下方接库存。",
        "线路可在基地试车，货物种类自选。运补给时，在收件端手持外层纸箱潜行右键，取出仍封好的补给包放入营地木桶，再用指南针盘点。"],
        [("create:item_vault", 8), ("create:brass_funnel", 4), ("minecraft:chain", 48)])
    end = add("handover", "交班 · 打开第一份储备", (8, 0), "minecraft:written_book",
        ("trail_delivery", "rescue_delivery", "freight"), [adv(RECORD + "opened")], [
        "交出两项记录即可：行粮到站、救援物资到站、蛙港成功收货，任选其二。喜欢小工坊可以手送两单；建设物流的队伍只需再跑一单。",
        "取出任意一种补给包，对空气右键亲手拆一次。此前已经拆过也算。交班后，可从摔落保护 IV、精准采集 I、抢夺 III 中任选一本。"],
        [("minecraft:iron_block", 8), ("minecraft:experience_bottle", 48)], mainline=True, minimum=2)
    end["rewards"].append(field_manual())
    add("banquet", "驻地餐桌 · 摆一桌盛宴", (4, -5), "farmersdelight:roast_chicken_block", ("trail",),
        [adv("farmersdelight:main/place_feast")], [
        "厨房还有一份自选委托。做一份可摆放的农夫乐事盛宴，例如烤鸡或酿南瓜，把它端上桌供大家分食。",
        "营地和家里的餐厅都能开席。已经得到过农夫乐事摆放盛宴成就的厨师可直接交单。"],
        [("minecraft:emerald", 16), ("minecraft:honey_bottle", 16)])
    add("pharmacy", "药房备货 · 强效治疗", (4, 4), "confluence:greater_healing_potion", ("rescue",),
        [item("confluence:greater_healing_potion", 3)], [
        "去困难模式的神圣地寻找妖精尘与水晶碎块。在汇流来世的炼药桌用 3 瓶瓶装水、3 份妖精尘和 1 份水晶碎块调出 3 瓶强效治疗药水。",
        "把这批药带在身上即可登记，留给出队的同伴。"],
        [("minecraft:gold_ingot", 16), ("minecraft:ender_pearl", 12)])
    add("photo", "夜哨底片", (5, -7), "exposure:camera", ("station",),
        [adv(RECORD + "night_watch"), adv("exposure:adventure/moment_in_time")], [
        "在主世界入夜至黎明前（游戏时间 13000 至 23000），用拾光定影把僵尸、骷髅或掠夺者之一拍进画面。可以挑一处有围栏的地方观察。",
        "另外，亲手冲印一张照片；题材自选，已有的冲印成就有效。把你的值夜照片挂在站里，给下一班人看看。"],
        [("minecraft:glow_item_frame", 8), ("minecraft:glow_ink_sac", 24)])
    add("patrol", "驻地委托 · 收回通行路线", (8, -3), "minecraft:shield", ("station",),
        [adv(RECORD + "patrol")], [
        "完成第五章任意一项首领讨伐：深入地下墓穴击败死者之王，或在府邸召唤并击败灾厄宰相。",
        "第五章记过的战果可以直接交单。给自己留足补给，做好准备再战。"],
        [("minecraft:diamond", 8), ("minecraft:golden_apple", 8)])
    add("reserve", "扩建验收 · 两套常备库存", (8, 4), "create:item_vault",
        ("handover", "trail_delivery", "rescue_delivery", "freight"), [adv(RECORD + "mixed_reserve")], [
        "三项业务都开通后，可以接下这笔扩建委托。让同一个营地木桶同时存有至少 4 包行粮和 4 包救援物资，用指南针盘点。",
        "领取保险库和黄铜漏斗，把旧的手运点扩成常设货站。货物可以继续使用。"],
        [("create:item_vault", 16), ("create:brass_funnel", 8), ("minecraft:powered_rail", 48)])
    previous_bosses = {boss: player_record({"advancements": {MOD + "relic_expeditions/" + boss: True}})
                       for boss in ("dead_king", "vizier")}
    write(DATA / "advancement/frontline/patrol.json", {"criteria": previous_bosses, "requirements": [list(previous_bosses)]})
    write(QUESTS / f"chapters/{FILENAME}.snbt", chapter)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", lang)


if __name__ == "__main__":
    main()
