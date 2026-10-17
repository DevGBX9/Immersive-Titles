#!/usr/bin/env python3
"""Checks that the mod's language file and the keys its sources ask for are the same set.

The screen reads its lines by key, so a key that is asked for and missing is a line of raw key text
in the middle of the screen, and a key that is written down and never asked for is a line left behind
by a change. Both are text facts about two files, which is all this checks - a text check and not a
compiler.

What it does not check is the names of the structures: a structure is named under the key of a
registry name the game would name one of its own by (`structure.<namespace>.<path>`), and that key is
built out of the id the structure catalog lists rather than written down as a literal, so the sources
here hold none of them. They are checked where the rows that need them are, by a check that reads the
catalog and the names together: {@code _inspect/check_structures.py}, which fails on a row with no
name as well as on a name no row is named by.

    python _inspect/check_lang_keys.py
"""

import json
import pathlib
import re
import sys

SOURCES = pathlib.Path("src/main/java")
LANG = pathlib.Path("src/main/resources/assets/immersive_titles/lang/en_us.json")

# the namespaces the mod owns: everything else a source names is a key of the game's own
OWNED = ("key.category.immersive_titles.", "key.immersive_titles.", "screen.immersive_titles.")

# the keys no source holds as a literal, since they are named by an id a catalog lists: the names of
# the structures, which the check beside this one reads out of the catalog and the language file
READ_BY_CATALOG = ("structure.",)

LITERAL = re.compile(r'"([A-Za-z0-9_.]+)"')

# the category of the mod's keys is registered as an identifier and the game looks its name up in the
# language file from that identifier, so the key it needs is named by the registration and not by a
# literal: Category.register(Identifier.fromNamespaceAndPath(<mod id>, "title")) asks for
# key.category.<mod id>.title
CATEGORY = re.compile(r'Category\.register\(\s*Identifier\.fromNamespaceAndPath\(\s*WandererTitles\.MOD_ID,\s*"([A-Za-z0-9_]+)"')


def main():
    declared = set(json.loads(LANG.read_text(encoding="utf-8")))
    asked = set()
    catalogued = {key for key in declared if key.startswith(READ_BY_CATALOG)}
    declared -= catalogued

    for path in sorted(SOURCES.rglob("*.java")):
        body = path.read_text(encoding="utf-8")

        for literal in LITERAL.findall(body):
            if literal.startswith(OWNED):
                asked.add(literal)

        for name in CATEGORY.findall(body):
            asked.add(f"key.category.immersive_titles.{name}")

    failures = 0

    for key in sorted(asked - declared):
        failures += 1
        print(f"MISSING {key} is asked for but not written down in {LANG.name}")

    for key in sorted(declared - asked):
        failures += 1
        print(f"UNUSED  {key} is written down but no source asks for it")

    if failures:
        print(f"{failures} key(s) do not line up ({len(asked)} asked, {len(declared)} declared)")
        return 1

    print(f"all {len(asked)} keys line up with the {len(declared)} of {LANG.name}, and the "
          f"{len(catalogued)} named by a catalog are checked where the catalog is")
    return 0


if __name__ == "__main__":
    sys.exit(main())
