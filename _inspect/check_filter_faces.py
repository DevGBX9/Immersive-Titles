#!/usr/bin/env python3
"""Checks the faces of the two filter buttons beside the list: that the face says which of the two
the list is being read by, and that a filter that has been turned off is plain again at once.

What the owner reported: the button of a filter stayed green after the filter was turned off, even
after clicking elsewhere in the screen, and the only thing that took the green away was pressing the
other filter. The green was the lit sprite of the pack - the sprite the game draws a button in while
the mouse is on it or the keyboard is on it (see drawButtonFace) - and a pressed button keeps the
keyboard, so the face went on saying "lit" long after the list had gone back to every biome, and a
click on the empty screen does not take the keyboard off it. What the owner asked for: the face of a
filter is green while the list is being read by it and plain grey while it is off, the way the tab of
the page being read is green (see tabSprite), so the second press of a filter is what turns its
background grey again.

This is read two ways, because the two answer different questions:

- the rule, as the presses the owner made: what the face of each filter is, press by press, run
  against a model of the rule. The same presses are run against the rule as it was before the fix,
  and the check fails if that older rule does *not* disagree with the table - a table that every
  rule passes would prove nothing,
- the code, as the lines it is written in: that each filter is painted through filterSprite rather
  than through the lit-while-hovered face, and that filterSprite names the lit sprite for the filter
  the list is being read by and the plain one for the other.

    python _inspect/check_filter_faces.py
"""

import pathlib
import re
import sys

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")

FILTERS = ("discovered", "remaining")
LIT = "green"
PLAIN = "plain"

# the presses the owner made: the first filter on, the same one off again, the other one on, the two
# swapped, and the filter of what was found turned off again
PRESSES = ("discovered", "discovered", "remaining", "discovered", "discovered")


def toggle(state, pressed):
    """The list after one press of a filter: an on filter pressed again turns it off, and the other
    filter is turned on instead (see toggleFilter)."""
    return "all" if state == pressed else pressed


def face(state):
    """The face of each filter while the list is being read by `state` (see filterSprite)."""
    return {name: (LIT if name == state else PLAIN) for name in FILTERS}


def walk():
    """The state of the list and the two faces after each of the owner's presses."""
    state = "all"
    steps = []

    for pressed in PRESSES:
        state = toggle(state, pressed)
        steps.append((pressed, state, face(state)))

    return steps


class OldFaces:
    """The faces as the screen painted them before the fix: lit while the mouse is on the button or
    the keyboard is on it (the game's isHoveredOrFocused), with a press leaving the keyboard on the
    button it landed on until another one takes it. The mouse is taken away after each press, which
    is the click elsewhere the owner made, so only the keyboard is left to light a face."""

    def __init__(self):
        self.state = "all"
        self.keyboard = None

    def press(self, pressed):
        self.state = toggle(self.state, pressed)
        self.keyboard = pressed

    def face(self):
        return {name: (LIT if name == self.keyboard else PLAIN) for name in FILTERS}


def report(failures):
    print()
    for failure in failures:
        print(f"FAIL {failure}")
    return 1


def between(source, header):
    """The body of the method `header`, read to its own closing line rather than to its first brace."""
    match = re.search(re.escape(header) + r"\s*\{(.*?)\n\t\}", source, re.S)
    return None if match is None else match.group(1)


def main():
    source = SCREEN.read_text(encoding="utf-8")
    failures = []

    # ---- the rule, as the owner's presses ----------------------------------------------------
    print("the face of each filter, press by press, the mouse taken away after every press:")

    expected = [
        ("discovered", {"discovered": LIT, "remaining": PLAIN}),
        ("all", {"discovered": PLAIN, "remaining": PLAIN}),
        ("remaining", {"discovered": PLAIN, "remaining": LIT}),
        ("discovered", {"discovered": LIT, "remaining": PLAIN}),
        ("all", {"discovered": PLAIN, "remaining": PLAIN}),
    ]

    steps = walk()
    for number, ((pressed, state, faces), (_, expected_faces)) in enumerate(zip(steps, expected), 1):
        ok = faces == expected_faces
        failures += [] if ok else [f"press {number} ({pressed}) leaves {faces}, "
                                   f"and the list is read by {state}"]
        print(f"  {'ok  ' if ok else 'FAIL'} press {number} ({pressed:10s}) -> "
              f"discovered {faces['discovered']:5s}, remaining {faces['remaining']:5s} "
              f"(the list: {state})")

    # the same presses under the rule as it was: if they pass there too, the table says nothing about
    # the fix and the fix could be taken out without this check noticing
    old = OldFaces()
    old_faces = []
    for pressed in PRESSES:
        old.press(pressed)
        old_faces.append(old.face())

    if all(faces == expected_face for faces, (_, expected_face) in zip(old_faces, expected)):
        failures.append("the presses pass under the face as it was before the fix, so they do not "
                        "hold it")
    else:
        print("  ok   the presses fail under the face as it was before the fix, as the owner "
              "reported")

    # ---- the code, as the lines it is written in --------------------------------------------
    faces = between(source, "private void drawButtonFaces(GuiGraphicsExtractor graphics)")
    if faces is None:
        failures.append("the screen paints no faces over the buttons any more")
    else:
        for name, filter_name in (("discoveredFilter", "DISCOVERED"), ("remainingFilter", "REMAINING")):
            painted = f"drawButtonFace(graphics, {name}, filterSprite(Filter.{filter_name}))"
            if painted not in faces:
                failures.append(f"the face of {name} is not painted with filterSprite, which is what "
                                f"says which filter the list is being read by")
            if re.search(rf"drawButtonFace\(graphics, {name}\)", faces):
                failures.append(f"the face of {name} is still painted by the lit-while-hovered rule, "
                                f"which is the bug")

        if "drawButtonFace(graphics, doneButton)" not in faces:
            failures.append("the button that closes the screen has lost the face it answers the "
                            "mouse with")

    sprite = between(source, "private Identifier filterSprite(Filter filter)")
    if sprite is None:
        failures.append("the screen names no sprite for a filter any more")
    elif not re.search(r"return this\.filter == filter\s*\?\s*LIT_BUTTON_SPRITE\s*:\s*"
                       r"BUTTON_SPRITE;", sprite):
        failures.append("filterSprite does not name the lit sprite for the filter the list is being "
                        "read by and the plain one for the other")

    icons = between(source, "private void drawFilterIcons(GuiGraphicsExtractor graphics)")
    if icons is None:
        failures.append("the marks of the two filters are not drawn any more")
    else:
        if "filter == Filter.DISCOVERED ? CHECK_COLOR : FILTER_OFF_COLOR" not in icons:
            failures.append("the mark of the filter of what was found no longer follows that filter")
        if "filter == Filter.REMAINING ? REMAINING_COLOR : FILTER_OFF_COLOR" not in icons:
            failures.append("the mark of the filter of what is left no longer follows that filter")

    if failures:
        return report(failures)

    print("\nthe face of a filter is the one the list is being read by, and a filter turned off is "
          "plain at once")
    return 0


if __name__ == "__main__":
    sys.exit(main())
