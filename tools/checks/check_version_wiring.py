#!/usr/bin/env python3
"""Checks that every enabled Minecraft version actually wires the mod up.

A version can compile and still do nothing: if the guard around a registration names a newer version
than the project it is built for, that whole registration drops out, and the jar installs - the mod
is listed, the class loads - and then has no keys, no mod list button and nothing drawn. That is a
one-line mistake that no compiler and no structural check can see, because the code it removed was
perfectly good code.

So the two entrypoints and the overlay are preprocessed for each version the build matrix enables, the
way the preprocessor does it, and what has to be there for the mod to be the mod is read back:

    python tools/checks/check_version_wiring.py
"""

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

import check_java_shape as shape  # noqa: E402  (the guard evaluation is the same one)

SOURCES = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client")
SETTINGS = pathlib.Path("settings.json")

# what each file has to contain once it is preprocessed for a version, and why: the entries of a
# tuple are the names the versions give the same thing, of which one has to be there
WIRING = {
    "WandererTitlesClient.java": [
        (("TitleOverlay.initialize();",), "the client entrypoint has to start the overlay"),
    ],
    "ModMenuIntegration.java": [
        (("implements ModMenuApi",), "the mod list has to be able to open the collection"),
        (("getModConfigScreenFactory",), "the mod list asks for the screen from this method"),
    ],
    "title/TitleOverlay.java": [
        (("registerKeyBinding(", "registerKeyMapping("), "the keys have to be registered"),
        (("HudElementRegistry.attachElementBefore(", "HudRenderCallback.EVENT.register("),
         "the titles have to be drawn in the HUD"),
        (("ClientTickEvents.END_CLIENT_TICK.register(",), "the keys have to be read every tick"),
        (("AFTER_CLIENT_LEVEL_CHANGE.register(", "AFTER_CLIENT_WORLD_CHANGE.register(", "levelChanged(client, client.level);"),
         "a world has to be entered and left"),
    ],
}


def main():
    settings = json.loads(SETTINGS.read_text(encoding="utf-8"))
    failures = 0

    for name in settings["versions"]:
        mc = shape.version(name)

        for file, expectations in WIRING.items():
            text = (SOURCES / file).read_text(encoding="utf-8")

            try:
                body = shape.compiled(text, mc)
            except ValueError as error:
                failures += 1
                print(f"UNREADABLE {file} at {name}: {error}")
                continue

            for names, why in expectations:
                if not any(name in body for name in names):
                    failures += 1
                    print(f"MISSING at {name}: {file} has none of {names} - {why}")

    if failures:
        print(f"{failures} thing(s) the enabled versions do not wire up")
        return 1

    print(f"all {len(settings['versions'])} enabled versions wire the mod up")
    return 0


if __name__ == "__main__":
    sys.exit(main())
