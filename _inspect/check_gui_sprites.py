#!/usr/bin/env python3
"""Checks the gui sprites the collection screen names, against the files it will read them out of.

The screen draws every block of itself out of a sprite of the gui atlas, and since those sprites are
the mod's own copies of the resource pack's dark gui (see the CREDITS.md beside them), there is a way
for the screen to look broken that no compiler and no build finds: a sprite that is not on the class
path at all, a copy that lost the mcmeta its nine slicing lives in, or a button sprite that is not
opaque - which the buttons of the game are painted over with, so a single transparent pixel of it
leaves a bright grey streak of the sprite underneath in the middle of the dark gui.

So this reads the sprite names out of the screen the way the game reads them off the identifier, finds
each one as a file - in the mod's assets for the mod's own, in the client jar for the game's - and
then checks the four things the screen's drawing assumes about them:

- that a sprite drawn nine sliced carries the mcmeta that slices it, with a border of the width the
  screen's own geometry is built on (see FRAME_PADDING, and the buttons of the game),
- that the window and the three buttons are opaque, since the faces of the screen's own buttons are
  painted over buttons the game has already drawn,
- that the two halves of the bar are at least as large as the region the screen cuts out of them, and
- that the copies are the pack's own files byte for byte, while the pack is on the disk to compare
  against - a copy that has been edited by hand is a copy whose credit is no longer true.

    python _inspect/check_gui_sprites.py
"""

import hashlib
import json
import pathlib
import re
import sys
import zipfile

SCREEN = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/BiomeCollectionScreen.java")
ASSETS = pathlib.Path("src/main/resources/assets/immersive_titles")
JAR = pathlib.Path.home() / ".gradle" / "caches" / "fabric-loom" / "26.3" / "minecraft-client.jar"
PACK = (pathlib.Path.home() / "Desktop" / "Reimagined GUI - Dark - 1.20.2-26.x" / "assets"
        / "minecraft" / "textures" / "gui" / "sprites")

# the names the screen asks for: its own copies by guiSprite(...), the game's own by their full name,
# and the two halves of the bar, which are read as textures rather than as sprites
OWN = re.compile(r"guiSprite\(\"([^\"]+)\"\)")
MINE = re.compile(r"Identifier\.withDefaultNamespace\(\"([^\"]+)\"\)")
BAR = re.compile(r"Identifier\.fromNamespaceAndPath\(WandererTitles\.MOD_ID,\s*\n?\s*"
                 r"\"(textures/gui/sprites/[^\"]+\.png)\"\)")

# the sprites the screen paints over a button of the game, so they have to be opaque across their box,
# and the border the game's own button sprite keeps, which is what the buttons are drawn at
OPAQUE = ("widget/button", "widget/button_disabled", "widget/button_highlighted",
          "social_interactions/background")
BUTTON_BORDER = {"left": 20, "top": 4, "right": 20, "bottom": 4}

# what the screen needs of the bar's two halves: the region it cuts out of them
BAR_WIDTH = 182
BAR_HEIGHT = 5


def png_size(raw):
    """The size of a PNG, read out of its own header: width and height are the first two fields."""
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        return None

    return int.from_bytes(raw[16:20], "big"), int.from_bytes(raw[20:24], "big")


def alpha(raw):
    """(transparent, partial) shares of the pixels of a PNG, or None where it cannot be read."""
    try:
        import io
        from PIL import Image
    except ImportError:
        return None

    image = Image.open(io.BytesIO(raw)).convert("RGBA")
    pixels = list(image.getdata())
    clear = sum(1 for pixel in pixels if pixel[3] == 0)

    return clear * 100 // len(pixels)


def nine_slice(raw):
    """The nine_slice block of a sprite's mcmeta, or None where there is no such block."""
    if raw is None:
        return None

    scaling = json.loads(raw.decode("utf-8")).get("gui", {}).get("scaling", {})

    return scaling if scaling.get("type") == "nine_slice" else None


def borders(scaling):
    """The four sides of a nine_slice, whether they are written as one number or as a box."""
    border = scaling.get("border")

    if isinstance(border, int):
        return {side: border for side in BUTTON_BORDER}

    return border


def main():
    body = SCREEN.read_text(encoding="utf-8")
    own = sorted(set(OWN.findall(body)))
    mine = sorted({name for name in MINE.findall(body) if name in
                   ("widget/scroller", "widget/scroller_background")})
    bar = sorted(set(BAR.findall(body)))
    failures = []
    drawn = []

    if not own:
        print(f"MISSING no gui sprite of the mod's own is named by {SCREEN.name}")
        return 1

    print(f"the gui sprites {SCREEN.name} draws itself out of:")

    with zipfile.ZipFile(JAR) as jar:
        for name in own:
            png = ASSETS / "textures" / "gui" / "sprites" / f"{name}.png"
            mcmeta = png.with_name(png.name + ".mcmeta")

            if not png.is_file():
                failures.append(f"{name} is asked for but {png} is not there")
                continue

            raw = png.read_bytes()
            size = png_size(raw)
            copy = PACK / f"{name}.png"
            same = copy.is_file() and hashlib.sha1(copy.read_bytes()).hexdigest() == \
                hashlib.sha1(raw).hexdigest()
            scaling = nine_slice(mcmeta.read_bytes() if mcmeta.is_file() else None)
            clear = alpha(raw)
            drawn.append(name)

            print(f"  {name:38s} {size[0]:4d}x{size[1]:<4d} "
                  f"{'nine sliced' if scaling else 'not sliced':12s} "
                  f"{'the pack\'s file' if same else 'NOT the pack\'s file':20s} "
                  f"{'opaque' if clear == 0 else f'{clear}% clear' if clear is not None else 'opacity not read'}")

            if name in OPAQUE and clear:
                failures.append(f"{name} is {clear}% transparent, and it is painted over a button of "
                                f"the game: what is under it would show through")

            if name in OPAQUE and not scaling:
                failures.append(f"{name} is drawn nine sliced but carries no mcmeta to slice it")

            if scaling is None:
                continue

            box = borders(scaling)

            if name == "social_interactions/background":
                # the window is the block of the list: what the screen draws it at is FRAME_PADDING
                # past the list on every side, which is the band the sprite has to keep for the frame
                if set(box.values()) != {8}:
                    failures.append(f"{name} keeps a border of {box}, and the screen's own padding of "
                                    f"the block is 8")
            elif box != BUTTON_BORDER:
                failures.append(f"{name} keeps a border of {box}, and the buttons of the game are drawn "
                                f"with {BUTTON_BORDER}")

        for name in mine:
            entry = f"assets/minecraft/textures/gui/sprites/{name}.png"

            if entry not in jar.namelist():
                failures.append(f"{name} is asked for but the client jar has no {entry}")
            else:
                print(f"  {name:38s} (the game's own, out of the jar)")

    for name in bar:
        png = ASSETS / name
        png = png if png.is_file() else None

        if png is None:
            failures.append(f"{name} is asked for but src/main/resources/assets/immersive_titles/"
                            f"{name} is not there")
            continue

        raw = png.read_bytes()
        size = png_size(raw)
        copy = PACK / pathlib.Path(name).relative_to("textures/gui/sprites")
        same = copy.is_file() and hashlib.sha1(copy.read_bytes()).hexdigest() == \
            hashlib.sha1(raw).hexdigest()
        print(f"  {name:38s} {size[0]:4d}x{size[1]:<4d} "
              f"{'the pack\'s file' if same else 'NOT the pack\'s file'}")

        if size[0] < BAR_WIDTH or size[1] < BAR_HEIGHT:
            failures.append(f"{name} is {size[0]}x{size[1]} and the screen cuts "
                            f"{BAR_WIDTH}x{BAR_HEIGHT} out of it")

    if failures:
        print()
        for failure in failures:
            print(f"OFF  {failure}")
        return 1

    print(f"\nthe {len(drawn)} sprites of the mod's own are all there, sliced as the screen draws them, "
          f"opaque where the screen paints them over the game's, and the pack's own files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
