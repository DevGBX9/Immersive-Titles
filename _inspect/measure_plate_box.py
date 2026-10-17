#!/usr/bin/env python3
"""Measures the hollow middle of the plate in the real frames of the clip.

The plate is one ornament over the name and one under it, with the name written into the hollow
between them; the box the overlay writes the name into is NAME_BOX_* in TitleOverlay, measured by
hand. This reads the frames themselves and reports, for each frame:

- the rows and columns of the plate's ink, as a box (the frames are white on transparent, so the
  plate is the alpha channel and nothing else),
- the hollow between the two ornaments, which is what the name has to sit in,
- and how far that hollow's middle is from the middle of the picture and from the box the overlay
  uses, so a name drawn into that box can be seen to be off centre before anyone builds anything.

    python _inspect/measure_plate_box.py
"""

import pathlib
import sys

from PIL import Image

FRAMES = pathlib.Path("src/main/resources/assets/immersive_titles/textures/title")
SAMPLES = [40, 60, 80, 100, 120, 160]

# the box TitleOverlay draws the name into, in frame pixels
BOX_X = 324
BOX_Y = 198
BOX_WIDTH = 807
BOX_HEIGHT = 120


def ink(image, threshold=128):
    """The box of the plate's ink: (left, top, right, bottom) of the pixels at or above the alpha."""
    alpha = image.getchannel("A")
    width, height = image.size
    pixels = alpha.load()

    left, top, right, bottom = width, height, -1, -1

    for y in range(height):
        for x in range(width):
            if pixels[x, y] >= threshold:
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)

    return left, top, right, bottom


def rows_of_ink(image, row, threshold=128):
    """Whether the row has any ink at all, and the columns it spans."""
    alpha = image.getchannel("A")
    pixels = alpha.load()
    columns = [x for x in range(image.size[0]) if pixels[x, row] >= threshold]

    return (columns[0], columns[-1]) if columns else None


def main():
    center_of_picture = 720.0

    for frame in SAMPLES:
        image = Image.open(FRAMES / f"frame_{frame:04d}.png").convert("RGBA")
        left, top, right, bottom = ink(image)

        # the hollow is what is left between the two ornaments: the rows of the frame the plate does
        # not touch, kept inside the columns the plate itself spans
        empty_rows = [y for y in range(top, bottom + 1) if rows_of_ink(image, y) is None]
        first = empty_rows[0] if empty_rows else None
        last = empty_rows[-1] if empty_rows else None

        print(f"frame_{frame:04d}")
        print(f"   plate ink: columns {left}..{right}, rows {top}..{bottom}")
        print(f"   plate middle: {(left + right) / 2.0:.1f} (picture middle {center_of_picture:.1f},"
              f" off by {(left + right) / 2.0 - center_of_picture:+.1f})")

        if first is not None:
            hollow_middle = (first + last) / 2.0
            box_middle = BOX_Y + BOX_HEIGHT / 2.0
            print(f"   hollow: rows {first}..{last}, middle {hollow_middle:.1f}"
                  f" (over the box middle {box_middle:.1f}, off by {box_middle - hollow_middle:+.1f})")

        box_center = BOX_X + BOX_WIDTH / 2.0
        print(f"   box the name is drawn into: columns {BOX_X}..{BOX_X + BOX_WIDTH},"
              f" middle {box_center:.1f} (plate middle {(left + right) / 2.0:.1f},"
              f" off by {box_center - (left + right) / 2.0:+.1f})")
        print()

    return 0


if __name__ == "__main__":
    sys.exit(main())
