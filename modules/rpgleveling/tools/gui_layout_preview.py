#!/usr/bin/env python3
"""Render a diagnostic of the CURRENT RpgScreen layout math (no game needed).

Mirrors RpgScreen.java + StatRow.java constants exactly so overlap/overflow
bugs can be SEEN. Output: PNG.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

# --- constants copied from the Java source ---
PANEL_W, HEADER_H, FOOTER_H = 340, 52, 30
ROW_H, BTN_W = 22, 18
PANEL_TOP = 20
SCALE = 3                      # MC GUI scale
SCR_W, SCR_H = 640, 360        # scaled screen size (1920x1080 / 3)

def text_w(s):
    """Approximate MC default font width: 6px per ascii, 9px per CJK."""
    w = 0
    for ch in s:
        w += 9 if ord(ch) > 0x2E80 else 6
    return w

def main(out_path):
    S = SCALE
    img = Image.new("RGB", (SCR_W*S, SCR_H*S), (90, 110, 90))
    d = ImageDraw.Draw(img, "RGBA")
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 8*S)
    except Exception:
        font = ImageFont.load_default()

    def rect(x0, y0, x1, y1, fill=None, outline=None, w=1):
        d.rectangle([x0*S, y0*S, x1*S, y1*S], fill=fill, outline=outline, width=w)

    def text(x, y, s, fill, shadow=False):
        if shadow:
            d.text((x*S+1, y*S+1), s, font=font, fill=(0, 0, 0, 180))
        d.text((x*S, y*S), s, font=font, fill=fill)

    # --- layout, exactly as the Java computes it ---
    left = (SCR_W - PANEL_W) // 2
    top = PANEL_TOP
    viewport = SCR_H - top - HEADER_H - FOOTER_H
    right = left + PANEL_W
    bottom = top + HEADER_H + viewport + FOOTER_H

    # translucent panel (PANEL_OPACITY default 0.85)
    rect(left-4, top-4, right+4, bottom+4, fill=(16, 16, 24, int(0.85*255)))
    text(left+4, top+2, "RPG 加点", (255, 255, 255), shadow=True)

    text(left+4, top+16, "等级 137", (255, 255, 255))          # no shadow in the Java
    rect(left+4, top+28, left+4+PANEL_W-8, top+34, fill=(32, 32, 32, 255))
    rect(left+4, top+28, left+4+180, top+34, fill=(85, 255, 85, 255))
    text(left+6, top+36, "1543 / 2840", (192, 192, 192))
    text(left+PANEL_W-110, top+16, "可用点数 42", (255, 215, 0))

    # rows
    overlaps = []
    names_cn = ["基础攻击伤害","基础生命值","基础防御力","基础盔甲韧性","基础移动速度",
                "击退抗性","生命恢复","基础伤害减免","暴击率","暴击伤害","生命偷取",
                "掉落倍率","经验获取倍率"]
    modes = ["数值","百分比"]
    d.rectangle([left*S, (top+HEADER_H)*S, right*S, (top+HEADER_H+viewport)*S], outline=(255,255,0,120))
    idx = 0
    for i in range(26):
        y = top + HEADER_H + i*ROW_H
        if y + ROW_H < top+HEADER_H or y > top+HEADER_H+viewport:
            continue
        stat = names_cn[i // 2]
        mode = modes[i % 2]
        rect(left, y, left+PANEL_W, y+ROW_H-1, fill=(255,255,255,20) if i==3 else (0,0,0,32))
        if i == 3:
            rect(left, y, left+2, y+ROW_H-1, fill=(255,170,51,255))
        # text boxes exactly as StatRow places them
        bx_name, bx_mode = left+6, left+110
        bx_pts = left+PANEL_W-3*BTN_W-46
        bx_val = left+PANEL_W-3*BTN_W-12
        boxes = [
            ("name", bx_name, text_w(stat)),
            ("mode", bx_mode, text_w(mode)),
            ("pts", bx_pts, text_w("100")),
            ("val", bx_val, text_w("28.08")),
            ("btn-1", right-3*BTN_W-4, BTN_W),
            ("btn+1", right-2*BTN_W-2, BTN_W),
            ("btn+10", right-BTN_W, BTN_W),
        ]
        for n, x0, w0 in boxes:
            rect(x0, y+6, x0+w0, y+16, outline=(255,0,0,255) if i==3 else None)
        # overlap detection
        for a in range(len(boxes)):
            for b in range(a+1, len(boxes)):
                n1,x1,w1 = boxes[a]; n2,x2,w2 = boxes[b]
                if x1 < x2+w2 and x2 < x1+w1:
                    if i == 3:
                        overlaps.append(f"row{i}: {n1} <-> {n2}")
        if i == 3:
            text(bx_name, y+7, stat, (255,255,255))
            text(bx_mode, y+7, mode, (160,160,160))
            text(bx_pts, y+7, "100", (255,215,0))
            text(bx_val, y+7, "28.08", (155,216,255))
            text(left+4, y+7, "!", (255,85,85), shadow=True)   # clamped marker
            for n,x0,w0 in [("btn-1",right-3*BTN_W-4,BTN_W),("btn+1",right-2*BTN_W-2,BTN_W),("btn+10",right-BTN_W,BTN_W)]:
                rect(x0, y+2, x0+BTN_W-1, y+ROW_H-3, fill=(0,0,0,64))
                text(x0+2, y+7, n.replace("btn",""), (255,255,255))
        idx += 1

    # respec button (drawn by vanilla, sits at height-26)
    rect(SCR_W//2-60, SCR_H-26, SCR_W//2+60, SCR_H-6, fill=(120,120,120,255), outline=(255,255,255,255))
    text(SCR_W//2-18, SCR_H-21, "洗点", (255,255,255))

    # header text overflow check
    hdr = text_w("可用点数 9999")
    if left+PANEL_W-110+hdr > right:
        overlaps.append(f"header points text overflows panel by {left+PANEL_W-110+hdr-right}px")

    text(6, 6, "CURRENT LAYOUT — red boxes = text/button extents", (255,255,0))
    text(6, 16, "row3 shows name/mode/pts/val vs buttons", (200,200,200))
    img.save(out_path)
    print("saved:", out_path)
    print("panel:", left, top, right, bottom, "viewport:", viewport)
    print("OVERLAPS FOUND:")
    for o in overlaps:
        print("  ", o)

main(sys.argv[1])
