#!/usr/bin/env python3
"""Checks the rule the automatic titles follow against a table of walks.

This is a model of the rule and not the mod: it mirrors the two decisions the mod makes on every
biome check - what BiomeNames.place reports for a player, and what TitleOverlay.checkBiome then
plays for it - so that the behaviour the docs describe can be read as a table of walks and held
against the code. It proves nothing about the game: what the game reports for a player in a cave,
and what the surface of the ground around them wears, is what the rule is written for, and that has
to be seen in the game itself.

    python _inspect/check_place_rule.py
"""

import sys

# BiomeNames.ROOF_DEPTH: how far the ground over the player has to rise before they count as being
# under it. What is measured is the game's MOTION_BLOCKING_NO_LEAVES heightmap, which leaves the
# leaves out of its ground but counts water in it - the walks below say so by giving the blocks
# that stand over the player.
ROOF_DEPTH = 4

# The three worlds the mod can be in, as the two properties of their dimension type that the rule
# reads: whether their ground has a sky over it, and whether a ceiling covers it.
WORLDS = {
    "overworld": (True, False),
    "nether": (False, True),
    "end": (True, False),
}

PLAINS = "minecraft:plains"
FOREST = "minecraft:forest"
DARK_FOREST = "minecraft:dark_forest"
SNOWY = "minecraft:snowy_plains"
OCEAN = "minecraft:ocean"
DEEP_OCEAN = "minecraft:deep_ocean"
DRIPSTONE = "minecraft:dripstone_caves"
DEEP_DARK = "minecraft:deep_dark"
NETHER = "minecraft:nether_wastes"
BASALT = "minecraft:basalt_deltas"
THE_END = "minecraft:the_end"
END_HIGHLANDS = "minecraft:end_highlands"


def surface(column, world):
    """The biome of the ground over the player's column, i.e. of the surface a player standing on
    that column would have under their feet, or None in a world whose ground a ceiling covers (the
    nether, a cave world): its biomes are stacked by depth instead of sitting under a surface, so
    there is no ground over a player to hold anything against."""
    sky_light, ceiling = WORLDS[world]

    if not sky_light or ceiling:
        return None

    return column


def place(here, column, roof, world, around=frozenset()):
    """The biome the titles name for a player: the biome they are in, except where the game is
    reporting ground rather than a place - under the biome over their head, once ROOF_DEPTH blocks
    of ground stand over them, and in any biome the surface of the ground around them wears (the
    biomes read around them, `around`), which is a place of the surface however deep the player is
    when the game reports it."""
    if here is None:
        return None

    over = surface(column, world)

    if over is None:
        return here

    if over == here:
        return here if roof < ROOF_DEPTH else None

    return None if here in around else here


class Titles:
    """TitleOverlay: the place it was last told about, what has already had its reveal, and what it
    played."""

    def __init__(self):
        self.last = None
        self.discovered = set()
        self.played = []

    def check(self, here, column=None, roof=0, world="overworld", around=frozenset()):
        # what the game reports for the player, what the ground over their column wears, and what
        # the surface of the ground around them wears
        biome = place(here, column, roof, world, around)

        if biome is None or biome == self.last:
            return None

        self.last = biome

        if biome in self.discovered:
            played = ("corner", biome)
        else:
            self.discovered.add(biome)
            played = ("reveal", biome)

        self.played.append(played)
        return played


# Every step is a place the player stands in: the biome the game reports there, the biome of the
# column the world has over them, how many blocks of ground are over their head, and what the titles
# have to have played by the time they are in it. A step may add a sixth element, the biomes the
# surface of the ground around the player wears, for the walks that are under the edge of a biome,
# where that is not just the one over their head; without it nothing around them wears anything.
SCENARIOS = [
    ("walking a world", "overworld", [
        ("on the surface of a plains", PLAINS, PLAINS, 0, ("reveal", PLAINS)),
        ("on the surface of a forest", FOREST, FOREST, 0, ("reveal", FOREST)),
        ("back out on the plains", PLAINS, PLAINS, 0, ("corner", PLAINS)),
        ("up in the air over a forest", FOREST, FOREST, -30, ("corner", FOREST)),
    ]),
    ("the cave under a forest", "overworld", [
        ("on the surface of a forest", FOREST, FOREST, 0, ("reveal", FOREST)),
        # the reported bug: this walk used to be titled as the biome over the player's head
        ("a few blocks into the cave", FOREST, FOREST, 12, None),
        ("deep in the cave, under a plains", PLAINS, PLAINS, 40, None),
        ("under a snowy plains further on", SNOWY, SNOWY, 40, None),
        ("back out on the snowy plains", SNOWY, SNOWY, 0, ("reveal", SNOWY)),
    ]),
    ("the mouth of a tunnel", "overworld", [
        ("on the surface of a plains", PLAINS, PLAINS, 0, ("reveal", PLAINS)),
        ("two blocks into the rock, still in the open", FOREST, FOREST, 2, ("reveal", FOREST)),
        ("four blocks in, under it", SNOWY, SNOWY, ROOF_DEPTH, None),
        ("back out on the snowy plains", SNOWY, SNOWY, 0, ("reveal", SNOWY)),
    ]),
    ("the ground that reaches under its neighbour", "overworld", [
        ("on the surface of a forest", FOREST, FOREST, 0, ("reveal", FOREST)),
        # the reported bug: a walk under the edge of a biome. The game reads the biome of a position
        # from the climate there, the climate of a cave is the climate of its depth, and the region
        # a surface biome reaches across the ground down there does not end where the same biome
        # ends on the surface - so the dark forest can be reported for a player the forest is over,
        # and the column over the player alone cannot tell that from a biome of the underground
        ("in the cave under the forest, reported as the dark forest", DARK_FOREST, FOREST,
         ROOF_DEPTH, None, frozenset({FOREST, DARK_FOREST})),
        # the same edge a couple of blocks under it: not being under ground does not make the biome
        # the surface around the player wears one of theirs either, and holding it back is the read
        # of the ground around them
        ("a shallow spot under the same edge", DARK_FOREST, FOREST, 2, None,
         frozenset({FOREST, DARK_FOREST})),
        ("deeper in the rock, back in the forest", FOREST, FOREST, 30, None),
        ("into a dripstone cave under the forest", DRIPSTONE, FOREST, 30, ("reveal", DRIPSTONE)),
        ("out of the cave onto the dark forest above", DARK_FOREST, DARK_FOREST, 0,
         ("reveal", DARK_FOREST)),
    ]),
    ("the cave biomes", "overworld", [
        ("on the surface of a forest", FOREST, FOREST, 0, ("reveal", FOREST)),
        ("into a dripstone cave", DRIPSTONE, FOREST, 30, ("reveal", DRIPSTONE)),
        ("deeper, into the deep dark", DEEP_DARK, FOREST, 40, ("reveal", DEEP_DARK)),
        ("back in the rock under the forest", FOREST, FOREST, 30, None),
        ("into that dripstone cave again", DRIPSTONE, FOREST, 30, ("corner", DRIPSTONE)),
        ("back out on the forest", FOREST, FOREST, 0, ("corner", FOREST)),
    ]),
    ("the water", "overworld", [
        ("swimming at the surface of an ocean", OCEAN, OCEAN, 0, ("reveal", OCEAN)),
        # water is ground to the heightmap the rule reads: a diver is under it
        ("diving four blocks down in the same water", DEEP_OCEAN, DEEP_OCEAN, ROOF_DEPTH, None),
        ("back up over the deep ocean", DEEP_OCEAN, DEEP_OCEAN, 0, ("reveal", DEEP_OCEAN)),
        ("into a cave under the ocean floor", DEEP_OCEAN, DEEP_OCEAN, 14, None),
        ("up and out of the water, over a beach", "minecraft:beach", "minecraft:beach", 0,
         ("reveal", "minecraft:beach")),
    ]),
    ("walking under the leaves", "overworld", [
        ("on the surface of a forest", FOREST, FOREST, 0, ("reveal", FOREST)),
        # a canopy is no roof: the heightmap the rule reads leaves the leaves out of it
        ("under the canopy of the same forest", FOREST, FOREST, 0, None),
        ("out of the wood into a plains", PLAINS, PLAINS, 0, ("reveal", PLAINS)),
    ]),
    ("a world with a ceiling over its ground", "nether", [
        # the nether: rock over every place a player can be, so no column to be under at all
        ("in the nether wastes", NETHER, NETHER, 90, ("reveal", NETHER)),
        ("into a basalt delta", BASALT, BASALT, 90, ("reveal", BASALT)),
        ("back in the nether wastes", NETHER, NETHER, 90, ("corner", NETHER)),
    ]),
    ("the end", "end", [
        ("on the central island", THE_END, THE_END, 0, ("reveal", THE_END)),
        ("out among the islands", END_HIGHLANDS, END_HIGHLANDS, 0, ("reveal", END_HIGHLANDS)),
    ]),
    ("joining a world inside a cave", "overworld", [
        ("the first check, in a cave under a plains", PLAINS, PLAINS, 30, None),
        ("out on the plains above it", PLAINS, PLAINS, 0, ("reveal", PLAINS)),
    ]),
]


def main():
    failures = 0

    for name, world, steps in SCENARIOS:
        titles = Titles()
        print(f"{name} ({world}):")

        for step in steps:
            where, here, column, roof, expected = step[:5]
            around = step[5] if len(step) > 5 else frozenset()
            played = titles.check(here, column, roof, world, around)
            ok = played == expected
            failures += 0 if ok else 1
            print(f"  {'ok  ' if ok else 'FAIL'} {where}: {played or 'nothing'}")

            if not ok:
                print(f"       expected {expected or 'nothing'}")

        print()

    if failures:
        print(f"{failures} step(s) do not match the rule as documented")
        return 1

    print("every step matches the rule as documented")
    return 0


if __name__ == "__main__":
    sys.exit(main())
