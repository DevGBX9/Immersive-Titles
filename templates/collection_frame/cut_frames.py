#!/usr/bin/env python3
"""Cuts the two lines framing the biome collection out of their sheet.

The two drawings - `Up-line.png` and `Down-line.png` in the owner's desktop - are white line art
on a flat dark sheet, saved as JPEG, so every pixel of them is a blend of the white of the art and
the grey of the sheet behind it. That blend is what this script turns back into what it means: the
alpha of a pixel is how much of it is art, and its colour is nothing but white. What comes out is
the same drawing with the sheet gone - no edge of a rectangle around it, no grey fringe on its
edges, and no trace of the sheet's noise left in it, which is what "cut out" has to mean for a
texture that is drawn over a world rather than over the sheet it was drawn on.

The alpha is not guessed at. The sheet is one grey (measured at 17) and the art is white (255), so
a pixel that came out of the camera at 136 is a pixel that was half covered by the art - the sheet
and the art mixed in the one proportion that lands on 136. Reading every pixel that way recovers
the coverage of the original drawing, its antialiasing included, instead of thresholding it into a
shape with broken edges.

What is above that - the noise of the sheet and the ring the JPEG leaves around every strong edge,
both of which read as a few percent of coverage - is taken out before anything is scaled, so the
haze cannot be baked into the line by the scaling that follows. What is left is cut down to the
art's own rectangle and scaled to exactly the size the screen draws it in, with an area filter, so
that the thin parts of the drawing keep their weight instead of being dropped or doubled by a
nearest neighbour scale.

The two sheets it cuts are kept next to it, so the cut can be remade from them at any time. Run it
from anywhere; it writes straight into the mod's assets:

    python templates/collection_frame/cut_frames.py

The sizes it writes are not free. Each drawing is written at twice the size the screen draws it in,
so that a game at gui scale 2 draws it one to one and a game at gui scale 1 has a clean area filter
to read - and, more importantly, at the share of height to width the drawing itself has. A line of
this kind is a drawing of a thing, not a pattern to be repeated or stretched: it is drawn centred
above or below the list at a size that keeps its own proportions, and stretching it to the width of
the list is what makes it read as a smear instead of as an ornament. The sizes below are therefore
one number each - the height the screen draws it at, doubled - with the width that height comes to.
"""

from pathlib import Path

import numpy as np
from PIL import Image

# the sheet the art was drawn on, and the white it was drawn in, as the sheet measures them
SHEET_GREY = 17.0
ART_WHITE = 255.0

# coverage below this is the sheet's noise and the JPEG's ring rather than the art, so it is dropped
# and what is left is stretched back over the whole range
NOISE = 0.06

# and the opposite: the contrast the line is given once it is scaled down, which turns a soft edge
# of a few percent into a soft edge of a pixel or two without turning the antialiasing into a step
CURVE_FROM = 0.12
CURVE_SPAN = 0.60

# what the screen draws the drawings as: the file, its name in the mod, and the size it is written
# at, which is a little over twice the size it is drawn in - the screen draws the line over the list
# at two fifths of this file and the one under it at a little under half. The two widths are the
# heights below at the drawings' own proportions: 20 gui pixels of the up line is 152 wide, 26 of the
# down line is 162.
# The heights are the one number that matters - a drawing of this kind is nearly 40% ink inside its
# own box, so its whole structure has to be given the room to resolve back out of it, and at a size
# that leaves it a few pixels tall only the boldest parts of it survive as a white smear.
SHEETS = [
    ("Up-line.png", "collection_up_line.png", 396, 52),
    ("Down-line.png", "collection_down_line.png", 362, 58),
]

SOURCE = Path(__file__).resolve().parent
DESTINATION = SOURCE.parents[1] / \
    "src" / "main" / "resources" / "assets" / "immersive_titles" / "textures" / "gui"


def coverage_of(path):
    """How much of every pixel of the drawing at `path` is art, from 0 to 1."""
    sheet = np.asarray(Image.open(path).convert("L"), dtype=np.float32)

    coverage = np.clip((sheet - SHEET_GREY) / (ART_WHITE - SHEET_GREY), 0.0, 1.0)

    return np.clip((coverage - NOISE) / (1.0 - NOISE), 0.0, 1.0)


def art_box(coverage):
    """The rectangle the art itself takes up, as (left, top, right, bottom), right and bottom out."""
    rows = np.where((coverage > 0.5).any(axis=1))[0]
    columns = np.where((coverage > 0.5).any(axis=0))[0]

    if rows.size == 0 or columns.size == 0:
        raise SystemExit("nothing is drawn in this sheet")

    return int(columns[0]), int(rows[0]), int(columns[-1]) + 1, int(rows[-1]) + 1


def cut(path, width, height):
    """The drawing at `path`, with its sheet gone, scaled to `width` x `height` pixels of alpha."""
    whole = coverage_of(path)
    left, top, right, bottom = art_box(whole)

    art = Image.fromarray((whole[top:bottom, left:right] * 255.0).round().astype(np.uint8), "L")
    art = art.resize((width, height), Image.BOX)

    alpha = np.asarray(art, dtype=np.float32) / 255.0
    alpha = np.clip((alpha - CURVE_FROM) / CURVE_SPAN, 0.0, 1.0)

    return np.round(alpha * 255.0).astype(np.uint8), (right - left, bottom - top)


def main():
    DESTINATION.mkdir(parents=True, exist_ok=True)

    for sheet, name, width, height in SHEETS:
        alpha, source = cut(SOURCE / sheet, width, height)

        # a pixel of a white line is white; only how much of it there is is what the alpha says
        picture = np.empty((height, width, 4), dtype=np.uint8)
        picture[..., :3] = 255
        picture[..., 3] = alpha

        target = DESTINATION / name
        Image.fromarray(picture, "RGBA").save(target, optimize=True)

        solid = (alpha >= 250).mean()
        clear = (alpha == 0).mean()
        print(f"{name}: {width}x{height} from {source[0]}x{source[1]} of art, "
              f"{solid:.1%} solid, {clear:.1%} clear, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
