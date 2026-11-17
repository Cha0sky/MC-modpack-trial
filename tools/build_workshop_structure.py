"""Convert the authored Builder schematic to a vanilla single-piece jigsaw structure.

Does not load Minecraft or touch any world. Only workshop-owned resources are written.
"""
from __future__ import annotations
import gzip
import json
import struct
from pathlib import Path
from import_settlement_schem import NbtReader, decode_varints, state_parts

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'assets/workshop-rekindled'
RES=ROOT/'modules/world-progression-core/src/main/resources/data/world_progression_core'

def tag(kind,value): return (kind,value)
def integer(value): return tag(3,value)
def short(value): return tag(2,value)
def string(value): return tag(8,value)
def compound(value): return tag(10,value)
def listing(kind,values): return tag(9,(kind,values))
def text(value):
    raw=value.encode('utf-8')
    return struct.pack('>H',len(raw))+raw
def payload(kind,value):
    if kind in (1,2,3,4,5,6): return struct.pack('>'+{1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind],value)
    if kind==8: return text(value)
    if kind==9:
        subtype,values=value
        return struct.pack('>Bi',subtype,len(values))+b''.join(payload(subtype,item) for item in values)
    if kind==10:
        return b''.join(bytes([subtype])+text(name)+payload(subtype,item)
                        for name,(subtype,item) in value.items())+b'\x00'
    raise ValueError(f'Unsupported output tag {kind}')
def write_json(relative,value):
    dest=RES/relative; dest.parent.mkdir(parents=True,exist_ok=True)
    dest.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    layout=json.loads((ASSETS/'layout.json').read_text('utf-8'))
    doc=NbtReader(gzip.decompress((ASSETS/'abandoned-workshop.schem').read_bytes())).root()
    doc=doc.get('Schematic',doc)
    width,height,depth=(doc[n] for n in ('Width','Height','Length'))
    assert [width,height,depth]==layout['size']
    source=doc if doc['Version']==2 else doc['Blocks']
    old_palette={index:name for name,index in source['Palette'].items()}
    indices=decode_varints(source['BlockData' if doc['Version']==2 else 'Data'],width*height*depth,old_palette)
    states={(i%width,i//(width*depth),i//width%depth):old_palette[index] for i,index in enumerate(indices)}
    for relay in layout['relays']:
        pos=tuple(relay['pos'])
        states[pos]=f'world_progression_core:workshop_relay[kind={relay["kind"]},facing={relay["facing"]},calibrated=false]'
        assert states[tuple(relay['connection'])]=='minecraft:air','Relay connection must remain empty'
    console=layout['console']
    states[tuple(console['pos'])]=f'world_progression_core:workshop_console[facing={console["facing"]},active=false]'

    block_nbt={}
    for sign in layout['signs']:
        block_nbt[tuple(sign['pos'])]={
            'id':string('minecraft:sign'), 'is_waxed':tag(1,0),
            'front_text':compound({'messages':listing(8,[json.dumps({'text':line},ensure_ascii=False) for line in sign['lines']]),
                                   'color':string('black'),'has_glowing_text':tag(1,0)}),
            'back_text':compound({'messages':listing(8,['{"text":""}']*4),'color':string('black'),'has_glowing_text':tag(1,0)})}
    block_nbt[tuple(layout['supply_barrel'])]={
        'id':string('minecraft:barrel'),'LootTable':string('minecraft:chests/village/village_toolsmith')}
    block_nbt[tuple(layout['guard_spawner'])]={
        'id':string('minecraft:mob_spawner'),'Delay':short(100),'MinSpawnDelay':short(1200),
        'MaxSpawnDelay':short(2400),'SpawnCount':short(1),'MaxNearbyEntities':short(2),
        'RequiredPlayerRange':short(8),'SpawnRange':short(2),
        'SpawnData':compound({'entity':compound({'id':string('minecraft:skeleton')})})}

    palette=[]; seen={}; blocks=[]
    for pos,state in states.items():
        if state not in seen:
            name,properties=state_parts(state)
            entry={'Name':string(name)}
            if properties: entry['Properties']=compound({key:string(value) for key,value in properties.items()})
            seen[state]=len(palette);palette.append(entry)
        entry={'pos':listing(3,list(pos)),'state':integer(seen[state])}
        if pos in block_nbt: entry['nbt']=compound(block_nbt[pos])
        blocks.append(entry)
    root={'DataVersion':integer(3955),'size':listing(3,[width,height,depth]),
          'palette':listing(10,palette),'blocks':listing(10,blocks),'entities':listing(10,[])}
    target=RES/'structure/abandoned_workshop.nbt';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(gzip.compress(b'\x0a\x00\x00'+payload(10,root),mtime=0))

    write_json('worldgen/template_pool/abandoned_workshop.json',{
        'name':'world_progression_core:abandoned_workshop','fallback':'minecraft:empty',
        'elements':[{'weight':1,'element':{'element_type':'minecraft:single_pool_element',
            'location':'world_progression_core:abandoned_workshop','processors':{'processors':[]},'projection':'rigid'}}]})
    write_json('worldgen/structure/abandoned_workshop.json',{
        'type':'minecraft:jigsaw','biomes':'#world_progression_core:has_structure/workshop',
        # In 1.21.1 the start piece is only added to the saved structure when depth > 0.
        # This template has no jigsaw connectors, so depth 1 still places just one piece.
        'start_pool':'world_progression_core:abandoned_workshop','size':1,
        'start_height':{'absolute':0},'project_start_to_heightmap':'WORLD_SURFACE_WG',
        'max_distance_from_center':32,'use_expansion_hack':False,
        'terrain_adaptation':'beard_thin','step':'surface_structures','spawn_overrides':{}})
    write_json('worldgen/structure_set/abandoned_workshop.json',{
        'structures':[{'structure':'world_progression_core:abandoned_workshop','weight':1}],
        'placement':{'type':'minecraft:random_spread','salt':20261116,'spacing':24,'separation':10,
                     'exclusion_zone':{'other_set':'minecraft:villages','chunk_count':4}}})
    write_json('tags/worldgen/biome/has_structure/workshop.json',{
        'replace':False,'values':['minecraft:plains','minecraft:sunflower_plains','minecraft:forest',
                                'minecraft:flower_forest','minecraft:birch_forest','minecraft:old_growth_birch_forest']})

    # One resource check: imported shape and all active devices must survive conversion.
    readback=NbtReader(gzip.decompress(target.read_bytes())).root()
    assert readback['size']==layout['size'] and len(readback['blocks'])==width*height*depth
    names=[entry['Name'] for entry in readback['palette']]
    devices=[b for b in readback['blocks'] if names[b['state']].startswith('world_progression_core:workshop_')]
    assert len(devices)==4 and all('nbt' not in b for b in devices)
    report={'structure_id':layout['structure_id'],'size':layout['size'],
            'blocks_with_air':len(blocks),'palette_states':len(palette),
            'device_positions':[b['pos'] for b in devices],
            'source':'assets/workshop-rekindled/abandoned-workshop.schem',
            'runtime':'vanilla jigsaw template in new chunks only','game_launched':False}
    (ASSETS/'build-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__': main()
