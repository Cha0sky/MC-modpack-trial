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


def milestone_supplies(name):
    # Append-only reward IDs: never rename or replace the existing network reward.
    packs={
        "arrival":[("minecraft:bread",32),("minecraft:torch",64),("minecraft:oak_log",64)],
        "mine":[("minecraft:cobblestone",128),("minecraft:coal",32),("minecraft:iron_ingot",16),("minecraft:copper_ingot",24)],
        "generator":[("minecraft:dirt",128),("minecraft:oak_log",64),("minecraft:stone",64)],
        "workshop":[("minecraft:chest",8),("minecraft:barrel",8),("minecraft:oak_log",128),("minecraft:stone_bricks",128),("minecraft:cooked_beef",32)],
        "king":[("minecraft:iron_chestplate",1),("minecraft:golden_apple",4),("minecraft:experience_bottle",8)],
        "nature_compass":[("minecraft:spyglass",1),("minecraft:lead",4)],
        "explorer_compass":[("minecraft:map",8),("minecraft:scaffolding",64)],
    }
    rewards=[]
    for item,count in packs.get(name,[]):
        stack={"id":item,"count":1}
        if item=="minecraft:iron_chestplate":
            stack["components"]={"minecraft:enchantments":{"levels":{
                "minecraft:protection":2,"minecraft:unbreaking":2}}}
        rewards.append({"id":uid(name+"/supplies_v1/"+item),"type":"item","item":stack,"count":count})
    return rewards


def main():
    chapter = {"id":uid("chapter"),"filename":"00_lostworld_chapter1","order_index":-1,
               "icon":{"id":"evolvingblade:star_eater"},"progression_mode":"linear","quests":[]}
    lang = {"chapter."+uid("chapter")+".title":"序章 · 从小屋出发"}

    def quest(name,title,desc,x,y,icon,needs=(),tasks=(),reward=False):
        q={"id":uid(name),"x":x,"y":y,"icon":{"id":icon},"min_width":300,
           "dependencies":[uid(n) for n in needs],"dependency_requirement":"all_completed",
           "progression_mode":"linear","optional":name in ("nature_compass","explorer_compass"),"hide_dependency_lines":False,
           "hide_until_deps_visible":False,"hide_until_deps_complete":False,
           "hide_lock_icon":False,"tasks":[],"rewards":[]}
        for i,t in enumerate(tasks):
            q["tasks"].append({"id":uid(name+"/task/"+str(i)),**t})
        if reward:
            q["rewards"].append({"id":uid(name+"/network"),"type":"item",
                "item":{"id":"beyonddimensions:net_creater","count":1},"count":1})
        q["rewards"].extend(milestone_supplies(name))
        chapter["quests"].append(q)
        lang["quest."+q["id"]+".title"]=title
        lang["quest."+q["id"]+".quest_desc"]=["【叙事占位："+title+"】",*desc]

    def adv(name):
        return {"type":"advancement","advancement":"world_progression_core:"+name,"criterion":"done"}

    def item(name,count=1):
        return {"type":"item","item":{"id":name,"count":1},"count":count,"consume_items":False}

    quest("arrival","在小屋里醒来",[
        "开场剧情暂留占位，后续补写。",
        "这里有床、空箱和工作台。沿门廊出入聚落，屋外的空地可以继续扩建。",
        "成长剑已采用标准路线。先领取面包、火把和原木，安顿下来再出发；任务书中的物资需要手动领取。"],
        0,0,"minecraft:red_bed",tasks=[adv("arrive")])
    quest("explore","走出聚落",["离开小屋，走到距离起点至少 96 格的地方，寻找树林、石头和矿洞。",
        "猩红与腐化区域已经生成，但在击败史莱姆王之前无法进入；不必在边界上反复尝试。"],
        2,0,"minecraft:compass",needs=["arrival"],tasks=[adv("explore")])
    quest("tools","准备下矿",["制作一把石镐。带上开局领取的食物与火把，再准备一些垫脚方块；多余物资可以先留在家里。"],
        4,0,"minecraft:stone_pickaxe",needs=["explore"],tasks=[item("minecraft:stone_pickaxe")])
    quest("mine","第一趟矿洞",["在低于聚落地面至少 12 格的主世界矿层，用镐实际挖掘石头或矿石。",
        "带回 32 个圆石、8 个煤炭和 6 个粗铁。任务检测不会消耗这些材料。",
        "完成后领取一批石料、燃料和铁铜锭，用来做工具与起步设备；不用为了每个基础零件反复跑矿洞。"],
        6,0,"minecraft:raw_iron",needs=["tools"],tasks=[adv("first_mine"),item("minecraft:cobblestone",32),item("minecraft:coal",8),item("minecraft:raw_iron",6)])
    quest("template","材料的另一种用法",["查看 JEI，制作泥土模板。当前无限之核使用材料直接合成模板，不需要空白模板。"],
        8,-1,"infinitecore:template_dirt",needs=["mine"],tasks=[item("infinitecore:template_dirt")])
    quest("generator","第一个泥土生成器",["亲手合成一个泥土生成器：8 个泥土模板围绕 1 块橡木木板。",
        "这一节点检测真实合成记录，单纯领取或捡到方块不算完成。",
        "领取泥土、木材和石材作为第一批扩建物资，生成器负责后续持续供应。"],
        10,-1,"infinitecore:dirt_generator",needs=["template"],tasks=[adv("craft_dirt_generator")])
    quest("alloy","机械动力的起点",["制作并保留至少 4 个安山合金，按 JEI 配方准备 Create 基础材料。"],
        8,1,"create:andesite_alloy",needs=["mine"],tasks=[item("create:andesite_alloy",4)])
    quest("create","一套基础机械方块",["制作传动杆、齿轮、手摇曲柄和安山机壳各 1 个。先用手摇曲柄理解转动与传动。",
        "本次试作检测持有这些部件；自动化规模、动力指标与配方调整后续再展开。"],
        10,1,"create:hand_crank",needs=["alloy"],tasks=[item("create:shaft"),item("create:cogwheel"),item("create:hand_crank"),item("create:andesite_casing")])
    quest("workshop","为旅途建立后方",["泥土生成器和机械动力的基础已经准备就绪。把小屋发展成自己的第一处基地。",
        "完成两条分支后，领取 1 个维度网络发生器和一批储物、建筑、远征物资，把家整理成可以随时出发的后方。"],
        12,0,"beyonddimensions:net_creater",needs=["generator","create"],tasks=[{"type":"checkmark"}],reward=True)
    quest("king","第一次首领挑战：史莱姆王",["在工作台合成史莱姆皇冠，准备装备与补给后召唤并击败史莱姆王。",
        "新增配方：8 个凝胶围绕 1 个金冠或铂金冠，产出 1 个史莱姆皇冠；皇冠本体沿用原生工作台配方。原祭坛配方保留。",
        "本章不改写汇流来世原生首领进度。",
        "史莱姆王击败记录按整个存档共享。普通史莱姆、首领仆从和取消的死亡不会打开区域。",
        "通关后领取保护Ⅱ、耐久Ⅱ铁胸甲，连同金苹果和经验瓶一起为边境调查做好准备。"],
        14,0,"minecraft:slime_block",needs=["workshop"],tasks=[adv("unlock_evil")])
    quest("next","通往猩红与腐化的道路",["史莱姆王倒下后，整个世界的猩红与腐化区域解除进入限制，重新登录后仍保持开放。",
        "第一章“重启第一座工坊”已可接续：调查腐化或猩红、完成机械加工、制造调谐器并修复旧工坊。",
        "两种指南针是可自行补齐的探索工具；第一章从本节点接续，不要求先把两种指南针都做完。"],
        16,0,"minecraft:map",needs=["king"],tasks=[{"type":"checkmark"}])
    quest("nature_compass","合成自然罗盘",["击败史莱姆王后，按 JEI 的原生配方亲手合成 1 个自然罗盘。",
        "检测自然罗盘的首次制作配方；仅持有、拾取或修复已有罗盘不算。击败史莱姆王之前的合成不计入此节点。",
        "领取望远镜和拴绳，把沿途值得停留的地方与动物带进自己的生活。"],
        18,-1,"naturescompass:naturescompass",needs=["next"],tasks=[adv("craft_natures_compass")])
    quest("explorer_compass","合成探险者指南针",["击败史莱姆王后，按 JEI 的原生配方亲手合成 1 个探险者指南针。",
        "检测指南针的首次制作配方；仅持有、拾取或修复已有指南针不算。击败史莱姆王之前的合成不计入此节点。",
        "领取空地图与脚手架，为之后的遗迹测绘、攀爬和搭建准备工具。"],
        18,1,"explorerscompass:explorerscompass",needs=["next"],tasks=[adv("craft_explorers_compass")])
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
    for name,recipe in (("craft_natures_compass","naturescompass:natures_compass"),
                        ("craft_explorers_compass","explorerscompass:explorers_compass")):
        write(RES / f"advancement/{name}.json",{"criteria":{"done":{
            "trigger":"minecraft:recipe_crafted","conditions":{"recipe_id":recipe,"player":[{
                "condition":"minecraft:entity_properties","entity":"this","predicate":{
                    "type_specific":{"type":"minecraft:player","advancements":{
                        "world_progression_core:unlock_evil":True}}}}]}}}})
    write(RES / "recipe/slime_crown_workbench.json",{
        "neoforge:conditions":[{"type":"neoforge:mod_loaded","modid":"confluence"}],
        "type":"minecraft:crafting_shaped","category":"misc","pattern":["GGG","GCG","GGG"],
        "key":{"G":{"item":"confluence:gel"},"C":[{"item":"confluence:gold_crown"},{"item":"confluence:platinum_crown"}]},
        "result":{"id":"confluence:slime_crown","count":1}})
    write(RES / "advancement/recipes/slime_crown_workbench.json",{
        "neoforge:conditions":[{"type":"neoforge:mod_loaded","modid":"confluence"}],
        "parent":"minecraft:recipes/root","criteria":{"has_gel":{"trigger":"minecraft:inventory_changed",
            "conditions":{"items":[{"items":["confluence:gel"]}]}}},
        "requirements":[["has_gel"]],"rewards":{"recipes":["world_progression_core:slime_crown_workbench"]}})
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
    assert len(chapter["quests"])==13
    verify_chapter(chapter)
    print("First chapter: 13 quests, post-King crafting tasks, workbench slime crown; stable unique positive IDs")


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
    for name in ("nature_compass","explorer_compass"):
        assert nodes[uid(name)]["dependencies"]==[uid("next")]
        assert nodes[uid(name)]["tasks"][0]["type"]=="advancement"
        assert nodes[uid(name)]["optional"]
    assert nodes[uid("workshop")]["rewards"][0]=={"id":uid("workshop/network"),"type":"item",
        "item":{"id":"beyonddimensions:net_creater","count":1},"count":1}


if __name__ == "__main__":
    main()
