# Immersive Titles

Cinematic, per-world **biome and structure titles** for Minecraft, on Fabric — client side only.

![The reveal in game](https://cdn.modrinth.com/data/cached_images/e40ae9edc74c6d5b07fd33b61aeeec4b2defd6b9_0.webp)

## What it does

- **First visit → a reveal.** Entering a biome or a structure for the first time in a world plays a
  full-screen ornamental plate (187 frames, 6.2 s, with a sound), and the name of the place is drawn
  into the plate in the game's own font.
- **Every visit after → a corner title.** The same place is then named by a small two-line title in
  the corner of the screen (a letter-spaced word over the name, in the shape of the small area title
  of *Hollow Knight: Silksong*), held for three seconds.
- **Structures count too** — walking into one, and also standing outside it and looking straight at it
  for a second and a half from within six blocks.
- **A collection screen** lists every biome of the world and every structure the game can recognize,
  by dimension, with what has been found, what is left, and a way to forget a discovery so its reveal
  plays again.
- **Remembered per world**, on disk, so the reveal belongs to the world it was found in — not to the
  session, and not to the name of the save.

## Controls

| key | action |
| --- | --- |
| `M` | open / close the collection |
| `T` | play the reveal of the biome you are in |
| `J` | play the corner title of the biome you are in |

All three are ordinary key mappings: rebind them in **Options → Controls → Key Binds** under
*Immersive Titles*.

## Supported versions

| | |
| --- | --- |
| Minecraft | **26.3**, **26.2** and **26.1.2** (all enabled, all built from the same sources) |
| Loader | Fabric Loader `>=0.11.4` |
| Requires | Fabric API `>=0.160.5` on 26.3, `>=0.160.0` on 26.2, `>=0.155.3` on 26.1.2 (each jar asks for the API it was built against) |
| Optional | Mod Menu (adds the collection to the mod list) |
| Java | 25 |

Older versions are kept configured but disabled; see [`docs/build-and-release.md`](docs/build-and-release.md)
for what re-enabling one takes.

### Downloads

Every build of `main` publishes one jar per enabled version, as public assets of the
[`dev-latest` development build](https://github.com/DevGBX9/Immersive-Titles/releases/tag/dev-latest):

- `immersive_titles-v0.1.0-mc26.3.jar` — for Minecraft 26.3
- `immersive_titles-v0.1.0-mc26.2.jar` — for Minecraft 26.2
- `immersive_titles-v0.1.0-mc26.1.2.jar` — for Minecraft 26.1, 26.1.1 and 26.1.2

No account is needed to download. The `dev-latest` tag is rewritten by each build, so those links keep
working and always point at the newest build.

### Known scope

- It is a **client side** mod: nothing is installed on a server, and no packet is sent.
- **Structures are named in worlds you host yourself** (single player, or LAN host). In a world on a
  dedicated server the client is never told about structures, so titles there name biomes only.
- The reveal is a pre-rendered clip: it is not generated from the world.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3, 26.2 or 26.1.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and this mod's jar in `.minecraft/mods`.
3. Optionally add [Mod Menu](https://modrinth.com/mod/modmenu) to reach the collection from the mod list.

## Project layout

```
.
├── .github/                  build and release workflows, issue templates
├── docs/                     architecture, build matrix, art pipeline
├── src/main/java/            the mod (com.devgx9.wanderers_titles)
│   └── …/client/             entrypoints, title/, collection/, catalog/, world/
├── src/main/resources/       fabric.mod.json, language files, sounds, textures, gui sprites
├── tools/                    checks, previews and the art tools (see tools/README.md)
├── versions/                 one Gradle project per Minecraft version (26.3, 26.2, 26.1.2)
├── build.gradle              the version matrix (preprocessor nodes)
├── common.gradle             what every version project is made of
├── settings.json             enabled / disabled Minecraft versions
└── gradle.properties         mod id, name, version, loader version
```

Nothing that ships is under `tools/` or `docs/`: `src/` is the mod, and everything else reads it.

## Development

The source is written for Minecraft 26.3 with the client rendering APIs of 26.x, and is compiled by
JDK 25. 26.2 is a child project of 26.3, and 26.1.2 is the child of 26.2: the preprocessor builds
each of them out of that same `src/`, and no `//#if` guard keeps any of the mod out of them, so the
three versions are one feature and one implementation.

```bash
./gradlew build          # all enabled versions
./gradlew :26.3:build    # just 26.3
./gradlew :26.2:build    # just 26.2
./gradlew :26.1.2:build  # just 26.1.2
```

The build is run by GitHub Actions (`.github/workflows/gradle.yml`) on every push that touches the
sources, the Gradle files or the workflows; the run summary lists the enabled versions and every jar
that was built.

`tools/` holds the static checks that guard the parts of the mod that are numbers and drawings rather
than logic — the structure list against the game's registry, the language keys against the sources,
the plate's geometry against the frames, the gui sprites against the files. They are plain Python and
need nothing but the repository:

```bash
python tools/checks/check_java_shape.py
python tools/checks/check_structures.py
```

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — how the titles, the place rule, the structures and
  the per-world memory fit together
- [`docs/build-and-release.md`](docs/build-and-release.md) — the version matrix, Gradle tasks, workflows
- [`docs/asset-pipeline.md`](docs/asset-pipeline.md) — the frame set, the drawings, and the tools behind them
- [`docs/version-support.md`](docs/version-support.md) — the version matrix, the package map by how
  close each package is to the game, and how to add an older version
- [`tools/README.md`](tools/README.md) — every check and preview, and what each holds to what

## Credits

- Mod by [DevGBX9](https://modrinth.com/user/DevGBX9).
- The gui sprites under `textures/gui/sprites/` come from the *Reimagined GUI - Dark* resource pack,
  used with the permission of its author — see
  [`CREDITS.md`](src/main/resources/assets/immersive_titles/textures/gui/sprites/CREDITS.md).
- Forks are welcome under the same license; keep the credit to the author.

## License

[MIT](LICENSE) — fork it, change it, ship it, keep the credit.
