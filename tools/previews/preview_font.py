#!/usr/bin/env python3
"""The game's own font, read out of the client jar, for the previews in this folder.

The mod draws its titles with the game's font, and this device does not build the mod, so the only
way to see a title before it is built is to draw it with the font the game itself uses: the atlas
(`assets/minecraft/textures/font/ascii.png`) and the character grid that goes with it, straight out
of the client jar that is already on disk.

An advance is what the game advances the pen by for a character: the width of the glyph's ink in its
cell, plus the one empty column the game keeps after every glyph. The space is not in the atlas at
all - the game takes it from its own space provider, four pixels wide.

That is one pixel short of the pen the game moves under the letters a title is set in: what the
owner's own screenshots measure is a pen of seven gui pixels for a letter of five of ink - six of ink
with the bold the titles are drawn in - where this model works out six. A line drawn here is therefore
a little narrower than the same line in the game, which is a difference of the picture and not of the
title: the mod asks the game for its own font when it places one, so what the previews in this folder
are for is the shape of a title rather than the last pixel of it.
"""

import io
import json
import pathlib
import zipfile

from PIL import Image

JAR = pathlib.Path(
    r"C:\Users\GBX\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft"
    r"\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar")

CELL = 8
SPACE_ADVANCE = 4


class Font:
    def __init__(self, atlas, chars):
        self.atlas = atlas
        self.cells = {}
        self.advances = {}

        for row, line in enumerate(chars):
            for column, char in enumerate(line):
                if char == "\0":
                    continue

                self.cells[char] = (column * CELL, row * CELL)
                self.advances[char] = ink_width(atlas, column * CELL, row * CELL) + 1

    def advance(self, char):
        if char == " ":
            return SPACE_ADVANCE
        return self.advances.get(char, SPACE_ADVANCE)

    def glyph(self, char):
        """The cell of a character as an image, or None for a character the atlas has not got."""
        cell = self.cells.get(char)
        if cell is None:
            return None
        x, y = cell
        return self.atlas.crop((x, y, x + CELL, y + CELL))

    def width(self, text):
        return sum(self.advance(char) for char in text)


def ink_width(atlas, x, y):
    """How many columns of a cell have any ink in them."""
    width = 0

    for column in range(CELL):
        for row in range(CELL):
            if atlas.getpixel((x + column, y + row))[3] > 0:
                width = column + 1
                break

    return width


def load_font():
    with zipfile.ZipFile(JAR) as jar:
        atlas = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
        default = json.loads(jar.read("assets/minecraft/font/include/default.json").decode("utf-8"))

    chars = next(p["chars"] for p in default["providers"] if p.get("file", "").endswith("ascii.png"))

    return Font(atlas, chars)


def text_width(font, text, bold=True):
    """The width of a line as the game measures it: bold text keeps the same advances."""
    return font.width(text)


def draw_text(font, text, image, x, y, scale, alpha, bold=True):
    """Draws a line of text into an image at a whole scale, as the game draws it."""
    pixels = image.load()
    pen = 0

    for char in text:
        glyph = font.glyph(char)
        advance = font.advance(char)

        if glyph is not None:
            for column in range(CELL):
                for row in range(CELL):
                    r, g, b, a = glyph.getpixel((column, row))
                    if a == 0:
                        continue
                    for dx in range(int(scale)):
                        for dy in range(int(scale)):
                            px = x + pen * scale + column * scale + dx
                            py = y + row * scale + dy
                            if 0 <= px < image.size[0] and 0 <= py < image.size[1]:
                                pixels[px, py] = (r if r else 255, g if g else 255, b if b else 255,
                                                  int(a * alpha / 255))
                    if bold:
                        # the game draws bold by drawing the glyph once more, one texel to the right
                        for dx in range(int(scale)):
                            for dy in range(int(scale)):
                                px = x + pen * scale + (column + 1) * scale + dx
                                py = y + row * scale + dy
                                if 0 <= px < image.size[0] and 0 <= py < image.size[1]:
                                    pixels[px, py] = (255, 255, 255, int(a * alpha / 255))

        pen += advance

    return pen
