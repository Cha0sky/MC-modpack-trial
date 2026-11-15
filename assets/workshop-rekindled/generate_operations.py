"""Authored abandoned workshop; exported through Minecraft Builder, then imported as vanilla NBT."""
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent
ops=[]
def box(x,y,z,xx,yy,zz,block):
    ops.append({'op':'cuboid','start':[x,y,z],'end':[xx,yy,zz],'block':block})
def put(x,y,z,block):
    ops.append({'op':'block','pos':[x,y,z],'block':block})

# A broad but shallow foundation lets vanilla beard terrain adapt the footprint.
box(0,0,0,23,0,19,'coarse_dirt')
box(2,0,3,15,0,18,'stone_bricks')
box(14,0,6,23,0,16,'stone_bricks')
box(4,0,5,13,0,16,'spruce_planks')
box(16,0,8,21,0,14,'polished_andesite')
box(6,0,0,11,0,4,'gravel')
for x,z in [(1,5),(22,2),(3,18),(16,18),(1,13),(21,17)]:
    put(x,0,z,'moss_block')

# Main workshop: exposed timber, a second-storey archive and a copper roof.
box(3,1,4,14,8,17,'stone_bricks')
box(4,1,5,13,8,16,'air')
box(4,5,5,13,5,16,'spruce_planks')
for x in (3,14):
    for z in (4,8,12,17):
        box(x,1,z,x,8,z,'stripped_spruce_log[axis=y]')
for z in (4,17):
    box(3,5,z,14,5,z,'stripped_spruce_log[axis=x]')
    box(3,8,z,14,8,z,'stripped_spruce_log[axis=x]')
for x in (3,14):
    box(x,5,4,x,5,17,'stripped_spruce_log[axis=z]')
    box(x,8,4,x,8,17,'stripped_spruce_log[axis=z]')
for x in (5,11):
    for y in (2,6):
        box(x,y,4,x+1,y+1,4,'air')
        box(x,y,5,x+1,y+1,5,'light_gray_stained_glass_pane')
        box(x,y-1,3,x+1,y-1,3,'spruce_stairs[facing=north,half=top]')
        box(x,y,17,x+1,y+1,17,'light_gray_stained_glass_pane')
for z in (6,10,14):
    box(3,2,z,3,3,z+1,'light_gray_stained_glass_pane')
    put(2,1,z,'spruce_stairs[facing=west,half=top]')
box(8,1,4,9,3,5,'air')
box(6,0,1,11,0,3,'stone_bricks')
for x in (6,11):
    box(x,1,1,x,3,1,'stripped_spruce_log[axis=y]')
box(6,4,1,11,4,3,'waxed_weathered_cut_copper_slab[type=top]')
put(7,3,2,'lantern[hanging=true]')

# Pitched roof, two-wide ridge; one damaged corner exposes old rafters.
for i in range(6):
    x0,x1=2+i,15-i
    box(x0,9+i,3,x0,9+i,18,'waxed_weathered_cut_copper_stairs[facing=east]')
    box(x1,9+i,3,x1,9+i,18,'waxed_weathered_cut_copper_stairs[facing=west]')
    for z in (4,17):
        if x0+1<=x1-1: box(x0+1,9+i,z,x1-1,9+i,z,'spruce_planks')
box(8,15,3,9,15,18,'waxed_weathered_cut_copper')
box(8,16,3,9,16,18,'waxed_weathered_cut_copper_slab')
box(2,9,14,4,11,16,'air')
box(4,10,14,4,10,16,'stripped_spruce_log[axis=z]')
put(4,11,16,'cobweb')

# Lower east machinery wing with a walkable signal terrace.
box(15,1,7,22,4,15,'polished_andesite')
box(16,1,8,21,4,14,'air')
for x in (15,22):
    for z in (7,11,15):
        box(x,1,z,x,4,z,'stripped_spruce_log[axis=y]')
box(14,5,6,23,5,16,'spruce_planks')
for z in (6,16): box(14,5,z,23,5,z,'waxed_weathered_cut_copper')
box(23,5,6,23,5,16,'waxed_weathered_cut_copper')
box(14,1,9,15,3,11,'air')
box(14,6,8,14,8,10,'air')
for x in range(15,24):
    put(x,6,16,'spruce_fence')
    if x<22: put(x,6,6,'spruce_fence')
for z in range(8,16): put(23,6,z,'spruce_fence')
box(22,2,9,22,3,10,'iron_bars')
box(22,2,13,22,3,14,'iron_bars')
for x in (16,20): put(x,1,15,'waxed_weathered_cut_copper')

# A genuine internal stair plus the alternate exterior approach.
box(4,5,12,5,5,16,'air')
for i in range(5):
    box(4,1+i,12+i,5,1+i,12+i,'spruce_stairs[facing=south]')
    if i: box(4,1,12+i,5,i,12+i,'spruce_planks')
for i in range(5):
    box(22,1+i,2+i,23,1+i,2+i,'stone_brick_stairs[facing=south]')
    if i: box(22,1,2+i,23,i,2+i,'mossy_stone_bricks')
box(2,1,11,3,3,12,'air')
put(2,0,11,'mossy_cobblestone')

# Work benches and readable interior zones; no chapter-reward chest.
box(7,1,16,10,1,16,'spruce_slab[type=top]')
put(7,1,15,'crafting_table')
put(10,1,15,'stonecutter[facing=north]')
put(11,1,16,'anvil[facing=north]')
box(7,6,15,10,7,16,'bookshelf')
box(10,6,6,12,6,6,'spruce_slab[type=top]')
put(11,7,6,'lantern')
put(12,6,7,'lectern[facing=south]')
put(7,6,8,'barrel[facing=up]')
put(6,2,7,'cobweb')
put(18,1,14,'furnace[facing=north]')
put(20,1,14,'blast_furnace[facing=north]')
box(21,1,12,21,8,12,'bricks')
put(21,9,12,'campfire[lit=false]')
box(16,1,8,16,2,8,'waxed_weathered_cut_copper')
box(16,1,12,16,2,12,'waxed_weathered_cut_copper')

# Stand-ins are replaced by registered interactive blocks during NBT conversion.
put(19,1,10,'chiseled_stone_bricks')
put(19,2,10,'orange_wool')
put(12,1,14,'chiseled_stone_bricks')
put(12,2,14,'cyan_wool')
put(19,6,7,'purple_wool')
put(10,1,8,'chiseled_stone_bricks')
put(10,2,8,'yellow_wool')
# The north connection port of the signal unit must remain reachable.
put(19,6,6,'air')
put(10,3,8,'oak_sign[rotation=8]')
put(19,3,10,'oak_sign[rotation=8]')
put(12,3,14,'oak_sign[rotation=12]')
put(18,6,8,'oak_sign[rotation=0]')

# One small, breakable guard source in the entrance alcove, with an open route around it.
put(5,1,7,'spawner')
box(4,1,6,4,2,8,'iron_bars')
put(5,1,8,'cobweb')
for x,y,z in [(10,4,11),(12,4,6),(17,4,10),(20,4,14),(7,8,10)]:
    put(x,y,z,'lantern[hanging=true]')
for x,y,z in [(3,1,6),(14,2,14),(7,1,17),(5,4,4),(11,7,17),(22,1,13),(3,7,15)]:
    put(x,y,z,'cracked_stone_bricks')
for x,z in [(1,7),(1,16),(16,18),(20,18)]:
    put(x,1,z,'oak_leaves[persistent=true]')

# Rear and west faces need real relief, not just timber flush with the masonry.
for z in (4,8,16): box(2,1,z,2,8,z,'stripped_spruce_log[axis=y]')
for x in (3,8,14): box(x,1,18,x,8,18,'stripped_spruce_log[axis=y]')
for x in (6,11):
    put(x,4,18,'spruce_fence')
    put(x,3,18,'lantern[hanging=true]')

layout={
    'structure_id':'world_progression_core:abandoned_workshop','size':[24,17,20],
    'entrance':[8,1,3], 'side_entrance':[2,1,11],
    'relays':[
        {'kind':'power','pos':[19,2,10],'facing':'south','connection':[19,2,11]},
        {'kind':'direction','pos':[12,2,14],'facing':'west','connection':[11,2,14]},
        {'kind':'signal','pos':[19,6,7],'facing':'north','connection':[19,6,6]}],
    'console':{'pos':[10,2,8],'facing':'south'},
    'supply_barrel':[7,6,8], 'guard_spawner':[5,1,7],
    'signs':[
        {'pos':[10,3,8],'lines':['旧工坊控制台','三处继电器就绪后','在此重新启动','普通建筑可以修复']},
        {'pos':[19,3,10],'lines':['动力继电器','彩色正面接轴','输入至少 8 RPM','再用调谐器校准']},
        {'pos':[12,3,14],'lines':['传动继电器','彩色正面接轴','正转至少 8 RPM','齿轮啮合可反向']},
        {'pos':[18,6,8],'lines':['信号继电器','彩色正面接轴','输入至少 8 RPM','用调谐器持续校准']}
    ]
}
HERE.mkdir(parents=True,exist_ok=True)
(HERE/'operations.json').write_text(json.dumps({'name':'abandoned-workshop','description':'Wood, stone and weathered copper workshop with a powered restoration quest.','operations':ops},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(HERE/'layout.json').write_text(json.dumps(layout,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Authored workshop: {len(ops)} shape/detail operations; {layout["size"]}')
