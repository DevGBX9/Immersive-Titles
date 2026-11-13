# Architecture

Immersive Titles is a **client side** Fabric mod: `fabric.mod.json` declares `environment: client`
and the only entrypoint is `WandererTitlesClient`, which reads the mod's own metadata and hands
everything to `TitleOverlay`. Nothing of the mod runs on a server.

Nothing in the mod is configured and nothing is registered with an item, a block or a packet: it
watches the place the player is in and draws over the HUD.

## What is titled, and when

Two titles, one rule: the **reveal** on the first visit of a biome or a structure in a world, the
**corner title** on every visit after it.

| trigger | first visit | every visit after |
| --- | --- | --- |
| entering a biome | reveal | corner title |
| entering a structure | reveal | corner title |
| looking at a structure from outside (≤ 6 blocks, 1.5 s) | reveal | nothing |
| `T` (Play Title Reveal) | reveal of the biome under the player | reveal of the biome under the player |
| `J` (Show Corner Title) | corner title of the biome under the player | corner title of the biome under the player |

The place is checked every 10 ticks (`CHECK_INTERVAL_TICKS`) once the player has moved at least one
block after joining, so the first title is about where the player actually is, not about what the
camera saw while the world was loading.

### Biomes: `BiomeNames.place`

The client is sent the biomes of the world, so a biome is read by looking at the player's position.
Below the surface that answer is the biome of the column the player is under, which is not a place
they are in, so `place` holds it against the ground:

1. in a world with no sky over the ground (the nether, a cave world) everything reported is a place
   and is titled;
2. if the surface of the player's own column wears the reported biome, it is theirs only while they
   stand on that surface — more than `ROOF_DEPTH` (4) blocks of ground over their head and nothing is
   titled;
3. if it wears another biome, the reported one is read as underground: it is titled only when the
   surface around the player (8 columns, 32 blocks out) does not wear it at all. That is what lets a
   dripstone cave, a lush cave or the deep dark be titled while a walk through a cave under one
   forest after another stays silent.

### Structures: `StructurePresence` and `StructureCatalog`

Structures are worked out on the server while the world is generated and the client is never told
about them, so the mod asks the only side that knows: `StructurePresence` listens to
`ServerTickEvents.END_LEVEL_TICK`, which is the server of a **world the player hosts themselves**
(the same game), and writes what each player is inside into a map that the client reads.

- *inside*: the game's own `getStructureWithPieceAt`, held by the region of the structure start, so
  the ground between the houses of a village is walked as the village;
- *looking at*: an invisible ray from the player's eyes along their line of sight, 6 blocks at most,
  clipped by the world, counted for 30 ticks (1.5 s) before it is handed over.

A world on a dedicated server cannot be reached at all: no structure is ever read there, and its
titles name biomes only. `StructureCatalog` is the list of structures the game can recognize, read
from its registry, with the families the game tags together (a village is one row, not five) and an
id of the mod's own for each family.

## What a world remembers

`WorldIdentity` names a world and gives it a folder; the discoveries are written into one plain text
file, one line per reveal:

| world | identity | file |
| --- | --- | --- |
| hosted (single player / LAN host) | a random UUID written once into the world folder | `<world>/immersive_titles/discovered.txt` |
| on a server | the address it was joined with (hashed into the file name) | `config/immersive_titles/servers/<address>-<hash>.txt` |

A line is a biome id (`minecraft:pale_garden`) or a structure id behind the marker `structure `
(`structure minecraft:fortress`), which is what keeps the two kinds apart in one file. Lines are
appended as they happen (a crash keeps the session's discoveries), and `#` starts a comment, so a
player can delete a line to get a single reveal back. Reading and writing are best effort: a file
that cannot be read or written is a line in the log, never a crash.

The identity of a hosted world is a value the world carries, so two worlds both called `New World`
cannot share anything, a copied world takes its discoveries along, and a deleted world takes them
with it.

## The collection screen

`BiomeCollectionScreen` is the list side of the same memory: a page of biomes (`BiomeCatalog`) and a
page of structures (`StructureCatalog`), each divided by dimension, each row checked when that
reveal has already happened, with a progress line and two filters (found / still to find). Hovering a
discovered row offers to **forget** it: with a confirmation, the line is taken out of the file and the
id is dropped from the session, and the reveal plays again the next time it is entered (after the
player walks a block, so it is walked into rather than handed straight back).

Opened from the mod's own key (`M`) it is that world's collection; opened from the Mod Menu entry it
is every world the client has written discoveries for (`DiscoveredBiomes.loadAll`).

## The reveal

- 187 PNG frames at 1440 x 720, 30 fps, 6.23 s, shipped in `src/main/resources/assets/immersive_titles/textures/title`;
- the frames are white with alpha carrying the luminance, so the plate glows over the world instead of
  covering it with black; the outer `FRAME_CROP` (9) rows are cropped off in the draw;
- played back on real time between drawn frames, holding still while the pause menu is open;
- only the frames still in use are kept loaded (`FRAMES_KEPT_LOADED` = 4) because one frame is ≈4 MiB
  of video memory;
- the name is not in the clip: the empty box in the middle is measured (`NAME_CENTER_X/Y`, room
  807 x 120) and the name is scaled into it and faded in as the opening flash clears (`VEIL`).

The corner title is pure text: the last word is the name at `CORNER_NAME_SCALE`, the words before it
stand over it letter-spaced and centred. It fades in, holds 3 s and fades out in the lower left
corner, and is lifted over the hotbar on screens narrower than the name.

Both are drawn by `HudElementRegistry` before `VanillaHudElements.CHAT`, which is what keeps them
under every screen the player opens.

## Files

| class | what it is |
| --- | --- |
| `WandererTitles` | mod id, logger, the name and version read from the jar |
| `WandererTitlesClient` | the client entrypoint |
| `TitleOverlay` | both titles, their timing and the checks that start them |
| `BiomeNames` | the biome id, its display name, and the place rule |
| `StructurePresence` | structures as the server side of a hosted world knows them |
| `StructureCatalog` | the structures the collection lists, grouped as the game groups them |
| `StructureNames` | the names a title and a row call a structure |
| `BiomeCatalog` | the biomes of the world, in the order the collection shows them |
| `CollectionEntry` | the one thing the collection draws, whichever catalog built it |
| `BiomeCollectionScreen` | the collection screen |
| `ModMenuIntegration` | the Mod Menu entry that opens the collection of every world |
| `WorldIdentity` | the identity and the file a world's discoveries live in |
| `DiscoveredBiomes`, `DiscoveredStructures` | reading and writing that file |
