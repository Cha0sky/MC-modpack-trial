"""Create a bounded empty GameTest template using vanilla NBT, without dependencies."""
import gzip
import struct
from pathlib import Path


def utf(value):
    b=value.encode("utf-8")
    return struct.pack(">H",len(b))+b


def tag(kind,name,payload):
    return bytes([kind])+utf(name)+payload


root=Path(__file__).resolve().parents[1]
dest=root/"modules/world-progression-core/src/gametest/resources/data/world_progression_core/structure/chapter_arena.nbt"
size=bytes([3])+struct.pack(">i",3)+struct.pack(">iii",48,20,44)
palette=bytes([10])+struct.pack(">i",1)+tag(8,"Name",utf("minecraft:air"))+b"\0"
empty=bytes([10])+struct.pack(">i",0)
data=tag(10,"",tag(3,"DataVersion",struct.pack(">i",3955))+tag(9,"size",size)
         +tag(9,"palette",palette)+tag(9,"blocks",empty)+tag(9,"entities",empty)+b"\0")
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_bytes(gzip.compress(data,mtime=0))
print("GameTest arena generated: 48 x 20 x 44, no production resources modified")
