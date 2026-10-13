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
- **Being in a cave** - below the surface the game reports the biome of the column a player is
  under instead of a biome they are in, so a walk through a cave under one surface biome after
  another is not a walk into biomes: a biome the surface of the ground around the player wears is
  neither titled nor written down as discovered. What is titled down there is a biome of its own -
  the cave biomes of the game, a dripstone cave, a lush cave, the deep dark, and the cave biomes of
  a mod as well, since the two are told apart by comparing the biome the player is in with the
  biomes the ground around them wears, not by a list of names. Reading the ground around the player
  is what the ground over their own column alone cannot do: the game reads the biome of a position
  from the climate there, the climate of a cave is the climate of its depth, and the region a
  surface biome reaches across the ground down there does not end where the same biome ends on the
  surface - so the ground over a player can wear one biome while the game reports another whose
  region reaches under it, and the eight surface columns read thirty-two blocks around the player
  are what tell the two apart. A player counts as being under ground once four blocks of what the
  game measures as the ground over them stand over their head. Leaves are left out of that measure,
  so walking under a canopy is unaffected, while water is part of it: an underground lake is ground
  like the stone around it, and a player diving that deep in the open sea is told what they swam
  into on the way back up. Either way the titles pick up again as soon as the player is back out in
  the open.

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

The same screen is what clicking the mod in [Mod Menu](https://modrinth.com/mod/modmenu) opens: it is
the only screen the mod has, so it is the one the mod list offers. Mod Menu is not a dependency -
a player without it simply has the mod's own key - and the one thing that differs between the two ways
in is what is behind them: the key is read in a world, while the mod list can be opened from the title
screen, where the mod lists the biomes it knows and says there is no world to count rather than
showing an empty list as if nothing had ever been found.

The biomes are listed from the ones a player meets while walking around to the ones that take a
portal, a long trip or a lucky world - mushroom fields, ice spikes and the pale garden are far down
the list, and the nether and the end are below them. Which biome belongs where is the mod's own
reading, written out by hand, because the game has no opinion on how rare a biome is. A biome from a
mod is listed with the name of the mod it comes from, after the biomes the list knows.

The list is narrower than the screen and centred, with the room at its sides left empty for two small
buttons of the mod's own: the one on the left keeps only the biomes that were discovered, the one on
the right only the ones that are left to find, and pressing the one that is already on puts the list
back to every biome. Each carries the mark its filter is about - the check the list uses for a found
biome, and a question mark for one that is still waiting - dim while off and lit while on, and says
what it does in a tooltip.

The screen is built out of the game's own pieces, so it matches the menus around it and follows a
resource pack that redraws them: every biome is drawn as one of the game's buttons, a biome that was
discovered is drawn as one of its disabled buttons, dimmed and checked, the panel behind the list is
the colour the game's own lists sit on inside a world, the frame around that panel is one of the
game's buttons drawn behind it a few pixels wider on every side, and the scrollbar is the one the
game's own lists are scrolled with. The rows are drawn rather than made of buttons that work, because
there is next to nothing to press on a biome: they cannot be clicked, hovered into a highlight or
focused. The scrollbar, on the other hand, is a scrollbar: it can be taken with the mouse and
dragged, and the list follows the hand exactly.

A biome that was discovered is also the one thing in the list that can be pressed. Hovering its row
draws the game's own cross button at the end of it, in the place the name of the mod a biome comes
from is drawn at, and pressing that button asks before anything happens: the game's confirmation
screen, with the biome's name and what forgetting it means. Forgetting a biome takes its reveal back
out of the world - the line is dropped from the file that world's discoveries are written to, and
from what the session remembers - so the next time the biome is entered, the full reveal plays again.
That is for a player who wants a reveal back: a biome walked into by accident, a file edited by hand,
a discovery that should not have been one. Nothing offers to forget anything while there is no world
being played, which is the way in the mod list can open the screen from the title screen.

The one thing in the screen that is not the game's own is the pair of lines the list is framed with,
over it and under it. They are the mod's own drawing - two white ornaments on a flat sheet, cut out of
it so that only the lines are left, and written into the mod's `textures/gui` large enough to be read
at the size they are drawn at. The list is drawn between them, and each line is centred over or under
it at the proportions of the drawing rather than stretched to its width, which is what tells the
player they are looking at a collection of things rather than at a menu. `templates/collection_frame`
holds the drawings themselves and the script that cuts them out of the sheet they were made on, so
the texture the mod ships can be remade from them.

The collection is drawn a little smaller than the screen it sits on. The list is given at most three
fifths of the screen's height, the two ornaments are drawn at two fifths of the drawings they are cut
from, and the list, the rows and the button that closes the screen come down with them - so the
collection sits on the screen with the world around it, instead of filling the screen with a list.
On a small screen, where three fifths is less room than the collection needs, the list is simply
given all of it.

Scrolling is smooth. The wheel and the keys say where the list is going, and the drawn list eases
after them, so the rows slide up and down instead of jumping. The slide is measured against the clock
rather than counted in frames, so it takes the same time at any frame rate, and it moves the rows by
whole pixels so the text does not shimmer on the way.

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
