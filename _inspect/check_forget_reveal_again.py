#!/usr/bin/env python3
"""Checks the path a forgotten discovery takes back to being a discovery again.

Forgetting the biome the player is standing in is the one case where a discovery is taken back and
given back in the same place, and it is a path nothing in the game runs on its own: the collection
screen asks for it, the titles hand the reveal back, and the discovery is written down again - in
the world's own file, which is what a later session loads and what the collection reads. Three
things have to hold for the player to see what was asked for:

- the reveal waits until the player has walked a block from where they stood when the discovery was
  taken back, so the title is walked into again rather than handed back by the screen that took it
  away (the screen is open over that very place),
- the discovery is written down again at that moment and not by the check that runs on movement,
  which only writes one down when the place the player is in *changes* - this is the case where it
  never changed, so a biome left to that check would be titled and never discovered again,
- a discovery that is walked into again later - out of the biome and back in - plays one reveal and
  no more, i.e. it stops waiting for one as soon as it is discovered.

That third one is the one that is easy to get wrong and impossible to see: a biome that is titled
and then titled again half a second later.

This is a text check of the mod's own source, in the manner of the others beside it: it reads the
methods as they are written rather than the game running them, because this device does not build
the mod.

    python _inspect/check_forget_reveal_again.py
"""

import pathlib
import re
import sys

OVERLAY = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/TitleOverlay.java")


def block_at(source, at):
    """The `{`..`}` block that begins at or after `at`, or None."""
    opened = source.find("{", at)
    if opened < 0:
        return None

    depth = 0
    for index in range(opened, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opened:index]
    return None


def body_of(source, name):
    """The body of the method `name`, from its `{` to the matching `}`, or None."""
    at = re.search(rf"(?:private|static|void|Public)[^;{{}}]*\b{name}\(", source)
    if at is None:
        return None

    return block_at(source, at.end())


def main():
    source = OVERLAY.read_text(encoding="utf-8")
    failures = []

    def body(name):
        found = body_of(source, name)
        if found is None:
            failures.append(f"{name} is not in {OVERLAY.name} any more")
        return found or ""

    # ---- what forgetting a discovery leaves behind -------------------------------------------
    for kind, file, id_name in (("Biome", "DiscoveredBiomes", "biomeId"),
                                ("Structure", "DiscoveredStructures", "structureId")):
        forget = body(f"forget{kind}")
        waiting = re.search(rf"{file}\.forget\(world, {id_name}\);\s*([A-Za-z]+)\(", forget)

        if waiting is None or waiting.group(1) != "queueRevealAgain":
            failures.append(f"forgetting a {kind.lower()} does not hand its reveal back to the "
                            f"player: the reveal is not queued where it is taken out of the file")

    queue = body("queueRevealAgain")
    if not re.search(r"REVEAL_AGAIN\.add\(id\)", queue):
        failures.append("a queued reveal is not put in REVEAL_AGAIN")
    if not re.search(r"revealAgainPosition = null", queue):
        failures.append("a queued reveal is not waited for from where the player stands when it is "
                        "forgotten")

    # ---- and what it takes to get it back ------------------------------------------------------
    again = body("checkRevealAgain")
    played = again.find("startReveal(")
    moved = again.find("REVEAL_AGAIN_DISTANCE_SQR")

    if moved < 0:
        failures.append("the reveal that waits does not wait for the player to move")
    elif played >= 0 and played < moved:
        failures.append("the reveal is played before the player has moved from where the discovery "
                        "was taken back")

    # each of the two is read in its own block, since the biome is read before the structure and the
    # reveal of the one is not the reveal of the other
    for what, id_name in (("biome", "biomeId"), ("structure", "structureId")):
        of = re.search(rf"REVEAL_AGAIN\.remove\({id_name}\)", again)
        if of is None:
            failures.append(f"the reveal of a {what} that was forgotten is never played again")
            continue

        block = block_at(again, of.end()) or ""
        played = block.find("startReveal(")

        for written, pattern in (("the {0} the player is in".format(what),
                                  rf"VISITED_[A-Z]+\.add\({id_name}\)"),
                                 ("the file of that {0}".format(what),
                                  rf"Discovered[A-Za-z]+\.remember\(world, {id_name}\)")):
            found = re.search(pattern, block)
            if found is None:
                failures.append(f"{written} is not written down again when the reveal comes back")
            elif played >= 0 and found.start() > played:
                failures.append(f"{written} is written down after the reveal of it is played rather "
                                f"than with it")

    # ---- a discovery that is discovered the usual way stops waiting -----------------------------
    for kind, set_name, id_name in (("Biome", "VISITED_BIOMES", "biomeId"),
                                    ("Structure", "VISITED_STRUCTURES", "structureId")):
        check = body(f"check{kind}")
        discovered = check.find(f"{set_name}.add({id_name})")
        dropped = check.find(f"REVEAL_AGAIN.remove({id_name})")

        if dropped < 0:
            failures.append(f"a {kind.lower()} that is walked into again keeps waiting for a "
                            f"reveal, so it would be titled twice")
        elif discovered >= 0 and dropped < discovered:
            failures.append(f"a {kind.lower()} stops waiting for its reveal before it is discovered")

    # ---- and the place the reveal is given back in is the one it was taken from ------------------
    for what, id_name, last_name in (("biome", "biomeId", "lastBiomeId"),
                                     ("structure", "structureId", "lastStructureId")):
        guard = re.search(rf"if \([^)]*{id_name}\.equals\({last_name}\)[^)]*"
                          rf"REVEAL_AGAIN\.remove\({id_name}\)\)", again, re.S)

        if guard is None:
            failures.append(f"the reveal of a {what} is given back wherever the player stands, so a "
                            f"place they walked into is titled twice: once here and once by the "
                            f"check that runs on a change")

    # ---- and the wait is dropped once the player has walked out of that place --------------------
    if not re.search(r"if \(!REVEAL_AGAIN\.isEmpty\(\)\s*&&\s*!REVEAL_AGAIN\.contains\(biomeId\)"
                     r"\s*&&\s*!REVEAL_AGAIN\.contains\(structureId\)\)\s*\{\s*REVEAL_AGAIN\.clear\(\);"
                     r"\s*revealAgainPosition = null;", again):
        failures.append("a wait the player walks out of is kept rather than dropped, so the place the "
                        "player is in would be read on every tick of the session")

    # ---- and one world's waiting never follows the player into the next -------------------------
    level = body("levelChanged")
    if "REVEAL_AGAIN.clear()" not in level or "revealAgainPosition = null" not in level:
        failures.append("a reveal that was waiting in one world is not dropped when a world is "
                        "entered, so it would be played in the next one")

    # ---- the waiting is only read while something is waiting ------------------------------------
    tick = body("tick")
    if not re.search(r"if \(!REVEAL_AGAIN\.isEmpty\(\)\)\s*\{\s*checkRevealAgain\(client\);", tick):
        failures.append("the tick reads the waiting reveals every tick rather than only while one "
                        "is waiting")

    if failures:
        for failure in failures:
            print(f"BROKEN {failure}")
        return 1

    print("a discovery forgotten where the player stands is titled and discovered again after one "
          "step, once")
    return 0


if __name__ == "__main__":
    sys.exit(main())
