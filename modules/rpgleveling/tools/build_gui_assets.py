#!/usr/bin/env python3
"""Turn the ComfyUI renders into mod GUI assets, then render a mock of the NEW
panel design so layout + art can be judged before any Java is written."""
import os, sys
from PIL import Image, ImageDraw, ImageFont

FRAME  = r"D:\大模型体验\ComfyUI_windows_portable\ComfyUI\output\rpgleveling_bg_00001_.png"
ATMOS  = r"D:\大模型体验\ComfyUI_windows_portable\ComfyUI\output\rpgleveling_bg2_00001_.png"

SLICE_SRC = 340      # px taken from each side of the 1024 frame
SLICE_OUT = 64       # px per slice in the packed texture -> 192x192

def build_nineslice(frame, out_path):
    im = frame.convert("RGBA")
    W, H = im.size
    s = SLICE_SRC
    regions = {
        (0,0): (0,0,s,s), (1,0): (s,0,W-2*s,s), (2,0): (W-s,0,s,s),
        (0,1): (0,s,s,H-2*s), (1,1): (s,s,W-2*s,H-2*s), (2,1): (W-s,s,s,H-2*s),
        (0,2): (0,H-s,s,s), (1,2): (s,H-s,W-2*s,s), (2,2): (W-s,H-s,s,s),
    }
    tex = Image.new("RGBA", (SLICE_OUT*3, SLICE_OUT*3))
    for (cx,cy),(x0,y0,x1,y1) in regions.items():
        tex.paste(im.crop((x0,y0,x0+x1,y0+y1)).resize((SLICE_OUT,SLICE_OUT), Image.LANCZOS),
                  (cx*SLICE_OUT, cy*SLICE_OUT))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    tex.save(out_path)
    print("panel texture:", out_path, tex.size)

def build_backdrop(atmos, out_path):
    im = atmos.convert("RGB").resize((512, 288), Image.LANCZOS)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    im.save(out_path, optimize=True)
    print("backdrop texture:", out_path, im.size, os.path.getsize(out_path), "bytes")

def draw_nineslice(dst, tex, x, y, w, h, s=SLICE_OUT):
    tw, th = tex.size
    for cy in range(3):
        for cx in range(3):
            sx, sy = cx*s, cy*s
            if cx == 1: dx, dw = x+s, w-2*s; sw = tw-2*s
            elif cx == 0: dx, dw, sw = x, s, s
            else: dx, dw, sw = x+w-s, s, s
            if cy == 1: dy, dh = y+s, h-2*s; sh = th-2*s
            elif cy == 0: dy, dh, sh = y, s, s
            else: dy, dh, sh = y+h-s, s, s
            if dw <= 0 or dh <= 0: continue
            dst.paste(tex.crop((sx, sy, sx+sw, sy+sh)).resize((dw, dh), Image.LANCZOS), (dx, dy))

def main(tex_dir, preview):
    panel_path   = os.path.join(tex_dir, "gui", "panel.png")
    back_path    = os.path.join(tex_dir, "gui", "backdrop.png")
    build_nineslice(Image.open(FRAME), panel_path)
    build_backdrop(Image.open(ATMOS), back_path)
    panel = Image.open(panel_path).convert("RGBA")
    back  = Image.open(back_path).convert("RGB")

    # ---- mock the NEW layout at MC GUI scale 3, 640x360 logical ----
    S = 3
    SW, SH = 640, 360
    img = Image.new("RGB", (SW, SH))
    img.paste(back.resize((SW, SH), Image.LANCZOS), (0, 0))
    d = ImageDraw.Draw(img, "RGBA")
    f  = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 9)
    fb = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 10)

    M = 10
    pw, ph = SW - 2*M, SH - 2*M
    px0, py0 = M, M
    draw_nineslice(img, panel, px0, py0, pw, ph)
    pad = 30
    ix0, ix1 = px0 + pad, px0 + pw - pad

    def T(x, y, s, col, font=None, shadow=True):
        font = font or f
        if shadow: d.text((x+1, y+1), s, font=font, fill=(0,0,0,190))
        d.text((x, y), s, font=font, fill=col)

    # header
    title = "RPG 加点"
    tw = d.textlength(title, font=fb)
    T((SW-tw)/2, py0+22, title, (242, 217, 138), fb)
    d.line([ix0, py0+40, ix1, py0+40], fill=(200,169,78,140))
    T(ix0, py0+48, "等级 137", (255,255,255))
    pts = "可用点数 42"
    T(ix1 - d.textlength(pts, font=f), py0+48, pts, (255,215,0))
    # xp bar
    bx0, bx1, by = ix0, ix1, py0+62
    d.rectangle([bx0, by, bx1, by+8], fill=(10,10,18,255), outline=(200,169,78,120))
    d.rectangle([bx0+1, by+1, bx0+1+int((bx1-bx0-2)*0.54), by+7], fill=(95,200,255,220))
    T(ix0, by+11, "1543 / 2840", (185,195,215))

    list_top = py0 + 82
    list_bot = py0 + ph - 40
    # column header
    T(ix0+14, list_top, "属性", (150,160,185))
    T(ix0+170, list_top, "模式", (150,160,185))
    T(ix0+268, list_top, "点数", (150,160,185))
    T(ix0+336, list_top, "数值", (150,160,185))
    d.line([ix0, list_top+13, ix1, list_top+13], fill=(255,255,255,40))

    ROWH = 24
    names = [("基础攻击伤害","数值"),("基础攻击伤害","百分比"),("基础生命值","数值"),
             ("基础生命值","百分比"),("基础防御力","数值"),("基础防御力","百分比"),
             ("基础盔甲韧性","数值"),("基础盔甲韧性","百分比"),("基础移动速度","数值"),
             ("基础移动速度","百分比"),("击退抗性","数值")]
    BTN_W, GAP = 20, 3
    btn_block = 3*BTN_W + 2*GAP
    btn_x = ix1 - btn_block
    val_right = btn_x - 8
    y = list_top + 16
    for i,(nm,md) in enumerate(names):
        if y + ROWH > list_bot: break
        hov = (i == 4)
        if i % 2 == 1: d.rectangle([ix0, y, ix1, y+ROWH-2], fill=(255,255,255,10))
        if hov: d.rectangle([ix0, y, ix1, y+ROWH-2], fill=(200,169,78,38))
        accent = (68,136,255,255) if md=="数值" else (255,170,51,255)
        d.rectangle([ix0, y, ix0+2, y+ROWH-2], fill=accent)
        T(ix0+14, y+7, nm, (240,244,255))
        T(ix0+170, y+7, md, (165,175,200))
        pv = str(37)
        T(ix0+268+30-d.textlength(pv,font=f), y+7, pv, (255,215,0))
        vv = "18.48"
        T(val_right-d.textlength(vv,font=f), y+7, vv, (155,216,255))
        for k,lab in enumerate(["-1","+1","+10"]):
            bx = btn_x + k*(BTN_W+GAP)
            hot = hov and k==1
            d.rectangle([bx, y+3, bx+BTN_W, y+ROWH-6], fill=(38,44,66,255) if not hot else (58,70,104,255),
                        outline=(200,169,78,180))
            T(bx+BTN_W/2-d.textlength(lab,font=f)/2, y+7, lab, (255,255,255))
        y += ROWH

    # footer button
    bw, bh = 120, 22
    bx = SW/2 - bw/2; byy = py0 + ph - 32
    d.rectangle([bx, byy, bx+bw, byy+bh], fill=(38,44,66,255), outline=(200,169,78,200))
    d.rectangle([bx+1, byy+1, bx+bw-1, byy+2], fill=(255,255,255,30))
    lb = "洗点 (重置所有加点)"
    T(bx+bw/2-d.textlength(lb,font=f)/2, byy+7, lb, (255,255,255))

    d.text((6, 4), "NEW LAYOUT MOCK - AI frame nine-slice panel + AI atmospheric backdrop", fill=(255,255,120,255))
    os.makedirs(os.path.dirname(preview), exist_ok=True)
    img.resize((SW*S, SH*S), Image.NEAREST).save(preview)
    print("preview:", preview)

main(sys.argv[1], sys.argv[2])
