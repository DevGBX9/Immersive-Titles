#!/usr/bin/env python3
"""Checks that the collection is drawn over the game's own menu background, and over nothing of the mod's.

What is behind the collection is what is behind every other screen of the game: the world the player
is in, blurred by the game's own menu blur - the one that follows the player's own menu background
blurriness setting - and then read through the game's own menu background. The screen draws no
background of its own at all: it used to draw a veil of its own over the blurred world, and that veil
is gone, so what is read behind the block is the game's own drawing and not a shade the mod made up.

None of it is visible from here (this device does not build the mod, and there is no world on it to
blur), so what this reads is the two sides that have to agree for it to work at all:

- the game's own `Screen`, out of the client jar of the version the shared sources are written for:
  it has to declare the background a screen inherits - the blur, and the menu background drawn over
  it - and the blur has to be the menu one, i.e. the method that reads the player's own menu
  background blurriness setting,
- the mod's own screen: it has to override none of that. An `extractBackground` of its own, a
  whole-screen `fillGradient` or a constant of its own for the shade of the world would all be the
  mod drawing a background of its own again.

    python _inspect/check_menu_background.py
"""

import pathlib
import re
import sys
import zipfile

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")
JAR = (pathlib.Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven" / "net"
       / "minecraft" / "minecraft-merged-deobf" / "26.3" / "minecraft-merged-deobf-26.3.jar")
PATCH = "net/minecraft/client/gui/screens/Screen.class"


def main():
    source = SCREEN.read_text(encoding="utf-8")
    failures = []

    if not JAR.is_file():
        print(f"SKIPPED {JAR} is not on this disk, so the game's own Screen is not read")
        return 0

    with zipfile.ZipFile(JAR) as jar:
        if PATCH not in jar.namelist():
            print(f"SKIPPED {PATCH} is not in {JAR.name}")
            return 0
        game = jar.read(PATCH)

    # the background the screen inherits has to be the game's own, and the blur in it the menu one
    for method in (b"extractBackground", b"extractBlurredBackground", b"extractMenuBackground"):
        if method not in game:
            failures.append(f"the game's own Screen has no {method.decode()} in {JAR.name}")
    if b"getMenuBackgroundBlurriness" not in game:
        failures.append("the game's own background is not the menu one: it does not read the "
                        "player's menu background blurriness setting")
    if b"GuiGraphicsExtractor" not in game:
        failures.append("the game's own Screen does not take a GuiGraphicsExtractor")

    # and the mod has to leave all of it to the game: the background is named in the class doc and
    # nowhere else, so what is read here is a declaration or a call of the mod's own
    if re.search(r"\bpublic void extractBackground\(", source):
        failures.append(f"{SCREEN.name} overrides the game's background again: the collection is "
                        "not read over the game's own menu background any more")
    if "extractBlurredBackground" in source or "extractMenuBackground" in source:
        failures.append(f"{SCREEN.name} draws a part of the game's background itself instead of "
                        "leaving the whole of it to the game")
    if re.search(r"fillGradient\(\s*0,\s*0,\s*width,\s*height", source):
        failures.append(f"{SCREEN.name} draws a whole-screen gradient of its own over the world")
    for name in ("VEIL_TOP", "VEIL_BOTTOM"):
        if name in source:
            failures.append(f"{SCREEN.name} keeps {name}: the shade over the world is the mod's own "
                            "again rather than the game's")
    if "@link Screen#extractBackground" not in source:
        failures.append(f"{SCREEN.name} no longer says whose background is drawn behind it")

    if failures:
        for failure in failures:
            print(f"BROKEN {failure}")
        return 1

    print("the collection is read over the game's own background: the game's menu blur of the world "
          "and the game's own menu background over it, with no veil of the mod's own")
    return 0


if __name__ == "__main__":
    sys.exit(main())
