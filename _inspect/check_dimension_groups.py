#!/usr/bin/env python3
"""Checks the dimensions the collection divides its biomes by against the game's own tags.

A world has no field that says which dimension a biome is in. What it has is the three biome tags the
game generates its dimensions from - `is_overworld`, `is_nether` and `is_end` - and those are what
`BiomeCatalog` files every biome by, so a biome of a mod is placed under the dimension its author
tagged it into. Where a tag says nothing the mod falls back on its own reading of what the game
ships: two short lists of the biomes of the nether and of the end, and the void, which is in none of
the three tags and belongs to no dimension at all.

Those two lists are the one place in the mod that copies a fact out of the game instead of reading it
there, so this holds them against the game itself: the lists are read out of `BiomeCatalog`, the
three tags out of the client jar that is already on disk, and the two are compared. What is checked
is what the sections of the collection rest on:

- the nether and end lists are exactly the biomes of the game's `is_nether` and `is_end` tags,
- every biome of the mod's own order is in exactly one dimension tag, but the void, which is in none,
- the mod's own reading of each of those biomes - what it falls back on when no tag places one - is
  the dimension the tags put it in, and nothing at all for the void,
- the dimensions are declared in the order the sections are shown in, and the list is sorted by them
  before anything else, so a section is a block of the list and not a name scattered through it,
- and every one of them has a name written down, which is what the line over a section is drawn with.

    python _inspect/check_dimension_groups.py
"""

import json
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from preview_font import JAR  # noqa: E402

CATALOG = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCatalog.java")
SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")
LANG = pathlib.Path("src/main/resources/assets/immersive_titles/lang/en_us.json")

# the dimensions of the collection, in the order the sections are shown in
DIMENSIONS = ("OVERWORLD", "NETHER", "END", "UNPLACED")

# the tags each of the three dimensions of a world is generated from, as the game names them
TAGS = ("is_overworld", "is_nether", "is_end")

KEY_PREFIX = "screen.immersive_titles.collection.dimension."


def ids_of(body, declaration, factory):
    """The ids of a `Set.of(...)` or `List.of(...)` a source declares, in the order they are written."""
    block = re.search(rf"{declaration} = {factory}\.of\((.*?)\);", body, re.S).group(1)

    return re.findall(r'"([^"]+)"', block)


def tags():
    """The three dimension tags of the game, out of the jar the build is against."""
    with zipfile.ZipFile(JAR) as jar:
        return {tag: set(json.loads(jar.read(f"data/minecraft/tags/worldgen/biome/{tag}.json"))["values"])
                for tag in TAGS}


def declared_dimensions(body):
    """The constants of the `Dimension` enum of the catalog, in the order they are declared in."""
    enum = re.search(r"\tenum Dimension\n\t\{\n(.*?)\n\t\}", body, re.S).group(1)

    return re.findall(r"^\t\t([A-Z_]+),?$", enum, re.M)


def main():
    body = CATALOG.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    lang = json.loads(LANG.read_text(encoding="utf-8"))

    order = ids_of(body, "DISCOVERY_ORDER", "List")
    nether = set(ids_of(body, "NETHER_BIOMES", "Set"))
    end = set(ids_of(body, "END_BIOMES", "Set"))
    void = re.search(r'THE_VOID = "([^"]+)"', body).group(1)
    game = tags()

    failures = []

    # the two lists the mod keeps by hand are the game's own tags, entry for entry
    for tag, declared, name in (("is_nether", nether, "NETHER_BIOMES"), ("is_end", end, "END_BIOMES")):
        for missing in sorted(game[tag] - declared):
            failures.append(f"{name} does not list {missing}, which the game's {tag} tag holds")
        for extra in sorted(declared - game[tag]):
            failures.append(f"{name} lists {extra}, which the game's {tag} tag does not hold")

    # and every biome of the order is in one of the tags, but the void, which is in none of them
    for id in order:
        held = [tag for tag in TAGS if id in game[tag]]

        if id == void:
            if held:
                failures.append(f"the void is in {held}, and the collection files it as having no dimension")
        elif len(held) != 1:
            failures.append(f"{id} is in {held or 'none'} of the game's dimension tags")

    if void not in order:
        failures.append(f"the void of the catalog is {void!r}, which its own order does not have")

    # what the mod reads for a biome no tag places is what the tags say, for every biome it knows
    def shipped(id):
        if id in nether:
            return "NETHER"
        if id in end:
            return "END"
        return "UNPLACED" if id == void else "OVERWORLD"

    for id in order:
        placed_by = next((tag for tag in TAGS if id in game[tag]), None)
        expected = {"is_overworld": "OVERWORLD", "is_nether": "NETHER", "is_end": "END"}.get(
            placed_by, "UNPLACED")

        if shipped(id) != expected:
            failures.append(f"the mod reads {id} as {shipped(id)}, where the game's tags put it in {expected}")

    # the sections of the screen are the dimensions of the catalog, in the same order, and the list is
    # sorted by them before it is sorted by anything else
    if declared_dimensions(body) != list(DIMENSIONS):
        failures.append(f"the catalog declares {declared_dimensions(body)} as its dimensions, not {list(DIMENSIONS)}")

    if not re.search(r"DISPLAY_ORDER = Comparator\s*\.comparing\(Entry::dimension\)", body):
        failures.append("the list is not sorted by dimension first, so a section would not be a block of it")

    if "for (BiomeCatalog.Dimension dimension : BiomeCatalog.Dimension.values())" not in screen:
        failures.append(f"{SCREEN.name} does not walk the dimensions of the catalog to open its sections")

    for dimension in DIMENSIONS:
        key = KEY_PREFIX + dimension.lower()

        if key not in lang:
            failures.append(f"{key} is not written down in {LANG.name}")
        if f'"{key}"' not in screen:
            failures.append(f"{SCREEN.name} does not read the section {key}")

    # what the sections come to, printed rather than trusted
    print(f"the dimensions of the collection, out of the tags of {JAR.name}:\n")

    for dimension in DIMENSIONS:
        ids = [id for id in order if shipped(id) == dimension] if dimension != "UNPLACED" else [void]
        name = lang.get(KEY_PREFIX + dimension.lower(), dimension)

        print(f"  {name:14s} {len(ids):3d}  {', '.join(id.replace('minecraft:', '') for id in ids)}")

    print(f"\n  {'every biome':14s} {len(order):3d}  in all, the game itself holds "
          f"{len(game['is_overworld']) + len(game['is_nether']) + len(game['is_end']) + 1} of them")

    if failures:
        print()

        for failure in failures:
            print(f"WRONG {failure}")
        return 1

    print("\nthe mod's own reading of the dimensions is the game's own tags, biome for biome")
    return 0


if __name__ == "__main__":
    sys.exit(main())
