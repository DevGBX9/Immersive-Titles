#!/usr/bin/env python3
"""What the screen draws at the far right end of a row of a discovery, read out of its own source
and held to the picture the owner asked to have back.

The button that gives a discovery back is drawn by the screen itself, and this device does not build
the mod, so the only way to know that the source draws the button that was asked for is to read the
screen's own numbers, faces and geometry out of the java file, draw a row with them the way the
screen draws one, and hold that drawing to the picture the owner sent - `download.png`, which is a
row of a discovery at three gui pixels to a pixel.

Everything the drawing below uses is read out of the source rather than written here: the size of the
button and the two colours of its mark out of the `static final int` lines, the two faces it wears
out of the `Identifier` lines they point at, the letter of the mark out of the `String` line, and the
place it is drawn at out of the two lines `forgetButtonX` and `forgetButtonY` return. What is checked
is that the drawing the source describes is the one in the picture, to the pixel, and that the button
stands inside its row rather than over or past it.

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
PICTURE = pathlib.Path(r"C:\Users\GBX\Desktop\download.png")


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
    if size <= 0 or size > row_h:
        failures.append(f"a button of {size} pixels does not stand in a row of {row_h}")

    picture = Image.open(PICTURE).convert("RGBA")
    want = (row_w * 3, row_h * 3)
    if picture.size != want:
        failures.append(f"{PICTURE.name} is {picture.size[0]}x{picture.size[1]}, and a row of the "
                        f"list at three gui pixels is {want[0]}x{want[1]}")

    def difference(a, b):
        pa, pb = a.load(), b.load()
        total, worst = 0, 0
        for x in range(picture.size[0]):
            for y in range(picture.size[1]):
                diff = max(abs(pa[x, y][i] - pb[x, y][i]) for i in range(4))
                total += diff
                worst = max(worst, diff)
        return total / (picture.size[0] * picture.size[1]), worst

    print(f"the screen draws that button {size} pixels square at the row's end "
          f"(columns {row_w - size}..{row_w - 1} of {row_w}, rows {(row_h - size) // 2}.."
          f"{(row_h - size) // 2 + size - 1} of {row_h}), with the letter \"{mark.group(1)}\" "
          f"{on_row:08X} on the row and {on_button:08X} on the button")

    # the picture is a row with the mouse on it and not on the button, so it is that drawing the
    # screen's own has to be the same as, to the pixel
    mean, worst = difference(pc.upscale(with_button(flat, on_row)), picture)
    print(f"  drawn with the mouse on the row      and held to {PICTURE.name}: mean channel "
          f"difference {mean:6.3f}, worst pixel {worst:3d}")
    if mean != 0:
        failures.append(f"what the source draws with the mouse on the row is not the picture the "
                        f"owner asked for back (mean {mean:.3f}, worst pixel {worst})")

    # the other state is the same row with the button lit under the hand, so what it may differ
    # from that drawing by is the button's own box and nothing else - a difference outside it is a
    # geometry that moved when the mouse moved onto the button
    box_x, box_y = (row_w - size) * 3, ((row_h - size) // 2) * 3
    lit_drawing, flat_drawing = pc.upscale(with_button(lit, on_button)), \
        pc.upscale(with_button(flat, on_row))
    pa, pb = lit_drawing.load(), flat_drawing.load()
    outside = [(x, y) for x in range(picture.size[0]) for y in range(picture.size[1])
               if not (box_x <= x < box_x + size * 3 and box_y <= y < box_y + size * 3)
               and pa[x, y] != pb[x, y]]
    mean, worst = difference(lit_drawing, picture)
    print(f"  drawn with the mouse on the button  and held to {PICTURE.name}: mean channel "
          f"difference {mean:6.3f}, worst pixel {worst:3d}")
    if outside:
        failures.append(f"the lit button changes the row outside its own box: {outside[0]}")

    return report(failures)


def report(failures):
    if failures:
        print()
        for failure in failures:
            print(f"OFF  {failure}")
        return 1

    print("\nthe source draws the button the picture shows, to the pixel, standing inside its row")
    return 0


if __name__ == "__main__":
    sys.exit(main())
