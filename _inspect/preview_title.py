#!/usr/bin/env python3
"""Draws the titles the way the mod now draws them, to be looked at, and measures them.

The device this runs on does not build the mod, so the two titles are drawn here with the game's own
font and the game's own sprites, out of the client jar that is already on disk, and then measured:

- the big title: the name in the plate, at the place the code now centres it (the middle of the
  plate's own drawing) against the place it was centred on before,
- the corner title: the two lines the code draws it in now - the last word of the name as the name,
  and the words before it as one small word over it, spaced letter by letter and centred on the name -
  against the single line it used to be drawn in, with the game's own hotbar sprite under both. It
  measures what the shape is made of: the share between the two sizes, the room between the letters
  of the small word, the room between the two lines, how far the small word stands off the middle of
  the name, and the room the two of them keep over the foot of the screen - the room of the left of the
  title, which is where the owner asked for the title of the corner to stand. A name low enough to
  reach the columns the game's own HUD takes is drawn over that HUD instead, and that case is measured
  here as well. The numbers the shape was taken from are read off the picture the owner sent by
  measure_small_title.py.

It writes _inspect/preview_title.html and prints what the pictures measure.

    python _inspect/preview_title.py
"""

import base64
import io
import pathlib
import sys
import zipfile

from PIL import Image

from preview_font import JAR, load_font, draw_text, text_width

FRAMES = pathlib.Path("src/main/resources/assets/immersive_titles/textures/title")
OUT = pathlib.Path("_inspect/preview_title.html")

FRAME_CROP = 9
CAP_HEIGHT = 7.0
MAX_WIDTH = 0.94
MAX_HEIGHT = 0.70

# the plate, measured on the frames: the room between the two ornaments, and the middle of it
PLATE_LEFT, PLATE_RIGHT = 444, 990
HOLLOW_TOP, HOLLOW_BOTTOM = 197, 342
PLATE_MIDDLE_X = 720.0
HOLLOW_MIDDLE_Y = 269.5

# where the code used to centre the name, and the room it used to size it in
OLD_CENTER_X, OLD_CENTER_Y = 727.5, 258.0
ROOM_WIDTH, ROOM_HEIGHT = 807, 120

FRAME = 60
CROP = (300, 120, 1140, 460)

# the corner title as the mod draws it: the name at 2.5 and the small word over it at 1, one gui
# pixel per pixel, with the single line of the name at 2.5 to compare the two shapes
CORNER_NAME_SCALE = 2.5
CORNER_WORD_SCALE = 1.0
CORNER_WORD_SPACING = 3
CORNER_LINE_GAP = 6
SCREEN = (640, 360)  # a small gui, where the hotbar is closest to the corner
CORNER_MARGIN = 20
CORNER_BOTTOM_MARGIN = CORNER_MARGIN  # the owner asked for the room the left of the title is given
HUD_ROOM, HUD_GAP = 39, 4
HUD_BAR_HALF = 91


def plate_name(font, name, center_x, center_y):
    """The plate's frame with the name in it, at the scale the code computes for it."""
    frame = Image.open(FRAMES / f"frame_{FRAME:04d}.png").convert("RGBA")
    width = text_width(font, name)
    scale = min(ROOM_HEIGHT * MAX_HEIGHT / CAP_HEIGHT, ROOM_WIDTH * MAX_WIDTH / max(1, width))

    text = Image.new("RGBA", (max(1, width), 8), (255, 255, 255, 0))
    draw_text(font, name, text, 0, 0, 1.0, 255)
    text = text.resize((max(1, round(width * scale)), max(1, round(8 * scale))), Image.NEAREST)

    left = round(center_x - width * scale / 2.0)
    top = round(center_y - (CAP_HEIGHT * scale) / 2.0)
    frame.alpha_composite(text, (left, top))

    return frame, scale


def name_ink(frame, plain):
    """The box of the pixels the name added to the frame: (left, top, right, bottom)."""
    box = [None, None, None, None]
    a = frame.load()
    b = plain.load()

    for y in range(frame.size[1]):
        for x in range(frame.size[0]):
            if a[x, y] != b[x, y]:
                box[0] = x if box[0] is None else min(box[0], x)
                box[1] = y if box[1] is None else min(box[1], y)
                box[2] = x if box[2] is None else max(box[2], x)
                box[3] = y if box[3] is None else max(box[3], y)

    return box


def hotbar():
    """The game's own hotbar sprite: 182 x 22, drawn from the bottom edge of the screen up."""
    with zipfile.ZipFile(JAR) as jar:
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/gui/sprites/hud/hotbar.png"))).convert("RGBA")


def text_image(font, text, scale, spacing=0):
    """A line of the game's font at a scale, one letter at a time while it is spaced."""
    width = text_width(font, text) + spacing * (len(text) - 1)
    image = Image.new("RGBA", (max(1, width), 8), (0, 0, 0, 0))
    pen = 0

    for letter in text:
        one = Image.new("RGBA", (max(1, font.advance(letter)), 8), (0, 0, 0, 0))
        draw_text(font, letter, one, 0, 0, 1.0, 255)
        image.alpha_composite(one, (pen, 0))
        pen += font.advance(letter) + spacing

    return image.resize((max(1, round(image.size[0] * scale)), max(1, round(image.size[1] * scale))),
                        Image.NEAREST)


def mock_screen():
    """A mock screen with the game's own hotbar and the row of hearts over it."""
    screen = Image.new("RGBA", SCREEN, (28, 30, 34, 255))
    bar = hotbar()
    screen.alpha_composite(bar, (SCREEN[0] // 2 - bar.size[0] // 2, SCREEN[1] - bar.size[1]))

    # the row of hearts over the hotbar, 9 pixels tall, 17 pixels over it (the game's own numbers)
    hearts = Image.new("RGBA", (182, 9), (200, 30, 30, 255))
    screen.alpha_composite(hearts, (SCREEN[0] // 2 - 91, SCREEN[1] - 39))

    return screen


def corner_name_row(font, name, screen=SCREEN):
    """The row the code draws the name at: the corner of the screen, or over the HUD where it reaches it."""
    if CORNER_MARGIN + name.size[0] > screen[0] / 2 - HUD_BAR_HALF:
        return screen[1] - HUD_ROOM - HUD_GAP - int(9 * CORNER_NAME_SCALE)

    return screen[1] - CORNER_BOTTOM_MARGIN - round(CAP_HEIGHT * CORNER_NAME_SCALE)


def corner_screen(font, text):
    """A mock screen with the corner title drawn on it: the name, and the small word over it."""
    screen = mock_screen()
    words = text.split(" ")
    name = text_image(font, words[-1], CORNER_NAME_SCALE)
    y = corner_name_row(font, name)
    screen.alpha_composite(name, (CORNER_MARGIN, y))

    if len(words) > 1:
        word = text_image(font, " ".join(words[:-1]), CORNER_WORD_SCALE, CORNER_WORD_SPACING)
        # the code centres the word on the name by the advances of the two, and rounds the half up
        # the way Java's Math.round does, not the way Python's round does
        x = CORNER_MARGIN + int((name.size[0] - word.size[0]) / 2 + 0.5)
        word_y = y - CORNER_LINE_GAP - round(CAP_HEIGHT * CORNER_WORD_SCALE)
        screen.alpha_composite(word, (x, word_y))

    return screen, y


def old_corner_screen(font, name):
    """The corner title as one line: the whole name at the one size, over the HUD as it was drawn."""
    screen = mock_screen()
    y = SCREEN[1] - HUD_ROOM - HUD_GAP - int(9 * CORNER_NAME_SCALE)
    screen.alpha_composite(text_image(font, name, CORNER_NAME_SCALE), (CORNER_MARGIN, y))

    return screen


def text_boxes(screen):
    """The lines of the corner title drawn on a mock screen, as (top, bottom, left, right) each.

    The columns left of the HUD's bar are read and not the screen's: the title is drawn there, and the
    sprites of the bar are not text.
    """
    pixels = screen.load()
    points = [(x, y)
              for y in range(SCREEN[1] // 2, SCREEN[1])
              for x in range(SCREEN[0] // 2 - HUD_BAR_HALF)
              if pixels[x, y][3] > 0 and pixels[x, y][0] > 170]
    boxes = []

    for band in bands({y for _, y in points}):
        line = [(x, y) for x, y in points if y in set(band)]
        boxes.append((band[0], band[-1], min(x for x, _ in line), max(x for x, _ in line)))

    return boxes


def bands(values):
    """The runs of rows or columns that have ink in them, one run per line or letter."""
    out = []

    for one in sorted(values):
        if out and one - out[-1][-1] <= 1:
            out[-1].append(one)
        else:
            out.append([one])

    return out


def main():
    font = load_font()
    plain = Image.open(FRAMES / f"frame_{FRAME:04d}.png").convert("RGBA")
    pictures = []

    print("the big title, measured in frame pixels (the plate's middle is column 720, row 269.5):")
    for name in ("PALE GARDEN", "SPRUCE TAIGA", "PLAINS"):
        for label, center_x, center_y in (("before", OLD_CENTER_X, OLD_CENTER_Y),
                                         ("now", PLATE_MIDDLE_X, HOLLOW_MIDDLE_Y)):
            frame, scale = plate_name(font, name, center_x, center_y)
            left, top, right, bottom = name_ink(frame, plain)
            middle_x = (left + right + 1) / 2.0
            middle_y = (top + bottom + 1) / 2.0
            print(f"  {name:14s} {label:6s} scale {scale:5.2f}  ink columns {left}..{right}"
                  f" -> middle {middle_x:6.1f} ({middle_x - PLATE_MIDDLE_X:+5.1f}),"
                  f" rows {top}..{bottom} -> middle {middle_y:6.1f} ({middle_y - HOLLOW_MIDDLE_Y:+5.1f})")

            buffer = io.BytesIO()
            frame.crop(CROP).save(buffer, "PNG")
            pictures.append((f"{name} - {label} - ink middle column {middle_x:.1f}, row {middle_y:.1f}"
                             if label == "now" else f"{name} - {label} the old box",
                             scale, base64.b64encode(buffer.getvalue()).decode()))

    print(f"\nthe corner title in a {SCREEN[0]}x{SCREEN[1]} gui: the HUD takes its feet"
          f" ({SCREEN[1] - 22} to {SCREEN[1]} of the hotbar, the row of hearts at {SCREEN[1] - 39})"
          f" and its columns from {SCREEN[0] // 2 - HUD_BAR_HALF} to {SCREEN[0] // 2 + HUD_BAR_HALF}:")

    for text in ("SPRUCE TAIGA", "PALE GARDEN", "PLAINS"):
        screen, name_row = corner_screen(font, text)
        boxes = text_boxes(screen)
        foot = boxes[-1][1]
        print(f"  {text}: the name at row {name_row}, its ink down to row {foot},"
              f" {SCREEN[1] - foot - 1} rows over the foot of the screen")

        if len(boxes) == 2:
            word, name = boxes
            word_rows = word[1] - word[0] + 1
            name_rows = name[1] - name[0] + 1
            gap = name[0] - word[1] - 1
            middles = abs((word[2] + word[3]) / 2 - (name[2] + name[3]) / 2)
            print(f"    the small word at rows {word[0]}-{word[1]}, columns {word[2]}-{word[3]}:"
                  f" {word_rows} rows against {name_rows} of the name ({word_rows / name_rows:.2f}),"
                  f" {gap} rows between the two, its middle {middles:.1f} off the middle of the name")

        buffer = io.BytesIO()
        screen.save(buffer, "PNG")
        pictures.append((f"corner title {text} - the two lines", CORNER_NAME_SCALE,
                         base64.b64encode(buffer.getvalue()).decode()))

    narrow = (320, 240)
    print(f"\n  on a {narrow[0]}x{narrow[1]} gui the HUD's columns start at {narrow[0] // 2 - HUD_BAR_HALF},"
          f" so a name that reaches them is drawn over the HUD and not through it:")

    for text in ("PALE GARDEN", "THE END"):
        name = text_image(font, text.split(" ")[-1], CORNER_NAME_SCALE)
        room = CORNER_MARGIN + name.size[0]
        row = corner_name_row(font, name, narrow)
        print(f"    {text}: the name {name.size[0]} wide reaches column {room}"
              f" ({'over the HUD at row' if room > narrow[0] / 2 - HUD_BAR_HALF else 'in the corner at row'}"
              f" {row})")

    screen = old_corner_screen(font, "SPRUCE TAIGA")
    buffer = io.BytesIO()
    screen.save(buffer, "PNG")
    pictures.append(("corner title SPRUCE TAIGA - one line", CORNER_NAME_SCALE,
                     base64.b64encode(buffer.getvalue()).decode()))

    html = ["""<!doctype html><html><head><meta charset="utf-8"><title>titles</title>
<style>
  body { margin: 0; background: #0c0c0c; color: #ddd; font: 13px monospace; }
  .grid { display: grid; grid-template-columns: repeat(3, 300px); gap: 6px; padding: 8px; }
  .cell { background: #050505; padding: 4px; }
  .cell img { width: 288px; image-rendering: pixelated; display: block; }
  .cap { color: #9fe; margin-bottom: 4px; min-height: 30px; }
</style></head><body><div class="grid">"""]

    for caption, scale, data in pictures:
        html.append(f'<div class="cell"><div class="cap">{caption}</div>'
                    f'<img src="data:image/png;base64,{data}"></div>')

    html.append("</div></body></html>")
    OUT.write_text("".join(html), encoding="utf-8")
    print(f"\nwrote {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
