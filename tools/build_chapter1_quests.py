"""Add the approved chapter with stable IDs; preserve all existing chapters/lang keys."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
QUESTS = ROOT / "staging/config/ftbquests/quests"
RES = ROOT / "modules/world-progression-core/src/main/resources/data/world_progression_core"


def uid(name):
    # FTB Quests 2101.1.27 uses Long.parseLong(hex, 16), not unsigned parsing.
    # Out-of-range IDs are replaced on load and dependency references disappear.
    value=int(hashlib.sha256(("lostworld.chapter1.v1/"+name).encode()).hexdigest()[:16],16)
    return f"{value & 0x7FFFFFFFFFFFFFFF:016X}"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


def main():
    chapter = {"id":uid("chapter"),"filename":"00_lostworld_chapter1","order_index":-1,
               "icon":{"id":"evolvingblade:star_eater"},"progression_mode":"linear","quests":[]}
    lang = {"chapter."+uid("chapter")+".title":"第一章 · 从小屋出发"}

    def quest(name,title,desc,x,y,icon,needs=(),tasks=(),reward=False):
        q={"id":uid(name),"x":x,"y":y,"icon":{"id":icon},"min_width":300,
           "dependencies":[uid(n) for n in needs],"dependency_requirement":"all_completed",
           "progression_mode":"linear","hide_dependency_lines":False,
           "hide_until_deps_visible":False,"hide_until_deps_complete":False,
           "hide_lock_icon":False,"tasks":[],"rewards":[]}
        for i,t in enumerate(tasks):
            q["tasks"].append({"id":uid(name+"/task/"+str(i)),**t})
        if reward:
            q["rewards"].append({"id":uid(name+"/network"),"type":"item",
                "item":{"id":"beyonddimensions:net_creater","count":1},"count":1})
        chapter["quests"].append(q)
        lang["quest."+q["id"]+".title"]=title
        lang["quest."+q["id"]+".quest_desc"]=desc

    def adv(name):
        return {"type":"advancement","advancement":"world_progression_core:"+name,"criterion":"done"}

    def item(name,count=1):
        return {"type":"item","item":{"id":name,"count":1},"count":count,"consume_items":False}

    quest("arrival","在小屋里醒来",[
        "村民发现你时，你身边只有一把剑。你在他们为你留出的屋子里醒来。",
        "这里有床、空箱和工作台，屋外的空地可以继续扩建。铁门两侧有按钮。",
        "成长剑已采用标准路线。先安顿下来，再踏上旅途。前言物资与完整玩法介绍将在后续制作。"],
        0,0,"minecraft:red_bed",tasks=[adv("arrive")])
    quest("explore","走出聚落",["离开小屋，走到距离起点至少 96 格的地方，寻找树林、石头和矿洞。",
        "猩红与腐化区域已经生成，但在击败史莱姆王之前无法进入；不必在边界上反复尝试。"],
        2,0,"minecraft:compass",needs=["arrival"],tasks=[adv("explore")])
    quest("tools","准备下矿",["制作一把石镐。食物、照明和其他工具由你自行准备；本次试作暂不额外赠送。"],
        4,0,"minecraft:stone_pickaxe",needs=["explore"],tasks=[item("minecraft:stone_pickaxe")])
    quest("mine","第一趟矿洞",["在低于聚落地面至少 12 格的主世界矿层，用镐实际挖掘石头或矿石。",
        "带回 32 个圆石、8 个煤炭和 6 个粗铁。任务检测不会消耗这些材料。"],
        6,0,"minecraft:raw_iron",needs=["tools"],tasks=[adv("first_mine"),item("minecraft:cobblestone",32),item("minecraft:coal",8),item("minecraft:raw_iron",6)])
    quest("template","材料的另一种用法",["查看 JEI，制作泥土模板。当前无限之核使用材料直接合成模板，不需要空白模板。"],
        8,-1,"infinitecore:template_dirt",needs=["mine"],tasks=[item("infinitecore:template_dirt")])
    quest("generator","第一个泥土生成器",["亲手合成一个泥土生成器：8 个泥土模板围绕 1 块橡木木板。",
        "这一节点检测真实合成记录，单纯领取或捡到方块不算完成。"],
        10,-1,"infinitecore:dirt_generator",needs=["template"],tasks=[adv("craft_dirt_generator")])
    quest("alloy","机械动力的起点",["制作并保留至少 4 个安山合金，按 JEI 配方准备 Create 基础材料。"],
        8,1,"create:andesite_alloy",needs=["mine"],tasks=[item("create:andesite_alloy",4)])
    quest("create","一套基础机械方块",["制作传动杆、齿轮、手摇曲柄和安山机壳各 1 个。先用手摇曲柄理解转动与传动。",
        "本次试作检测持有这些部件；自动化规模、动力指标与配方调整后续再展开。"],
        10,1,"create:hand_crank",needs=["alloy"],tasks=[item("create:shaft"),item("create:cogwheel"),item("create:hand_crank"),item("create:andesite_casing")])
    quest("workshop","为旅途建立后方",["泥土生成器和机械动力的基础已经准备就绪。把小屋发展成自己的第一处基地。",
        "完成两条分支后，领取 1 个维度网络发生器。它是本次试作唯一新增的物品奖励。"],
        12,0,"beyonddimensions:net_creater",needs=["generator","create"],tasks=[{"type":"checkmark"}],reward=True)
    quest("king","第一次首领挑战：史莱姆王",["做好装备与补给，按汇流来世的原生方式召唤并击败史莱姆王。",
        "召唤条件以当前模组实际玩法与 JEI 为准。本章不改写汇流来世原生首领进度。",
        "史莱姆王击败记录按整个存档共享。普通史莱姆、首领仆从和取消的死亡不会打开区域。"],
        14,0,"minecraft:slime_block",needs=["workshop"],tasks=[adv("unlock_evil")])
    quest("next","通往猩红与腐化的道路",["史莱姆王倒下后，整个世界的猩红与腐化区域解除进入限制，重新登录后仍保持开放。",
        "第一章试作到这里结束。第二章的探索目标、敌人和进一步发展路线尚未制作。"],
        16,0,"minecraft:map",needs=["king"],tasks=[{"type":"checkmark"}])
    write(QUESTS / "chapters/00_lostworld_chapter1.snbt",chapter)
    for locale in ("zh_cn","en_us"):
        dest=QUESTS / f"lang/{locale}.snbt"
        existing=json.loads(dest.read_text("utf-8-sig"))
        existing.update(lang)
        write(dest,existing)
    for name in ("arrive","explore","first_mine","unlock_evil"):
        write(RES / f"advancement/{name}.json",{"criteria":{"done":{"trigger":"minecraft:impossible"}}})
    write(RES / "advancement/craft_dirt_generator.json",{"criteria":{"done":{
        "trigger":"minecraft:recipe_crafted","conditions":{"recipe_id":"infinitecore:dirt_generator"}}}})
    write(RES / "tags/worldgen/biome/locked_evil_biomes.json",{"replace":False,"values":[
        {"id":"confluence:"+b,"required":False} for b in
        ("the_corruption","the_corruption_desert","the_corruption_tundra","the_crimson","the_crimson_desert","the_crimson_tundra")]})
    ids=[]
    for path in (QUESTS / "chapters").glob("*.snbt"):
        doc=json.loads(path.read_text("utf-8-sig")); ids.append(doc["id"])
        for q in doc.get("quests",[]):
            ids.append(q["id"])
            for kind in ("tasks","rewards"):
                ids.extend(t["id"] for t in q.get(kind,[]))
    assert len(ids)==len(set(ids)),"Quest IDs collide"
    assert len(chapter["quests"])==11
    verify_chapter(chapter)
    print("First chapter: 11 quests, unique IDs across all chapters, one network generator reward")


def verify_chapter(chapter):
    """Guard the real ID/parser failure and the branch join that gates the reward."""
    objects=[chapter]+chapter["quests"]+[t for q in chapter["quests"] for k in ("tasks","rewards") for t in q[k]]
    assert all(0<int(o["id"],16)<=0x7FFFFFFFFFFFFFFF for o in objects),"ID outside signed-positive FTB range"
    nodes={q["id"]:q for q in chapter["quests"]}
    assert chapter["progression_mode"]=="linear"
    for q in nodes.values():
        assert q["progression_mode"]=="linear" and q["dependency_requirement"]=="all_completed"
        assert all(d in nodes for d in q["dependencies"]),"Dangling dependency"
    reached=set()
    for q in chapter["quests"]:
        assert set(q["dependencies"])<=reached,"Cycle or disconnected ordering"
        assert q["dependencies"] or q["id"]==uid("arrival"),"Unexpected unlocked root"
        reached.add(q["id"])
    assert nodes[uid("workshop")]["dependencies"]==[uid("generator"),uid("create")]
    assert nodes[uid("king")]["dependencies"]==[uid("workshop")]
    assert sum(len(q["rewards"]) for q in nodes.values())==1


if __name__ == "__main__":
    main()
