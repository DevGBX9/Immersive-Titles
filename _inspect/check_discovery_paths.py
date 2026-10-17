#!/usr/bin/env python3
"""Checks that the file a world writes its discoveries to is the file the collection reads them from.

WorldIdentity builds the path of a hosted world from the folder the game keeps that world in, and
DiscoveredBiomes#loadAll is handed every such path by scanning the saves folder. The two have to be
the same layout, or the collection of every world would be quietly empty:

    <game directory>/saves/<world>/immersive_titles/discovered.txt

That layout was read off the game itself, not guessed: net.minecraft.client.Minecraft resolves
"saves" against the game directory before it makes the LevelStorageSource it keeps worlds in, and
the world folder it hands out for LevelResource.ROOT is the folder of that world.

This is a text check of the mod's own sources: it holds the two sides to the same constants, so a
change to one of them cannot leave the other behind.

    python _inspect/check_discovery_paths.py
"""

import pathlib
import re
import sys

CLIENT = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client")
IDENTITY = CLIENT / "WorldIdentity.java"
DISCOVERED = CLIENT / "DiscoveredBiomes.java"


def main():
    identity = IDENTITY.read_text(encoding="utf-8")
    discovered = DISCOVERED.read_text(encoding="utf-8")
    failures = []

    # the two halves of the layout, declared once and used by both sides
    for name, value in (("WORLD_FOLDER", "immersive_titles"), ("DISCOVERED_FILE", "discovered.txt"),
                        ("SAVES_FOLDER", "saves")):
        if not re.search(rf'{name} = "{value}"', identity):
            failures.append(f"{IDENTITY.name} does not declare {name} as \"{value}\"")

    # every path of a discoveries file is built from those constants, on both sides
    for side, pattern in (
            ("the world the player is in", r"folder\.resolve\(DISCOVERED_FILE\)"),
            ("the scan of the saves folder",
             r"world\.resolve\(WORLD_FOLDER\)\.resolve\(DISCOVERED_FILE\)")):
        if not re.search(pattern, identity):
            failures.append(f"{side} does not build its discoveries path out of the constants")

    # nothing but the declaration and those two builds writes the file name out
    literals = [match for match in re.findall(r'"[^"]*discovered\.txt"', identity)
                if not re.search(rf'DISCOVERED_FILE = {re.escape(match)}', identity)]
    if literals:
        failures.append(f"{IDENTITY.name} spells a discoveries file out by hand: {literals}")

    # the scan is rooted in the game directory and the config directory
    if not re.search(r"getGameDir\(\)\.resolve\(SAVES_FOLDER\)", identity):
        failures.append("the scan does not look in the saves folder of the game directory")
    if not re.search(r"getConfigDir\(\)\.resolve\(SERVERS_FOLDER\)", identity):
        failures.append("the scan does not look in the config folder of the server worlds")

    # one world and every world are read through the same reading of one file
    if "read(world.file())" not in discovered:
        failures.append(f"{DISCOVERED.name} does not read one world through #read")
    if not re.search(r"for \(Path file : WorldIdentity\.discoveredFiles\(\)\)\s*\{\s*discovered\.addAll\(read\(file\)\);",
                     discovered):
        failures.append(f"{DISCOVERED.name} does not read every world through #read")

    if failures:
        for failure in failures:
            print(f"BROKEN {failure}")
        return 1

    print("the written and the read layout of a world's discoveries are the same")
    return 0


if __name__ == "__main__":
    sys.exit(main())
