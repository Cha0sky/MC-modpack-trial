"""Stable cross-chapter destinations for the campaign, journal and boss guide."""
import hashlib
import json
from typing import NamedTuple


def quest_id(book, key):
    raw = hashlib.sha256(f"lostworld.{book}.v1/{key}".encode()).hexdigest()[:16]
    return f"{int(raw, 16) & 0x7fffffffffffffff:016X}"


def turn_page(label, target):
    return json.dumps({"text": label, "color": "aqua", "underlined": True,
                       "clickEvent": {"action": "change_page", "value": target}},
                      ensure_ascii=False, separators=(",", ":"))


MAIN_GROUP = quest_id("campaign_groups", "campaign")
GUIDE_GROUP = quest_id("campaign_groups", "guides")
JOURNAL = "journey_journal"
BESTIARY = "expedition_bestiary"


class Leg(NamedTuple):
    filename: str
    book: str
    start: str
    end: str
    title: str
    subtitle: str
    note: str
    text: str
    icon: str

    def quest(self, key):
        return quest_id(self.book, key)


LEGS = (
    Leg("00_lostworld_chapter1", "chapter1", "arrival", "next", "序章 · 从小屋出发",
        "镇里给了你一张床。带上旧剑，去看看通往边境的路。",
        "第一次认得回家的路", "我想不起自己从哪儿来，却已经认得回镇的路了。打倒史莱姆王以后，原先走不通的边境也能踏进去了。下次回镇，带一捆那边的木头。", "minecraft:red_bed"),
    Leg("01_workshop_rekindled", "workshop", "commission", "restore", "第一章 · 重启第一座工坊",
        "边境的木头能做成胶。先让机器吃进原料、交出第一批成品，再去把旧工坊的轴接回来。",
        "维修簿里的箭头", "在换向节点旁画一个箭头，在旁边写上八转。下一个来修工坊的人看到这两笔，就知道该从哪根轴开始查。原来一门手艺能这样留下来。", "world_progression_core:rough_tuner"),
    Leg("02_heritage_survey", "heritage", "welcome", "archive_open", "第二章 · 遗产调查",
        "机械、术式和遗址各留下一条线索。带回两份完整的调查，合写一册档案。",
        "翻开第一册档案", "我翻开档案，本想找一个熟悉的名字。读到的却是能重新接好的机器、还可以施放的术式。黄金时代的人曾用这两种本领一同求知。要找回他们的历史，也许可以先学会他们做过的事。", "world_progression_core:heritage_archive"),
    Leg("03_world_turn", "world_turn", "prepare", "first_ingot", "第三章 · 世界转折",
        "出发时让厨房备餐，回程时让新矿入炉。迎战血肉首领前，先给队伍留一条退路。",
        "神锤响过以后", "血肉首领倒下、地貌变化、神锤敲响祭坛。我把三件事分行记下，最后一行夹了一张新矿的炉料单。那些金属原本藏在哪里？等下一趟带回证据，再往下写。", "confluence:pwnhammer"),
    Leg("04_field_equipment", "field_equipment", "bench", "trial", "第四章 · 困难模式整备",
        "第一炉锭给矿班做工具，还是给护卫打甲？锯边剩下的料也别扔，两班都等着一副夹模。",
        "谁来试这批新料", "工单背面有两个草图。一张画着坡底的轨头，一张画着岗亭顶上的灯钩。我把模留在机械手里，给下一班画了第三张图，告诉他们从哪里换坯。", "confluence:cobalt_ingot"),
    Leg("05_relic_expeditions", "relic_expeditions", "provisions", "chart", "第五章 · 遗址远征",
        "去墓穴或府邸带回手记、照片或战果。愿意跑两地的测绘员，还可用展图交差。",
        "地图给谁看", "来时我只想找到入口。把归档地图摊开，才发觉自己还得回答另一件事：没来过这里的人，怎样也能找到？下一趟就沿这张图走，在能落脚的地方点一堆火。", "minecraft:filled_map"),
    Leg("06_frontline_depot", "frontline", "dispatch", "handover", "第六章 · 前线补给站",
        "在地图指向的地方立站。行粮、救援、货运办成两项，再拿一张领用单去桶边交班。",
        "桶边的火", "离开的时候，我把营火留在木桶旁。晚到的人可以在这里吃一顿饭，脚手架也有地方找。这本手记第一页，是别人留给我的一张床。到这里，我也给后来的人留了点东西。", "minecraft:campfire"),
)

# Attach field advice where the decision is made. Reading a page adds no prerequisites.
HUNT_LINKS = {
    ("chapter1", "crown"): ("对照召唤物与退路", "king_slime"),
    ("chapter1", "king"): ("首领战册 · 史莱姆王", "king_slime"),
    ("workshop", "wroughtnaut"): ("首领战册 · 钢铁守护者", "wroughtnaut"),
    ("heritage", "expedition_challenge"): ("挑选对手 · 六项调查委托", "survey"),
    ("heritage", "ice_recovery"): ("查找巨兽和持晶条件", "frostmaw"),
    ("world_turn", "flesh"): ("下界备忘 · 形态与战后变化", "flesh"),
    ("relic_expeditions", "dead_king"): ("首领战册 · 死者之王", "dead_king"),
    ("relic_expeditions", "vizier"): ("首领战册 · 灾厄宰相", "vizier"),
    ("relic_expeditions", "servant_bounty"): ("查阅府邸邀战方法", "vizier"),
    ("frontline", "patrol"): ("查阅两份驻地悬赏", "contracts"),
}


def finish_chapter(chapter, language):
    leg = next(leg for leg in LEGS if leg.filename == chapter["filename"])
    chapter["group"] = MAIN_GROUP
    language[f"chapter.{chapter['id']}.chapter_subtitle"] = [leg.subtitle]
    if leg == LEGS[0]:
        language[f"quest.{leg.quest(leg.start)}.quest_desc"].append(
            turn_page("翻开旅人手记 · 我该从哪儿走", quest_id(JOURNAL, "index")))
    # Open the journal map, where visibility respects the team's actual progress.
    # Direct links to a quest open its detail pane even before its marker is visible.
    language[f"quest.{leg.quest(leg.end)}.quest_desc"].append(
        turn_page("完成后翻开手记 · " + leg.note, quest_id(JOURNAL, "chapter")))
    for (book, name), (label, target) in HUNT_LINKS.items():
        if book == leg.book:
            language[f"quest.{leg.quest(name)}.quest_desc"].append(turn_page(label, quest_id(BESTIARY, target)))
