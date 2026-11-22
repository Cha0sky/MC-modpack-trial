"""Author the journey journal, field bestiary and FTB chapter groups."""
from build_gear_guide import guide_link
from build_heritage_quests import QUESTS, merge, write
from campaign_navigation import BESTIARY, GUIDE_GROUP, JOURNAL, LEGS, MAIN_GROUP, quest_id, turn_page


def page(chapter, lang, key, title, subtitle, icon, position, lines, *, after=()):
    qid = quest_id(chapter["filename"], key)
    node = {"id": qid, "x": position[0], "y": position[1], "min_width": 310, "icon": {"id": icon},
            "optional": True, "dependencies": list(after), "tasks": [], "rewards": []}
    if after:
        node.update(progression_mode="linear", hide_until_deps_complete=True, hide_details_until_startable=True,
                    dependency_requirement="all_completed", hide_dependency_lines=True)
    chapter["quests"].append(node)
    lang[f"quest.{qid}.title"] = title
    lang[f"quest.{qid}.quest_subtitle"] = subtitle
    lang[f"quest.{qid}.quest_desc"] = lines


def journal():
    book = {"id": quest_id(JOURNAL, "chapter"), "filename": JOURNAL, "group": MAIN_GROUP,
            "order_index": 6, "icon": {"id": "minecraft:writable_book"}, "quests": []}
    lang = {f"chapter.{book['id']}.title": "旅人手记",
            f"chapter.{book['id']}.chapter_subtitle": ["翻到正在走的那一段。每完成一章，会留下一页手记。"]}
    page(book, lang, "index", "这一路怎样走", "行程索引 · 章末添页", "minecraft:writable_book", (0, 0), [
        "先在小镇站稳脚，再到边境找那座停转的工坊。下面的地名能翻到对应章节；归来时，再看手记里添了什么。",
        *[turn_page(leg.title, leg.quest(leg.start)) for leg in LEGS],
        turn_page("查首领与世界变化", quest_id(BESTIARY, "index")), guide_link("查装备与饰品", "index"),
    ])
    for i, leg in enumerate(LEGS):
        lines = [leg.text, turn_page("翻回本章成果", leg.quest(leg.end))]
        if i + 1 < len(LEGS):
            next_leg = LEGS[i + 1]
            lines.append(turn_page("下一站 · " + next_leg.title.split(" · ")[1], next_leg.quest(next_leg.start)))
        else:
            lines.append(turn_page("回站接一份扩建委托", leg.quest("reserve")))
        page(book, lang, leg.book, leg.note, leg.title + " · 归来记", leg.icon, (i * 1.8, 2), lines,
             after=(leg.quest(leg.end),))
    page(book, lang, "wood_samples", "两块木样", "夹页 · 边境采样", "world_progression_core:tainted_fiber", (2, 4), [
        "乌木和暗影木并排时很好认。可它们都能磨出纤维，拌成同一种胶。要是只把成品带回去，谁还分得出它来自哪片林子？留一块原木，把采样地写在旁边。",
        turn_page("翻到两地采样", LEGS[1].quest("both_regions")),
    ], after=(LEGS[1].quest("both_regions"), LEGS[1].quest("binder")))
    page(book, lang, "two_witnesses", "镜头外的一行字", "夹页 · 两种现场记录", "exposure:camera", (9, 4), [
        "旧人曾用精密的仪器望向群星。我只拍了一面墙，回来却分不清暗下来的是纹样，还是提灯挪开了一步。把站立的位置也写下来，下次请同伴从那里再看一眼。",
        turn_page("再去现场记一笔", LEGS[5].quest("notebook")), turn_page("把记录绘成回访图", LEGS[5].quest("chart")),
    ], after=(LEGS[5].quest("field_photo"), LEGS[5].quest("notebook")))
    return book, lang


def hunts():
    book = {"id": quest_id(BESTIARY, "chapter"), "filename": BESTIARY, "group": GUIDE_GROUP,
            "order_index": 0, "icon": {"id": "confluence:slime_crown"}, "quests": []}
    lang = {f"chapter.{book['id']}.title": "首领战册",
            f"chapter.{book['id']}.chapter_subtitle": ["先找这场仗的用处，再准备召唤物和退路。"]}
    destinations = {
        "turning": ("世界转折", "打赢后，整个存档都会看到变化。", "confluence:pwnhammer", 0),
        "survey": ("调查委托", "第二章走远征路线时，六位对手任选一位，再走访两类遗迹。也可以用工程和奥术两线完成调查。", "minecraft:spyglass", 1),
        "contracts": ("遗址悬赏", "死者之王和灾厄宰相各算一份现场凭据。也可以记手记、拍照片，或踏勘两地后展出归档图。", "minecraft:filled_map", 2),
    }
    # Each field entry links back to the real quest that settles its record.
    cards = (
        ("turning", "king_slime", "史莱姆王", "序章 · 打开边境", "confluence:slime_crown", [
            "工作台中央放金冠或铂金冠，周围摆 8 个凝胶。带上做好的史莱姆皇冠，到有横移空间的平地使用。",
            "它收拢身体时先看落点。用跑鞋横移或用云朵瓶越过它，等它落稳再还击。击败本体后，全世界通往猩红和腐化的路开放；第一批边境木就是修工坊的原料。",
        ], 0, "king"),
        ("turning", "flesh", "血肉墙／血肉山", "第三章 · 任一形态开启困难模式", "confluence:guide_voodoo_doll", [
            "在下界先找一段可以持续移动的路线。手持向导巫毒玩偶长按使用，核对提示里的形态，再把玩偶丢入下界岩浆。",
            "两种形态任选一场。打赢后世界进入困难模式，地形开始转化；带神锤敲祭坛，去找钴矿或钯金矿。档案台能查到战果和转化进度。",
        ], 3, "flesh"),
        ("survey", "wroughtnaut", "钢铁守护者", "第一章支线 · 战果可用于远征", "mowziesmobs:wrought_axe", [
            "用探险者指南针搜 mowziesmobs:wrought_chamber，进入地下石室。给巨斧留出空间，等重劈落空、斧头陷进地面，再绕到它背后出手。",
            "赢下第一章「沉睡的铁甲」后，第二章可沿用战果。它的千锻巨斧可右键横扫，也可潜行右键震地。",
        ], 1, "wroughtnaut"),
        ("survey", "frostmaw", "霜冻巨兽", "第二章 · 雪地远征", "mowziesmobs:ice_crystal", [
            "到雪地寻找沉睡的巨兽；探险者指南针可搜 mowziesmobs:frostmaw_spawn。近身攻击会惊醒它，先留出绕到侧面的空间。",
            "打倒本体可交一份远征战果。它仍握着冰霜结晶时由玩家击败，才有结晶可取；持晶长按右键能吹出冰霜旋风。",
        ], 2, "expedition_challenge"),
        ("survey", "eye", "克苏鲁之眼", "第二章 · 夜间邀战", "confluence:suspicious_looking_eye", [
            "在祭坛用 6 个晶状体制作可疑眼球。选夜晚到开阔处使用，留出横向闪避冲刺的空间。",
            "想走远征线又不愿深入墓室时，可以挑这一场；另两类遗迹勘察仍需实地完成。",
        ], 2, "expedition_challenge"),
        ("survey", "worm", "世界吞噬怪", "第二章 · 腐化调查", "confluence:worm_food", [
            "在祭坛以 30 份魔粉和 15 份腐肉制成蠕虫诱饵，带到腐化之地使用。魔粉可以用当地的魔菇研制。",
            "把战场留在能看清地面的位置。它分节出土时先避开头部，整场战斗结束后再回档案台整理战果。",
        ], 2, "expedition_challenge"),
        ("survey", "brain", "克苏鲁之脑", "第二章 · 猩红调查", "confluence:bloody_spine", [
            "在祭坛用 30 份毒粉、15 个椎骨制作血腥脊椎，前往猩红之地使用。毒粉可由当地的毒蘑菇制成。",
            "先腾出能反复转身的战场，处理围上来的随从，再追本体。选这一位也能交同一份远征委托。",
        ], 2, "expedition_challenge"),
        ("survey", "skull_lord", "骷髅领主", "第二章 · 大坟墓调查", "goety:crypt_urn", [
            "用探险者指南针搜 goety:crypt。进入大坟墓，先清开来路，再查看墓穴瓮；开瓮时可能遭遇领主。",
            "目标是骷髅领主本体，已有诡厄对应击败成就也可登记。若补给不足，可以先撤回，另选同阶段对手。",
        ], 2, "expedition_challenge"),
        ("contracts", "dead_king", "死者之王", "第五章 · 地下墓穴", "irons_spellbooks:wayward_compass", [
            "跟随归途指南针，或搜 irons_spellbooks:catacombs。深入墓室，与沉睡的遗骸互动才会唤醒死者之王。",
            "若只想勘察，可以在惊醒它之前带走现场记录。决定开战时，先把退出内室的路认清。战果可用于第五章制图，也可留给第六章的驻地委托。",
        ], 5, "dead_king"),
        ("contracts", "vizier", "灾厄宰相", "第五章 · 林地府邸", "goety:dark_scroll", [
            "先找到原版林地府邸。工作台从上到下摆黑色染料、魔法布料和不死图腾，做成黑暗卷轴，在府邸内长按使用。",
            "盯住宰相本体，分身倒下时继续观察。本人或仆从的原生讨伐成就都可承接；战果能作第五章的调查凭据。",
        ], 5, "vizier"),
    )
    def jump(title, key):
        return turn_page(title, quest_id(BESTIARY, key))

    page(book, lang, "index", "先看这场仗的用处", "世界门槛与自由讨伐", "minecraft:map", (0, 0), [
        "史莱姆王管边境，血肉首领管困难模式。想备战这两场，翻「世界转折」；想挑选一份适合当前配装的悬赏，翻后两册。",
        *[jump(v[0], k) for k, v in destinations.items()], guide_link("查战前装备与饰品", "index"),
    ])
    for key, (title, desc, icon, row) in destinations.items():
        subset = [c for c in cards if c[0] == key]
        page(book, lang, key, title, "首领战册 · 选一位对手", icon, (row * 4, 2), [
            desc, *[jump(c[2], c[1]) for c in subset], jump("回到战册目录", "index"),
        ])
        for i, (_, slug, name, sub, picture, lines, chapter, task) in enumerate(subset):
            links = [turn_page("翻到对应任务", LEGS[chapter].quest(task))]
            if slug == "king_slime":
                links.append(guide_link("挑一件开荒饰品", "starter"))
            elif slug == "flesh":
                links.append(turn_page("回工坊准备行军餐", LEGS[3].quest("pack")))
            elif slug == "frostmaw":
                links.append(turn_page("接单 · 雪地结晶回收", LEGS[2].quest("ice_recovery")))
            elif slug == "vizier":
                links.append(turn_page("接单 · 让仆从完成最后一击", LEGS[5].quest("servant_bounty")))
            page(book, lang, slug, name, sub, picture, (row * 4 + (i % 2) * 1.4, 4 + 1.5 * (i // 2)),
                 [*lines, *links, jump("回到「" + title + "」", key)])
    return book, lang


def main():
    groups = [(MAIN_GROUP, "远征主线"), (GUIDE_GROUP, "随身手册")]
    write(QUESTS / "chapter_groups.snbt", {"chapter_groups": [{"id": k} for k, _ in groups]})
    language = {f"chapter_group.{key}.title": title for key, title in groups}
    for chapter, words in (journal(), hunts()):
        write(QUESTS / f"chapters/{chapter['filename']}.snbt", chapter)
        language.update(words)
    for locale in ("zh_cn", "en_us"):
        merge(QUESTS / f"lang/{locale}.snbt", language)


if __name__ == "__main__":
    main()
