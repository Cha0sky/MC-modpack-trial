"""Draw the Star Eater's handheld sprites. Run from any working directory with Pillow."""
from pathlib import Path
from math import cos, sin, pi

from PIL import Image, ImageDraw, ImageFilter


MODULE = Path(__file__).resolve().parents[1]
TEXTURES = MODULE / "src/main/resources/assets/evolvingblade/textures/item"
SIZE = 48
INK, LEATHER, BRAID = "#19212e", "#483841", "#a37959"
BRONZE, BRASS, IVORY = "#946248", "#d4a663", "#ffe4a2"
# Coordinates run from the crossguard along the spine. Width is perpendicular to the edge.
BLADES = [
    [(0,-2),(17,-2),(19,-1),(23,-1),(21,1),(24,1),(18,3),(1,3)],
    [(0,-3),(16,-3),(27,-2),(33,0),(26,2),(7,3),(2,4)],
    [(0,-3),(12,-3),(28,-2),(38,0),(29,2),(12,2),(6,4),(1,3)],
    [(0,-3),(8,-5),(12,-2),(21,-3),(28,-6),(35,-8),(39,-7),(36,-3),(32,0),(24,3),(15,6),(8,5),(3,3)],
    [(0,-4),(9,-5),(13,-3),(31,-3),(42,-1),(35,1),(24,2),(18,4),(9,3),(5,6)],
    [(0,-5),(5,-7),(8,-4),(12,-7),(25,-7),(29,-9),(28,-5),(34,-6),(41,0),(30,5),(17,6),(11,3),(7,6),(1,5)],
    [(0,-4),(7,-6),(12,-3),(21,-5),(26,-5),(24,-3),(37,-3),(43,-1),(39,2),(31,3),(27,5),(20,4),(12,6),(8,4),(2,6)],
    [(0,-4),(7,-6),(12,-3),(23,-5),(20,-2),(32,-3),(39,-3),(44,0),(34,3),(30,3),(26,5),(15,5),(10,7),(5,5),(1,6)],
]
GUARDS = [
    [(-2,-4),(0,-4),(1,4),(-1,4)],                       # Tang and a plain ferrule
    [(2,-7),(3,-6),(1,-3),(1,4),(-1,7),(-3,7),(-1,3),(-1,-3)],
    [(4,-8),(5,-7),(2,-4),(1,-2),(1,3),(4,7),(3,8),(-1,5),(-2,2),(-1,-4)],
    [(4,-8),(3,-5),(1,-2),(1,3),(5,7),(3,9),(0,8),(1,6),(-3,4),(-2,-3)],
    [(6,-9),(6,-7),(2,-3),(2,3),(6,7),(7,10),(2,8),(-2,4),(-2,-3),(1,-6)],
    [(6,-10),(4,-7),(5,-6),(1,-2),(1,3),(5,5),(4,7),(6,9),(2,10),(-2,6),(-4,2),(-3,-4),(1,-8)],
    [(6,-9),(5,-6),(2,-3),(2,3),(6,6),(7,10),(3,9),(0,6),(-3,5),(-4,0),(-2,-4),(2,-8)],
    [(8,-10),(7,-6),(4,-6),(1,-2),(1,3),(5,5),(6,4),(8,10),(4,9),(0,7),(-2,4),(-4,2),(-2,-4),(0,-6),(4,-8)],
]
# Shadow, bevel, reflection, and luminous inlay.
STEEL = [
    ("#454c55","#707e84","#a6acad","#b49865"),
    ("#324855","#69928f","#c6dabe","#ead07a"),
    ("#264560","#438da2","#b4f4ea","#68f7f6"),
    ("#39243f","#8b475d","#ffc0a6","#ff727d"),
    ("#273754","#6a80b7","#d8eeff","#79e7ff"),
    ("#553544","#b56e48","#ffeab2","#ffad45"),
    ("#30254d","#655697","#e1c9ff","#c494ff"),
    ("#20293b","#687689","#e4ebed","#ffe2a2"),
]


def xy(s, w):
    return round(13 + .72 * (s + w)), round(34.5 + .72 * (w - s))


def points(coords):
    return [xy(s, w) for s, w in coords]


def draw_blade(stage, frame, frames):
    canvas = Image.new("RGBA", (SIZE, SIZE))
    draw = ImageDraw.Draw(canvas)
    shadow, bevel, edge, glow = STEEL[stage]
    blade = BLADES[stage]
    end = max(s for s, _ in blade)
    mask = Image.new("L", canvas.size)
    ImageDraw.Draw(mask).polygon(points(blade), fill=255)
    metal = Image.new("RGBA", canvas.size, shadow)
    face = ImageDraw.Draw(metal)
    face.polygon(points([(0,-8),(end+2,-8),(end,0),(end-8,0),(10,-1),(5,1),(0,0)]), fill=bevel)
    face.line(points(blade[:max(3, len(blade)//2)]), fill=edge, width=2)
    face.line(points([(3,1),(9,0),(end-5,-1),(end,0)]), fill=bevel, width=1)
    if stage >= 2:
        face.line(points([(5,0),(11,-1),(14,0),(end-5,-1)]), fill=glow)
        # A travelling glint stays inside the metal on every frame.
        spot = 7 + round((end-13) * (.5-.5*cos(2*pi*frame/frames)))
        face.line(points([(spot-1,1),(spot+2,-2)]), fill="#fff5dc", width=2)
    canvas.paste(metal, mask=mask)
    poly = lambda shape,color: draw.polygon(points(shape),fill=color)
    line = lambda shape,color: draw.line(points(shape),fill=color)
    if stage in (4,6,7):
        cut_at = {4:19, 6:24, 7:26}[stage]
        # A split edge, then two completely separated star-metal splinters on the final tiers.
        poly([(cut_at-2,-5),(cut_at,-5),(cut_at+3,2),(cut_at+2,4)], (0,0,0,0))
        if stage == 7:
            poly([(34,-3),(36,-4),(36,2),(35,3)],(0,0,0,0))
    # Counterweight, diagonal leather wrap, guard and the changing stone in the socket.
    poly([(-14,0),(-12,-2),(-10,-2),(-8,-1),(-2,-1),(-1,2),(-10,2),(-12,3)],LEATHER)
    for s in (-10,-7,-4):
        line([(s,-1),(s+1,2)],BRAID)
    poly([(-14,0),(-12,-2),(-10,0),(-12,2)],BRASS)
    line([(-14,0),(-12,-2)],IVORY)
    poly(GUARDS[stage], BRONZE)
    line(GUARDS[stage][:max(3,len(GUARDS[stage])//2)],BRASS)
    if stage:
        poly([(-3,0),(0,-2),(3,0),(0,2)],BRASS)
        line([(-1,0),(1,-1)],glow)
    if stage >= 5:
        line([(0,3),(3,6),(4,7)],IVORY)
        line([(0,-4),(3,-7)],IVORY)
    if stage == 2:
        line([(-2,4),(-4,6),(-7,5),(-10,1)],BRASS)
    if stage == 4:
        poly([(8,4),(10,6),(21,6),(15,8),(9,8),(6,6)],bevel)
        line([(10,6),(21,6)],edge)
    if stage == 6:
        poly([(22,8),(24,6),(29,6),(26,8)],bevel)
        line([(23,7),(29,6)],edge)
    if stage == 7:
        for s,w in ((14,-8),(32,6)):
            poly([(s-3,w),(s,w-1),(s+2,w),(s,w+1)],glow)
    alpha = canvas.getchannel("A")
    outline = Image.new("RGBA", canvas.size, INK)
    outline.putalpha(alpha.filter(ImageFilter.MaxFilter(3)))
    return Image.alpha_composite(outline,canvas)


def main():
    preview = Image.new("RGB", (SIZE*8,SIZE), "#17252e")
    for stage in range(8):
        count = 1 if stage < 2 else 6 if stage < 5 else 8
        sheet = Image.new("RGBA", (SIZE, SIZE*count))
        for i in range(count):
            sprite = draw_blade(stage,i,count)
            sheet.paste(sprite,(0,SIZE*i))
            if i == 0:
                preview.paste(sprite,(stage*SIZE,0),sprite)
        sheet.save(TEXTURES / f"star_eater_stage{stage}.png")
    out = MODULE / "build/reports/art"
    out.mkdir(parents=True, exist_ok=True)
    preview.resize((SIZE*8*3,SIZE*3),Image.Resampling.NEAREST).save(out / "stage-gallery.png")
    instrument = Image.new("RGB", (96,96), "#101f2b")
    dial = ImageDraw.Draw(instrument)
    for radius,color in ((42,"#2d4850"),(40,"#294149"),(33,"#233840"),(18,"#21333f")):
        dial.ellipse((48-radius,48-radius,48+radius,48+radius),outline=color)
    for index in range(32):
        angle = index*pi/16
        length = 5 if index % 4 == 0 else 2
        def point(radius):
            return (round(48+radius*cos(angle)),round(48+radius*sin(angle)))
        dial.line((point(38),point(38+length)),fill="#99764d" if length==5 else "#426069")
    for x,y in ((12,24),(23,51),(18,71),(71,26),(64,84),(77,66),(47,8),(33,19),(73,45)):
        dial.point((x,y),fill="#81a5a2")
    for x,y in ((4,4),(91,4),(4,91),(91,91)):
        dial.rectangle((x-1,y-1,x+1,y+1),fill="#83694e")
    gui = TEXTURES.parent / "gui"
    gui.mkdir(parents=True,exist_ok=True)
    instrument.save(gui / "astrolabe.png")


if __name__ == "__main__":
    main()
