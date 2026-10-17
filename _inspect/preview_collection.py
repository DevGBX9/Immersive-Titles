#!/usr/bin/env python3
"""Draws the parts of the collection screen that were changed, the way the game draws them, and
measures them.

The device this runs on does not build the mod, so the two changed places - the strip of the two tabs
and the cross at the end of a row of a discovery - are drawn here out of the sprites the screen names,
with the game's own font, and with the game's own sprite drawing read out of the client jar: a sprite
is nine sliced or tiled exactly as `GuiGraphicsExtractor.blitSprite` does it, borders clamped to half
of the box and the inner segments repeated rather than stretched (the pack's mcmeta does not ask for
`stretch_inner`), so what is drawn here is what the screen draws.

Each place is drawn twice, as it was and as it is now:

- the strip: the page being read used to be a button of the pack under a black wash of the mod's and a
  line of gold along its bottom edge, which is the green gone dark; it is now the pack's own lit
  button, untouched.
- the end of a row of a discovery: the button that gives a reveal back has been drawn three ways in
  this repository - the pack's own button twelve pixels square with the game's own `X` over it, then
  the pack's whole cross at eighteen pixels in a row of fourteen, then the pack's own square button,
  a row tall, with that letter in the pack's red - and it is the first of the three that the screen
  draws again: twelve pixels square at the far right end of the row, flat and grey while the mouse is
  only on the row, lit and red while it is on the button itself.

What the pictures are for is the second one, and they are measured as well as drawn: the columns of
the row the control changes are counted, and the colours it is drawn in are held against the row's
own, since what a control drawn in the colours of the row under it comes to is a seam in the row.

    python _inspect/preview_collection.py
"""

import base64
import io
import json
import pathlib
import re
import sys
import zipfile

from PIL import Image

import preview_font

HERE = pathlib.Path(__file__).resolve().parent
SHIPPED = HERE.parents[0] / "src/main/resources/assets/immersive_titles/textures/gui/sprites"
PACK = (pathlib.Path.home() / "Desktop" / "Reimagined GUI - Dark - 1.20.2-26.x" / "assets"
        / "minecraft" / "textures" / "gui" / "sprites")
SCREEN = HERE.parents[0] / "src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java"
OUT = HERE / "preview_collection.html"

# the drawing of the strip before the change: the wash over the button of the tab being read and over
# the other one, and the line of the accent along the bottom edge of the first (the constants the
# screen carried as TAB_ACTIVE_WASH, TAB_INACTIVE_WASH and the accent line of drawTabs)
OLD_TAB_ACTIVE_WASH = 0x99000000
OLD_TAB_INACTIVE_WASH = 0x40000000

SCALE = 3


class Sprite:
    """A gui sprite: its drawing and the nine slice its mcmeta asks for, if it has one."""

    def __init__(self, image, border=None):
        self.image = image
        self.border = border

    @property
    def size(self):
        return self.image.size


def load(path):
    """Loads a sprite of the mod's own, sliced by whatever mcmeta sits beside it."""
    image = Image.open(path).convert("RGBA")
    meta = path.with_name(path.name + ".mcmeta")

    border = None
    if meta.is_file():
        scaling = json.loads(meta.read_text(encoding="utf-8"))["gui"]["scaling"]
        if scaling.get("type") == "nine_slice":
            sides = scaling["border"]
            if isinstance(sides, int):
                sides = {name: sides for name in ("left", "top", "right", "bottom")}
            border = sides

    return Sprite(image, border)


def from_jar(name):
    """Loads one of the game's own sprites out of the client jar, sliced by its own mcmeta."""
    with zipfile.ZipFile(preview_font.JAR) as jar:
        image = Image.open(io.BytesIO(
            jar.read(f"assets/minecraft/textures/gui/sprites/{name}.png"))).convert("RGBA")
        try:
            raw = jar.read(f"assets/minecraft/textures/gui/sprites/{name}.png.mcmeta")
        except KeyError:
            raw = None

    border = None
    if raw is not None:
        scaling = json.loads(raw)["gui"]["scaling"]
        if scaling.get("type") == "nine_slice":
            sides = scaling["border"]
            if isinstance(sides, int):
                sides = {name: sides for name in ("left", "top", "right", "bottom")}
            border = sides

    return Sprite(image, border)


def tinted(piece, tint):
    """The piece as it is read through a colour, the way the game multiplies a sprite by one."""
    if tint == 0xFFFFFFFF:
        return piece

    r = (tint >> 16) & 0xFF
    g = (tint >> 8) & 0xFF
    b = tint & 0xFF
    a = (tint >> 24) & 0xFF

    flat = piece.convert("RGBA")
    pixels = flat.load()
    for y in range(flat.size[1]):
        for x in range(flat.size[0]):
            pr, pg, pb, pa = pixels[x, y]
            pixels[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa * a // 255)

    return flat


def crop_scaled(sprite, sx, sy, sw, sh, dw, dh, tint, tiled):
    """One segment of a sprite: a region of it, taken to a box of the screen the way the game does.

    A segment the mcmeta does not ask to have stretched is repeated at its own size, and the last
    tile of a row or a column is cut to the box rather than squeezed into it, which is what
    `blitTiledSprite` does; a segment that is stretched is one region scaled to the box.
    """
    piece = sprite.image.crop((sx, sy, sx + sw, sy + sh))

    if not tiled or dw < sw or dh < sh:
        if tiled:
            piece = piece.crop((0, 0, min(sw, dw), min(sh, dh)))
        else:
            piece = piece.resize((max(1, dw), max(1, dh)), Image.NEAREST)
        return tinted(piece, tint)

    out = Image.new("RGBA", (dw, dh), (0, 0, 0, 0))
    for i in range(0, dw, sw):
        for j in range(0, dh, sh):
            tile = piece.crop((0, 0, min(sw, dw - i), min(sh, dh - j)))
            out.alpha_composite(tinted(tile, tint), (i, j))

    return out


def blit(canvas, sprite, x, y, w, h, tint=0xFFFFFFFF):
    """Draws a sprite at a box of the screen, nine sliced as `blitSprite` slices it.

    The borders are clamped to half of the box: a sprite drawn smaller than its own two ends has its
    ends meet in the middle of the box, and that is the drawing a small button of the pack's comes to.
    """
    if w <= 0 or h <= 0:
        return

    if sprite.border is None:
        canvas.alpha_composite(crop_scaled(sprite, 0, 0, sprite.size[0], sprite.size[1], w, h,
                                           tint, False), (x, y))
        return

    border = sprite.border
    width, height = sprite.size
    left = min(border["left"], w // 2)
    right = min(border["right"], w // 2)
    top = min(border["top"], h // 2)
    bottom = min(border["bottom"], h // 2)

    # the four corners, at their own size
    for sx, sy, sw, sh, dx, dy in ((0, 0, left, top, x, y),
                                   (width - right, 0, right, top, x + w - right, y),
                                   (0, height - bottom, left, bottom, x, y + h - bottom),
                                   (width - right, height - bottom, right, bottom,
                                    x + w - right, y + h - bottom)):
        if sw > 0 and sh > 0:
            canvas.alpha_composite(crop_scaled(sprite, sx, sy, sw, sh, sw, sh, tint, False), (dx, dy))

    # the middle of the top and of the bottom edge, and the middle of the sprite between them
    middle_w = w - left - right
    middle_h = h - top - bottom
    if middle_w > 0:
        for sy, sh, dy in ((0, top, y), (height - bottom, bottom, y + h - bottom),
                           (top, height - top - bottom, y + top)):
            if sh > 0 and (dy != y + top or middle_h > 0):
                canvas.alpha_composite(
                    crop_scaled(sprite, left, sy, width - left - right, sh, middle_w, sh, tint, True),
                    (x + left, dy))
    if middle_h > 0:
        for sx, sw, dx in ((0, left, x), (width - right, right, x + w - right)):
            if sw > 0:
                canvas.alpha_composite(
                    crop_scaled(sprite, sx, top, sw, height - top - bottom, sw, middle_h, tint, True),
                    (dx, y + top))


def fill(canvas, x, y, w, h, colour):
    """A plain fill of a colour, alpha and all, over what is already there."""
    out = Image.new("RGBA", (w, h), ((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF,
                                     (colour >> 24) & 0xFF))
    canvas.alpha_composite(out, (x, y))


def border(canvas, x, y, w, h, colour):
    """The one pixel line the row the mouse is on is held in."""
    fill(canvas, x, y, w, 1, colour)
    fill(canvas, x, y + h - 1, w, 1, colour)
    fill(canvas, x, y, 1, h, colour)
    fill(canvas, x + w - 1, y, 1, h, colour)


def text(canvas, font, line, x, y, colour, shadow=True):
    """A line of the game's own font, with the shadow the game draws under it.

    The shadow is the text's own colour taken down to a quarter of itself, which is what the game
    deepens it by, and not a grey: a red line casts a dark red shadow.
    """
    r = (colour >> 16) & 0xFF
    g = (colour >> 8) & 0xFF
    b = colour & 0xFF
    a = (colour >> 24) & 0xFF

    def piece(colour_of_it, at):
        flat = Image.new("RGBA", (max(1, font.width(line)), 9), (0, 0, 0, 0))
        preview_font.draw_text(font, line, flat, 0, 0, 1.0, a, bold=False)
        pixels = flat.load()
        for py in range(flat.size[1]):
            for px in range(flat.size[0]):
                _, _, _, pa = pixels[px, py]
                if pa:
                    pixels[px, py] = (colour_of_it[0], colour_of_it[1], colour_of_it[2], pa)
        canvas.alpha_composite(flat, at)

    if shadow:
        piece((r // 4, g // 4, b // 4), (x + 1, y + 1))

    piece((r, g, b), (x, y))


def numbers():
    """Every `static final int` of the screen, with its own arithmetic followed, as a name -> value map."""
    source = SCREEN.read_text(encoding="utf-8")
    expressions = dict(re.findall(r"static final int (\w+)\s*=\s*([^;]+);", source))
    values = {}

    def value_of(name, seen=()):
        if name in values:
            return values[name]
        if name not in expressions or name in seen:
            raise KeyError(name)
        body = " ".join(expressions[name].split())
        for other in set(re.findall(r"\b[A-Za-z_]\w*\b", body)):
            if other in expressions:
                body = re.sub(r"\b" + other + r"\b", str(value_of(other, seen + (name,))), body)
        values[name] = int(eval(body, {"__builtins__": {}}, {}))
        return values[name]

    for name in expressions:
        try:
            value_of(name)
        except (KeyError, NameError, SyntaxError, ValueError):
            pass

    return values


def upscale(image):
    return image.resize((image.size[0] * SCALE, image.size[1] * SCALE), Image.NEAREST)


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, "PNG")
    return base64.b64encode(buffer.getvalue()).decode()


def main():
    font = preview_font.load_font()
    k = numbers()

    button = load(SHIPPED / "widget/button.png")
    lit = load(SHIPPED / "widget/button_highlighted.png")
    waiting = load(SHIPPED / "widget/button_disabled.png")

    # the square button of the build before the last one, out of the pack itself: the mod no longer
    # ships it, and what it is kept for here is the picture of what was read then
    pack_square = PACK / "container" / "loom" / "pattern_selected.png"
    previous = load(pack_square) if pack_square.is_file() else None

    # the cross of the pack the two steps before that one were drawn with, out of the pack itself
    pack_cross = PACK / "container/beacon/cancel.png"
    cross = load(pack_cross) if pack_cross.is_file() else None

    tab_w, tab_h, tab_gap = k["TAB_WIDTH"], k["TAB_HEIGHT"], k["TAB_GAP"]
    row_h, pitch = k["ROW_HEIGHT"], k["ROW_PITCH"]
    margin, check_x, name_x = k["TEXT_MARGIN"], k["CHECK_X"], k["NAME_X"]
    row_w = k["LIST_MAX_WIDTH"] - k["SCROLLBAR_WIDTH"] - k["SCROLLBAR_MARGIN"]
    gold, hover = k["ACCENT_COLOR"], k["HOVER_COLOR"]
    found_colour = k["FOUND_COLOR"]
    check_colour = k["CHECK_COLOR"]
    tab_on, tab_off = k["TAB_ON_COLOR"], k["TAB_OFF_COLOR"]
    forget_set, forget_lit = k["FORGET_COLOR"], k["FORGET_HOVERED_COLOR"]

    # the sizes the two earlier drawings were drawn at: the pack's own square button at the fourteen
    # of a row, and the pack's whole cross at eighteen in a row of fourteen
    previous_size = 14
    big_cross = 18

    names = ("Biomes", "Structures")
    pictures = []
    findings = []

    # ------------------------------------------------------------------ the strip of the two tabs
    def tab_strip(hovered, changed):
        """The strip as the screen draws it: `hovered` is the tab under the mouse, or -1 for none.

        The game draws a button it is handed as the lit one while the mouse or the keyboard is on it,
        and the screen paints each of these two tabs over that button with one sprite of its own: the
        reading tab wears the lit sprite for as long as it is the page on the screen (the change), and
        the other one wears the sprite the game drew under it.
        """
        strip = Image.new("RGBA", (2 * tab_w + tab_gap, tab_h), (0, 0, 0, 0))
        x = 0

        for index, name in enumerate(names):
            reading = index == 0
            face = lit if (hovered == index or (changed and reading)) else button
            blit(strip, face, x, 0, tab_w, tab_h)

            if not changed:
                fill(strip, x, 0, tab_w, tab_h,
                     OLD_TAB_ACTIVE_WASH if reading else OLD_TAB_INACTIVE_WASH)
                if reading:
                    fill(strip, x, tab_h - 1, tab_w, 1, gold)

            text(strip, font, name, x + (tab_w - font.width(name)) // 2,
                 (tab_h - 9) // 2, tab_on if reading else tab_off)
            x += tab_w + tab_gap

        return strip

    # one pixel of each tab's own face, left of the name written over it: the names are centred, so
    # eight pixels into a tab is face in both of them
    reading_pixel = (8, tab_h // 3)
    other_pixel = (tab_w + tab_gap + 8, tab_h // 3)

    for label, hovered, changed in (("before, nothing under the mouse", -1, False),
                                    ("before, the mouse on the page being read", 0, False),
                                    ("before, the mouse on the other tab", 1, False),
                                    ("now, nothing under the mouse", -1, True),
                                    ("now, the mouse on the page being read", 0, True),
                                    ("now, the mouse on the other tab", 1, True)):
        strip = tab_strip(hovered, changed)
        reading = strip.getpixel(reading_pixel)
        other = strip.getpixel(other_pixel)

        findings.append(f"{label}: the tab of the page being read is drawn in "
                        f"{reading[0]:02X}{reading[1]:02X}{reading[2]:02X}, the other in "
                        f"{other[0]:02X}{other[1]:02X}{other[2]:02X}")
        pictures.append((f"the strip of the two tabs - {label}", strip))

    findings.append("the pack's own two buttons are 63AB3F at that pixel lit and 3F3F3F flat, "
                    "so the green the pack draws is the reading tab's and the other is the flat one")

    # ------------------------------------------------------------------ the end of a row of a discovery
    def draw_row(control, on_button=False):
        """One row: the pack's button under the line the mouse holds it in, its check and name, and
        the control at its end - in the order the screen draws the three (see drawItem).

        The control is what the screen draws there now - the pack's own button twelve pixels square,
        flat while the mouse is only on the row and lit while it is on the button itself - or one of
        the two drawings it was given before this and no longer is.
        """
        canvas = Image.new("RGBA", (row_w, row_h), (0, 0, 0, 0))
        blit(canvas, button, 0, 0, row_w, row_h)
        border(canvas, 0, 0, row_w, row_h, hover)

        if control == "previous":
            # the build before the last one: the pack's own square button, a row tall, with the
            # game's letter in the pack's red
            size, x = previous_size, row_w - previous_size
            if previous is not None:
                blit(canvas, previous, x, (row_h - size) // 2, size, size)
                text(canvas, font, "X", x + (size - font.width("X")) // 2,
                     (row_h - size) // 2 + (size - 9) // 2, forget_lit)
        elif control == "cross":
            # the build before that one: the pack's whole cross, eighteen pixels, in a row of
            # fourteen, one margin of the row's end in
            size, x = big_cross, row_w - margin - big_cross
            if cross is not None:
                blit(canvas, cross, x, (row_h - size) // 2, size, size, 0xFFFFFFFF)
        else:
            # now: the pack's own button twelve pixels square, with the game's letter over it - flat
            # and grey while the mouse is only on the row, lit and red while it is on the button
            size, x = k["FORGET_SIZE"], row_w - k["FORGET_SIZE"]
            blit(canvas, lit if on_button else button, x, (row_h - size) // 2, size, size)
            text(canvas, font, "X", x + (size - font.width("X")) // 2,
                 (row_h - size) // 2 + (size - 9) // 2,
                 forget_lit if on_button else forget_set)

        text(canvas, font, "\u2714", check_x, (row_h - 9) // 2, check_colour)
        text(canvas, font, "Pale Garden", name_x, (row_h - 9) // 2, found_colour)

        return canvas

    # the same row without any control at its end: what the other three are read against, line and
    # text and all, so what is measured is the control and nothing else
    plain_row = Image.new("RGBA", (row_w, row_h), (0, 0, 0, 0))
    blit(plain_row, button, 0, 0, row_w, row_h)
    border(plain_row, 0, 0, row_w, row_h, hover)
    text(plain_row, font, "\u2714", check_x, (row_h - 9) // 2, check_colour)
    text(plain_row, font, "Pale Garden", name_x, (row_h - 9) // 2, found_colour)

    for label, control, on_button in (("the build before the last, the pack's square button at fourteen",
                                       "previous", False),
                                      ("the build before that one, the pack's cross at eighteen", "cross",
                                       False),
                                      ("now, the mouse on the row", "square", False),
                                      ("now, the mouse on the button", "square", True)):
        drawn = draw_row(control, on_button)

        # what the control changes of the row it is drawn in: the columns it paints differently from
        # the row alone, and how far those are from the row's own colours
        a, b = drawn.load(), plain_row.load()
        columns = []
        contrast = 0
        for x in range(row_w):
            different = 0
            for y in range(row_h):
                if a[x, y] != b[x, y]:
                    different += 1
                    contrast = max(contrast, max(abs(a[x, y][i] - b[x, y][i]) for i in range(3)))
            if different:
                columns.append(x)

            # nothing of the list's own is measured: the columns of the name and the check are the
            # same in both, so a column that changed is one the control changed

        span = (f"columns {columns[0]}..{columns[-1]} of {row_w}" if columns else "nothing")
        ink = [a[x, y] for x in columns for y in range(row_h) if a[x, y] != b[x, y]]
        brightest = max(ink, key=lambda pixel: pixel[0] + pixel[1] + pixel[2]) if ink else (0, 0, 0, 0)
        reddest = max(ink, key=lambda pixel: pixel[0] - max(pixel[1], pixel[2])) if ink else (0, 0, 0, 0)

        # what the control draws of the row's own fourteen rows, and of the columns it takes
        drawn_rows = sorted({y for x in columns for y in range(row_h) if a[x, y] != b[x, y]})
        findings.append(f"{label}: {len(columns)} of the {row_w} columns of the row changed "
                        f"({span}), rows {drawn_rows[0]}..{drawn_rows[-1]} of the row's {row_h}, "
                        f"up to {contrast} of a channel away from the row's own drawing, "
                        f"its brightest pixel {brightest[0]:02X}{brightest[1]:02X}{brightest[2]:02X} "
                        f"and the reddest {reddest[0]:02X}{reddest[1]:02X}{reddest[2]:02X}")

        pictures.append((f"the end of a row of a discovery, {label}", drawn))

    # ------------------------------------------------------------------ the page
    html = ["""<!doctype html><html><head><meta charset="utf-8"><title>the collection</title>
<style>
  body { margin: 0; background: #0c0c0c; color: #ddd; font: 13px monospace; }
  h2 { font: 13px monospace; color: #9cf; margin: 16px 12px 4px; }
  p { margin: 6px 12px; max-width: 1100px; line-height: 1.5; color: #bbb; }
  .shot { background: #050505; padding: 8px 12px; }
  .shot img { image-rendering: pixelated; display: block; width: 90%; max-width: 1100px; }
  .cap { color: #9fe; margin-bottom: 6px; }
</style></head><body>
<h2>the collection, as the game draws it</h2>
<p>Every rectangle here is drawn out of the sprite the screen names, sliced the way
<code>blitSprite</code> slices it, at the gui pixels the screen's own numbers put it at. The three
pictures are at three gui pixels to a pixel of the screen.</p>
"""]

    for caption, image in pictures:
        html.append(f'<div class="shot"><div class="cap">{caption}</div>'
                    f'<img src="data:image/png;base64,{png(upscale(image))}"></div>')

    html.append("<h2>what the pictures measure</h2>\n<ul>")
    for finding in findings:
        html.append(f"<li>{finding}</li>")
    html.append("</ul></body></html>")

    OUT.write_text("".join(html), encoding="utf-8")

    for finding in findings:
        print(f"  {finding}")
    print(f"\nwrote {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
