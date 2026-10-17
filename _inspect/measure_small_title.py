#!/usr/bin/env python3
"""Measures the small title of the picture the owner sent, which the corner title is shaped after.

The corner title of the mod is the small of its two titles, and the owner asked for it to be drawn in
the shape of the small title of Hollow Knight: Silksong - a small word over the name it belongs to.
That shape is not an impression: this reads it off the picture, so the share between the two sizes,
the room between the letters of the small word and the room between the two lines can be taken from
what the picture actually draws rather than guessed at.

What it measures, in the lower left corner of the picture:

- the rows and the columns every line of the title has ink in, which is what gives the two cap
  heights and the room between them,
- the letters of a line as the gaps between them, which is the tracking the picture sets its small
  word at,
- and how far the small word stands over the name: the two middles, against the two edges.

The picture is the screenshot the owner clipped (1366 x 768), kept out of the mod: it is a capture of
another game and no part of it is an asset of this one. What the mod takes from it is the proportions
of a layout, and it draws them with the game's own font (see preview_title.py).

    python _inspect/measure_small_title.py
"""

import pathlib
import sys

from PIL import Image

SHOT = pathlib.Path(
    r"C:\Users\GBX\AppData\Local\Packages\MicrosoftWindows.Client.Core_cw5n1h2txyewy"
    r"\TempState\ScreenClip\{A1972CEA-68B6-42C1-9EDB-C102EE6BC15E}.png")

THRESHOLD = 170  # luminance of a pixel that is a letter and not the dark room behind it
REGION = (0, 500, 560, 768)  # x0, x1, y0, y1 of the corner the title sits in


def ink(pixels, x0, x1, y0, y1):
    """The pixels of the region that are text, as (x, y)."""
    return [(x, y)
            for y in range(y0, y1)
            for x in range(x0, x1)
            if sum(pixels[x, y][:3]) / 3 > THRESHOLD]


def bands(rows, spacing=2):
    """Splits the rows or the columns that carry ink into the separate lines or letters of a title."""
    out = []

    for one in sorted(rows):
        if out and one - out[-1][-1] <= spacing:
            out[-1].append(one)
        else:
            out.append([one])

    return out


def main():
    if not SHOT.exists():
        print(f"the picture is not on this disk any more: {SHOT}")
        return 0

    image = Image.open(SHOT).convert("RGB")
    width, height = image.size
    pixels = image.load()
    x0, x1, y0, y1 = REGION

    text = ink(pixels, x0, x1, y0, y1)
    if not text:
        print("no text found in the corner of the picture")
        return 1

    lines = bands({y for _, y in text})
    print(f"picture: {width}x{height}\n")

    measured = []
    for line in lines:
        points = [(x, y) for x, y in text if y in set(line)]
        left = min(x for x, _ in points)
        right = max(x for x, _ in points)
        letters = bands({x for x, _ in points}, spacing=1)
        gaps = [b[0] - a[-1] - 1 for a, b in zip(letters, letters[1:])]
        widths = [len(letter) for letter in letters]

        print(f"line rows {line[0]}-{line[-1]} (height {line[-1] - line[0] + 1}),"
              f" columns {left}-{right} (width {right - left + 1})")
        print(f"  {len(letters)} letters, {widths} wide, gaps {gaps}")

        measured.append({"top": line[0], "bottom": line[-1], "left": left, "right": right,
                         "height": line[-1] - line[0] + 1, "letters": len(letters),
                         "gaps": gaps, "widths": widths})

    if len(measured) != 2:
        print(f"\n{len(measured)} line(s) of text in the corner, not the two of a small title")
        return 1

    word, name = measured
    print(f"\nthe small word over the name: {word['height']} rows against {name['height']},"
          f" {word['height'] / name['height']:.2f} of it")
    print(f"the room between the two lines: {name['top'] - word['bottom'] - 1} rows,"
          f" {(name['top'] - word['bottom'] - 1) / name['height']:.2f} of the name")
    print(f"the middle of the small word is column {(word['left'] + word['right'] + 1) / 2},"
          f" the middle of the name column {(name['left'] + name['right'] + 1) / 2}:"
          f" {(word['left'] + word['right'] + 1) / 2 - (name['left'] + name['right'] + 1) / 2:+.1f}")
    print(f"the left of the name is column {name['left']},"
          f" {name['left'] / width:.3f} of the width of the picture")
    print(f"the foot of the name is row {name['bottom']},"
          f" {height - name['bottom'] - 1} rows over the bottom of the picture"
          f" ({(height - name['bottom'] - 1) / height:.3f} of its height)\n")

    for label, line in (("small word", word), ("name", name)):
        if not line["gaps"]:
            continue
        gap = sum(line["gaps"]) / len(line["gaps"])
        letter = sum(line["widths"]) / len(line["widths"])
        print(f"the {label}: letters {letter:.1f} wide with {gap:.1f} between them,"
              f" {gap / letter:.2f} of a letter")

    return 0


if __name__ == "__main__":
    sys.exit(main())
