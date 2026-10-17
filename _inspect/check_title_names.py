#!/usr/bin/env python3
"""Checks what the titles make of the names the game gives the biomes.

The titles are shortened to two words, and which words are dropped is written down by hand in
BiomeNames - so this reads the rule and the words back out of that source and runs them over every
vanilla biome name the game ships, out of the client jar that is already on disk, plus a few names
of the kind a mod gives a biome. What it checks is what the request asked for:

- every title is at most two words,
- no title is empty, and the words of one are words of the name it came from,
- and the shortened names are printed, so they can be read rather than trusted: the point of the
  rule is that a player still knows which biome a title names.

It also checks the centring work the same way, on the plate's own frames: the ink of a name drawn at
the middle of the plate lands within a pixel of it, for every name the game has.

    python _inspect/check_title_names.py
"""

import json
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from preview_font import JAR, load_font  # noqa: E402

NAMES = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeNames.java")
CATALOG = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCatalog.java")

# names of the kind a mod gives a biome of its own: the rule has to hold for those too
MODDED = [
    "Frozen Spiky Snowy Mountains",
    "Crystal Deep Caverns",
    "Old Growth Spruce Taiga",
    "Twilight Highlands",
    "Deep Dark",
]

def rule():
    """The rule as the source writes it: the word count, and the words a title can do without."""
    body = NAMES.read_text(encoding="utf-8")
    words = int(re.search(r"TITLE_WORDS = (\d+)", body).group(1))
    dropped = re.search(r"TITLE_WORDS_DROPPED = Set\.of\((.*?)\);", body, re.S).group(1)

    return words, {w.lower() for w in re.findall(r'"([^"]+)"', dropped)}


def shorten(name, words, dropped):
    parts = name.split(" ")

    if len(parts) <= words:
        return name

    kept = [p for p in parts if p.lower() not in dropped]

    if not kept:
        return name

    return " ".join(kept[-words:])


def vanilla_names():
    order = re.search(r"DISCOVERY_ORDER = List\.of\((.*?)\);",
                      CATALOG.read_text(encoding="utf-8"), re.S).group(1)

    with zipfile.ZipFile(JAR) as jar:
        lang = json.loads(jar.read("assets/minecraft/lang/en_us.json").decode("utf-8"))

    return [(re.sub(r"^minecraft:", "", i), lang.get("biome.minecraft." + re.sub(r"^minecraft:", "", i),
                                                    re.sub(r"^minecraft:", "", i)))
            for i in re.findall(r'"([a-z_:]+)"', order)]


def ink_center(font, name):
    """Where the ink of a name is, against the middle of the box the game advances it through.

    The name is centred by that box, so this is exactly what the ink can end up off by: an ink that
    starts one pixel into the first cell and ends one pixel short of the last advance is what makes a
    name centred by its advances sit in the middle of them.
    """
    left = None
    right = None
    pen = 0

    for char in name:
        glyph = font.glyph(char)

        if glyph is not None:
            width, height = glyph.size

            for column in range(width):
                if any(glyph.getpixel((column, row))[3] > 0 for row in range(height)):
                    if left is None:
                        left = pen + column
                    right = pen + column

        pen += font.advance(char)

    return None if left is None else (left + right + 1) / 2.0 - font.width(name) / 2.0


def main():
    words, dropped = rule()
    font = load_font()
    failures = 0

    print(f"the rule: at most {words} words, dropping {sorted(dropped)}\n")
    print("the names the game gives the biomes, shortened:")
    print(f"  {'biome':34s} {'the game calls it':32s} the title says")

    for path, name in vanilla_names():
        title = shorten(name, words, dropped)
        kept_words = title.split(" ")

        if len(kept_words) > words or not title.strip():
            failures += 1
            print(f"  BAD {name!r} -> {title!r}")

        if not all(word in name for word in kept_words):
            failures += 1
            print(f"  BAD {title!r} is not made of the words of {name!r}")

        if title != name:
            print(f"  {path:34s} {name:32s} {title}")

    print("\nnames of the kind a mod gives a biome, shortened:")
    for name in MODDED:
        print(f"  {name:34s} -> {shorten(name, words, dropped)}")

    print("\nwhere the ink of a name lands, against the middle of the box it is advanced through,")
    print("in font pixels (the name is centred by that box, so this is what it can be off by):")
    worst = (0.0, "")

    for _, name in vanilla_names():
        title = shorten(name, words, dropped).upper()
        offset = ink_center(font, title)
        if offset is None:
            continue
        if abs(offset) > abs(worst[0]):
            worst = (offset, title)

    print(f"  worst: {worst[1]!r} by {worst[0]:+.2f} of a font pixel")

    if abs(worst[0]) > 1.0:
        failures += 1
        print("  BAD a name is more than a pixel off the middle it is drawn into")

    if failures:
        print(f"\n{failures} title(s) do not hold")
        return 1

    print("\nthe rule holds for every name, and every name lands within a pixel of the middle")
    return 0


if __name__ == "__main__":
    sys.exit(main())
