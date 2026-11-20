"""Add the approved chapter with stable IDs; preserve all existing chapters/lang keys."""
from pathlib import Path
import hashlib
import json
from build_prologue_training import add_training

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


def main(write_languages=True):
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
        lang["quest."+q["id"]+".quest_desc"]=desc

    def adv(name):
        return {"type":"advancement","advancement":"world_progression_core:"+name,"criterion":"done"}

    def item(name,count=1):
        return {"type":"item","item":{"id":name,"count":1},"count":count,"consume_items":False}

    quest("arrival","在小屋里醒来",[
        "床边的箱子可以存东西。打开任务书领面包、火把和原木，带上成长剑出门。"],
        0,0,"minecraft:red_bed",tasks=[adv("arrive")])
    quest("explore","走出聚落",[
        "沿小镇外的路走出至少 96 格，找一处矿洞。猩红和腐化的边界会在史莱姆王倒下后打开。"],
        2,0,"minecraft:compass",needs=["arrival"],tasks=[adv("explore")])
    quest("tools","准备下矿",[
        "做一把石镐。带上火把和垫脚石，把暂时用不到的东西留在家里。"],
        4,0,"minecraft:stone_pickaxe",needs=["explore"],tasks=[item("minecraft:stone_pickaxe")])
    quest("mine","第一趟矿洞",[
        "从聚落地面往下至少 12 格，亲手挖一块石头或矿石。带回 32 圆石、8 煤炭和 6 粗铁。"],
        6,0,"minecraft:raw_iron",needs=["tools"],tasks=[adv("first_mine"),item("minecraft:cobblestone",32),item("minecraft:coal",8),item("minecraft:raw_iron",6)])
    quest("template","材料的另一种用法",[
        "在 JEI 中查看泥土模板的配方，先用泥土做出第一个模板。"],
        8,-1,"infinitecore:template_dirt",needs=["mine"],tasks=[item("infinitecore:template_dirt")])
    quest("generator","第一个泥土生成器",[
        "在工作台中间放橡木木板，周围摆满 8 个泥土模板。亲手做出的生成器可以持续供应泥土。"],
        10,-1,"infinitecore:dirt_generator",needs=["template"],tasks=[adv("craft_dirt_generator")])
    quest("alloy","机械动力的起点",[
        "制作 4 个安山合金，留给接下来的传动部件。"],
        8,1,"create:andesite_alloy",needs=["mine"],tasks=[item("create:andesite_alloy",4)])
    quest("create","一套基础机械方块",[
        "备齐传动杆、齿轮、手摇曲柄和安山机壳各 1 个。把曲柄接到齿轮上，右键转动它。"],
        10,1,"create:hand_crank",needs=["alloy"],tasks=[item("create:shaft"),item("create:cogwheel"),item("create:hand_crank"),item("create:andesite_casing")])
    quest("workshop","为旅途建立后方",[
        "泥土生成器管原料，曲柄带动机械。领走维度网络发生器，把矿物和下一趟出门的行装分开收好。"],
        12,0,"beyonddimensions:net_creater",needs=["generator","create"],tasks=[{"type":"checkmark"}],reward=True)
    quest("king","第一次首领挑战：史莱姆王",[
        "选一处开阔地，使用史莱姆皇冠召唤史莱姆王。横向跑开它的落点，等它落地再还击。"],
        14,0,"minecraft:slime_block",needs=["workshop"],tasks=[adv("unlock_evil")])
    quest("next","通往猩红与腐化的道路",[
        "猩红和腐化的道路已经打开。去边境带一批木料回来，第一座工坊就有了开工的材料。"],
        16,0,"minecraft:map",needs=["king"],tasks=[{"type":"checkmark"}])
    quest("nature_compass","合成自然罗盘",[
        "击败史莱姆王后，亲手做一个自然罗盘。用它搜索腐化或猩红，出发前记下回家的方向。"],
        18,-1,"naturescompass:naturescompass",needs=["next"],tasks=[adv("craft_natures_compass")])
    quest("explorer_compass","合成探险者指南针",[
        "击败史莱姆王后，亲手做一个探险者指南针。选择结构名称，它会指引你寻找遗迹。"],
        18,1,"explorerscompass:explorerscompass",needs=["next"],tasks=[adv("craft_explorers_compass")])
    add_training(chapter, lang, uid)
    write(QUESTS / "chapters/00_lostworld_chapter1.snbt",chapter)
    for locale in (("zh_cn","en_us") if write_languages else ()):
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
    assert len(chapter["quests"])==22
    verify_chapter(chapter)
    print("Prologue: 22 quests; three weapon drills, field kit, shared-world catch-up and post-King sword practice")


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
    assert nodes[uid("king")]["dependencies"]==[uid("crown"),uid("campaign")]
    assert nodes[uid("king")]["min_required_dependencies"]==1
    assert nodes[uid("crown")]["dependencies"]==[uid(n) for n in ("workshop","allocate","blade","loadout")]
    assert nodes[uid("loadout")]["dependencies"]==[uid(n) for n in ("melee","archery","magic")]
    assert nodes[uid("loadout")]["min_required_dependencies"]==1
    for name in ("nature_compass","explorer_compass"):
        assert nodes[uid(name)]["dependencies"]==[uid("next")]
        assert nodes[uid(name)]["tasks"][0]["type"]=="advancement"
        assert nodes[uid(name)]["optional"]
    assert nodes[uid("workshop")]["rewards"][0]=={"id":uid("workshop/network"),"type":"item",
        "item":{"id":"beyonddimensions:net_creater","count":1},"count":1}


if __name__ == "__main__":
    main()
