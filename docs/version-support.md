# Supporting another Minecraft version

The sources under `src/` are written for **one** Minecraft version at a time — today 26.3 — and every
other version is a Gradle project that the preprocessor builds out of those same sources. Supporting
an older version is therefore not a rewrite: it is choosing which lines have to differ, and knowing
which files are close enough to the game to be the ones that do.

## The matrix: which file decides what

| file | what it decides |
| --- | --- |
| `settings.json` | `versions` = the versions built on every run, `disabled_versions` = versions kept for later |
| `build.gradle` (`preprocess { }`) | one `createNode('<version>', <int>, <mappings>)` per version, and the `link(parent, child)` lines that say which sources a version inherits |
| `versions/mainProject` | the core project (`26.3`); its sources *are* the root `src/` folder |
| `versions/<version>/gradle.properties` | `minecraft_version`, `minecraft_dependency`, `fabric_api_version`, `modmenu_version`, `game_versions` for that version |
| `common.gradle` | what every version project is made of: the Loom flavour (obfuscated or not), dependencies, resources, licence |
| `.github/workflows/matrix_prep.yml` | turns `settings.json` into the build matrix the workflows run |

A version's own `versions/<version>/src` folder is only compiled for that version, and only exists
when that version has to differ. There is none today.

## The package map: how close each package is to the game

The packages are ordered below by how much of the game they touch, which is the order a new version
breaks them in.

| package | files | what it holds | how fragile |
| --- | --- | --- | --- |
| `.client.title` | `TitleOverlay` | the reveal and the corner title, their keys, and when they play | **most** — HUD layer registration (`HudElementRegistry`), the screen the game shows (`Gui.screen` / `Gui.setScreen`), key mappings (`KeyMapping`, `KeyMappingHelper`, `KeyMapping.Category`, `InputConstants`), `DeltaTracker`, the gui drawing (`GuiGraphicsExtractor`, `pose`, `blit`, `text`, `RenderPipelines`), `Window`'s gui scale, `SimpleSoundInstance` |
| `.client.collection` | `BiomeCollectionScreen`, `CollectionEntry` | the collection screen and the rows it lists | **most** — `Screen` (`init`, `render`, `keyPressed(KeyEvent)`, `mouseClicked`, `extractBackground`), `Button`, `ConfirmScreen`, sprite blits and scissors |
| `.client.world` | `StructurePresence` | what the server knows the player is inside, and the look at a structure | **a lot** — `ServerLevel`, `ServerPlayer`, block positions, ray tracing, structure regions |
| `.client.catalog` | `StructureCatalog` | the structures the game ships, and the region of one | **a lot** — server-side structure lookups and bounding boxes |
| `.client.catalog` | `BiomeCatalog` | the biomes a level registered, by dimension | **some** — the biome registry, dimension tags, `ClientLevel` |
| `.client.catalog` | `BiomeNames`, `StructureNames` | registry ids turned into the names the game shows | **some** — registry lookups and `Component` |
| `.client.world` | `WorldIdentity` | which world is open, and where its discoveries file goes | **some** — `MinecraftServer` and the saves folder |
| `.client.world` | `DiscoveredBiomes`, `DiscoveredStructures` | the discoveries of a world, on disk | **none** — plain files and a logger; a version that changes rendering never touches them |
| `.client` | `WandererTitlesClient`, `ModMenuIntegration` | the two entrypoints | **some** — `ClientModInitializer`, and Mod Menu, which already carries `//#if MC >= 26.3` guards |
| *(root)* | `WandererTitles` | the mod id, the logger and the metadata | **none** |

The dependency direction follows the same order: `title` → `collection` / `catalog` / `world`, and
`collection` → `catalog` / `world`. There is exactly one edge back: the collection screen asks the
titles what of this world was discovered, and asks them to forget a discovery
(`TitleOverlay.discoveredBiomes`, `forgetBiome`, …), because the titles are what hold that memory for
the session. That pair is the only two-way dependency in the mod, and it is the reason
`client.title` and `client.collection` are the two packages to look at together when the screen or
HUD APIs move.

## Adding a version

1. `settings.json`: move the version from `disabled_versions` into `versions`.
2. `build.gradle`: uncomment its `createNode(...)` line, and hang it off an **enabled** node
   (`<enabled>.link('<version>', ...)`). Sources are inherited from enabled nodes only, so a version
   can never hang off a disabled one.
3. `versions/<version>/gradle.properties`: the Minecraft, Fabric API and Mod Menu versions built for
   it, plus the `minecraft_dependency` range and `game_versions`.
4. Push: the GitHub workflows build the new matrix entry and report the jars. Nothing is built on the
   owner's device.

## Where a difference goes

- **A few lines**: guard them in the shared source, the way `ModMenuIntegration` already does:

  ```java
  //#if MC >= 26.3
  …
  //#endif
  ```

- **A whole file that differs**: put it under `versions/<version>/src/...` at the same path as the
  shared one. A copy drifts from the shared file, so this is the last resort, not the first.

- **A dependency that only one version has**: `versions/<version>/gradle.properties`, read by
  `common.gradle`.

## What to check afterwards

- `tools/checks/` reads the shared sources by path (`client/title/TitleOverlay.java`,
  `client/collection/BiomeCollectionScreen.java`, …). A file that moves between packages has to be
  updated there too — `check_java_shape.py` and `check_lang_keys.py` walk the whole tree and need
  nothing.
- The build itself is only ever the GitHub run; the static checks on this device are what catch a
  broken path before it is pushed.
