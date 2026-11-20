"""Paint the eight longswords at their native 16-pixel item resolution (Pillow)."""
import json
from math import cos, pi, sin
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


MODULE = Path(__file__).resolve().parents[1]
TEXTURES = MODULE / "src/main/resources/assets/evolvingblade/textures/item"
REPORTS = MODULE / "build/reports/art/longsword"
SIZE = 16
GUARD = (4, 11)

# Colours are grouped in planes: far edge, shadow bevel, face, lit bevel,
# tip highlight. The inlay is drawn wholly inside the two steel edges.
STEEL = (
    ("#36383c", "#595c5a", "#868981", "#b6b8ab", "#d9dbcc"),
    ("#2b393a", "#465955", "#84958a", "#bdcbb8", "#e6ecd4"),
    ("#22383f", "#39616a", "#70a4a1", "#b0dbce", "#e2f2db"),
    ("#3d3040", "#65515b", "#99828a", "#dab9af", "#fae1c1"),
    ("#29334a", "#455c72", "#8097a6", "#c1d6da", "#eff4e4"),
    ("#493337", "#69524d", "#a98e69", "#dec9a0", "#fff0c4"),
    ("#302c42", "#54465f", "#90819a", "#cbbdd2", "#f0e1e4"),
    ("#253241", "#3c5262", "#788b93", "#bfcecd", "#f0eee0"),
)
# Upper quillon relative to the guard boss, from the root to its finial.
# Reflect the other arm across the long axis, then adjust the finials on
# the two asymmetric guards after drawing the grip.
QUILLONS = (
    ((-1, -1), (-2, -2)),
    ((-1, -1), (-2, -2), (-3, -3)),
    ((-1, -1), (-2, -2), (-2, -3)),
    ((-1, -1), (-2, -1), (-3, -2)),
    ((-1, -1), (-2, -2), (-2, -3)),
    ((-1, -1), (-2, -2), (-3, -2), (-3, -3)),
    ((-1, -1), (-2, -1), (-3, -2), (-3, -3)),
    ((-1, -1), (-2, -2), (-3, -2), (-3, -3)),
)
HILT = (
    ("#36373a", "#726954", "#aaa085"),
    ("#403632", "#ae804e", "#e1bf84"),
    ("#323d3e", "#748c86", "#cee0bf"),
    ("#493335", "#a97853", "#ebbb88"),
    ("#303746", "#858c9c", "#d5dfda"),
    ("#48343b", "#bd8d53", "#f6d795"),
    ("#373043", "#94809c", "#d2b9ce"),
    ("#403538", "#b78b57", "#f4d9a3"),
)
GEMS = (None, None, "#78c6c3", "#c96665", "#8ec2d3", "#cc714d", "#bd95cc", "#94ccbc")
GRIP_DARK, GRIP_WRAP = "#49362f", "#956d4d"

CURRENT = (3, 2, 2, 1, 1, 0, 0, 0, 0, 1, 1, 2)
ENAMEL = {
    3: ("#744958", "#965365", "#bc6771", "#e99588"),
    4: ("#435677", "#557497", "#77a2b6", "#acdadc"),
    5: ("#875845", "#ad714b", "#d19459", "#f7c988"),
    6: ("#4b4164", "#6e5888", "#9680b3", "#ceb5d6"),
    7: ("#354f67", "#526b87", "#74999d", "#b3ddc6"),
}
FRAME_TICKS = {3: 5, 4: 4, 5: 4, 6: 4, 7: 4}
# The coloured fuller ends before the thrusting point.
INLAY_END = (0, 0, 9, 12, 14, 12, 16, 16)


def draw_blade(stage, frame):
    sprite = Image.new("RGBA", (SIZE, SIZE))
    pen = ImageDraw.Draw(sprite)
    rim, shadow, face, edge, glint = STEEL[stage]
    hilt_rim, hilt_face, hilt_light = HILT[stage]
    tip = 20 if stage == 0 else 22
    for y in range(SIZE):
        for x in range(SIZE):
            # Twice the distance along and across the diagonal, in grid steps.
            along = (x - GUARD[0]) - (y - GUARD[1])
            across = x + y - sum(GUARD)
            if not 1 <= along <= tip:
                continue
            width = 2
            if stage in (4, 5, 7) and along <= 5:
                width = 3
            if along >= tip - (6 if stage in (2, 4, 6) else 4):
                width = 1
            if abs(across) > width:
                continue
            # Leave the top of the point one pixel wide. An evenly lit 2x2
            # cap makes a diagonal edge look round at inventory scale.
            if along == tip - 1 and across == -1:
                continue
            if width == 1:
                color = {-1: edge, 0: face, 1: shadow}[across]
                if along == tip:
                    color = edge
                elif along == tip - 1:
                    color = face
                elif along == tip - 2:
                    color = glint
            else:
                color = {-3: shadow, -2: shadow, -1: edge, 0: face, 1: shadow, 2: rim, 3: rim}[across]
                if across == 1 and along <= 7:
                    color = face
            if across == 0 and 3 <= along <= INLAY_END[stage]:
                if stage in ENAMEL:
                    step = (along // 2 - frame) % len(CURRENT)
                    color = ENAMEL[stage][CURRENT[step]]
                else:
                    color = GEMS[stage]
            if 4 <= stage <= 6 and along == 3 and abs(across) == 1:
                color = hilt_face
            if stage == 5 and along == 5 and across == -1:
                color = hilt_light
            if stage == 7 and along in (2, 3) and abs(across) <= 2:
                color = {-2: hilt_face, -1: hilt_light, 0: hilt_face,
                         1: hilt_face, 2: hilt_rim}[across]
            pen.point((x, y), color)

    # The dark leather grip is kept separate from the metal; a square pommel
    # closes the diagonal at the opposite corner from the point.
    pen.line([(0, 14), (3, 11)], GRIP_DARK)
    pen.line([(1, 15), (4, 12)], hilt_rim)
    pen.line([(0, 15), GUARD], GRIP_WRAP)
    pen.point((3, 12), GRIP_DARK)
    pen.point((2, 12), GRIP_WRAP)
    pen.rectangle((0, 14, 1, 15), fill=hilt_rim)
    pen.point((0, 14), hilt_light)
    pen.point((1, 14), hilt_face)

    arm = QUILLONS[stage]
    full_guard = [(GUARD[0]+dx, GUARD[1]+dy) for dx, dy in reversed(arm)]
    full_guard.append(GUARD)
    full_guard.extend((GUARD[0]-dy, GUARD[1]-dx) for dx, dy in arm)
    if stage == 3:
        full_guard[-1] = (7, 13)  # One quillon rises towards the cutting edge.
    elif stage == 6:
        full_guard[0] = (2, 8)    # A silver hook opposite a blunt tail.
    # A one-pixel underside makes the crossguard read across the shaft on
    # both a dark hotbar and a pale inventory slot.
    for offset in ((-1, 0), (0, 1)):
        pen.line([(x+offset[0], y+offset[1]) for x, y in full_guard], hilt_rim)
    pen.line(full_guard, hilt_face)
    pen.point(full_guard[0], hilt_light)
    pen.point(full_guard[-1], hilt_face if stage < 4 else hilt_light)
    if stage == 6:
        pen.point((7, 13), hilt_face)
    pen.point(GUARD, GEMS[stage] or hilt_light)
    if stage in (5, 6, 7):
        pen.point((GUARD[0]+1, GUARD[1]), hilt_light)
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


def gallery_labels():
    try:
        font = ImageFont.truetype("msyh.ttc", 15)
    except OSError:
        return ImageFont.load_default(), ("0", "I", "II", "III", "IV", "V", "VI", "VII")
    language = TEXTURES.parents[1] / "lang/zh_cn.json"
    with language.open(encoding="utf8") as source:
        strings = json.load(source)
    return font, [strings[f"evolvingblade.stage.{stage}"] for stage in range(8)]


def gallery(sprites, labels):
    font, names = labels
    width, height, pixels = 126, 132, SIZE * 6
    image = Image.new("RGB", (4 * width, 2 * height), "#40454b")
    draw = ImageDraw.Draw(image)
    for stage, sprite in enumerate(sprites):
        col, row = stage % 4, stage // 4
        x, y = col * width, row * height
        large = sprite.resize((pixels, pixels), Image.Resampling.NEAREST)
        image.paste(large, (x + (width - pixels)//2, y + 6), large)
        draw.text((x + width//2, y + 115), names[stage], fill="#e4e1d9", font=font, anchor="mm")
    return image


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
    labels = gallery_labels()
    gallery(frames, labels).save(REPORTS / "stage-gallery.png")
    plate(frames).save(REPORTS / "stage-inventory.png")
    # Both 48-tick and 60-tick cycles return to their first frame after 240 ticks.
    ticks = sorted(set(range(0, 240, 4)) | set(range(0, 240, 5)))
    sequence = [gallery([draw_blade(s, t // FRAME_TICKS.get(s, 1)) for s in range(8)], labels) for t in ticks]
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
