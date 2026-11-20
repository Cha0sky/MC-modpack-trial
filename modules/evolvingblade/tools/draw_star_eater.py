"""Draw the Star Eater on Minecraft's 16-pixel item grid. Requires Pillow."""
import json
from math import cos, pi, sin
from pathlib import Path

from PIL import Image, ImageDraw


MODULE = Path(__file__).resolve().parents[1]
TEXTURES = MODULE / "src/main/resources/assets/evolvingblade/textures/item"
REPORTS = MODULE / "build/reports/art"
SIZE = 16

# Each row is a scanline at game resolution. The right margin is implicit.
# O outline, S shadow, M face, E cutting edge, H tip, ~ moving enamel, g/i bronze.
BLADES = (
    # Blank: the blunt, unfinished end of a short iron bar.
    """
...........
...........
...........
...........
..........O.O
.........OMES
........OEMSO
.......OEMSO
......OEMSO
.....OSMSO
....OSSSO
.....OOO
""",
    # Awakening: a narrow, pointed blade with a broad uninterrupted bevel.
    """
..............
.............OO
............OHO
...........OEMO
..........OEMSO
.........OEMSO
........OEMSO
.......OEMSO
......OEMSO
.....OEMSO
....OSMSO
.....OOO
""",
    # Spirit edge: a long lancet. The single coloured flute is still.
    """
..............OO
.............OHO
............OEMO
...........OE~SO
..........OE~SO
.........OE~SO
........OE~SO
.......OE~SO
......OEMSO
.....OEMSO
....OSMSO
.....OOO
""",
    # Soul drinker: a swept tip, red enamel and a small backward barb at the heel.
    """
...............O
.............OEO
............OHEO
.........OOOEMSO
.........OEMS~O
........OEM~SO
.......OEM~SO
......OEM~~SO
.....OEM~MSSO
.....OS~MSOO
....OSMSOO
.....OOO
""",
    # Rift: the secondary prong rises from the right-hand edge of the blade.
    """
..............OO
.............OHO
............OEEO
...........OE~SO
..........OE~SO
.........OE~SO.O
........OE~SO.OE
.......OE~SO.OEO
......OE~~SOOEO
.....OEM~SMSSO
....OSMMSSOOO
.....OOOOO
""",
    # Kingslayer: a heavy cleaver with three stepped teeth along the spine.
    """
..............OO
............OOHO
..........OOEEMO
..........OEMS~O
.......OOEEMS~SO
........OEM~~SO
......OOEM~~SSO
......OEM~~MSO
.....OEM~~MSO
.....OSM~MSSO
....OSMSSOOO
.....OOOO
""",
    # Starfall: night-blue leaf blade, with one moon-shaped hollow.
    """
..............OO
............OOHO
...........OEMEO
..........OE~~SO
........OOE~~SSO
.......OEM~~SOO
......OES~~SO
.....OES~MSO
.....OES..EO
.....OSS..EO
....OEMSSEOO
.....OOOOO
""",
    # Eventide: an ivory edge frames an ink-blue channel. A gilded recurved
    # rib bridges the opening at the heel; the silhouette stays still.
    """
.............OOO
............OEHO
..........OOEMEO
.........OHE~~SO
........OEM~~SEO
.......OEM~~SO.O
......OEM~~SO.Oi
.....OE~~MEO.OgO
....OES@~SO..igO
....OEg@gO..igO
....OSggiOgggO
.....OSSSOOO
......OOO
""",
)

# Grips grow around the same attachment pixel (5,10). Dots on a fitting
# leave the underlying metal visible. The last hilt carries the opening,
# with no halo or detached pixels around the icon.
GRIP = """
...OxO
..OwxO
.OxwO
OgwO
Oig
OO
"""
GUARDS = (
    (7, """
...
...OO
..OggO
...OpgO
....OOpO
......OO
"""),
    (7, """
..OO
..OgO
...OigO
....OgppO
.....OOpgO
.......OOO
"""),
    (6, """
..OO
..OgO
...OgO
...Og@O
....OgppOO
.....OOgpgO
......OggO
......OOO
"""),
    (6, """
...OO
..OgO
..OgOO
...Oi@O
....OgppO
......OgO
......OpgO
.......OO
"""),
    (6, """
..OO
..OiO
..OgOO
...Og@O
....OgppOO
.....OOggiO
.......OggO
........OO
"""),
    (5, """
...O
..OgO
..OiO
..OgOO
...Og@O
....OgppOO
.....OOgigO
.......OpgO
........OgO
.........O
"""),
    (6, """
.OO
.OgOO
..OigO
...Op@O
....OgpgO
.....OOgO.O
.......OggO
........OO
"""),
    (6, """
.OOO
.OigO
..OggO
...Oi@gO
....OgigpO
.....OOigiO
.......OpgO
........OO
"""),
)

# The leather and brass repeat across the set. The dark edge is tinted
# towards the metal. Colour bands are opaque and use nearest-neighbour pixels.
FITTINGS = {"x": "#583b32", "w": "#ab7650", "p": "#855535",
            "g": "#cb9151", "i": "#f6d58b"}
METALS = (
    ("#302e38", "#51515b", "#858389", "#b8b5aa", "#dfd9c4", "#a6754c"),
    ("#273940", "#44595b", "#7a9992", "#bbd4ba", "#e7ebc9", "#e1b871"),
    ("#203944", "#345966", "#54868d", "#a6ddd6", "#dcf6e6", "#53aebd"),
    ("#382b3c", "#623c50", "#9a5862", "#e3a28a", "#ffe2b3", "#df766d"),
    ("#222f49", "#395372", "#6a92a8", "#c2e0de", "#e5f5e7", "#6dbdc0"),
    ("#3b2d3c", "#70494c", "#a66a4b", "#eab971", "#fff1b9", "#ffc46c"),
    ("#28233e", "#473853", "#716681", "#b9b1c9", "#ede5ed", "#bca4db"),
    ("#25263b", "#38445b", "#657586", "#cfddd9", "#fff3ce", "#ffe2a0"),
)
# One accent travels up the core, with adjacent cells sharing a colour. Dark
# and middle values occupy most of the cycle, leaving a single light crest.
ENAMEL = {
    3: ("#623c50", "#98505e", "#cc7471", "#f4b18b"),
    4: ("#354d70", "#466f93", "#69a6b8", "#addbd8"),
    5: ("#70494c", "#ae6649", "#e49b55", "#ffe2a0"),
    6: ("#413452", "#705685", "#a998ca", "#dbd9ee"),
    7: ("#3a3456", "#52548b", "#67969d", "#b0efcf"),
}
CURRENT = (3, 2, 2, 1, 1, 0, 0, 0, 0, 1, 1, 2)
FRAME_TICKS = {3: 5, 4: 4, 5: 4, 6: 4, 7: 4}


def paint(sprite, grid, start_y, palette, stage, frame):
    for y, row in enumerate(grid.strip("\n").splitlines(), start_y):
        if y >= SIZE or len(row) > SIZE:
            raise ValueError(f"Stage {stage}: row {y} exceeds the item grid")
        for x, mark in enumerate(row):
            if mark == ".":
                continue
            if mark == "~" and stage in ENAMEL:
                phase = ((x - y) // 2 - frame) % len(CURRENT)
                color = ENAMEL[stage][CURRENT[phase]]
            else:
                color = palette[mark]
            sprite.putpixel((x, y), (*bytes.fromhex(color[1:]), 255))


def draw_blade(stage, frame):
    palette = {**FITTINGS, **dict(zip("OSMEH@", METALS[stage]))}
    palette["~"] = palette["@"]
    sprite = Image.new("RGBA", (SIZE, SIZE))
    paint(sprite, BLADES[stage], 0, palette, stage, frame)
    paint(sprite, GRIP, 10, palette, stage, frame)
    top, guard = GUARDS[stage]
    paint(sprite, guard, top, palette, stage, frame)
    return sprite


def plate(sprites, zoom=1):
    """A dark hotbar and a light inventory, drawn at integer scale."""
    width = 8 * 20 + 2
    image = Image.new("RGB", (width, 42), "#191b21")
    draw = ImageDraw.Draw(image)
    for row, (border, well, lip) in enumerate((
            ("#111318", "#393c43", "#62656a"), ("#656565", "#a4a4a4", "#e1e1e1"))):
        for stage, sprite in enumerate(sprites):
            x, y = 1 + stage * 20, 1 + row * 20
            draw.rectangle((x, y, x+19, y+19), fill=lip)
            draw.rectangle((x, y, x+18, y+18), fill=border)
            draw.rectangle((x+1, y+1, x+18, y+18), fill=well)
            image.paste(sprite, (x+2, y+2), sprite)
    return image.resize((width*zoom, 42*zoom), Image.Resampling.NEAREST)


def main():
    frames = []
    for stage in range(8):
        count = len(CURRENT) if stage in ENAMEL else 1
        strip = Image.new("RGBA", (SIZE, SIZE * count))
        for frame in range(count):
            strip.paste(draw_blade(stage, frame), (0, SIZE * frame))
        texture = TEXTURES / f"star_eater_stage{stage}.png"
        strip.save(texture)
        if stage in ENAMEL:
            metadata = {"animation": {"frametime": FRAME_TICKS[stage], "interpolate": False}}
            texture.with_suffix(".png.mcmeta").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf8")
        frames.append(strip.crop((0, 0, SIZE, SIZE)))
    REPORTS.mkdir(parents=True, exist_ok=True)
    plate(frames, 4).save(REPORTS / "stage-gallery.png")
    plate(frames).save(REPORTS / "stage-inventory.png")
    # Both 48-tick and 60-tick cycles return to their first frame after 240 ticks.
    ticks = sorted(set(range(0, 240, 4)) | set(range(0, 240, 5)))
    sequence = [plate([draw_blade(s, t // FRAME_TICKS.get(s, 1)) for s in range(8)], 4) for t in ticks]
    durations = [(end - start) * 50 for start, end in zip(ticks, ticks[1:] + [240])]
    sequence[0].save(REPORTS / "stage-flow.gif", save_all=True, append_images=sequence[1:],
                     duration=durations, loop=0, optimize=True)
    draw_astrolabe()


def draw_astrolabe():
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
