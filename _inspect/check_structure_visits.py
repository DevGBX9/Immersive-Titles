#!/usr/bin/env python3
"""Checks how a structure is counted as a visit: what its entry is read from, what its end is, and
that leaving one is what the next visit is read from.

What the owner reported: walking into a structure for the first time plays its reveal, but walking
out of it and back in plays nothing at all - no reveal, and no corner title either. The reason was
one line: the structure the player was last inside was kept as they left it, so the structure they
walked back into was still "the one they are in", the check saw no change, and neither title was
played. The game itself drops a player from a structure once they are out of it (see
{@link StructurePresence}), so the only thing that has to be dropped with it is the mod's own
memory of it - and it is the two things a structure has to tell apart that a biome does not, since
a player can leave a structure without leaving the biome it stands in, or walk from one structure
straight into another.

What the owner reported about a village: the game stands a village up piece by piece - its houses
and its streets - and the ground between the pieces belongs to no piece of it, so a player walking
that ground was read as leaving the village, and touching a house again played its corner title
again, as if the village had been left and entered anew. What the owner asked for: the entry of a
visit is still the touch of a piece, and what is left is the whole of the structure rather than any
piece of it - the region the game worked out for every piece of it at once - given up the moment it
is left, and read the same way again on the next visit (see {@link StructureCatalog#region}).

This is read two ways, because the two answer different questions:

- the rule, as a table of walks: what the owner asked for, walk by walk, run against a model of the
  rule. The same walks are run against the rules as they were before each fix, and the check fails
  if those older rules do *not* disagree with the table - a table that every rule passes would
  prove nothing,
- the code, as the lines it is written in: which of the two the titles play, that the memory of a
  structure is written back to nothing when the player is inside none, and that a visit is entered
  by a piece of the structure and held by the region of the whole of it until that is left.

    python _inspect/check_structure_visits.py
"""

import pathlib
import re
import sys

OVERLAY = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java")
CATALOG = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/StructureCatalog.java")
PRESENCE = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/StructurePresence.java")

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


# The village the owner reported about, as the model of it: two pieces the game stands it on - its
# houses - with ground between them that belongs to no piece, and the region of the village over
# both and the ground, which is the box the game worked out for every piece of it at once.
HOUSE_WEST = (10, 20)
HOUSE_EAST = (26, 40)
REGION = (10, 40)


class Presence:
    """StructurePresence#inside: what the player is inside, kept by the region of the structure that
    was entered once - with `keep_region` choosing between the rule as it is and the piece rule."""

    def __init__(self, keep_region=True):
        self.keep_region = keep_region
        self.region = None

    def check(self, x):
        """The structure of the game at `x`: the region of the one entered while it still holds the
        player, and a piece of one - which is where a visit is entered - otherwise."""
        if self.keep_region and self.region and self.region[0] <= x <= self.region[1]:
            return VILLAGE

        self.region = None

        for from_x, to_x in (HOUSE_WEST, HOUSE_EAST):
            if from_x <= x <= to_x:
                if self.keep_region:
                    self.region = REGION

                return VILLAGE

        return None


# How a visit to that village goes, step by step: where the player stands, what the game says they
# are inside there, and what the titles have to have played by the time they are there.
REGION_SCENARIOS = [
    ("through a village the game stands up piece by piece", [
        ("out in the open", 5, None, None),
        ("into the west house", 15, VILLAGE, ("reveal", VILLAGE)),
        ("the ground between the houses", 23, VILLAGE, None),
        ("out through the east house", 33, VILLAGE, None),
        ("out of the village", 50, None, None),
        ("back, onto the ground between them", 23, None, None),
        ("into the east house", 33, VILLAGE, ("corner", VILLAGE)),
        ("the ground between them again", 23, VILLAGE, None),
        ("out through the west house", 15, VILLAGE, None),
        ("out of the village again", 50, None, None),
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


def region_walk(keep_region):
    """Every region scenario, step by step, under one presence rule: what the game says the player
    is inside at each step (see Presence), and what the titles play for it."""
    steps = []

    for name, waypoints in REGION_SCENARIOS:
        visits = Visits()
        presence = Presence(keep_region)

        for where, x, inside, expected in waypoints:
            answer = presence.check(x)
            steps.append((name, where, answer, visits.check(answer), inside, expected))

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

    for name, where, answer, played, expected_inside, expected in region_walk(keep_region=True):
        ok = answer == expected_inside and played == expected
        failures += 0 if ok else 1
        print(f"  {'ok  ' if ok else 'FAIL'} {name}: {where} -> "
              f"{answer or 'nothing'}, {played or 'nothing'}"
              + ("" if ok else f" (expected {expected_inside or 'nothing'}, {expected or 'nothing'})"))

    # the same walks under the rule as it was: if they pass there too, the table says nothing about
    # the region and the region could be taken out without this check noticing
    older = region_walk(keep_region=False)
    if all(answer == expected_inside and played == expected
           for _, _, answer, played, expected_inside, expected in older):
        print("FAIL the region walks pass under the piece rule, so they do not hold it")
        failures += 1
    else:
        print("  ok   the region walks fail under the piece rule, as the owner reported")

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

    catalog = CATALOG.read_text(encoding="utf-8")

    # what a visit is entered by is still a piece of the structure standing where the player is
    start = re.search(r"\n\t\tprivate Optional<StructureStart> startAt\(BlockPos pos\)\s*\{(.*?)\n\t\t\}",
                      catalog, re.S)
    if start is None or "getStructureWithPieceAt" not in start.group(1):
        print("BROKEN the structure a player is inside is not read piece by piece any more, so the "
              "entry of a visit is not the touch of a piece the owner asked for")
        failures += 1

    # and what is kept with the answer is the box the game worked out for the whole of it
    region = re.search(r"\n\t\tOptional<Region> region\(BlockPos pos\)\s*\{(.*?)\n\t\t\}", catalog, re.S)
    if region is None or "getBoundingBox()" not in region.group(1):
        print("BROKEN the region of the structure is not the box the game worked out for the whole of "
              "it, so the ground between its pieces would be read as leaving it")
        failures += 1

    if not re.search(r"\n\trecord Region\(String structure, BoundingBox box\)", catalog):
        print("BROKEN no region is kept with the row of the structure, so a visit could not be held "
              "by the whole of one")
        failures += 1

    presence = PRESENCE.read_text(encoding="utf-8")

    # the region is kept per player, and a reading the region still holds ends there rather than
    # asking the game again
    if not re.search(r"\n\tprivate static final Map<UUID, StructureCatalog\.Region> INSIDE = "
                     r"new HashMap<>\(\);", presence):
        print("BROKEN the structure each player is inside is kept without the region of it")
        failures += 1

    held = re.search(r"\n\tprivate static void inside\(ServerLevel level\)\s*\{(.*?)\n\t\}", presence, re.S)
    if held is None:
        print("BROKEN the presence of a player in a structure is not read any more")
        failures += 1
    else:
        if "region.box().isInside(player.blockPosition())" not in held.group(1):
            print("BROKEN a player the region of the structure still holds is not kept in it, so the "
                  "ground between the pieces of a village is read as leaving it")
            failures += 1
        if "StructureCatalog.region(level, player.blockPosition())" not in held.group(1):
            print("BROKEN the structure a player is inside is not read with the region of it")
            failures += 1
        if "StructureCatalog.at(level, player.blockPosition())" in held.group(1):
            print("BROKEN a visit is read from the row of the structure alone, without the region")
            failures += 1

    # what the titles are handed is the row of the structure and not the region, which is the mod's
    # own memory of the visit rather than a name of anything
    handed = re.search(r"\n\tprivate static String insideStructure\(UUID id\)\s*\{(.*?)\n\t\}", presence, re.S)
    if handed is None or "region.structure()" not in handed.group(1):
        print("BROKEN the name of the structure handed to the client is not read out of the region")
        failures += 1

    if failures:
        print(f"{failures} step(s) or line(s) do not match the rule")
        return 1

    print("a structure is a visit of its own each time the player walks out of it and back in, and "
          "its region - every piece of it inside one box - is what the visit is held by until it is left")
    return 0


if __name__ == "__main__":
    sys.exit(main())
