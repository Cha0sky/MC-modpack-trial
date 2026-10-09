"""Generate original Chinese FTB quest content; recommendation, not hard gates."""
from pathlib import Path
import hashlib
import json
from quest_expansion import EXTRA_CHAPTERS

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "staging" / "config" / "ftbquests" / "quests"


def uid(value):
    return f"{int(hashlib.sha256(value.encode()).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF:016X}"


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# key, title, item (None means explicitly manual), amount, practical guidance
CHAPTERS = [
    ("01_home", "安家与启程", "minecraft:campfire", [
        ("welcome", "把远征的起点变成家", None, 1, "这是一条推荐路线：你可以提前探索、获取装备，也可以先留下来建设。主线不会限制你的自由。先打开 JEI 查看配方，读完后手动勾选。基地保护来自实际放置的庇护石，勾选任务不会自动保护住所。"),
        ("table", "第一张工作台", "minecraft:crafting_table", 1, "选址时留出工厂、农田与女仆居所的位置。获得一张工作台；物品检测任务不消耗材料。已经建造的方块可拿回背包完成检测，然后重新放置。"),
        ("shelter", "可以停留的住所", None, 1, "建一个有屋顶、照明和出入口的临时住所，并放置任意颜色的床。建筑形状由你决定，完成后手动勾选。屋顶、床和火把不能替代后续的完整安全区机制。"),
        ("light", "给夜晚一点光", "minecraft:torch", 16, "准备 16 支火把，照亮居所与附近道路。火把只负责普通照明；不能承诺阻挡外来敌人或特殊袭击。留意地下空洞。"),
        ("ward", "从此安心建设", "stargarden_sanctuary:sanctuary_ward", 1, "用四块玻璃、四个铜锭和一支火把合成庇护石，放在基地。默认覆盖水平半径48格内的全部高度，放置启用、拆除解除，多块可重叠扩建。右键查看范围；玩家和伙伴在区内获得伤害保护，敌对生物进入会被移除而不会产生掉落或成长收益。刷怪场、Boss召唤与练习场放在保护区外。物品检测只确认你取得庇护石，仍需亲自放置。"),
        ("chests", "东西各有归处", "minecraft:chest", 2, "准备两个箱子，至少将建筑材料与远征装备分开。给未来的加工线预留输入和输出箱位置。无需为了这个任务整理到完美。"),
        ("furnace", "稳定的第一炉", "minecraft:furnace", 1, "先有稳定的燃料和熔炼位置，再考虑更高产能。制作一个熔炉；原矿、燃料和产物尽量放在附近。"),
        ("farm", "一小片农田", None, 1, "种植一种你容易找到的作物，设置水源与补种空间。不要为了任务寻找指定稀有作物；能连续收获才是目标。完成后手动勾选。"),
        ("bread", "随时能够出发", "minecraft:bread", 8, "准备 8 个面包作为最初的应急补给。后续厨房与女仆可以丰富食谱；现在先避免每次准备远征都重新找食物。"),
        ("shield", "给失误留余地", "minecraft:shield", 1, "准备盾牌，并在普通敌人面前练习防御和退路。并非所有模组攻击都能格挡，遇到陌生技能先观察。"),
        ("growth", "认识自己的成长", None, 1, "打开 RPG 成长界面，阅读生命、攻击、防御和其他属性。选择适合当前玩法的加点方向；不要为了练级放弃建设和探索。此任务只确认你已阅读与操作，手动勾选，不会赠送技能点。"),
        ("blade", "一把与你一起成长的剑", "evolvingblade:star_eater", 1, "噬星之刃有个人成长记录，模式选择会影响成长门槛。先阅读界面说明再确认，当前模组选择通常永久锁定。已有赠剑可直接完成检测，不要求重复合成。"),
        ("trip", "第一次短途远行", None, 1, "带好食物、照明和返程路线，进行一次你愿意承担的短途探索。找一处地形、资源或遗迹，安全返回即可。这里没有自动击杀或探索检测，返回后手动记录；不要求挑战 Boss。"),
        ("gohei", "认识未来的伙伴", "touhou_little_maid:hakurei_gohei", 1, "制作博丽的御币，并通过女仆模组的说明和 JEI 了解祭坛与召唤。不要用危险环境催促养成；先给伙伴准备住所，再安排工作。"),
        ("maid_home", "为女仆留一个位置", None, 1, "准备女仆的活动空间、存放物品的位置和休息区域，了解跟随、工作及待机的切换方式。召唤或招募首位女仆后，先熟悉界面。此阶段以实际安置体验为目标，完成后手动勾选。"),
        ("garden", "第一处喜欢的风景", None, 1, "做一条小路、花坛、观景台或窗边角落。选你喜欢的材料，不按固定模板建造。风景建设也是进度的一部分，完成后手动勾选。"),
        ("kitchen", "从食物到生活", "farmersdelight:cooking_pot", 1, "制作农夫乐事厨锅，按它的加热要求摆放。先学会一种能稳定获得原料的料理；后续再把厨房、农田和女仆工作串起来。"),
    ]),
    ("02_workshop", "转动的生活", "create:water_wheel", [
        ("alloy", "机械动力的起点", "create:andesite_alloy", 8, "准备 8 个安山合金，查看 JEI 中现有配方。它是许多初级动力部件的起点；先做一条小型有效生产线，再扩建厂房。"),
        ("wheel", "让水流转动起来", "create:water_wheel", 1, "制作水车并按机械动力的思索演示布置水流。取得物品即可完成检测；放置后的转动与应力是否正常，需要你实际观察。"),
        ("shaft", "把动力送到需要的地方", "create:shaft", 8, "准备传动杆，规划从水车到机器的路线。短而清晰的线路更容易维护；注意转速、方向和应力。"),
        ("cog", "改变方向与速度", "create:cogwheel", 8, "使用齿轮让动力转向，尝试大小齿轮配合。先查看思索演示，避免只复制结构却不知道为什么转动。"),
        ("press", "第一道加工", "create:mechanical_press", 1, "制作动力冲压机，接入动力。为它准备合适的工作平台与输入材料；不要把取得机器当作完成整条自动化。"),
        ("depot", "给加工一个工作面", "create:depot", 1, "制作置物台，用于初期冲压等加工。布局时留下观察机器工作和取回产物的空间。"),
        ("sheet", "看见加工的结果", "create:iron_sheet", 4, "获得 4 个铁板，体验一次从材料到产物的转换。取得物品会自动检测；若由探索等其他途径得到，也允许完成。推荐你仍亲自尝试冲压流程。"),
        ("goggles", "读懂工厂的状态", "create:engineers_goggles", 1, "工程师护目镜能帮助查看机器信息。把观察转速、应力和吞吐量变成习惯，会比盲目增加机器更省力。"),
        ("stone_core", "把重复采集交给机器", "infinitecore:stone_generator", 1, "制作石头生成器。无限之核负责减轻重复采集；机械动力继续负责加工、物流与工程。请通过 JEI 核对对应模板和当前配方，生成器本身不会在任务完成时自动工作。"),
        ("stone_template", "模板决定产物", "infinitecore:template_stone", 1, "制作或取得石头生成模板，按照无限之核界面说明放入兼容的生成器。模板的实际可选产物和速度以模组运行表现为准；先确认输入模板后确实开始生产。"),
        ("hopper", "产物不必一直手动拿", "minecraft:hopper", 1, "准备漏斗，试着连接生成器或工厂的输出和储物箱。具体输出面与容器支持需实际操作确认；先保证小系统工作再复制扩建。"),
        ("loop", "一条真正有用的小循环", None, 1, "完成一个能持续使用的小系统：稳定原材料来源、加工或整理环节、储物输出。可以包含无限之核，也可以先用其他来源。运行一段时间，检查卡料与满箱问题后手动勾选。"),
        ("partner_work", "伙伴与工厂各有所长", None, 1, "为女仆安排一项适合当前装备和任务能力的工作，并检查收集范围、存放位置与补给。不要预设所有自动化都能由女仆完成；先实现一项清楚、稳定的分工。完成后手动勾选。"),
        ("plan_trip", "下一次远征，由你选择", None, 1, "整理补给与装备，选择下一次探索方向：魔法、汇流世界、遗迹或 Boss 线索都可以。先把想完成的目标写下来；基地发展和冒险可以交替进行，不必等这一章全清才出发。手动勾选仅记录你的计划。"),
    ]),
]


def main():
    languages = {}
    content = []
    seen = set()
    chapters = CHAPTERS + EXTRA_CHAPTERS
    for order, (filename, title, icon, rows) in enumerate(chapters):
        chapter_id = uid("chapter:" + filename)
        languages[f"chapter.{chapter_id}.title"] = title
        quests = []
        for index, (key, name, item, count, description) in enumerate(rows):
            qid, tid, rid = [uid(kind + ":" + filename + ":" + key) for kind in ["quest", "task", "reward"]]
            for identifier in [qid, tid, rid]:
                if identifier in seen:
                    raise RuntimeError("Duplicate ID")
                seen.add(identifier)
            entity = item.split("|", 1)[1] if item and item.startswith("kill|") else None
            task = {"id": tid, "type": "kill" if entity else "item" if item else "checkmark"}
            if entity:
                task.update({"entity": entity, "value": count})
            elif item:
                task.update({"item": {"id": item, "count": 1}, "count": count})
            reward = "minecraft:golden_carrot" if entity else "minecraft:poppy" if filename == "05_homestead" else "minecraft:bread"
            quests.append({"id": qid, "x": (index % 4) * 2.5, "y": (index // 4) * 2.0, "icon": {"id": icon if entity else item or icon}, "tasks": [task], "rewards": [{"id": rid, "type": "item", "item": {"id": reward, "count": 1}, "count": 4 if entity else 2}], "min_width": 300})
            languages[f"quest.{qid}.title"] = name
            languages[f"quest.{qid}.quest_desc"] = [description]
            content.append({"chapter": title, "title": name, "quest_id": qid, "completion": f"有效击杀 {count} × {entity}" if entity else f"背包持有 {count} × {item}" if item else "玩家手动确认", "description": description})
        save(OUT / "chapters" / (filename + ".snbt"), {"id": chapter_id, "filename": filename, "order_index": order, "icon": {"id": icon}, "quests": quests})
    save(OUT / "data.snbt", {"version": 13, "progression_mode": "flexible", "default_consume_items": False, "default_reward_team": False, "default_autoclaim_rewards": "disabled", "detection_delay": 20, "fallback_locale": "zh_cn", "drop_book_on_death": False, "pause_game": False})
    save(OUT / "chapter_groups.snbt", {"chapter_groups": []})
    for locale in ["zh_cn", "en_us"]:
        save(OUT / "lang" / (locale + ".snbt"), languages)
    save(ROOT / "evidence" / "quest-content.json", content)
    lines = ["# 任务内容 v0.2", "", "五章初中期内容；未配置强制配方或装备门槛。物品任务检查背包持有，不消耗物品，不证明机器已经工作；击杀任务采用 FTB KillTask。建筑、养成与体验目标由玩家手动确认。远航与终局仍待制作。", ""]
    for row in content:
        lines.extend(["## " + row["chapter"] + " · " + row["title"], "", "完成条件：" + row["completion"], "", row["description"], ""])
    (ROOT / "QUESTS-v0.2.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"chapters": len(chapters), "quests": len(content), "automatic_item": sum(row["completion"].startswith("背包") for row in content), "automatic_kill": sum(row["completion"].startswith("有效击杀") for row in content), "manual": sum(row["completion"] == "玩家手动确认" for row in content)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
