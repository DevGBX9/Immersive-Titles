#!/usr/bin/env python3
"""Checks that the mod changes screens the way the game changes its own, and not with the game's
show-it-now call that draws a frame of its own.

What the owner reported: opening the collection, or moving from one screen of the mod to another (the
confirmation the forget button asks with, and back), put a black flash over the whole screen for a
moment. The cause was one call: the mod put its screens up with `Minecraft.setScreenAndShow`, which is
not the way a screen is changed while a world is behind it - it sets the screen and then draws a frame
of its own right there, and the frame it draws is one the game extracts with no level in it. The level
is what the game clears the window for, so what is handed to the window is one frame of a screen over a
world that has just been cleared - the flash - before the next frame draws the world again under the
same screen. The game's own screens change with `Gui.setScreen` (see the game's own `Screen.onClose`),
which only sets the screen: the drawing is left to the next frame, which is a frame with the world in it.

The game's side of that is read out of the client jar of the version the shared sources are written for:

- the game's own `Screen` changes screens with `Gui.setScreen` and forces no frame of its own,
- `Minecraft` is where the show-it-now call is, and it is a call that draws a frame (`renderFrame`),
- `GameRenderer` is where a frame is told whether it has a level in it (`shouldRenderLevel`), which is
  the flag the forced frame turns off.

What is not read here is a frame of the game itself - this device builds nothing and runs no game - so
what is held is the two rules that follow from it: no call of the mod's puts a screen on the screen by
force, and every screen the mod sets goes through the game's own screen change. A check that passed on
the code as it was would hold nothing, and this one does not: it fails on every `setScreenAndShow(` the
mod had before, which is the whole of what was wrong.

    python _inspect/check_screen_switch.py
"""

import pathlib
import re
import sys
import zipfile

SOURCES = pathlib.Path("src/main/java/com/devgx9/wanderers_titles")
SCREEN = SOURCES / "client/BiomeCollectionScreen.java"
OVERLAY = SOURCES / "client/TitleOverlay.java"
JAR = (pathlib.Path.home() / ".gradle" / "caches" / "fabric-loom" / "minecraftMaven" / "net"
       / "minecraft" / "minecraft-merged-deobf" / "26.3" / "minecraft-merged-deobf-26.3.jar")
GAME_SCREEN = "net/minecraft/client/gui/screens/Screen.class"
MINECRAFT = "net/minecraft/client/Minecraft.class"
RENDERER = "net/minecraft/client/renderer/GameRenderer.class"

# Every screen the mod sets, as the call it is written in: the collection from the world (TitleOverlay),
# the collection back to the world or to the screen it was opened out of, and the confirmation the
# forget button asks with together with the collection it comes back to (BiomeCollectionScreen).
SCREEN_CHANGES = [
    (OVERLAY, "client.gui.setScreen(new BiomeCollectionScreen(",
     "the collection is not put up through the game's own screen change"),
    (SCREEN, "minecraft.gui.setScreen(parent)",
     "the collection is not closed through the game's own screen change"),
    (SCREEN, "minecraft.gui.setScreen(new ConfirmScreen(",
     "the confirmation the forget button asks with is not set through the game's own screen change"),
    (SCREEN, "minecraft.gui.setScreen(this)",
     "the collection is not set again through the game's own screen change once the confirmation is"),
]


def main():
    failures = []

    # the mod: the game's own screen change everywhere, and the forced one nowhere - not as a call, i.e.
    # not as the thing the code does, since the javadoc of both files names it to explain why not
    for path in sorted(SOURCES.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        for found in re.findall(r"\.setScreenAndShow\(", source):
            failures.append(f"{path.name} puts a screen up with the game's forced one again: the frame "
                            f"it draws is a frame with no level in it, which is the black flash")

    for path, call, complaint in SCREEN_CHANGES:
        if call not in path.read_text(encoding="utf-8"):
            failures.append(f"{path.name}: {complaint}")

    # closing into the world is the game's own answer - there is no screen to set - so the mod hands
    # that one case to the game rather than setting nothing itself
    screen = SCREEN.read_text(encoding="utf-8")
    if not re.search(r"if \(parent == null\)\s*\{\s*super\.onClose\(\);", screen):
        failures.append(f"{SCREEN.name} closes into the world itself instead of leaving that to the "
                        f"game's own screen close")

    # and what the two screens say about it: a rule kept silently is a rule nobody can hold to anything
    for path in (SCREEN, OVERLAY):
        source = path.read_text(encoding="utf-8")
        if "setScreenAndShow" not in source or "flash" not in source:
            failures.append(f"{path.name} no longer says why the game's forced screen change is not the "
                            f"one a screen is put up with while a world is behind it")

    # the game's side, out of the client jar, and skipped rather than guessed when it is not on this disk
    if not JAR.is_file():
        print(f"SKIPPED {JAR} is not on this disk, so the game's own screen change is not read")
        game = None
    else:
        with zipfile.ZipFile(JAR) as jar:
            names = jar.namelist()
            game = {name: jar.read(name) for name in (GAME_SCREEN, MINECRAFT, RENDERER)
                    if name in names}

            for name in (GAME_SCREEN, MINECRAFT, RENDERER):
                if name not in names:
                    failures.append(f"{name} is not in {JAR.name}")

    if game:
        own = game.get(GAME_SCREEN, b"")
        if b"setScreen" not in own:
            failures.append("the game's own Screen no longer changes screens at all")
        if b"setScreenAndShow" in own or b"renderFrame" in own:
            failures.append("the game's own Screen draws a frame of its own when it changes screens, so "
                            "the forced call and the plain one are not two different things")
        if b"Gui" not in own:
            failures.append("the game's own Screen does not change screens through its Gui")

        minecraft = game.get(MINECRAFT, b"")
        if b"setScreenAndShow" not in minecraft:
            failures.append("the game has no show-it-now call any more, so this check has gone stale")
        if b"renderFrame" not in minecraft:
            failures.append("the game's show-it-now call draws no frame of its own, so it is not what "
                            "the black flash was made of")

        renderer = game.get(RENDERER, b"")
        if b"shouldRenderLevel" not in renderer:
            failures.append("a frame of the game is no longer told whether it has a level in it, so the "
                            "forced frame is not a frame without the world")

    if failures:
        for failure in failures:
            print(f"BROKEN {failure}")
        return 1

    print("the mod puts every screen of its up through the game's own screen change (Gui.setScreen), and "
          "leaves the drawing of it to the next frame - a frame with the world in it - instead of forcing "
          "a frame of the game that has had the world cleared out of it")
    return 0


if __name__ == "__main__":
    sys.exit(main())
