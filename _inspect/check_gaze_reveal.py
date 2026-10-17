#!/usr/bin/env python3
"""Checks the look trigger: a structure the laser of a player reaches from outside, and what it hands over.

What the owner asked for: standing outside a structure, keeping the look on it for a second and a half,
plays the big title of that structure - for structures only, and for the big reveal only. The look is
read as an invisible laser rather than as the block under the crosshair: a line from the player's eyes
along the way they are facing, no longer than six blocks, asked whether it has entered the region of a
structure at all - so the block the eye happens to land on does not have to be one of the structure's own,
which is what the owner reported was required of it before.

What the laser is not is a laser that goes through the world. The second thing the owner asked for is that
a wall is a wall, so the line is the player's own line of sight: it is clipped against the blocks a player
cannot walk through before it is read against the structures of the world (StructurePresence#lookedAt), and
the block it stopped on is read with it - which is what keeps a look at the outside wall of a house from
being a look at nothing at all and makes it a look at the village the house belongs to. A structure behind
a wall is not looked at, and a laser over grass, flowers or the water of a river is not stopped by them.

Whether a player's line of sight has reached a structure is a question only the server can answer, since
the client is told nothing about structures (see StructurePresence), so the laser and the length of the
look are read there (StructurePresence#lookedAt, StructureCatalog#along) and the title is played on the
client (see TitleOverlay#checkGaze). What this reads is all three:

- the rule, as a table of looks: one reading per step - what the laser is on, and what the server has
  handed over by the time of it. One reading is `GAZE_INTERVAL_TICKS` ticks and a look has to last
  `GAZE_TICKS` of them, both read out of the source rather than written here, so a look of a second and
  a half is what the table holds the code to,
- a second table of worlds: one look over each of them - a structure in the open, one behind a wall, the
  outer wall of a structure, a structure seen past the wall of another - and what the laser reaches in
  them. The same looks are read under the rule as it was, i.e. a laser nothing stops, and the check fails
  if that older rule does not disagree with the table,
- the laser, as the lines it is written in: that it is the player's own line of sight over six blocks,
  that the world stops it - the blocks with a body to them, and no fluid - that what is read is the line
  as far as it got and, when it was stopped, the block it stopped on, that it is read against structures
  at every position of it and not only at the block under the crosshair, that the structures of the world
  are worked out once for the whole line, and that a structure the player is inside does not count,
- the client, as the lines it is written in: that the reveal is played for a structure that had no
  reveal yet, written down as discovered, with no corner title and with the walk into it left a visit of
  its own.

    python _inspect/check_gaze_reveal.py
"""

import collections
import pathlib
import re
import sys

PRESENCE = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/StructurePresence.java")
CATALOG = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/StructureCatalog.java")
OVERLAY = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java")

VILLAGE = "minecraft:village_plains"
MONUMENT = "minecraft:monument"

# How many ticks a second of the game is measured in, which is what a second and a half comes to.
TICKS_PER_SECOND = 20

# The look the owner asked for: a second and a half of looking, from six blocks away at the most.
LOOK_SECONDS = 1.5
LOOK_BLOCKS = 6.0


def constant(source, name, kind):
    """The value of a constant of the source, as an int or a float."""
    found = re.search(rf"{name} = ([0-9.]+)(?:[DFdf])?;", source)
    assert found is not None, f"{name} is not in the source"

    return kind(found.group(1))


def body_of(source, signature):
    """The body of a method, read to its own closing line rather than to its first `}`."""
    found = re.search(re.escape(signature) + r"\s*\{(.*?)\n\t\}", source, re.S)

    return None if found is None else found.group(1)


class Looks:
    """StructurePresence#gazing: one reading of the laser per step, and what it handed over.

    `hand_over_once` is the rule as it is. With it off, the reveal is handed over again at every reading
    that finds the look still going - which is what a player who is told to keep looking would get, and
    what the table below has to catch.
    """

    def __init__(self, interval, ticks, hand_over_once=True):
        self.interval = interval
        self.ticks = ticks
        self.hand_over_once = hand_over_once
        self.look = None  # (structure, ticks counted, told)

    def read(self, on_structure):
        if on_structure is None:
            self.look = None
            return None

        if self.look is None or self.look[0] != on_structure:
            self.look = (on_structure, 0, False)
            return None

        structure, counted, told = self.look
        counted += self.interval
        enough = counted >= self.ticks
        told = told or (enough and not self.hand_over_once)

        if enough and not told:
            told = True
            handed = structure
        else:
            handed = None

        self.look = (structure, counted, told)
        return handed


# What the laser reaches, step by step, and what the server has to hand over by the end of it. `None` is
# a laser that reached no structure at all.
SCENARIOS = [
    ("a laser held on one structure for a second and a half",
     [VILLAGE] * 7, [VILLAGE]),
    ("one reading short of it",
     [VILLAGE] * 6, []),
    ("looked away and back at the same structure",
     [VILLAGE] * 3 + [None] + [VILLAGE] * 7, [VILLAGE]),
    ("looked away for a single reading near the end",
     [VILLAGE] * 6 + [None] + [VILLAGE], []),
    ("the laser moving onto a structure of its own",
     [VILLAGE] * 3 + [MONUMENT] * 7, [MONUMENT]),
    ("a look held long after it was handed over",
     [VILLAGE] * 20, [VILLAGE]),
    ("a laser that reaches no structure at all",
     [None] * 10, []),
    ("a laser that never rests on one",
     [VILLAGE, MONUMENT, VILLAGE, MONUMENT], []),
]


def walk(interval, ticks, hand_over_once):
    """Every scenario run as the list of (name, handed, expected) it comes to under one rule."""
    steps = []

    for name, laser, expected in SCENARIOS:
        looks = Looks(interval, ticks, hand_over_once)
        handed = [reached for reached in (looks.read(step) for step in laser) if reached is not None]
        steps.append((name, handed, expected))

    return steps


# One look over one world: `stopped` is the world stopping the laser within the six blocks it reaches,
# `reached` what the line itself reached before it was stopped, `on_wall` the structure the block it
# stopped on belongs to, and `through` what the laser would have reached over the whole of the line, i.e.
# with the world stopping nothing at all - which is the rule as it was before a wall was a wall to it, and
# the one answer the two rules disagree about.
Look = collections.namedtuple("Look", "stopped reached on_wall through")


class Sight:
    """StructurePresence#lookedAt: what one look reaches, with `stop_at_walls` choosing between the rule as
    it is and the rule as it was, i.e. a laser the world does not stop at all."""

    def __init__(self, stop_at_walls=True):
        self.stop_at_walls = stop_at_walls

    def reach(self, look):
        if self.stop_at_walls and look.stopped:
            # the line is read as far as the world let it get, and the block it stopped on is read with
            # it: a look at the outer wall of a house is a look at the village the house belongs to
            return look.reached if look.reached is not None else look.on_wall

        return look.through


# The worlds a look is read over, and what it has to reach in each of them. `None` is a look that
# reaches no structure.
LOOKS = [
    ("a structure standing in the open within reach",
     Look(False, VILLAGE, None, VILLAGE), VILLAGE),
    ("a structure behind a wall",
     Look(True, None, None, VILLAGE), None),
    ("the outer wall of a structure, where the line's own positions land outside it",
     Look(True, None, VILLAGE, None), VILLAGE),
    ("the wall of one structure with another standing past it",
     Look(True, None, VILLAGE, MONUMENT), VILLAGE),
    ("a structure the line reaches before a wall of another stops it",
     Look(True, MONUMENT, VILLAGE, MONUMENT), MONUMENT),
    ("a laser over open ground that reaches nothing",
     Look(False, None, None, None), None),
]


def looks_reach(stop_at_walls):
    """Every look as the list of (name, reached, expected) it comes to under one rule."""
    return [(name, Sight(stop_at_walls).reach(look), expected) for name, look, expected in LOOKS]


def main():
    presence = PRESENCE.read_text(encoding="utf-8")
    catalog = CATALOG.read_text(encoding="utf-8")
    overlay = OVERLAY.read_text(encoding="utf-8")
    failures = 0

    interval = constant(presence, "GAZE_INTERVAL_TICKS", int)
    ticks = constant(presence, "GAZE_TICKS", int)
    distance = constant(presence, "GAZE_DISTANCE", float)
    step = constant(catalog, "LINE_STEP", float)

    # the look the owner asked for, in the game's own units: a second and a half, and six blocks
    wanted = LOOK_SECONDS * TICKS_PER_SECOND
    if ticks / TICKS_PER_SECOND != LOOK_SECONDS:
        print(f"BROKEN a look has to last {LOOK_SECONDS} seconds of the game, i.e. {wanted} ticks; "
              f"GAZE_TICKS is {ticks}")
        failures += 1
    if distance != LOOK_BLOCKS:
        print(f"BROKEN the laser has to reach {LOOK_BLOCKS} blocks from the player; "
              f"GAZE_DISTANCE is {distance}")
        failures += 1
    if interval <= 0 or interval > ticks:
        print(f"BROKEN a look is read every {interval} ticks against a look of {ticks}")
        failures += 1

    for name, handed, expected in walk(interval, ticks, hand_over_once=True):
        ok = handed == expected
        failures += 0 if ok else 1
        print(f"  {'ok  ' if ok else 'FAIL'} {name}: handed over {handed or 'nothing'}"
              + ("" if ok else f" (expected {expected or 'nothing'})"))

    # the same table under the rule that keeps handing the same reveal over: if it passes there too,
    # the rows saying a look is handed over once hold nothing
    loose = walk(interval, ticks, hand_over_once=False)
    if all(handed == expected for _, handed, expected in loose):
        print("FAIL the looks pass when the same look is handed over at every reading, so the table "
              "does not hold a reveal to being handed over once")
        failures += 1
    else:
        print("  ok   a look that keeps handing its reveal over at every reading fails the table")

    # and the worlds a look is read over: what the laser reaches in each of them, and that the rule as it
    # was - a laser nothing stops - reads them differently, since that reaches what a wall hides
    for name, reached, expected in looks_reach(stop_at_walls=True):
        ok = reached == expected
        failures += 0 if ok else 1
        print(f"  {'ok  ' if ok else 'FAIL'} {name}: reached {reached or 'nothing'}"
              + ("" if ok else f" (expected {expected or 'nothing'})"))

    older_looks = looks_reach(stop_at_walls=False)
    if all(reached == expected for _, reached, expected in older_looks):
        print("FAIL the looks pass under the rule as it was, i.e. a laser a wall does not stop, so the "
              "table does not hold the world to stopping the laser")
        failures += 1
    else:
        print("  ok   the looks fail under the rule as it was, i.e. a laser that reaches through walls")

    # the laser itself: the player's own line of sight, six blocks long, read as the line it is and not
    # as the block at the end of it
    laser = body_of(presence, "private static String lookedAt(ServerLevel level, ServerPlayer player)")
    if laser is None:
        print("BROKEN the server no longer reads what a player is looking at")
        failures += 1
    else:
        if not re.search(r"player\.getViewVector\(1\.0F\)\.scale\(GAZE_DISTANCE\)", laser):
            print("BROKEN the laser is not cast along the way the player is facing, six blocks long")
            failures += 1
        if not re.search(r"level\.clip\(new ClipContext\(eye, reach, ClipContext\.Block\.COLLIDER,\s*"
                         r"ClipContext\.Fluid\.NONE, player\)\)", laser):
            print("BROKEN the laser is not clipped against the blocks with a body to them, so a "
                  "structure behind a wall, a hill or a tree is looked at through it")
            failures += 1
        if not re.search(r"stop\.getType\(\) != HitResult\.Type\.MISS", laser):
            print("BROKEN the line is read to its far end whether the world stopped it or not")
            failures += 1
        if "StructureCatalog.along(level, eye, end)" not in laser:
            print("BROKEN the laser is not read as a line against the structures of the world, as far "
                  "as the world let it get")
            failures += 1
        if "StructureCatalog.at(level, stop.getBlockPos())" not in laser:
            print("BROKEN the block the laser stopped on is not read, so looking at the outer wall of "
                  "a structure is a look at nothing at all")
            failures += 1
        elif laser.index("StructureCatalog.along(level, eye, end)") > laser.index(
                "StructureCatalog.at(level, stop.getBlockPos())"):
            print("BROKEN the block the laser stopped on is read before the line is, so the look is the "
                  "block under the crosshair again")
            failures += 1
        if not re.search(r"if \(structure == null && stopped\)", laser):
            print("BROKEN the block the laser stopped on is read whether the line reached anything or "
                  "not, so what is in sight is not read first")
            failures += 1
        if not re.search(r"structure == null \|\| structure\.equals\(INSIDE\.get\(player\.getUUID\(\)\)\)",
                         laser):
            print("BROKEN a structure the player is inside counts as one looked at, so standing in a "
                  "structure and looking at it would play its reveal")
            failures += 1

    # and how a line is read: every position of it is asked, nothing of the world stops it, and the
    # structures of the world are worked out once between them all
    along = body_of(catalog, "static Optional<String> along(ServerLevel level, Vec3 from, Vec3 to)")
    if along is None:
        print("BROKEN the catalog reads no line at all, so a look has no way of reaching a structure "
              "the eye is not on a block of")
        failures += 1
    else:
        if not re.search(r"for \(int step = 1; step <= steps; step\+\+\)", along):
            print("BROKEN a line is not walked position by position")
            failures += 1
        if "BlockPos.containing(point)" not in along:
            print("BROKEN a position of a line is not read as the block it is in")
            failures += 1
        if not re.search(r"if \(reached\.isPresent\(\)\)\s*\{\s*return reached;", along):
            print("BROKEN a line does not stop at the first structure it reaches")
            failures += 1
        if "LISTED_AS.keySet()" in along:
            print("BROKEN the structures of the world are worked out for every position of a line "
                  "instead of once for the whole of it")
            failures += 1
        if not re.search(r"Optional<Sight> reading = sight\(level\);", along):
            print("BROKEN the structures of the world are not read once for the line")
            failures += 1
        if not re.search(r"Math\.ceil\(length / LINE_STEP\)", along):
            print("BROKEN the positions of a line are not worked out from its length at LINE_STEP")
            failures += 1

    if step <= 0 or step > 1.0:
        print(f"BROKEN a line is read every {step} blocks, which is coarser than the block a structure "
              f"can be thick and thin enough to slip between two positions")
        failures += 1

    # a look is handed over once, and the laser leaving the structure ends it
    if not re.search(r"boolean told = look\.told\(\) \|\| ticks >= GAZE_TICKS;", presence):
        print("BROKEN a look is not counted up to GAZE_TICKS, or is not remembered as one look")
        failures += 1
    if not re.search(r"if \(told && !look\.told\(\)\)\s*\{\s*LOOKED_AT\.put\(id, structure\);", presence):
        print("BROKEN a look that has lasted long enough is not handed over once and once only")
        failures += 1
    if not re.search(r"if \(structure == null\)\s*\{\s*LOOKING\.remove\(id\);", presence):
        print("BROKEN a laser that left the structure does not end the look")
        failures += 1

    # what the client reads is taken out of there, so the same look cannot be read twice
    taken = re.search(r"static Optional<String> takeLookedAt\(Minecraft client\)(.*?)\n\t\}", presence, re.S)
    if taken is None or "LOOKED_AT.remove(client.player.getUUID())" not in taken.group(1):
        print("BROKEN the reveal of a look is not taken out of what the server wrote down")
        failures += 1

    # and the client: the reveal of the looked-at structure, written down as a discovery, and no
    # corner title - a look is the way to the reveal and not a way of reading a structure already read
    gaze = body_of(overlay, "private static void checkGaze(Minecraft client)")
    if gaze is None:
        print("BROKEN the client plays nothing for a structure the game says it was looked at")
        failures += 1
    else:
        if "StructurePresence.takeLookedAt(client)" not in gaze:
            print("BROKEN the client does not read what the server watched the laser for")
            failures += 1
        if not re.search(r"!\s*VISITED_STRUCTURES\.add\(structureId\)", gaze):
            print("BROKEN a look is not read as a first visit of the structure: the reveal would be "
                  "played for a structure that already had one")
            failures += 1
        if "DiscoveredStructures.remember(world, structureId)" not in gaze:
            print("BROKEN a structure looked at is not written down as discovered, so the collection "
                  "would never mark it found")
            failures += 1
        if not re.search(r"startReveal\(StructureNames\.displayName\(structureId\)\)", gaze):
            print("BROKEN the big reveal of the structure looked at is not played")
            failures += 1
        if "startCornerTitle" in gaze:
            print("BROKEN a look plays the corner title, which the owner asked it not to: a look is "
                  "the big reveal and nothing else")
            failures += 1
        if "lastStructureId" in gaze:
            print("BROKEN a look changes the structure the player is inside, so walking into the "
                  "structure just looked at would not be a visit of its own")
            failures += 1

    # the look waits for the player to move after a world is joined, like every other title
    moved = re.search(r"distanceToSqr\(joinPosition\) >= MOVED_DISTANCE_SQR\)\s*\{(.*?)\n\t\t\}",
                      overlay, re.S)
    if moved is None or "checkGaze(client);" not in moved.group(1):
        print("BROKEN a look is read without waiting for the player to move after joining a world, so "
              "a structure the camera happened to be on could be titled as the world loads")
        failures += 1

    if failures:
        print(f"{failures} look(s) or line(s) do not match the rule")
        return 1

    print("a laser six blocks long, held on a structure the player is standing outside of for a second "
          "and a half - with the world stopping it, so that what is looked at is what is in sight - gets "
          "that structure its reveal once, discovers it, and gets nothing once it has had its reveal")
    return 0


if __name__ == "__main__":
    sys.exit(main())
