#!/usr/bin/env python3
"""Adds the Patchouli guide-book item icon (16x16)."""
import os, sys
from PIL import Image
K=(26,22,32,255); G=(232,181,58,255); H=(255,236,170,255)
B=(38,62,122,255); Y=(120,226,255,255)
BOOK = [
    "................",
    "................",
    "..KKKKKKKKKKK...",
    ".KGGGBBBBBBBK...",
    ".KGGGBBBBBBBK...",
    ".KGGGBBBBBBBK...",
    ".KGGGBBYYYBBK...",
    ".KGGGBYHHHYBK...",
    ".KGGGBYHHHYBK...",
    ".KGGGBBYYYBBK...",
    ".KGGGBBBBBBBK...",
    ".KGGGBBBBBBBK...",
    ".KGGGBBBBBBBK...",
    "..KKKKKKKKKKK...",
    "................",
    "................",
]
out_dir = sys.argv[1]
os.makedirs(out_dir, exist_ok=True)
im = Image.new("RGBA",(16,16),(0,0,0,0))
pal = {'K':K,'G':G,'H':H,'B':B,'Y':Y}
for y,row in enumerate(BOOK):
    assert len(row)==16, (y,len(row))
    for x,ch in enumerate(row):
        if ch!='.': im.putpixel((x,y), pal[ch])
p = os.path.join(out_dir,"guide_book.png")
im.save(p); print("wrote", p)
