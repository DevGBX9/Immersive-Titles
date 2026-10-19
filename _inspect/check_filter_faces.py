#!/usr/bin/env python3
"""Checks the faces of the two filter buttons beside the list: that a face says which of the two the
list is being read by or which of them the mouse is on, and that a filter turned off is plain again
as soon as the mouse leaves it.

What the owner reported first: the button of a filter stayed green after the filter was turned off,
even after clicking elsewhere in the screen, and the only thing that took the green away was pressing
the other filter. The green was the lit sprite of the pack - the sprite the game draws a button in
while the mouse is on it or the keyboard is on it (see drawButtonFace) - and a pressed button keeps
the keyboard, so the face went on saying "lit" long after the list had gone back to every biome, and
a click on the empty screen does not take the keyboard off it. The fix then was a face painted by the
filter alone (see filterSprite), so the second press turned its background grey at once.

What the owner asked next: keep that, and light the face of a filter green under the mouse as well,
for those two buttons only, while the mouse is on one of them - the button looks like the filter it
would turn on before it is pressed. So the rule now has two lights and neither is the keyboard:

- a filter is green while the list is being read by it,
- a filter is green while the mouse is on its button, whether or not that filter is on,
- and a filter is plain while it is off and the mouse is away, whatever the keyboard is left on.

This is read two ways, because the two answer different questions:

- the rule, as the hand moves over the two buttons the way the owner described: what the face of each
  filter is, step by step, run against a model of the rule. The same steps are run against the two
  rules as they were before - the lit-while-hovered-or-focused face of the report, and the face of
  the filter alone - and the check fails if either of those older rules does *not* disagree with the
  table,
- the code, as the lines it is written in: that each filter is painted through filterSprite with its
  own button, that filterSprite names the lit sprite for the filter the list is being read by or the
  button under the mouse, that the tabs are not given the mouse, and that the marks of the two
  filters still follow the filters rather than the mouse.

    python _inspect/check_filter_faces.py
"""

import pathlib
import re
import sys

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")

FILTERS = ("discovered", "remaining")
LIT = "green"
PLAIN = "plain"

# what the hand does, in order: the presses the owner made, with the mouse sitting on the button
# pressed and taken away again after it, and the mouse moved onto the other filter while it is off.
# None is a step that presses nothing; `mouse` is the filter whose button the mouse is on, if any.
ACTIONS = (
    ("nothing yet", None, None),
    ("press discovered", "discovered", "discovered"),
    ("take the mouse away, the keyboard left on discovered", None, None),
    ("move the mouse onto remaining while it is off", None, "remaining"),
    ("take the mouse away from remaining", None, None),
    ("press discovered again", "discovered", "discovered"),
    ("take the mouse away, the keyboard left on discovered", None, None),
    ("press remaining", "remaining", "remaining"),
    ("take the mouse away from remaining", None, None),
    ("press discovered", "discovered", "discovered"),
    ("take the mouse away from discovered", None, None),
    ("press discovered again", "discovered", "discovered"),
    ("take the mouse away, the keyboard left on discovered", None, None),
)

# the faces the owner described, in FILTERS order: discovered, remaining
EXPECTED = (
    (PLAIN, PLAIN),   # nothing is read by either filter and the hand is elsewhere
    (LIT, PLAIN),     # discovered on, and the mouse is on the button that is on
    (LIT, PLAIN),     # discovered still on, the mouse away
    (LIT, LIT),       # remaining off, the mouse on it: green under the hand
    (LIT, PLAIN),     # the mouse leaves: remaining off and plain again
    (LIT, PLAIN),     # pressed again, still under the hand: the green of the hover
    (PLAIN, PLAIN),   # mouse away: discovered off, and the keyboard it keeps does not light it
    (PLAIN, LIT),     # remaining on, and the mouse on the button that is on
    (PLAIN, LIT),     # remaining still on, the mouse away
    (LIT, PLAIN),     # discovered on (remaining turned off), the mouse on it
    (LIT, PLAIN),     # the mouse away: discovered still on
    (LIT, PLAIN),     # pressed again, still under the hand: the green of the hover
    (PLAIN, PLAIN),   # mouse away: the list back to all, and the keyboard lights nothing
)


def toggle(state, pressed):
    """The list after one press of a filter: an on filter pressed again turns it off, and the other
    filter is turned on instead (see toggleFilter)."""
    return "all" if state == pressed else pressed


def face(state, mouse):
    """The face of each filter while the list is being read by `state` and the mouse is on `mouse`
    (see filterSprite): green for the filter being read by, green for the button under the mouse,
    and plain for the other."""
    return {name: (LIT if name == state or name == mouse else PLAIN) for name in FILTERS}


def hand():
    """The state, the mouse and the keyboard down the owner's steps, with the face the rule gives
    each of them."""
    state = "all"
    keyboard = None
    steps = []

    for action, pressed, mouse in ACTIONS:
        if pressed is not None:
            state = toggle(state, pressed)
            keyboard = pressed

        steps.append((action, state, mouse, keyboard, face(state, mouse)))

    return steps


def old_hovered_or_focused(steps):
    """The faces as the screen painted them before the first fix: lit while the mouse is on the
    button or the keyboard is on it, and the keyboard is left on the button a press landed on."""
    return [{name: (LIT if name == mouse or name == keyboard else PLAIN) for name in FILTERS}
            for _, _, mouse, keyboard, _ in steps]


def old_filter_only(steps):
    """The faces of the last fix: lit for the filter the list is being read by, and nothing for the
    mouse."""
    return [{name: (LIT if name == state else PLAIN) for name in FILTERS}
            for _, state, _, _, _ in steps]


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

    # ---- the rule, as the hand moves over the two ---------------------------------------------
    print("the face of each filter as the hand moves over the two buttons:")

    steps = hand()
    for number, ((action, state, mouse, keyboard, faces), expected) in enumerate(zip(steps, EXPECTED), 1):
        expected = dict(zip(FILTERS, expected))
        ok = faces == expected
        failures += [] if ok else [f"step {number} ({action}) leaves {faces}, and the list is read "
                                   f"by {state} with the mouse on {mouse}"]

        hand_on = "nothing" if mouse is None else mouse
        print(f"  {'ok  ' if ok else 'FAIL'} {action:58s} discovered {faces['discovered']:5s}, "
              f"remaining {faces['remaining']:5s} (the list: {state}, the mouse: {hand_on})")

    # the same steps under the two rules as they were: if either of them passes too, the table does
    # not hold the change and that part of the rule could be taken out without this check noticing
    old_focused = old_hovered_or_focused(steps)
    old_plain = old_filter_only(steps)
    expected_list = [dict(zip(FILTERS, expected)) for expected in EXPECTED]

    if all(faces == expected for faces, expected in zip(old_focused, expected_list)):
        failures.append("the steps pass under the face lit by the mouse or the keyboard, so they do "
                        "not hold the keyboard being left out")
    else:
        print("  ok   the steps fail under the face lit by the mouse or the keyboard, as the owner "
              "reported")

    if all(faces == expected for faces, expected in zip(old_plain, expected_list)):
        failures.append("the steps pass under the face of the filter alone, so they do not hold the "
                        "face of the filter under the mouse")
    else:
        print("  ok   the steps fail under the face of the filter alone, as the owner asked")

    # ---- the code, as the lines it is written in --------------------------------------------
    faces_body = between(source, "private void drawButtonFaces(GuiGraphicsExtractor graphics)")
    if faces_body is None:
        failures.append("the screen paints no faces over the buttons any more")
    else:
        for name, filter_name in (("discoveredFilter", "DISCOVERED"), ("remainingFilter", "REMAINING")):
            painted = (f"drawButtonFace(graphics, {name}, filterSprite(Filter.{filter_name}, {name}))")
            if painted not in faces_body:
                failures.append(f"the face of {name} is not painted with filterSprite and its own "
                                f"button, which is what lets the mouse light it")
            if re.search(rf"drawButtonFace\(graphics, {name}\)", faces_body):
                failures.append(f"the face of {name} is painted by the lit-while-hovered face, which "
                                f"is the bug")

        if "drawButtonFace(graphics, doneButton)" not in faces_body:
            failures.append("the button that closes the screen has lost the face it answers the "
                            "mouse with")

        if "drawButtonHovers" in source:
            failures.append("the screen still paints the gold line around the buttons, which the "
                            "owner asked to replace with the green face")

    sprite = between(source, "private Identifier filterSprite(Filter filter, Button button)")
    if sprite is None:
        failures.append("filterSprite does not take the button the mouse can be on any more")
    elif not re.search(r"return this\.filter == filter \|\| button\.isHovered\(\)\s*\?\s*"
                       r"LIT_BUTTON_SPRITE\s*:\s*BUTTON_SPRITE;", sprite):
        failures.append("filterSprite does not name the lit sprite for the filter the list is being "
                        "read by or the button under the mouse, and the plain one for the other")

    tab = between(source, "private Identifier tabSprite(Page tab)")
    if tab is not None and "isHovered" in tab:
        failures.append("the tabs are lit by the mouse too, and the owner asked for the two filters "
                        "only")

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

    print("\nthe face of a filter is the one the list is being read by or the one under the mouse, "
          "and the keyboard alone leaves it plain")
    return 0


if __name__ == "__main__":
    sys.exit(main())
