# Immersive Titles

[![Dev Builds](https://github.com/DevGBX9/Wanderers-Titles/actions/workflows/gradle.yml/badge.svg)](https://github.com/DevGBX9/Wanderers-Titles/actions/workflows/gradle.yml)
[![License](https://img.shields.io/github/license/DevGBX9/Wanderers-Titles.svg)](LICENSE)

Displays cinematic biome titles when entering a biome, with a large first-visit title and a compact title on later visits.

> **Status: work in progress.**
> Both reveals are implemented and playable on 26.3, including the biome trigger, the biome name in
> the title card and the first visit / later visit behaviour.

Immersive Titles is a **client side mod**: fabric.mod.json declares it as client only, the mod has
no common or server entrypoint, and everything it does is done by the client it is installed on. A
server does not need it, and installing it there does nothing.

It asks the loader for [Fabric API](https://modrinth.com/mod/fabric-api), so Fabric API has to be
installed next to it, for the same Minecraft version: the mod does not carry its own copy of the
parts of Fabric API it calls, it needs them to be there.

## What works right now

The big reveal opens with a short impact sound, `title_reveal.ogg`, at the same moment the plate
arrives. It is played on the master channel rather than as a UI sound, so that pausing the game
holds the sound with the picture instead of letting it run on behind the pause menu.

Opening the pause menu holds a running reveal on the frame it is showing, behind the menu, and
closing the menu carries on from that same frame instead of playing the clip from its start again;
the small corner title holds the same way. Nothing else holds them: the inventory, a chest, the chat
and every other screen are not an interruption - the game does not pause for them either - so a
reveal keeps playing behind them and is further along, or already over, when they are closed.

While the reveal plays, the name of the biome is written into the empty middle of the ornamental
plate with the game font, scaled so that any biome name fits between the ornaments. The name is not
part of the clip: it comes from the clip's own light, i.e. it is hidden while the opening flash
covers the plate and it appears as the flash clears, then it fades out with the plate.

The clip is 2:1, so it is fitted into the screen instead of being stretched to it, which leaves a
strip of the world above and below. The opening flash fills the clip edge to edge, so those strips
used to end the clip with a visible seam right across the screen. The overlay now continues the flash
into them with the row of the clip right inside its edge, stretched across the strip: the clip's own
pixels, because its picture is not uniform along that edge - the flash is brighter in the middle than
at the sides - and no single colour can match it.

The clip also carries a one pixel bright rind along the top and the bottom of its picture, where the
opening flash was cut off: the outermost row of the picture is up to fifty levels brighter than the
rows right inside it and drew a hard white line across the screen. The first and the last nine rows
of every frame are therefore left out of the picture that is drawn, which removes the rind, drops the
transparent margin the render kept there with it and costs nothing of the glow. Outside the flash the
edge of the clip is transparent in every frame, so nothing is drawn over the world the rest of the
time.

## When a title plays

The biome the player is in is checked twice a second, which is why the check is free.

- **First entry into a biome** - the full cinematic reveal, with that biome's name in the plate.
- **Every entry after that** - only the small corner title, so a biome reveal stays something that
  happens once.
- **Joining a world** - the titles wait until the player actually moves, one block is enough, so the
  first title is about the biome the player ends up standing in instead of about whatever the world
  was showing while it was still loading.
- Leaving and re-entering a biome counts as an entry again, which is what brings up the corner title.

The names come from the game's own biome translations, so they follow the language of the client; a
biome without a translation falls back to its id with underscores as spaces.

Which biomes have already had their reveal is remembered per world, so the reveal belongs to the
world it happened in: leaving a world and joining it again later only brings the corner title back
for everything already seen, while another world still has its own first visits to hand out.

A world is named by an id it owns itself, never by anything read from its name: the first time the
mod is in a world the player hosts, it writes a random UUID into that world's own folder, and the id
can only ever belong to that world. Two worlds that share a name, or a world that is renamed, can
therefore not share or lose anything - every folder carries its own id, a world that is copied takes
its id along with the copy, and a world that is deleted takes its id and its list of discoveries
with it, because both live in its own folder:

- `<world>/immersive_titles/identity.txt` - the id of that world, written once.
- `<world>/immersive_titles/discovered.txt` - the biomes of that world, one biome id per line,
  appended as each one is discovered. Deleting a line gives that single biome its reveal again, and
  deleting the file starts that world over.

A world on a server is not the client's to write to, and the game tells a client nothing about a
server world that no other server world could also have, so the discoveries of a server world are
kept in `config/immersive_titles/servers`, one file per address the player joined with.

## The biome collection

The list of everything there is to find is a key of the mod's own, `K` by default and rebindable in
the controls screen: it opens a screen with every biome the world has, the ones already discovered
checked and dimmed, and a line at the top counting them. Opening it pauses the game exactly like the
escape menu does, and the world behind it is blurred the same way.

The biomes are listed from the ones a player meets while walking around to the ones that take a
portal, a long trip or a lucky world - mushroom fields, ice spikes and the pale garden are far down
the list, and the nether and the end are below them. Which biome belongs where is the mod's own
reading, written out by hand, because the game has no opinion on how rare a biome is. A biome from a
mod is listed with the name of the mod it comes from, after the biomes the list knows.

## What the mod is meant to do

Immersive Titles is planned as a client side mod that treats every biome like a discovered location:

- **First discovery of a biome** - a large cinematic title card is presented in the middle of the screen,
  with elegant ornaments and its own multi step animation: an entrance, a slight overshoot, a settle,
  a short readable hold and an animated exit. It should feel like a location reveal instead of an
  ordinary HUD notification. *(implemented: the clip and the biome name in its plate)*
- **Later visits** - when the player leaves an already discovered biome and enters it again, the biome
  name is shown as a much smaller title near the lower-left corner, with a shortened animation.
  *(implemented)*
- **Triggering** - a title is shown when the player's biome actually changes, not continuously while
  standing inside the same biome. Leaving and re-entering a biome triggers it again. *(implemented)*
- `minecraft:plains` is planned as the first experimental biome. The architecture is meant to be
  extended to all biomes later.

The intended visual language of the title card is a near-black composition with bright white uppercase
display type and symmetrical, thin ornaments above and below the biome name. All fonts and decorative
assets shipped with the mod are original work of this project.

## Minecraft versions

| Minecraft | Status |
| --- | --- |
| **26.3** | enabled - core project and active development target for the Immersive Titles feature |
| **1.20.1** | disabled for now, configuration kept so it can be re-enabled later |
| all other versions | disabled for now, configuration kept so they can be re-enabled later |

- `settings.json` contains the enabled build matrix (`versions`) and the versions that are currently
  disabled (`disabled_versions`).
- `build.gradle` contains the preprocessor node definitions, including the commented out definitions of
  the disabled versions, together with the steps needed to re-enable one.
- `versions/mainProject` names the core project, i.e. the version whose sources are the root `src/` folder
  and from which the other enabled versions are derived.
- The core project compiles the root `src/` folder as it is, and a `versions/<version>/src` folder is only
  compiled for child versions. Since 26.3 is the core project right now, all of the mod code, including
  the title reveals, lives in `src/`.

## Building

The authoritative build environment is GitHub Actions: every push is built there through the
[Dev Builds workflow](https://github.com/DevGBX9/Wanderers-Titles/actions/workflows/gradle.yml).
The build matrix only contains the enabled versions listed above, and the enabled/disabled matrix is
printed into the run summary of every build.

`./gradlew build` is the command the workflow runs for every enabled version. Each version builds
`immersive_titles-v<version>.jar`, i.e. the jar reports the plain version of `gradle.properties` -
`immersive_titles-v0.1.0.jar` right now - and the build number of the run that produced it is not
part of the mod or of the jar's name. Which version a jar is for comes from the run it was
downloaded from.

## License

Licensed under the [MIT License](LICENSE).

The project is based on [fallen's fabric mod template](https://github.com/Fallen-Breath/fabric-mod-template),
thanks to Fallen_Breath for it.
