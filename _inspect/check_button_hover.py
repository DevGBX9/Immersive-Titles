#!/usr/bin/env python3
"""Checks the hover of the four buttons whose face says a state - the two tabs and the two filters:
that the mouse is answered by a line around the button and not by the lit face of it.

The face of each of those four buttons is the state of the screen: the lit sprite of a tab is the
page being read, and the lit sprite of a filter is the end of the list being kept (see tabSprite and
filterSprite). A face lit under the mouse would therefore say that the page or the filter it stands
for is the state, which is the thing the face is already saying - the filter face was the owner's
last report, a button that went on saying "on" after the filter behind it had been turned off.

So the mouse is answered by the line a row of the list answers it with - the gold of HOVER_COLOR,
one pixel outside the button - and the face is left to the state. The buttons whose face is a press
rather than a state (the one that closes the screen and the one a row offers) keep the lit face of
the game's own button.

This is read two ways, because the two answer different questions:

- the rule, as the mouse moves over the four buttons: whether the line is around the one under the
  mouse and nowhere else, and whether the face under it is still the state. The same walks are run
  against the rule as it was before the hover was added - the lit face under the mouse - and the
  check fails if that older rule does *not* disagree with the table,
- the code, as the lines it is written in: that the hover is drawn for those four and for nothing
  else, that it is gated by isHovered, that it is drawn one pixel outside the button in HOVER_COLOR,
  and that the four faces are still painted from tabSprite and filterSprite.

    python _inspect/check_button_hover.py
"""

import pathlib
import re
import sys

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")

BUTTONS = ("biome tab", "structure tab", "discovered filter", "remaining filter")
STATE = "state"
LIT = "lit"


def lines(mouse):
    """The line drawn around each of the four while the mouse is on `mouse`, which may be None."""
    return {name: mouse == name for name in BUTTONS}


def faces(mouse):
    """The face of each of the four: the state, whatever the mouse is doing (see tabSprite and
    filterSprite)."""
    return {name: STATE for name in BUTTONS}


def old_faces(mouse):
    """The face as the rule before this change painted it: the lit sprite under the mouse, which is
    the game's own face of a button."""
    return {name: (LIT if mouse == name else STATE) for name in BUTTONS}


def between(source, header):
    """The body of the method `header`, read to its own closing line rather than to its first brace."""
    match = re.search(re.escape(header) + r"\s*\{(.*?)\n\t\}", source, re.S)
    return None if match is None else match.group(1)


def flat(body):
    """A method body with its whitespace flattened, for the one-line calls that wrap in the source."""
    return re.sub(r"\s+", " ", body)


def report(failures):
    print()
    for failure in failures:
        print(f"FAIL {failure}")
    return 1


def main():
    source = SCREEN.read_text(encoding="utf-8")
    failures = []

    # ---- the rule, as the mouse moves over the four ------------------------------------------
    print("the line and the face of the four buttons, the mouse on each of them in turn:")

    walks = [None] + list(BUTTONS)
    for mouse in walks:
        drawn = lines(mouse)
        shown = faces(mouse)
        ok_lines = sum(drawn.values()) == (0 if mouse is None else 1) and \
            (mouse is None or drawn[mouse])
        ok_faces = all(face == STATE for face in shown.values())

        failures += [] if ok_lines and ok_faces else \
            [f"the mouse on {mouse or 'nothing'} leaves lines {drawn} and faces {shown}"]

        where = "nowhere" if mouse is None else mouse
        print(f"  {'ok  ' if ok_lines and ok_faces else 'FAIL'} mouse on {where:17s} -> "
              f"a line around {sum(drawn.values())} button(s), the faces still say the state")

    # the same walks under the face lit by the mouse: if they pass there too, the table says nothing
    # about the change and the line could be taken out without this check noticing
    if all(old_faces(mouse) == faces(mouse) for mouse in walks):
        failures.append("the walks pass under the face lit by the mouse, so they do not hold the line")
    else:
        print("  ok   the walks fail under the face lit by the mouse, as the owner asked")

    # ---- the code, as the lines it is written in --------------------------------------------
    hovers = between(source, "private void drawButtonHovers(GuiGraphicsExtractor graphics)")
    if hovers is None:
        failures.append("the screen draws no hover for the buttons any more")
    else:
        body = flat(hovers)
        if "for (Page tab : Page.values())" not in body or \
                "drawButtonHover(graphics, tabs.get(tab));" not in body:
            failures.append("the two tabs no longer answer the mouse with the line")
        for name in ("discoveredFilter", "remainingFilter"):
            if f"drawButtonHover(graphics, {name});" not in body:
                failures.append(f"{name} no longer answers the mouse with the line")
        if "doneButton" in body:
            failures.append("the button that closes the screen is given the line too, and its face "
                            "already answers the mouse")

    hover = between(source, "private void drawButtonHover(GuiGraphicsExtractor graphics, Button button)")
    if hover is None:
        failures.append("the screen draws no line around a button any more")
    else:
        body = flat(hover)
        if "if (!button.isHovered())" not in body or "return;" not in body:
            failures.append("the line is not gated by the mouse being on the button")
        if not re.search(r"drawBorder\(graphics, button\.getX\(\) - 1, button\.getY\(\) - 1, "
                         r"button\.getX\(\) \+ button\.getWidth\(\) \+ 1, "
                         r"button\.getY\(\) \+ button\.getHeight\(\) \+ 1, HOVER_COLOR\)", body):
            failures.append("the line is not drawn one pixel outside the button in the gold of the "
                            "hovered row")

    faces_body = between(source, "private void drawButtonFaces(GuiGraphicsExtractor graphics)")
    if faces_body is None:
        failures.append("the screen paints no faces over the buttons any more")
    else:
        body = flat(faces_body)
        if "drawButtonFace(graphics, tabs.get(tab), tabSprite(tab));" not in body:
            failures.append("the face of a tab is no longer painted from the page being read")
        for name, filter_name in (("discoveredFilter", "DISCOVERED"), ("remainingFilter", "REMAINING")):
            if f"drawButtonFace(graphics, {name}, filterSprite(Filter.{filter_name}));" not in body:
                failures.append(f"the face of {name} is no longer painted from the filter it shows")
        if "drawButtonFace(graphics, doneButton)" not in body:
            failures.append("the button that closes the screen no longer wears the lit face of the "
                            "game's own button")

    gold = re.search(r"static final int HOVER_COLOR\s*=\s*(0x[0-9A-Fa-f]+);", source)
    if gold is None:
        failures.append("the screen names no gold for the hovered row any more")
    else:
        row = re.search(r"drawBorder\(graphics, listLeft, y, rowRight, y \+ ROW_HEIGHT, HOVER_COLOR\)",
                        source)
        if row is None:
            failures.append("the rows no longer answer the mouse with the same gold line")

    if failures:
        return report(failures)

    print("\nthe four state buttons answer the mouse with the gold line of a hovered row, and their "
          "faces stay the state")
    return 0


if __name__ == "__main__":
    sys.exit(main())
