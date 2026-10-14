"""Reproducible vanilla starter settlement blueprint; does not write an instance/save."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "modules/world-progression-core"
blocks = {}


def put(x, y, z, state):
    blocks[(x, y, z)] = "minecraft:" + state


def box(x1, y1, z1, x2, y2, z2, state):
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                put(x, y, z, state)


def house(x, z, w, d, player=False):
    box(x, 0, z, x+w-1, 0, z+d-1, "spruce_planks")
    for y in range(1, 5):
        for dx in range(w):
            for dz in range(d):
                if dx in (0, w-1) or dz in (0, d-1):
                    corner = dx in (0, w-1) and dz in (0, d-1)
                    put(x+dx, y, z+dz, "stripped_spruce_log[axis=y]" if corner else "oak_planks")
    for dx in (2, w-3):
        for y in (2, 3):
            put(x+dx, y, z, "glass_pane")
            put(x+dx, y, z+d-1, "glass_pane")
    for y in (2, 3):
        put(x, y, z+d//2, "glass_pane")
        put(x+w-1, y, z+d//2, "glass_pane")
    doorx = x+w//2
    for dy, half in ((1, "lower"), (2, "upper")):
        put(doorx, dy, z, f"{'iron' if player else 'oak'}_door[facing=south,half={half},hinge=left]")
    if player:
        put(doorx-1, 2, z-1, "stone_button[face=wall,facing=north]")
        put(doorx-1, 2, z+1, "stone_button[face=wall,facing=south]")
    # Gabled roof, sealed across the interior, and one-block eaves.
    for dx in range(-1, w+1):
        ry = 5 + min(dx+1, w-dx)//2
        box(x+dx, ry, z-1, x+dx, ry, z+d, "spruce_planks")
    for y in range(5, 5+w//4+1):
        for dx in range(w):
            if y < 5+min(dx+1, w-dx)//2:
                put(x+dx, y, z, "oak_planks")
                put(x+dx, y, z+d-1, "oak_planks")
    put(x+w//2, 4, z+d//2, "lantern[hanging=true]")
    for bx, bz in ((x+2,z+d-3),):
        put(bx, 1, bz, "red_bed[facing=north,part=foot]")
        put(bx, 1, bz-1, "red_bed[facing=north,part=head]")
    if player:
        put(x+w-3, 1, z+d-3, "chest[facing=north]")
        put(x+w-3, 1, z+d-5, "chest[facing=north]")
        put(x+w-3, 1, z+2, "crafting_table")
    else:
        put(x+w-3, 1, z+d-3, "white_bed[facing=north,part=foot]")
        put(x+w-3, 1, z+d-4, "white_bed[facing=north,part=head]")


def main():
    blocks.clear()
    box(0, 0, 0, 44, 0, 38, "grass_block")
    # T-shaped compact paths, with an open exit north toward the journey.
    box(21, 0, 0, 23, 0, 34, "dirt_path")
    box(7, 0, 18, 37, 0, 20, "dirt_path")
    house(4, 22, 13, 13, player=True)
    house(5, 4, 9, 10)
    house(30, 5, 9, 10)
    box(9, 0, 20, 11, 0, 21, "dirt_path")
    box(8, 0, 14, 10, 0, 18, "dirt_path")
    box(33, 0, 15, 35, 0, 18, "dirt_path")
    # Empty expansion plot east of the player's home, no advanced machinery.
    for x,z in ((19,26),(19,33),(29,26),(29,33)):
        put(x,1,z,"oak_fence")
    # Small well beside the central road.
    box(25,0,17,29,0,21,"cobblestone")
    box(26,0,18,28,0,20,"water")
    for x,z in ((25,17),(25,21),(29,17),(29,21)):
        box(x,1,z,x,3,z,"oak_fence")
    box(25,4,17,29,4,21,"spruce_slab[type=bottom]")
    # Shared farm: players still explore and mine for all chapter progression.
    box(33,0,25,41,0,33,"farmland[moisture=7]")
    box(37,0,25,37,0,33,"water")
    for x in range(33,42):
        for z in range(25,34):
            if x != 37:
                put(x,1,z,"wheat[age=3]")
    put(32,1,25,"composter[level=0]")
    put(31,1,26,"barrel[facing=up]")
    for x,z in ((19,4),(25,11),(19,18),(25,29),(3,21),(42,23)):
        box(x,1,z,x,3,z,"oak_fence")
        put(x,4,z,"lantern")
    palette = sorted(set(blocks.values()))
    doc = {
        "schema":1, "size":[45,12,39], "home":[10,1,28], "bed":[6,1,32],
        "villagers":[[9,1,9],[34,1,10],[32,1,27]],
        "palette":palette,
        "blocks":[[x,y,z,palette.index(s)] for (x,y,z),s in sorted(blocks.items(),key=lambda v:(v[0][1],v[0][2],v[0][0]))],
    }
    dest = MODULE / "src/main/resources/data/world_progression_core/settlement/starter.json"
    dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(doc,ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    build_modules()
    # A plan illustration, deliberately labelled as a blueprint rather than a game screenshot.
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="900" height="840" viewBox="0 0 900 840">',
           '<rect width="900" height="840" fill="#eef1df"/>',
           '<text x="35" y="38" font-size="24" font-family="Microsoft YaHei">启程聚落 · 建筑平面草图（非游戏截图）</text>']
    colors = {"grass_block":"#aac782","dirt_path":"#cab27a","spruce_planks":"#907047","oak_planks":"#c6a575","water":"#5faac5","farmland":"#a37a54","cobblestone":"#8c948e"}
    for z in range(39):
        for x in range(45):
            state=blocks.get((x,0,z),"minecraft:grass_block").split(":")[1].split("[")[0]
            c=colors.get(state,"#cab888")
            svg.append(f'<rect x="{45+x*18}" y="{80+z*18}" width="18" height="18" fill="{c}"/>')
    for x,z,w,d,label in ((4,22,13,13,"玩家之家"),(5,4,9,10,"村民之家"),(30,5,9,10,"村民之家"),(19,26,11,8,"扩建空地"),(32,24,10,11,"公共农田")):
        svg.append(f'<rect x="{45+x*18}" y="{80+z*18}" width="{w*18}" height="{d*18}" fill="none" stroke="#55483c" stroke-width="3"/>')
        svg.append(f'<text x="{45+(x+w/2)*18}" y="{80+(z+d/2)*18}" text-anchor="middle" font-size="18" font-family="Microsoft YaHei">{label}</text>')
    svg.append('<text x="465" y="99" font-size="16" font-family="Microsoft YaHei">↑ 出发</text>')
    svg.append('<text x="40" y="815" font-size="17" font-family="Microsoft YaHei">玩家之家：床、空箱、工作台；门两侧按钮。无额外赠送物资。</text></svg>')
    preview = ROOT / "docs/design/chapter1-settlement-plan.svg"
    preview.write_text("\n".join(svg)+"\n",encoding="utf-8")
    print(f"Blueprint: {len(blocks)} blocks, {len(palette)} states, 45 x 39 footprint")


def build_modules():
    """Extract separate terraces from the existing authored buildings; compact house fallback."""
    modules=[]
    def extract(name,x,z,w,d,entry,desired,villagers=(),home=None,bed=None):
        selected={(bx-x,y,bz-z):s for (bx,y,bz),s in blocks.items() if x<=bx<x+w and z<=bz<z+d}
        states=sorted(set(selected.values()))
        part={"id":name,"size":[w,max(p[1] for p in selected)+1,d],"entry":entry,"desired":desired,
              "villagers":list(villagers),"palette":states,
              "blocks":[[*p,states.index(s)] for p,s in sorted(selected.items(),key=lambda v:(v[0][1],v[0][2],v[0][0]))]}
        if home is not None:part["home"]=home
        if bed is not None:part["bed"]=bed
        modules.append(part)
    extract("player_home",3,21,15,15,[7,0,-1],[0,0],home=[7,1,7],bed=[3,1,11])
    extract("villager_west",4,3,11,12,[5,0,-1],[-18,-19],villagers=[[5,1,6]])
    extract("villager_east",29,4,11,12,[5,0,-1],[21,-18],villagers=[[5,1,6],[6,1,5]])
    extract("well",25,17,5,5,[2,0,-1],[3,-13])
    extract("farm",31,23,12,13,[6,0,-1],[23,17])
    extract("expansion_plot",19,26,11,8,[5,0,-1],[18,2])
    old=blocks.copy()
    blocks.clear(); box(0,0,0,8,0,10,"grass_block"); house(1,1,7,9,player=True)
    extract("player_compact",0,0,9,11,[4,0,-1],[0,0],home=[4,1,5],bed=[3,1,7])
    blocks.clear(); blocks.update(old)
    for part in modules:
        w,_,d=part["size"]
        lookup={tuple(b[:3]):part["palette"][b[3]] for b in part["blocks"]}
        for point in part.get("villagers",[])+[part[k] for k in ("home","bed") if k in part]:
            assert 0<=point[0]<w and 0<=point[2]<d
        for point in part["villagers"]:
            assert tuple(point) not in lookup and tuple([point[0],point[1]+1,point[2]]) not in lookup
        if "home" in part:
            assert tuple(part["home"]) not in lookup and tuple([part["home"][0],2,part["home"][2]]) not in lookup
            assert lookup[tuple(part["bed"])].startswith("minecraft:red_bed")
            assert sum(s.startswith("minecraft:chest") for s in lookup.values())==2
    dest=MODULE/"src/main/resources/data/world_progression_core/settlement/modules.json"
    dest.write_text(json.dumps({"schema":1,"modules":modules},ensure_ascii=False,separators=(",",":"))+"\n",encoding="utf-8")
    draw_modular_plan(modules)
    print("Independent templates: 6 village parts and 1 compact player-house fallback")


def draw_modular_plan(modules):
    # Distances/grades are adapted in game. This is only a layout reference.
    names={"player_home":"玩家房屋 · H0","villager_west":"村民房屋 · H1","villager_east":"村民房屋 · H2",
           "well":"水井 · H3","farm":"公共农田 · H4","expansion_plot":"扩建地块 · H5"}
    parts=[m for m in modules if m["id"] in names]
    svg=['<svg xmlns="http://www.w3.org/2000/svg" width="900" height="700" viewBox="0 0 900 700">',
         '<rect width="900" height="700" fill="#eef1df"/>',
         '<text x="35" y="40" font-size="24" font-family="Microsoft YaHei">分栋聚落 · 各部件独立定高（布局示意，非游戏截图）</text>']
    def pixel(x,z):return 270+x*12,330+z*10
    for m in parts:
        x,z=m['desired'];w,_,d=m['size']; px,pz=pixel(x,z)
        color='#c6a575' if 'home' in m['id'] or 'villager' in m['id'] else '#a7c78e'
        svg.append(f'<rect x="{px}" y="{pz}" width="{w*12}" height="{d*10}" fill="{color}" stroke="#55483c" stroke-width="3"/>')
        svg.append(f'<text x="{px+w*6}" y="{pz+d*5}" text-anchor="middle" font-size="16" font-family="Microsoft YaHei">{names[m["id"]]}</text>')
    svg.append('<text x="35" y="625" font-size="18" font-family="Microsoft YaHei">H0–H5 分别由当地地形决定，连接路以台阶/短桥适应高差。</text>')
    svg.append('<text x="35" y="657" font-size="17" font-family="Microsoft YaHei">优先玩家房屋，陡坡采用小屋后备；床、两只空箱、工作台保留。</text></svg>')
    (ROOT/"docs/design/chapter1-modular-settlement-plan.svg").write_text('\n'.join(svg)+'\n',encoding='utf-8')


if __name__ == "__main__":
    main()
