#!/usr/bin/env python3
"""Checks the two drawings that frame the list against the screen that draws them.

The lines over and under the biome collection are drawn by
`templates/collection_frame/draw_ornaments.py` at exactly the size the screen draws them at - one
texture pixel to one gui pixel - and what makes that work is a set of properties of the drawings
rather than of the code that reads them:

- each is the size the screen's own constants name, and the one over the list is as wide as the strip
  of the two tabs it stands over, since the plate and the bar of two pages are one thing,
- both are about a quarter shorter than the drawings they replace, which is what the frame of the list
  was asked to cost (30 gui pixels to 22, 26 to 18),
- and both are thin and even: a thread is a pixel or two, nothing anywhere is wider than the head of the
  needle, every stroke keeps clear of the edge of the drawing, and the left half of it is the right half
  in a mirror.

The drawings are read as pictures here and the sizes out of the screen's source: a check of two text
facts, one of them a PNG and one of them the constants of the screen, and no compiler takes part in it.

    python _inspect/check_ornaments.py
"""

import pathlib
import re
import sys

from PIL import Image

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")
DRAWING = pathlib.Path("templates/collection_frame/draw_ornaments.py")
TEXTURES = pathlib.Path("src/main/resources/assets/immersive_titles/textures/gui")

DECLARATION = re.compile(r"(?:private|static|final|\s)*int\s+([A-Z][A-Z_0-9]*)\s*=\s*([^;]+);")
LITERAL = re.compile(r"^[A-Za-z_0-9\s+*/\-()]+$")

# the two drawings, the name of the constants the screen reads them by, and the height the drawing
# they were made to replace was drawn at - what "a quarter shorter" is measured against
DRAWINGS = [
    ("collection_up_line.png", "UP_LINE_WIDTH", "UP_LINE_HEIGHT", 30),
    ("collection_down_line.png", "DOWN_LINE_WIDTH", "DOWN_LINE_HEIGHT", 26),
]

# what "thin" means here: no more than this many pixels of ink in any one column of the drawing, and
# no stroke within this many pixels of its edge. A column is how a thread that runs across the drawing
# is measured - a row of it can be as long as the thread is, and that is the thread being a thread -
# and the needle is the one part of the drawing that is meant to be tall rather than long, so its own
# middle columns are read against the size the drawing gives it instead.
THICKEST_COLUMN = 4
MARGIN = 1

# and what the drawings are meant to carry: airy, where the pair they replace carried two fifths of
# their box. A drawing of this kind that came in much above this is a smear of the kind they replaced.
LEAST_INK = 0.03
MOST_INK = 0.15


def constants():
    """Every int constant the screen declares, evaluated in the order it declares them."""
    known = {}

    for name, expression in DECLARATION.findall(SCREEN.read_text(encoding="utf-8")):
        # a constant too long for one line is written on two, and the arithmetic between the names of
        # them is the same whether it is read on one line or two, so the wrapping is undone here
        expression = " ".join(expression.split())

        if LITERAL.match(expression) and "//" not in expression:
            known[name] = int(eval(expression, {"__builtins__": {}}, known))   # noqa: S307

    return known


def shares():
    """How much of its own height the drawing gives the needle, and how wide the head of it is."""
    body = DRAWING.read_text(encoding="utf-8")
    found = {}

    for name in ("NEEDLE_SHARE", "HEAD_SHARE"):
        match = re.search(rf"^{name} = ([0-9.]+)$", body, re.MULTILINE)

        if match is None:
            print(f"MISSING {name} is not a constant of {DRAWING.name}")
            raise SystemExit(1)

        found[name] = float(match.group(1))

    return found


def main():
    known = constants()
    needle = shares()
    failures = []

    for name, width_key, height_key, before in DRAWINGS:
        if width_key not in known or height_key not in known:
            print(f"MISSING {width_key} or {height_key} is not a constant of {SCREEN.name}")
            return 1

        image = Image.open(TEXTURES / name)
        alpha = image.getchannel("A")
        width, height = image.size
        pixels = alpha.load()

        column = [sum(1 for y in range(height) if pixels[x, y] > 127) for x in range(width)]
        row = [sum(1 for x in range(width) if pixels[x, y] > 127) for y in range(height)]
        ink = sum(alpha.get_flattened_data()) / (255.0 * width * height)
        mirrored = all((pixels[x, y] > 127) == (pixels[width - 1 - x, y] > 127)
                       for y in range(height) for x in range(width))

        # the needle is the middle of the drawing, as wide as the head the drawing gives it: what is
        # measured against the thread rule is everything beside it
        head = max(2, int(round(height * needle["HEAD_SHARE"] / 2.0)) * 2)
        middle = width // 2
        beside = [count for x, count in enumerate(column) if abs(x - middle) > head // 2]

        drawn = (known[width_key], known[height_key])
        tall = int(round(height * needle["NEEDLE_SHARE"]))
        thickest = max(beside)
        tallest = max(column)
        margins = [next(i for i, count in enumerate(column) if count),
                   next(i for i, count in enumerate(column[::-1]) if count),
                   next(i for i, count in enumerate(row) if count),
                   next(i for i, count in enumerate(row[::-1]) if count)]

        print(f"{name}")
        print(f"   {width}x{height}, the screen draws it at {drawn[0]}x{drawn[1]}, "
              f"{before} tall before: {100.0 * (before - drawn[1]) / before:.0f}% shorter")
        print(f"   {ink:.1%} of its box is ink, its thickest thread column is {thickest} pixels and its "
              f"tallest is the needle's {tallest} of {tall}")
        print(f"   it keeps {min(margins)} pixel(s) off its edge and is mirrored exactly: {mirrored}")

        if (width, height) != drawn:
            failures.append(f"{name} is {width}x{height} while the screen draws it at "
                            f"{drawn[0]}x{drawn[1]}")

        if not 25 <= 100.0 * (before - drawn[1]) / before <= 35:
            failures.append(f"{name} is not between a quarter and a third shorter than the drawing "
                            f"it replaces: {before} gui pixels to {drawn[1]}")

        if not mirrored:
            failures.append(f"{name} is not a mirror of itself, so its two halves are two drawings")

        if not LEAST_INK <= ink <= MOST_INK:
            failures.append(f"{name} carries {ink:.1%} of its box in ink, against the "
                            f"{LEAST_INK:.0%} to {MOST_INK:.0%} a drawing of this kind is read at")

        if thickest > THICKEST_COLUMN:
            failures.append(f"{name} has {thickest} pixels of ink in one column of thread, so something "
                            f"beside the needle is not a thread")

        if tallest > tall:
            failures.append(f"{name} is {tallest} pixels tall in one column against the {tall} the "
                            f"needle of it is drawn at")

        if min(margins) < MARGIN:
            failures.append(f"{name} comes within {min(margins)} pixel(s) of its own edge")

    # the plate of the screen is the width of the strip of the two tabs under it: the two are one thing,
    # and a drawing of a width of its own would leave the pair out of true
    if "TAB_STRIP_WIDTH" not in known:
        print("MISSING TAB_STRIP_WIDTH is not a constant of the screen")
        return 1

    if known["UP_LINE_WIDTH"] != known["TAB_STRIP_WIDTH"]:
        failures.append(f"the line over the list is {known['UP_LINE_WIDTH']} gui pixels wide against "
                        f"the {known['TAB_STRIP_WIDTH']} of the strip of tabs it stands over")

    if failures:
        print()

        for failure in failures:
            print(f"OFF  {failure}")
        return 1

    print(f"\nthe plate is the {known['TAB_STRIP_WIDTH']} gui pixels of the strip of tabs under it, and both "
          "drawings are thin, even and as wide as the screen draws them")
    return 0


if __name__ == "__main__":
    sys.exit(main())
