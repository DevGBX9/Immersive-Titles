#!/usr/bin/env python3
"""Checks the structures the collection lists against the game the build is against.

The collection files its structures by hand, so that a row is one thing a player finds rather than a
variant of it, and the mod is the side that has to be right about the game: which structures exist,
which of them the game tags together, and which dimension each of them generates in. This reads all
three out of the client jar that is already on disk and holds the list to them.

What is checked is what the sections, the rows and the names of the page rest on:

- every structure the game ships is in exactly one row, and every row covers structures the game
  actually has - so a structure added by a later version is a row the list is missing, and a row for
  something no world generates is a row the list should not have,
- a row of a group is the game's own tag of that name: its members are exactly the structures of the
  tag that generate in the dimension the row is filed under, which is what makes the ruined portals
  the one group the collection splits - the game's tag holds the seven of both worlds,
- a structure listed on its own is remembered by the game's own id for it, and a group by an id of the
  mod's own namespace, so no row can be mistaken for a structure of the game,
- the dimension of a row is the dimension of every structure of it, read off the biome tag each of
  them generates in, since that is the only thing the game states about which world a structure is of,
- the rows come in the order of the dimensions, so a section of the list is a block of it and not a
  name scattered through it,
- every row has a name written down in the mod's own language file, under the key the game names
  things of a registry by (`structure.<namespace>.<path>`), at most two words long, the room a plate
  has for a name,
- no two rows of one dimension read the same, since two rows a player reads alike are one thing to
  them and two things to their list,
- and the five structures the game itself hands an advancement out for - the ones its own location
  criteria name - are all covered, because that is the part of the game the list started from and a
  structure a player is told they entered should not be one the collection has no row for.

    python _inspect/check_structures.py
"""

import json
import pathlib
import re
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from preview_font import JAR  # noqa: E402

CATALOG = pathlib.Path("src/main/java/com/devgx9/wanderers_titles/client/StructureCatalog.java")
PROPERTIES = pathlib.Path("gradle.properties")
LANG = pathlib.Path("src/main/resources/assets/immersive_titles/lang/en_us.json")

NAMESPACE = "minecraft"
DIRECTIONS = {"is_overworld": "OVERWORLD", "is_nether": "NETHER", "is_end": "END"}

# the room a plate has for the name of a title, and so the length a name of the collection is written
# at: the same two words the names of the biomes are shortened to
TITLE_WORDS = 2


def strip_comments(body):
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    return re.sub(r"//[^\n]*", "", body)


def split_arguments(text):
    """The top level arguments of a call, i.e. the ones no comma inside a call of their own splits."""
    arguments = []
    depth = 0
    current = ""

    for char in text:
        if char in "([":
            depth += 1
        elif char in ")]":
            depth -= 1

        if char == "," and depth == 0:
            arguments.append(current.strip())
            current = ""
        else:
            current += char

    if current.strip():
        arguments.append(current.strip())

    return arguments


def rows(body):
    """The rows of the list, as the source writes them: one structure, or a family of them.

    What is read is the block the list itself is written in, so the two places a row is built from -
    the `one` and the `family` this class is written with - are not read as rows of their own.
    """
    body = strip_comments(body)
    start = body.index("LISTED = List.of(")
    depth = 0
    end = start

    while True:
        if body[end] == "(":
            depth += 1
        elif body[end] == ")":
            depth -= 1

            if depth == 0:
                break

        end += 1

    body = body[start:end]
    listed = []

    for match in re.finditer(r"\b(one|family)\(", body):
        depth = 1
        index = match.end()

        while depth:
            if body[index] == "(":
                depth += 1
            elif body[index] == ")":
                depth -= 1

            index += 1

        arguments = split_arguments(body[match.end():index - 1])
        dimension = re.search(r"Dimension\.([A-Z_]+)", " ".join(arguments)).group(1)
        strings = [word for argument in arguments for word in re.findall(r'"([^"]*)"', argument)]

        if match.group(1) == "one":
            listed.append({"kind": "one", "name": strings[0], "members": strings, "dimension": dimension})
        else:
            listed.append({"kind": "family", "name": strings[0], "members": strings[1:],
                           "dimension": dimension})

    return listed


def tag_members(jar, folder, tag, seen=None):
    """The members of a tag of the game, with the tags it points at opened in turn."""
    seen = seen or set()
    members = set()

    for value in json.loads(jar.read(f"data/{NAMESPACE}/tags/{folder}/{tag}.json"))["values"]:
        value = value["id"] if isinstance(value, dict) else value

        if value.startswith("#"):
            nested = value[1:].split(":", 1)[-1]

            if nested not in seen:
                seen.add(nested)
                members |= tag_members(jar, folder, nested, seen)
        else:
            members.add(value)

    return members


def structure_dimension(jar, biomes, biome_tags):
    """The dimension a structure generates in, read off the biome tag it declares for itself."""
    if isinstance(biomes, str) and biomes.startswith("#"):
        members = tag_members(jar, "worldgen/biome", biomes[1:].split(":", 1)[-1])
    elif isinstance(biomes, list):
        members = {biome["id"] if isinstance(biome, dict) else biome for biome in biomes}
    else:
        members = set()

    return [direction for direction, tag in biome_tags.items() if members & tag]


def main():
    body = CATALOG.read_text(encoding="utf-8")
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    mod_id = re.search(r"^\s*mod_id\s*=\s*(\S+)", PROPERTIES.read_text(encoding="utf-8"),
                       re.M).group(1)
    listed = rows(body)
    failures = []

    with zipfile.ZipFile(JAR) as jar:
        names = jar.namelist()
        game = {f"{NAMESPACE}:" + path.split("/")[-1][:-5]: json.loads(jar.read(path))
                for path in names
                if re.fullmatch(rf"data/{NAMESPACE}/worldgen/structure/[a-z_]+\.json", path)}
        biome_tags = {direction: tag_members(jar, "worldgen/biome", tag)
                      for tag, direction in DIRECTIONS.items()}
        structure_tags = {path.split("/")[-1][:-5]
                          for path in names
                          if re.fullmatch(rf"data/{NAMESPACE}/tags/worldgen/structure/[a-z_]+\.json",
                                          path)}
        dimensions = {id: structure_dimension(jar, data.get("biomes"), biome_tags)
                      for id, data in game.items()}
        tags = {tag: tag_members(jar, "worldgen/structure", tag) for tag in structure_tags}
        advanced = set()

        for path in names:
            if not re.fullmatch(rf"data/{NAMESPACE}/advancement/[a-z_]+/[a-z_]+\.json", path):
                continue

            conditions = json.dumps(json.loads(jar.read(path)).get("criteria", {}))

            for structure in re.findall(r'"structures":\s*"([^"]+)"', conditions):
                advanced.add(structure)

    # every row is a structure the game has, a group of them the game tags together, or a group of the
    # structures of one dimension of such a tag
    covered = {}

    for row in listed:
        row["id"] = row["name"] if row["kind"] == "one" else f"{mod_id}:{row['name']}"

        if row["kind"] == "one":
            if row["members"] != [row["name"]]:
                failures.append(f"the row {row['name']} lists {row['members']} rather than itself")
            if row["name"] not in game:
                failures.append(f"the row {row['name']} is not a structure the game ships")
        else:
            if not row["id"].startswith(f"{mod_id}:"):
                failures.append(f"the group {row['name']} is not remembered in the namespace of the mod")
            if row["name"] not in structure_tags:
                failures.append(f"the group {row['name']} is not a tag of structures the game ships")
            else:
                # the members of a group are the structures of the game's own tag that generate in the
                # dimension the row is filed under: the one tag the game leaves in two worlds is the
                # ruined portals, and the collection splits it into the row of each of them
                expected = {member for member in tags[row["name"]]
                            if member in game and dimensions[member] == [row["dimension"]]}

                if set(row["members"]) != expected:
                    failures.append(f"the group {row['name']} lists {sorted(set(row['members']))} where "
                                    f"the game's own tag has {sorted(expected)} in {row['dimension']}")

        for member in row["members"]:
            if member not in game:
                failures.append(f"the row {row['id']} lists {member}, which the game does not ship")
                continue

            if member in covered:
                failures.append(f"{member} is listed twice, in {covered[member]} and in {row['id']}")
            covered[member] = row["id"]

            if dimensions[member] != [row["dimension"]]:
                failures.append(f"{member} generates in {dimensions[member] or 'no dimension'}, "
                                f"and the row {row['id']} files it under {row['dimension']}")

    for missing in sorted(set(game) - set(covered)):
        failures.append(f"{missing} is a structure of the game and no row of the list covers it")

    for structure in sorted(advanced):
        if structure not in covered:
            failures.append(f"the game hands out {structure} for entering it, and no row covers it")

    # the sections of the collection are the dimensions of the catalog, in the order of the enum, so
    # the rows have to be written in that order and not interleaved
    order = re.findall(r"^\t\t([A-Z_]+),?$", re.search(r"\tenum Dimension\n\t\{\n(.*?)\n\t\}",
                                                       pathlib.Path(
                                                               "src/main/java/com/devgx9/"
                                                               "wanderers_titles/client/"
                                                               "BiomeCatalog.java"
                                                       ).read_text(encoding="utf-8"),
                                                       re.S).group(1), re.M)
    written = [row["dimension"] for row in listed]

    if written != sorted(written, key=order.index):
        failures.append(f"the rows are filed as {written} rather than dimension by dimension")

    # and every row has a name of its own, written down and no longer than a title
    named = {}

    for row in listed:
        namespace, path = row["id"].split(":", 1)
        key = f"structure.{namespace}.{path}"
        name = lang.get(key)

        if name is None:
            failures.append(f"{key} is not written down in {LANG.name}")
            continue

        if not name.strip() or len(name.split()) > TITLE_WORDS:
            failures.append(f"{key} reads {name!r}, which is not a name of at most {TITLE_WORDS} words")

        if (row["dimension"], name) in named:
            failures.append(f"{key} reads {name!r} as {named[(row['dimension'], name)]} does, in the "
                            f"same dimension")
        named[(row["dimension"], name)] = key

    for key in sorted(key for key in lang if key.startswith("structure.")):
        if key not in {f"structure.{row['id'].split(':', 1)[0]}.{row['id'].split(':', 1)[1]}"
                       for row in listed}:
            failures.append(f"{key} is written down and no row of the list is named by it")

    print(f"the structures the collection lists, against the {len(game)} the game ships:\n")

    for dimension in order:
        section = [row for row in listed if row["dimension"] == dimension]

        if not section:
            continue

        print(f"  {dimension.title():9s} {len(section):3d}  "
              + ", ".join(lang.get(f"structure.{row['id'].split(':', 1)[0]}."
                                   f"{row['id'].split(':', 1)[1]}", row['id'])
                          for row in section))

    listed_ids = {member for row in listed for member in row["members"]}
    print(f"\n  {'rows':9s} {len(listed):3d}  covering {len(listed_ids)} structures; "
          f"{len(advanced)} of them the game hands an advancement out for, and all of those are listed")

    if failures:
        print()

        for failure in failures:
            print(f"WRONG {failure}")
        return 1

    print("\nthe list is the game's own structures, grouped by its own tags and filed where their "
          "biomes put them")
    return 0


if __name__ == "__main__":
    sys.exit(main())
