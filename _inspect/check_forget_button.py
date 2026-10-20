#!/usr/bin/env python3
"""What the screen draws at the far right end of a row of a discovery, read out of its own source.

The button that gives a discovery back is drawn by the screen itself, and this device does not build
the mod, so the only way to know that the source draws the button that was asked for is to read the
screen's own numbers, faces and geometry out of the java file, draw a row with them the way the
screen draws one, and hold that drawing to the source alone.

Everything the drawing below uses is read out of the source rather than written here: the size of the
button and the two colours of its mark out of the `static final int` lines, the two faces it wears
out of the `Identifier` lines they point at, the letter of the mark out of the `String` line, and the
place it is drawn at out of the two lines `forgetButtonX` and `forgetButtonY` return. What is checked
is that the drawing the source describes stands inside its row, that the mark fits in the button,
and that the button's face and its lit face change nothing outside the button's own box.

    python _inspect/check_forget_button.py
"""
import io
import pathlib
import re
import sys

from PIL import Image

import preview_collection as pc
import preview_font

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")


def main():
    source = SCREEN.read_text(encoding="utf-8")
    numbers = pc.numbers()
    font = preview_font.load_font()
    failures = []

    # ---- what the source says the button is -------------------------------------------------
    size = numbers.get("FORGET_SIZE")
    on_row = numbers.get("FORGET_COLOR")
    on_button = numbers.get("FORGET_HOVERED_COLOR")

    for name, value in (("FORGET_SIZE", size), ("FORGET_COLOR", on_row),
                        ("FORGET_HOVERED_COLOR", on_button)):
        if value is None:
            failures.append(f"{name} is not one of the screen's own numbers any more")

    mark = re.search(r"private static final String FORGET_MARK = \"([^\"]*)\";", source)
    if mark is None:
        failures.append("the screen names no letter for the mark of that button")
        return report(failures)

    def face_of(constant):
        """The sprite file one of the two faces of the button is drawn out of, from its name.

        The two forget constants point at the two the screen's buttons are named with, so what is
        followed is the chain of names until a sprite the mod ships is named.
        """
        value = constant
        for _ in range(4):
            line = re.search(rf"Identifier {value} = ([^;]+);", source)
            if line is None:
                return None, f"{value} is not declared in the screen any more"

            value = line.group(1).strip()
            named = re.search(r'guiSprite\("([^"]+)"\)', value)
            if named is not None:
                path = pc.SHIPPED / f"{named.group(1)}.png"
                if not path.is_file():
                    return None, f"{constant} is drawn out of {path}, which is not shipped"
                return pc.load(path), None

            if not re.fullmatch(r"[A-Z_]+", value):
                break

        return None, f"{constant} is not one of the screen's own gui sprites: {value}"

    flat, error = face_of("FORGET_BUTTON_SPRITE")
    if error:
        failures.append(error)
    lit, error = face_of("FORGET_HOVERED_SPRITE")
    if error:
        failures.append(error)

    # where the screen draws it: the two lines that give the place, read for the arithmetic they do
    body = re.search(r"private static int forgetButtonX\(int rowRight\)\s*\{(.*?)\n\t\}", source,
                     re.S)
    if body is None or "rowRight - FORGET_SIZE" not in body.group(1):
        failures.append("the button is no longer drawn at the far right end of its row")
    body = re.search(r"private static int forgetButtonY\(int y\)\s*\{(.*?)\n\t\}", source, re.S)
    if body is None or "(ROW_HEIGHT - FORGET_SIZE) / 2" not in body.group(1):
        failures.append("the button is no longer centred in the height of its row")

    drawn = re.search(r"private void drawForgetButton.*?^\t\}", source, re.S | re.M)
    if drawn is None:
        failures.append("the screen draws no such button at all")
        return report(failures)

    if "onButton ? FORGET_HOVERED_SPRITE : FORGET_BUTTON_SPRITE" not in drawn.group(0):
        failures.append("the button does not wear its lit face under the hand")
    if "onButton ? FORGET_HOVERED_COLOR : FORGET_COLOR" not in drawn.group(0):
        failures.append("the mark does not turn red under the hand")

    if failures:
        return report(failures)

    # ---- the row, drawn the way drawItem draws one ------------------------------------------
    row_h = numbers["ROW_HEIGHT"]
    row_w = numbers["LIST_MAX_WIDTH"] - numbers["SCROLLBAR_WIDTH"] - numbers["SCROLLBAR_MARGIN"]
    row = pc.load(pc.SHIPPED / "widget/button.png")

    def row_alone():
        canvas = Image.new("RGBA", (row_w, row_h), (0, 0, 0, 0))
        pc.blit(canvas, row, 0, 0, row_w, row_h)
        pc.border(canvas, 0, 0, row_w, row_h, numbers["HOVER_COLOR"])
        pc.text(canvas, font, "\u2714", numbers["CHECK_X"], (row_h - 9) // 2,
                numbers["CHECK_COLOR"])
        pc.text(canvas, font, "Pale Garden", numbers["NAME_X"], (row_h - 9) // 2,
                numbers["FOUND_COLOR"])
        return canvas

    def with_button(face, colour):
        canvas = row_alone()
        x, top = row_w - size, (row_h - size) // 2
        pc.blit(canvas, face, x, top, size, size)
        pc.text(canvas, font, mark.group(1), x + (size - font.width(mark.group(1))) // 2,
                top + (size - 9) // 2, colour)
        return canvas

    # the button has to stand inside its row: the row is drawn from the list's left edge to where
    # the scroller begins, and a button past either end of it is a button outside the row
    if size <= 0 or size > min(row_h, row_w):
        failures.append(f"a button of {size} pixels does not stand in a row of {row_h} by {row_w}")
        return report(failures)

    x, top = row_w - size, (row_h - size) // 2
    if x < 0 or top < 0 or x + size > row_w or top + size > row_h:
        failures.append(f"the button at {x}..{x + size - 1} by {top}..{top + size - 1} "
                        f"does not stand inside a row of {row_w} by {row_h}")

    text_height = 9  # the game's font line height, which the drawing above centres the mark in
    if font.width(mark.group(1)) > size or text_height > size:
        failures.append(f"the mark \"{mark.group(1)}\" is {font.width(mark.group(1))} by "
                        f"{text_height} and does not fit a button of {size}")

    def differing_outside(a, b, box):
        """How many pixels differ inside the box, and where the first one outside it is."""
        pa, pb = a.load(), b.load()
        inside = 0
        outside = []
        for yy in range(row_h):
            for xx in range(row_w):
                if pa[xx, yy] == pb[xx, yy]:
                    continue
                if box[0] <= xx < box[2] and box[1] <= yy < box[3]:
                    inside += 1
                else:
                    outside.append((xx, yy))
        return inside, outside

    box = (x, top, x + size, top + size)
    flat_drawing = with_button(flat, on_row)
    lit_drawing = with_button(lit, on_button)
    flat_inside, flat_outside = differing_outside(flat_drawing, row_alone(), box)
    lit_inside, lit_outside = differing_outside(lit_drawing, flat_drawing, box)

    print(f"the screen draws that button {size} pixels square at the row's end "
          f"(columns {x}..{x + size - 1} of {row_w}, rows {top}..{top + size - 1} of {row_h}), "
          f"with the letter \"{mark.group(1)}\" {on_row:08X} on the row and {on_button:08X} "
          f"on the button")

    if flat_outside:
        failures.append(f"the button face paints outside its own box: {flat_outside[0]}")
    if lit_outside:
        failures.append(f"the lit button changes the row outside its own box: {lit_outside[0]}")
    if flat_inside == 0:
        failures.append("the button's mark is not drawn inside its box")
    if lit_inside == 0:
        failures.append("the lit face is not different from the flat one inside its box")

    print(f"  the face and its mark change {flat_inside} pixel(s) inside the button and "
          f"{len(flat_outside)} outside")
    print(f"  the lit face changes {lit_inside} pixel(s) inside the button and "
          f"{len(lit_outside)} outside")

    return report(failures)


def report(failures):
    if failures:
        print()
        for failure in failures:
            print(f"OFF  {failure}")
        return 1

    print("\nthe source draws the button inside its row, its mark fits, and neither face reaches "
          "outside the button's box")
    return 0


if __name__ == "__main__":
    sys.exit(main())
