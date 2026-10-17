#!/usr/bin/env python3
"""Checks how a structure is counted as a visit, and that leaving one is what the next visit is read
from.

What the owner reported: walking into a structure for the first time plays its reveal, but walking
out of it and back in plays nothing at all - no reveal, and no corner title either. The reason was
one line: the structure the player was last inside was kept as they left it, so the structure they
walked back into was still "the one they are in", the check saw no change, and neither title was
played. The game itself drops a player from a structure the moment their own block is out of it
(see {@link StructurePresence}), so the only thing that has to be dropped with it is the mod's own
memory of it - and it is the two things a structure has to tell apart that a biome does not, since a
player can leave a structure without leaving the biome it stands in, or walk from one structure
straight into another.

This is read two ways, because the two answer different questions:

- the rule, as a table of walks: what the owner asked for, walk by walk, run against a model of the
  rule. The same walks are run against the rule as it was before the fix, and the check fails if
  that older rule does *not* disagree with the table - a table that both rules pass would prove
  nothing,
- the code, as the lines it is written in: which of the two the titles play, and that the memory of
  a structure is written back to nothing when the player is inside none.

    python _inspect/check_structure_visits.py
"""

import pathlib
import re
import sys

OVERLAY = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java")

VILLAGE = "minecraft:village_plains"
MINESHAFT = "minecraft:mineshaft"


class Visits:
    """TitleOverlay#checkStructure: what the player is inside, what has had its reveal, and what was
    played - with `record_leave` choosing between the rule as it is and the rule as it was."""

    def __init__(self, record_leave=True):
        self.record_leave = record_leave
        self.last = None
        self.discovered = set()
        self.played = []

    def check(self, inside):
        if inside is None:
            if self.record_leave:
                self.last = None

            return None

        if inside == self.last:
            return None

        self.last = inside

        if inside in self.discovered:
            played = ("corner", inside)
        else:
            self.discovered.add(inside)
            played = ("reveal", inside)

        self.played.append(played)
        return played


# The walk the owner described, step by step: what the game says the player is inside, and what the
# titles have to have played by the time they are there.
SCENARIOS = [
    ("into a village and out again", [
        ("walking in the open", None, None),
        ("into the village", VILLAGE, ("reveal", VILLAGE)),
        ("out of it, in the same biome", None, None),
        ("back into the village", VILLAGE, ("corner", VILLAGE)),
        ("out of it again", None, None),
        ("back into the village again", VILLAGE, ("corner", VILLAGE)),
    ]),
    ("one structure straight into another", [
        ("into a mineshaft", MINESHAFT, ("reveal", MINESHAFT)),
        ("out of the mineshaft", None, None),
        ("into an end city of its own", "minecraft:end_city", ("reveal", "minecraft:end_city")),
        ("back into the mineshaft", MINESHAFT, ("corner", MINESHAFT)),
    ]),
    ("standing inside without leaving", [
        ("into the village", VILLAGE, ("reveal", VILLAGE)),
        ("a step further in", VILLAGE, None),
        ("a step further in again", VILLAGE, None),
        ("out of it", None, None),
        ("in again", VILLAGE, ("corner", VILLAGE)),
    ]),
]


def walk(record_leave):
    """Every scenario as the list of (where, played, expected) it comes to under one rule."""
    steps = []

    for name, waypoints in SCENARIOS:
        visits = Visits(record_leave)

        for where, inside, expected in waypoints:
            steps.append((name, where, visits.check(inside), expected))

    return steps


def main():
    failures = 0

    for name, where, played, expected in walk(record_leave=True):
        ok = played == expected
        failures += 0 if ok else 1
        print(f"  {'ok  ' if ok else 'FAIL'} {name}: {where} -> {played or 'nothing'}"
              + ("" if ok else f" (expected {expected or 'nothing'})"))

    # the same walks under the rule as it was: if they pass there too, the table says nothing about
    # the fix and the fix could be taken out without this check noticing
    older = walk(record_leave=False)
    if all(played == expected for _, _, played, expected in older):
        print("FAIL the walks pass under the rule as it was before the fix, so they do not hold it")
        failures += 1
    else:
        print("  ok   the walks fail under the rule as it was before the fix, as the owner reported")

    source = OVERLAY.read_text(encoding="utf-8")

    # the code has to write the leaving down: inside none, the memory of the structure is nothing
    body = re.search(r"private static void checkStructure\(Minecraft client\)\s*\{(.*?)\n\t\}", source,
                     re.S)
    if body is None:
        print("BROKEN the titles check no structure any more")
        failures += 1
    else:
        # the branch is read to its own closing line rather than to its first `}`: the comments in
        # it carry javadoc links, whose braces are not the braces of the code
        leaving = re.search(r"if \(structureId == null\)\s*\{(.*?)\n\t\t\}", body.group(1), re.S)

        if leaving is None:
            print("BROKEN the check reads a structure that is not there as one that is, which is the "
                  "bug: leaving a structure keeps it as the one the player is inside")
            failures += 1
        elif not re.search(r"lastStructureId = null;", leaving.group(1)):
            print("BROKEN being inside no structure does not clear the structure the player was last "
                  "inside")
            failures += 1
        elif "startReveal(" in leaving.group(1) or "startCornerTitle(" in leaving.group(1):
            print("BROKEN a title is played for being inside no structure")
            failures += 1

        # a change of structure is what plays a title, and it is the two the screen has: the reveal
        # of one that was never visited, and the corner title of one that was
        if not re.search(r"if \(VISITED_STRUCTURES\.add\(structureId\)\)\s*\{[^}]*"
                         r"startReveal\(name\);", body.group(1), re.S):
            print("BROKEN the reveal of a structure that was never visited is not played")
            failures += 1
        if not re.search(r"else\s*\{\s*startCornerTitle\(name\);\s*\}", body.group(1)):
            print("BROKEN the corner title of a structure that was visited before is not played")
            failures += 1

    if failures:
        print(f"{failures} step(s) or line(s) do not match the rule")
        return 1

    print("a structure is a visit of its own each time the player walks out of it and back in")
    return 0


if __name__ == "__main__":
    sys.exit(main())
