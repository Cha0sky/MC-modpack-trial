"""Static artwork/layout preview, not a screenshot or proof of Minecraft rendering.

Reads only built source assets and optional local vanilla item art; writes docs/visual-preview.png.
System-font label positions mirror GeneratorScreen; production text is rendered by Minecraft.
"""
from pathlib import Path
import io
import zipfile
from PIL import Image, ImageDraw, ImageFont
from build_assets import MODULE, RESOURCES, TIERS, COLORS

FONT_FILE = Path(r"C:\Windows\Fonts\msyh.ttc")
NAMES = ("泥土", "木头", "石头", "铜", "铁", "金", "钻石", "下界合金")


def font(size):
    return ImageFont.truetype(str(FONT_FILE), size) if FONT_FILE.exists() else ImageFont.load_default()


def texture(relative):
    return Image.open(RESOURCES / "assets/infinitecore/textures" / relative).convert("RGBA")


def cube(tier):
    canvas = Image.new("RGBA", (121, 121))
    planes = (
        (texture(f"block/{tier}_generator_top.png"), ((0,30),(60,0),(120,30),(60,60)), (16/120,-16/60,8,16/120,16/60,-8)),
        (texture(f"block/{tier}_generator.png"), ((0,30),(60,60),(60,120),(0,90)), (16/60,0,0,-8/60,16/60,-8)),
        (texture(f"block/{tier}_generator.png"), ((60,60),(120,30),(120,90),(60,120)), (16/60,0,-16,8/60,16/60,-24)),
    )
    for image, polygon, transform in planes:
        projected = image.transform(canvas.size, Image.Transform.AFFINE, transform, Image.Resampling.NEAREST)
        mask = Image.new("L", canvas.size)
        ImageDraw.Draw(mask).polygon(polygon, fill=255)
        canvas.paste(projected, (0,0), mask)
    return canvas


def fit(draw, xy, text, width, color, selected_font):
    while text and draw.textlength(text, font=selected_font) > width:
        text = text[:-2] + "…" if not text.endswith("…") else text[:-2] + "…"
    draw.text(xy, text, font=selected_font, fill=color)


def main_gui(item):
    canvas = texture("gui/generator.png").crop((0,0,176,216))
    draw = ImageDraw.Draw(canvas)
    f = font(9)
    text, muted = "#DDE3E7", "#9BA7B0"
    draw.text((8,5), "铜生成器", font=f, fill=text)
    draw.rectangle((124,4,167,21), fill="#50616E", outline="#9AA9B3")
    draw.text((139,7), "IO", font=f, fill=text)
    draw.rectangle((8,16,118,17), fill=COLORS[3])
    canvas.alpha_composite(texture("item/template_copper.png"), (12,26))
    canvas.alpha_composite(item, (42,26))
    for x, y in ((12,54),(30,54),(48,54)): canvas.alpha_composite(item, (x,y))
    fit(draw,(64,27),"钻石",104,text,f)
    fit(draw,(64,38),"铜模板 · 无消耗",104,muted,f)
    for xy, value in (((83,54),"资源库存 3/9"),((83,67),"36 tick/次"),((83,79),"理论 0.56 个/s"),((83,91),"模板倍率 ×1.0")):
        fit(draw,xy,value,80,text,f)
    draw.rectangle((83,103,124,106), fill=COLORS[3])
    fit(draw,(12,113),"运行中 · 每次生成 1 个",152,"#AFD2BD",f)
    draw.text((8,123),"物品栏",font=f,fill=muted)
    return canvas


def io_gui():
    canvas = texture("gui/generator_io.png").crop((0,0,176,216))
    draw = ImageDraw.Draw(canvas)
    f = font(9)
    draw.text((8,5),"六面物品接口",font=f,fill="#DDE3E7")
    draw.rectangle((124,4,167,21),fill="#50616E",outline="#9AA9B3")
    draw.text((135,7),"返回",font=f,fill="#DDE3E7")
    draw.rectangle((8,16,118,17),fill=COLORS[3])
    for row,(direction,name,mode,color,capable) in enumerate((
            ("上","箱子","输入","#B05050",True), ("下","漏斗","输出","#5080C8",True),
            ("北","无邻接方块","关闭","#6E6E6E",False), ("南","箱子","双向","#C070C0",True),
            ("西","石头","关闭","#6E6E6E",False), ("东","无邻接方块","关闭","#6E6E6E",False))):
        y=31+row*23
        draw.rectangle((8,y+1,10,y+19),fill=color)
        draw.text((14,y+6),direction,font=f,fill="#DDE3E7")
        fit(draw,(36,y+2),name,68,"#DDE3E7",f)
        fit(draw,(36,y+12),"有物品接口" if capable else "无物品接口",68,"#83CFA6" if capable else "#9BA7B0",f)
        draw.rectangle((110,y+2,167,y+19),fill="#50616E",outline="#9AA9B3")
        draw.text((129,y+5),mode,font=f,fill="#DDE3E7")
    draw.text((8,178),"输入：仅吸入生成模板",font=f,fill="#9BA7B0")
    draw.text((8,191),"输出：仅推出资源库存",font=f,fill="#9BA7B0")
    return canvas


def main():
    preview = Image.new("RGB",(1200,1010),"#172129")
    draw = ImageDraw.Draw(preview)
    draw.text((22,12),"无限之核 1.1.0 重建 · 八阶机器与 GUI",font=font(26),fill="#E2E8ED")
    draw.text((22,50),"静态资源投影与布局示意；系统字体，示例物品/邻居；尚未进行游戏渲染验收。",font=font(16),fill="#A6B6C3")
    for index,tier in enumerate(TIERS):
        x=20+index*147
        preview.paste(cube(tier),(x,80),cube(tier))
        draw.text((x+8,206),f"{index+1} · {NAMES[index]}",font=font(16),fill=COLORS[index])
        card=texture(f"item/template_{tier}.png").resize((48,48),Image.Resampling.NEAREST)
        preview.paste(card,(x+35,234),card)
    draw.text((30,296),"主界面：模板 → 完整目标预览 → 库存与实际间隔",font=font(18),fill="#DDE3E7")
    draw.text((632,296),"IO 页面：世界方向 / 邻居 / 接口 / 模式",font=font(18),fill="#DDE3E7")
    item=Image.new("RGBA",(16,16))
    vanilla=Path.home()/".gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"
    if vanilla.exists():
        with zipfile.ZipFile(vanilla) as archive:
            item=Image.open(io.BytesIO(archive.read("assets/minecraft/textures/item/diamond.png"))).convert("RGBA")
    main=main_gui(item).resize((528,648),Image.Resampling.NEAREST)
    io_preview=io_gui().resize((528,648),Image.Resampling.NEAREST)
    preview.paste(main,(30,330),main)
    preview.paste(io_preview,(632,330),io_preview)
    output=MODULE/"docs/visual-preview.png"
    output.parent.mkdir(parents=True,exist_ok=True)
    preview.save(output)
    print(output)


if __name__=="__main__":
    main()
