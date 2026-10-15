"""Authoring source for Emberstead; run here to reproduce operations/layout JSON."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OPS = []
OX = OZ = 0

def box(x0,y0,z0,x1,y1,z1,block):
    OPS.append(dict(op='cuboid', start=[OX+x0,y0,OZ+z0],end=[OX+x1,y1,OZ+z1],block=block))

def block(x,y,z,b):
    OPS.append(dict(op='block',pos=[OX+x,y,OZ+z],block=b))

def beam(x0,y,z0,x1,z1):
    box(x0,y,z0,x1,y,z1,'stripped_spruce_log[axis='+('x' if x1!=x0 else 'z')+']')

def door(x,z):
    for y,half in [(1,'lower'),(2,'upper')]:
        block(x,y,z,f'spruce_door[facing=north,half={half},hinge=left,open=false,powered=false]')

def bed(x,z,color):
    block(x,1,z,f'{color}_bed[facing=south,part=foot,occupied=false]')
    block(x,1,z+1,f'{color}_bed[facing=south,part=head,occupied=false]')

def roof_x(x0,x1,z0,z1,y,mat='deepslate_tile',cap='deepslate_tiles'):
    # Ridge runs north/south. Stair high halves face toward the ridge.
    width=x1-x0+1
    for i in range(width//2):
        box(x0+i,y+i,z0,x0+i,y+i,z1,f'{mat}_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
        box(x1-i,y+i,z0,x1-i,y+i,z1,f'{mat}_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
        # triangular front/back gable fills below the roof
        if i:
            box(x0+i,y,z0+1,x1-i,y+i-1,z0+1,'spruce_planks')
            box(x0+i,y,z1-1,x1-i,y+i-1,z1-1,'spruce_planks')
    mid=x0+width//2
    box(mid,y+width//2,z0,mid,y+width//2,z1,cap)
    box(mid,y+width//2+1,z0,mid,y+width//2+1,z1,'deepslate_tile_slab[type=bottom,waterlogged=false]')

def roof_z(x0,x1,z0,z1,y):
    width=z1-z0+1
    for i in range(width//2):
        box(x0,y+i,z0+i,x1,y+i,z0+i,'waxed_weathered_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
        box(x0,y+i,z1-i,x1,y+i,z1-i,'waxed_weathered_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
    if width%2:
        box(x0,y+width//2,z0+width//2,x1,y+width//2,z0+width//2,'waxed_weathered_cut_copper_slab[type=bottom,waterlogged=false]')

def shell(x0,z0,x1,z1):
    box(x0,0,z0,x1,0,z1,'spruce_planks')
    box(x0,1,z0,x1,4,z1,'calcite')
    box(x0+1,1,z0+1,x1-1,4,z1-1,'air')
    for x,z in [(x0,z0),(x1,z0),(x0,z1),(x1,z1)]:
        box(x,1,z,x,4,z,'stripped_spruce_log[axis=y]')
    beam(x0,4,z0,x1,z0); beam(x0,4,z1,x1,z1)
    beam(x0,4,z0,x0,z1); beam(x1,4,z0,x1,z1)

def window_front(x,z):
    box(x,2,z,x+1,3,z,'glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]')
    box(x,1,z-1,x+1,1,z-1,'spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
    block(x-1,2,z-1,'spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]')
    block(x+2,2,z-1,'spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]')

def window_side(x,z):
    box(x,2,z,x,3,z+1,'glass_pane[north=true,east=false,south=true,west=false,waterlogged=false]')

def porch(x0,x1,z0,z1,y=4):
    box(x0,0,z0,x1,0,z1,'spruce_planks')
    for x in [x0,x1]:
        box(x,1,z0,x,y-1,z0,'spruce_fence[north=false,east=false,south=false,west=false,waterlogged=false]')
    box(x0-1,y,z0-1,x1+1,y,z1,'waxed_weathered_cut_copper_slab[type=bottom,waterlogged=false]')
    block(x0,y-1,z0+1,'lantern[hanging=true,waterlogged=false]')

def planter(x,z):
    block(x,1,z,'spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]')
    block(x,2,z,'potted_fern')

def home():
    box(1,0,2,13,0,15,'cobbled_deepslate')
    box(0,0,0,14,0,1,'moss_block')
    box(7,0,0,9,0,5,'mossy_cobblestone')
    shell(3,6,11,14)
    shell(1,3,6,8)
    box(4,1,6,6,3,7,'air')
    box(5,1,7,7,3,7,'air')
    roof_z(0,7,2,9,5)
    roof_x(2,12,5,15,5)
    # A 4-block deep, asymmetric covered porch breaks the main mass.
    porch(7,11,2,5)
    box(8,1,6,8,3,6,'air'); door(8,6)
    window_front(3,3)
    box(9,2,6,10,3,6,'glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]')
    window_side(11,9); window_side(3,10)
    box(7,2,14,9,3,14,'glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]')
    bed(5,11,'red')
    block(10,1,12,'chest[facing=west,type=single,waterlogged=false]')
    block(10,1,9,'chest[facing=west,type=single,waterlogged=false]')
    block(10,1,13,'crafting_table')
    block(3,1,4,'spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    block(3,1,6,'spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    block(4,1,5,'spruce_fence[north=false,east=false,south=false,west=false,waterlogged=false]')
    block(4,2,5,'spruce_pressure_plate[powered=false]')
    beam(3,4,8,11,8); beam(3,4,12,11,12); beam(1,4,5,6,5)
    for x,z in [(7,8),(7,12),(3,5)]: block(x,3,z,'lantern[hanging=true,waterlogged=false]')
    # Sealed chimney: no campfire, fire or loot; decorative copper hood.
    box(10,1,13,10,11,13,'bricks')
    block(10,12,13,'brick_wall[up=true,north=none,east=none,south=none,west=none,waterlogged=false]')
    block(10,13,13,'waxed_weathered_cut_copper_slab[type=bottom,waterlogged=false]')
    block(10,1,13,'crafting_table')
    for x,z in [(1,12),(12,7),(12,14),(0,4)]:
        block(x,1,z,'azalea_leaves[persistent=true,waterlogged=false]')
    planter(5,2); planter(12,4)
    block(12,1,3,'lantern[hanging=false,waterlogged=false]')
    block(3,0,15,'mossy_cobblestone'); block(10,0,2,'mossy_cobblestone')

def cottage(east=False):
    box(1,0,2,9,0,11,'cobbled_deepslate')
    box(4,0,0,6,0,3,'mossy_cobblestone')
    shell(2,4,8,10)
    roof_x(1,9,3,11,5)
    porch(3,7,1,3)
    box(5,1,4,5,3,4,'air'); door(5,4)
    window_front(2,4); window_side(8,6)
    box(4,2,10,6,3,10,'glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]')
    bed(3,8,'yellow' if east else 'white'); bed(7,8,'white')
    block(3,1,5,'spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]')
    block(7,1,5,'spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]')
    beam(2,4,7,8,7); block(5,3,7,'lantern[hanging=true,waterlogged=false]')
    box(8,5,9,8,9,9,'bricks'); block(8,10,9,'brick_wall[up=true,north=none,east=none,south=none,west=none,waterlogged=false]')
    for x,z in [(0,7),(9,10),(1,11)]: block(x,1,z,'azalea_leaves[persistent=true,waterlogged=false]')
    planter(8,2)
    block(1,1,3,'lantern[hanging=false,waterlogged=false]')
    if east:
        box(0,0,5,0,0,10,'moss_block')
        block(0,1,5,'potted_dandelion')

def well():
    box(0,0,0,6,0,6,'mossy_cobblestone')
    box(1,1,2,5,1,5,'stone_bricks')
    box(2,1,3,4,1,4,'water[level=0]')
    for x in [1,5]:
        box(x,2,3,x,4,3,'stripped_spruce_log[axis=y]')
    roof_z(0,6,1,6,5)
    box(0,1,1,0,1,5,'azalea_leaves[persistent=true,waterlogged=false]')
    beam(1,5,3,5,3); block(3,4,3,'lantern[hanging=true,waterlogged=false]')
    block(6,1,1,'lantern[hanging=false,waterlogged=false]')

def farm():
    box(0,0,0,12,0,14,'dirt')
    box(0,0,0,12,0,0,'mossy_cobblestone') # stable (0,0,0) normalization anchor
    box(1,0,2,11,0,12,'farmland[moisture=7]')
    box(6,0,2,6,0,12,'water[level=0]')
    box(0,0,1,0,0,14,'mossy_cobblestone'); box(12,0,1,12,0,14,'mossy_cobblestone')
    box(0,0,14,12,0,14,'mossy_cobblestone')
    for x in range(1,12):
        if x==6: continue
        box(x,1,2,x,1,12,'wheat[age=3]' if x<6 else 'carrots[age=3]')
    for x in [0,12]: box(x,1,1,x,1,13,'spruce_fence[north=true,east=false,south=true,west=false,waterlogged=false]')
    for z in [0,14]:
        box(0,1,z,5,1,z,'spruce_fence[north=false,east=true,south=false,west=true,waterlogged=false]')
        box(7,1,z,12,1,z,'spruce_fence[north=false,east=true,south=false,west=true,waterlogged=false]')
    box(5,0,0,7,0,1,'mossy_cobblestone')
    box(6,1,2,6,1,4,'spruce_slab[type=bottom,waterlogged=false]')
    for x,z in [(0,0),(12,0),(0,14),(12,14)]:
        block(x,2,z,'lantern[hanging=false,waterlogged=false]')
    block(11,1,13,'composter[level=0]')

def expansion():
    box(0,0,0,10,0,8,'grass_block[snowy=false]')
    box(4,0,0,6,0,6,'coarse_dirt')
    for x in [0,10]: box(x,1,1,x,1,8,'spruce_fence[north=true,east=false,south=true,west=false,waterlogged=false]')
    box(0,1,8,10,1,8,'spruce_fence[north=false,east=true,south=false,west=true,waterlogged=false]')
    for x,z in [(0,1),(10,1)]:
        box(x,1,z,x,2,z,'stripped_spruce_log[axis=y]')
        block(x,3,z,'lantern[hanging=false,waterlogged=false]')
    for x,z in [(1,7),(9,7)]: block(x,1,z,'azalea_leaves[persistent=true,waterlogged=false]')

MODULES = [
 dict(id='player_home',bounds=[14,23,15,17],entry=[8,0,-1],desired=[0,0],home=[8,1,10],bed=[5,1,11],villagers=[]),
 dict(id='villager_west',bounds=[0,17,11,13],entry=[5,0,-1],desired=[-14,-6],villagers=[[4,1,7],[6,1,7]]),
 dict(id='villager_east',bounds=[32,20,11,13],entry=[5,0,-1],desired=[18,-3],villagers=[[4,1,7],[6,1,7]]),
 dict(id='well',bounds=[18,8,7,7],entry=[3,0,-1],desired=[4,-15],villagers=[]),
 dict(id='farm',bounds=[0,0,13,15],entry=[6,0,-1],desired=[-14,-23],villagers=[]),
 dict(id='expansion_plot',bounds=[31,4,11,9],entry=[5,0,-1],desired=[17,-19],villagers=[]),
]
for m,fn in zip(MODULES,[home,cottage,lambda:cottage(True),well,farm,expansion]):
    OX,OZ,_,_=m['bounds']; fn()
OX=OZ=0
# One visual revision: break the long main side wall with a timber pier.
box(25,1,35,25,4,35,'stripped_spruce_log[axis=y]')
# Ground-scatter corner markers retain the layout's complete 43 x 40 extent.
block(42,0,32,'moss_block'); block(28,0,39,'moss_block')

ROOT.mkdir(parents=True,exist_ok=True)
(ROOT/'operations.json').write_text(json.dumps(dict(name='emberstead',description='Emberstead: six warm living spaces. Minecraft 1.21.1 modular starter settlement.',operations=OPS),ensure_ascii=True,indent=2)+'\n',encoding='utf-8')
(ROOT/'layout.json').write_text(json.dumps(dict(schema=1,schematic='emberstead.schem',modules=MODULES),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'{len(OPS)} operations; 43 x 14 x 40 bounding box; six modules')
