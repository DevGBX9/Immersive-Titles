#!/usr/bin/env python3
"""Checks where the collection screen sets its block of the screen, against the numbers it is built of.

The screen draws one block: the title, the line counting the discoveries, the line over the list, the
strip of the two tabs, the list, the line under it and the button that closes the screen. That block used to hang from the top of
the screen - the title was drawn at a fixed eight gui pixels and the button followed the list - so on a
tall screen the room under the button was the room the layout had left over: on the screen the owner
sent, 37 gui pixels under the button against 8 over the title.

This is a model of the layout and not the screen: it reads the constants out of the screen's source,
evaluates the arithmetic between them the way Java would, and puts the block where the screen puts it,
for a table of screen sizes. What it checks is what the change was for:

- the room over the title is the room under the button, and the room at the left of the list is the
  room at its right, on every screen and to the pixel - the screen gives what no halving can place to
  the list rather than to one of the two margins,
- the block never leaves the screen, and never comes closer than SCREEN_MARGIN to its edges,
- the list is no wider than LIST_MAX_WIDTH and no taller than LIST_MAX_HEIGHT_PERCENT of the screen,
- and the strip of the two tabs is as wide as the line drawn over it - the plate of the screen - and no
  wider than the list it stands over, so the plate and the bar of pages are one over the other and
  neither reaches past the list on any screen,
- and the strip of the two tabs rests on the frame of the list: the room LIST_TOP gives the top of the
  block is exactly the room the strip and the frame take, so the bar of pages ends where the frame of
  the list begins and the two edges are one line - the pages are what the list holds, and a bar set off
  from it by a gap would read as a row of buttons over a list rather than as the top of it,
- and the two filter buttons stand beside the block rather than in it: each is set off the frame of the
  list and not off the list itself, so neither is drawn over the band of the block, and both still fit
  on the screen with SCREEN_MARGIN to spare at the sides.

It proves nothing about the game: that the block lands where this says it does is a fact about the
source and not about the screen, and it has to be seen in the game itself.

    python _inspect/check_block_balance.py
"""

import pathlib
import re
import sys

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")

DECLARATION = re.compile(r"(?:private|static|final|\s)*int\s+([A-Z][A-Z_0-9]*)\s*=\s*([^;]+);")
LITERAL = re.compile(r"^[A-Za-z_0-9\s+*/\-()]+$")

# the screens to read the layout on, in gui pixels: the size a window comes to at the scale the game
# picks for it, including the screen the owner sent (683 x 357, a 1366 x 768 window at scale 2)
SIZES = [(320, 240), (427, 240), (480, 270), (512, 288), (640, 360), (683, 357), (854, 480),
         (960, 540), (1280, 720)]


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


def layout(k, width, height):
    """What the screen's init() works out for a screen of (`width`, `height`) gui pixels."""
    list_width = max(0, min(k["LIST_MAX_WIDTH"], width - 2 * k["SIDE_SPACE"]))
    room = max(0, height - k["LIST_TOP"] - k["FOOTER_HEIGHT"] - 2 * k["SCREEN_MARGIN"])
    list_height = min(room, height * k["LIST_MAX_HEIGHT_PERCENT"] // 100)
    spare = height - (k["LIST_TOP"] + list_height + k["FOOTER_HEIGHT"])

    # what no halving can place is taken off the list, on both axes, so the margins come out equal
    list_width = max(0, list_width - (width - list_width) % 2)
    if spare > 1:
        list_height -= spare % 2

    block = k["LIST_TOP"] + list_height + k["FOOTER_HEIGHT"]
    blockTop = max(k["SCREEN_MARGIN"], (height - block) // 2)
    listLeft = (width - list_width) // 2

    return {"list": (list_width, list_height),
            "over": blockTop, "under": height - (blockTop + block),
            "left": listLeft, "right": width - (listLeft + list_width)}


def main():
    k = constants()
    failures = []

    for name in ("TITLE_Y", "PROGRESS_Y", "LIST_TOP", "FOOTER_HEIGHT", "SCREEN_MARGIN",
                 "LIST_MAX_WIDTH", "LIST_MAX_HEIGHT_PERCENT", "UP_LINE_HEIGHT", "DOWN_LINE_HEIGHT",
                 "UP_LINE_WIDTH", "TAB_WIDTH", "TAB_STRIP_WIDTH", "TAB_HEIGHT", "TAB_GAP",
                 "TABS_GAP", "FRAME_PADDING", "SIDE_SPACE", "DONE_HEIGHT", "FILTER_BUTTON_SIZE",
                 "FILTER_BUTTON_GAP"):
        if name not in k:
            print(f"MISSING {name} is not declared as a constant of {SCREEN.name}")
            return 1

    print(f"the block of the screen, read off the constants of {SCREEN.name}:")
    print(f"  {k['LIST_TOP']} gui pixels over the list, {k['FOOTER_HEIGHT']} under it, "
          f"at most {k['LIST_MAX_WIDTH']} x {k['LIST_MAX_HEIGHT_PERCENT']}% of the screen, "
          f"never closer to an edge than {k['SCREEN_MARGIN']}")
    print(f"  the line over the list {k['UP_LINE_WIDTH']} x {k['UP_LINE_HEIGHT']} gui pixels, "
          f"the one under it {k['DOWN_LINE_HEIGHT']} tall\n")
    strip_bottom = k["TABS_Y"] + k["TAB_HEIGHT"]
    frame_top = k["LIST_TOP"] - k["FRAME_PADDING"]

    # a filter button is set off the frame of the list, so what it takes at one side of the list is the
    # frame, the gap and the button itself - and SIDE_SPACE is what the screen keeps clear beside the
    # list for that and for SCREEN_MARGIN over it
    button_space = k["FRAME_PADDING"] + k["FILTER_BUTTON_GAP"] + k["FILTER_BUTTON_SIZE"]

    print(f"  each of the two filter buttons {k['FILTER_BUTTON_SIZE']} x {k['FILTER_BUTTON_SIZE']}, "
          f"set {k['FILTER_BUTTON_GAP']} gui pixels off the {k['FRAME_PADDING']}-pixel frame of the "
          f"list: {button_space} gui pixels beside the list, and {k['SIDE_SPACE']} kept clear for them")
    print(f"  the strip of two tabs, each {k['TAB_WIDTH']} x {k['TAB_HEIGHT']} with "
          f"{k['TAB_GAP']} between them: {k['TAB_STRIP_WIDTH']} wide, as wide as the line over it")
    print(f"  the strip ends at {strip_bottom} gui pixels under the top of the block and the frame of "
          f"the list starts at {frame_top}: {strip_bottom - frame_top} between the two\n")

    print(f"  {'screen':>12s}  {'list':>10s}  {'over':>4s} {'under':>5s}  {'left':>4s} {'right':>5s}")
    worst = (0, "")

    for width, height in SIZES:
        m = layout(k, width, height)
        over, under = m["over"], m["under"]
        left, right = m["left"], m["right"]
        list_width, list_height = m["list"]
        screen, size = f"{width}x{height}", f"{list_width}x{list_height}"
        off = max(abs(over - under), abs(left - right))

        if off > worst[0]:
            worst = (off, screen)
        if off:
            failures.append(f"on a {screen} screen the block is {off} pixels off the middle")
        if over < k["SCREEN_MARGIN"] or under < k["SCREEN_MARGIN"]:
            failures.append(f"on a {screen} screen the block is closer to an edge than SCREEN_MARGIN")
        if list_width > k["LIST_MAX_WIDTH"] or list_height > height * k["LIST_MAX_HEIGHT_PERCENT"] // 100:
            failures.append(f"on a {screen} screen the list is larger than its share of the screen")
        if k["TAB_STRIP_WIDTH"] > list_width:
            failures.append(f"on a {screen} screen the strip of tabs is wider than the list it stands over")
        if left - button_space < k["SCREEN_MARGIN"]:
            failures.append(f"on a {screen} screen a filter button comes within "
                            f"{left - button_space} gui pixels of the edge of the screen, while "
                            f"SCREEN_MARGIN is {k['SCREEN_MARGIN']}")

        print(f"  {screen:>12s}  {size:>10s}  {over:4d} {under:5d}  {left:4d} {right:5d}")

    # the two filter buttons are drawn off the frame of the list and not off the list itself: the room
    # SIDE_SPACE keeps at one side of the list is what has to hold the frame, the gap, a button and the
    # margin over it, or the buttons are drawn over the block and pushed towards the edge of the screen
    if k["SIDE_SPACE"] < button_space + k["SCREEN_MARGIN"]:
        failures.append(f"SIDE_SPACE is {k['SIDE_SPACE']} gui pixels while the frame, the gap, a filter "
                        f"button and SCREEN_MARGIN come to {button_space + k['SCREEN_MARGIN']}: a filter "
                        f"button would be drawn over the frame of the list or past the margin")

    # the strip of the two tabs is drawn at the width of the line over it: the two are one bar of pages
    # under one plate, and a line of a width of its own would leave the pair out of true
    if abs(k["TAB_STRIP_WIDTH"] - k["UP_LINE_WIDTH"]) > 1:
        failures.append(f"the strip of tabs comes to {k['TAB_STRIP_WIDTH']} gui pixels against the "
                        f"{k['UP_LINE_WIDTH']} of the line drawn over it")

    if k["TAB_STRIP_WIDTH"] != 2 * k["TAB_WIDTH"] + k["TAB_GAP"]:
        failures.append("the strip of tabs is not the two of them and the gap between them")

    # the bar of the two pages rests on the list those two pages are of: the strip is drawn from TABS_Y
    # and the frame of the list is the game's own button standing FRAME_PADDING over the panel, so the
    # room LIST_TOP leaves the top of the block is what has to put the two edges on one line
    if strip_bottom != frame_top:
        failures.append(f"the strip of tabs ends at {strip_bottom} gui pixels while the frame of the list "
                        f"starts at {frame_top}: {strip_bottom - frame_top} between the two")

    print(f"\nthe largest difference between two opposite margins, on any of those screens: "
          f"{worst[0]} pixel(s) (on {worst[1]})")

    if failures:
        print()

        for failure in failures:
            print(f"OFF  {failure}")
        return 1

    print("the block is set in the middle of every screen, the strip of tabs holds the width of the "
          "line over it and rests on the frame of the list, and the two filter buttons stand clear of "
          "that frame")
    return 0


if __name__ == "__main__":
    sys.exit(main())
