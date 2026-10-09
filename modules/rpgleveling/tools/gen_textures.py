#!/usr/bin/env python3
"""Generate all art for the RPG leveling mod.

  textures/item/*.png   16x16 item icons (deliberate pixel art)
  textures/gui/*.png    panel body pattern + corner ornament
Also writes a zoomed preview sheet for review.
"""
import math, os, sys
from PIL import Image, ImageDraw

K=(26,22,32,255); G=(232,181,58,255); g=(150,106,24,255); H=(255,236,170,255)
B=(38,62,122,255); W=(246,249,255,255); C=(120,226,255,255)
P=(234,220,172,255); p=(198,176,120,255); q=(146,124,78,255)
N=(62,224,106,255); n=(28,130,54,255); E=(176,255,208,255)
R=(198,60,46,255); r=(128,32,24,255)

# ---------- item icons ----------
SCROLL = [
    "................",
    "...KKKKKKKKKK...",
    "..KqqppppppqqK..",
    "..KpPPPPPPPPpK..",
    "..KpPPPPPPPPpK..",
    "..KpPPGGGGPPpK..",
    "..KpPPGGGGPPpK..",
    "..KpPGGGGGGPpK..",
    "..KpPGGGGGGPpK..",
    "..KpPPGGGGPPpK..",
    "..KpPPGGGGPPpK..",
    "..KpPPPPPPPPpK..",
    "..KpPPPPPPPPpK..",
    "..KqqppppppqqK..",
    "...KKKKKKKKKK...",
    "................",
]
CRYSTAL = [
    "................",
    ".......KK.......",
    "......KEEN......",
    ".....KEEENNK....",
    "....KEEENNNNK...",
    "...KEEENNNNNNK..",
    "..KEENNNNNNNNNK.",
    "..KENNNNNNNNNNK.",
    "..KNNNNNNNNNNNK.",
    "..KnNNNNNNNNNnK.",
    "...KnnNNNNNnnK..",
    "....KnnNNNnnK...",
    ".....KnnNnnK....",
    "......KnnnK.....",
    ".......KKK......",
    "................",
]

def from_map(rows, pal):
    im = Image.new("RGBA", (16,16), (0,0,0,0))
    for y,row in enumerate(rows):
        assert len(row)==16, (y,len(row),row)
        for x,ch in enumerate(row):
            if ch != '.':
                im.putpixel((x,y), pal[ch])
    return im

def respec_token():
    gr=[[None]*16 for _ in range(16)]
    def put(x,y,c):
        if 0<=x<16 and 0<=y<16: gr[y][x]=c
    for y in range(16):
        for x in range(16):
            d=math.hypot(x+.5-7.5, y+.5-7.5)
            if d<=6.5: put(x,y,K)
            if d<=5.6: put(x,y,g if (x+y)>18 else H if (x+y)<12 else G)
            if d<=4.2: put(x,y,B)
            if 2.6<=d<=3.5:
                a=math.degrees(math.atan2(y+.5-7.5, x+.5-7.5))
                if not (-95 <= a <= 5):      # gap in the upper-right quadrant
                    put(x,y,W)
    # arrowhead closing the gap
    for (x,y) in [(10,3),(11,4),(10,4),(11,5),(10,5),(9,4),(12,4)]:
        put(x,y,W)
    put(9,3,K); put(12,5,K)
    return gr

def to_img(gr):
    im=Image.new("RGBA",(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            if gr[y][x]: im.putpixel((x,y), gr[y][x])
    return im

# ---------- gui panel body : seamless 16x16 dark rune pattern ----------
def panel_body():
    im=Image.new("RGBA",(16,16),(18,19,28,255))
    px=im.load()
    for y in range(16):
        for x in range(16):
            if (x+y)%2==0: px[x,y]=(21,22,32,255)
    # faint diamond studs on a seamless 8px lattice
    for cy in (3,11):
        for cx in (3,11):
            px[cx,cy]=(30,32,46,255)
            px[cx-1,cy]=(24,25,36,255); px[cx+1,cy]=(24,25,36,255)
            px[cx,cy-1]=(24,25,36,255); px[cx,cy+1]=(24,25,36,255)
    return im

# ---------- gold corner ornament 16x16 ----------
def corner():
    im=Image.new("RGBA",(16,16),(0,0,0,0))
    px=im.load()
    def L(x,y,c):
        if 0<=x<16 and 0<=y<16: px[x,y]=c
    # outer gold arc
    for t in range(15):
        L(t,0,G); L(0,t,G)
    for t in range(1,10):
        L(t,1,g); L(1,t,g)
    # inner highlight
    for t in range(2,8):
        L(t,2,H if t<5 else G); L(2,t,H if t<5 else G)
    # leafy flourish
    for (x,y) in [(4,4),(5,4),(4,5),(6,3),(3,6),(5,5)]: L(x,y,g)
    L(6,4,G); L(4,6,G); L(7,2,H); L(2,7,H)
    for (x,y) in [(8,2),(9,2),(2,8),(2,9)]: L(x,y,g)
    return im

def main(out_root, preview):
    item_dir=os.path.join(out_root,"item"); gui_dir=os.path.join(out_root,"gui")
    os.makedirs(item_dir,exist_ok=True); os.makedirs(gui_dir,exist_ok=True)
    made={}
    made["respec_token"]=to_img(respec_token())
    made["skill_scroll"]=from_map(SCROLL,{'K':K,'q':q,'p':p,'P':P,'G':G})
    made["experience_crystal"]=from_map(CRYSTAL,{'K':K,'N':N,'n':n,'E':E})
    for name,im in made.items():
        im.save(os.path.join(item_dir,name+".png")); print("item:",name)
    gui={"panel_body":panel_body(),"corner":corner()}
    for name,im in gui.items():
        im.save(os.path.join(gui_dir,name+".png")); print("gui:",name)

    Z=12
    allimgs={**{k:v for k,v in made.items()}, **{k:v for k,v in gui.items()}}
    w=16*Z*len(allimgs)+20*(len(allimgs)+1)
    sheet=Image.new("RGBA",(w,16*Z+40),(48,52,60,255))
    d=ImageDraw.Draw(sheet); x=20
    for name,im in allimgs.items():
        sheet.alpha_composite(im.resize((16*Z,16*Z),Image.NEAREST),(x,20))
        d.text((x,6),name,fill=(255,255,255,255)); x+=16*Z+20
    os.makedirs(os.path.dirname(preview),exist_ok=True); sheet.save(preview)
    print("preview:",preview)

main(sys.argv[1], sys.argv[2])
