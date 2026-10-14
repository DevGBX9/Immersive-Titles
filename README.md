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
plate with the game font, in capitals and at most two words, scaled so that any biome name fits
between the ornaments. The name is centred on the middle of the plate as the frames themselves
measure it - the column both ornaments are drawn about and the row in the middle of the room they
leave - and a name longer than two words is shortened to the two that name the biome, with the words
that only say which kind of a place it is taken out: `Old Growth Spruce Taiga` is titled `SPRUCE
TAIGA`, `Deep Lukewarm Ocean` is `LUKEWARM OCEAN`, and `Soul Sand Valley` is `SOUL VALLEY`. The name
is not part of the clip: it comes from the clip's own light, i.e. it is hidden while the opening flash
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
checked and the ones still waiting dimmed, and a line at the top counting them - the same count again
under that line, as a thin bar, so how much of the collection is found is read at a glance before a
word of it is read, with the title of the screen set a fifth smaller than the game's own font because
that count is what the screen is opened for. Opening it pauses the
game exactly like the escape menu does; what the world behind it is not is blurred - it is seen through
a veil of the mod's own instead, a little lighter than the one the game's menus use, because the block
the collection is set in is drawn over it in the mod's own colours and a world left sharp behind it is
a world the player is still in. A screen opened with no world behind it - the mod list, from the title
screen - keeps the game's own background, since a panorama is a thing to leave alone.

The same screen is what clicking the mod in [Mod Menu](https://modrinth.com/mod/modmenu) opens: it is
the only screen the mod has, so it is the one the mod list offers. Mod Menu is not a dependency - a
player without it simply has the mod's own key - and the two ways in do not show the same thing. The
key shows the world in front of the player. The mod list shows the collection itself, over every world
the player has at once: what each of those worlds remembers is read together - the world folders of
the worlds they host themselves, and the config files of the servers they have joined - so a biome
found in any of them is checked here, wherever it was found. That is also why nothing there offers to
forget a biome: forgetting a discovery takes it out of the file of one world, and this list is not one
world's. The line under the title counts what was found across all of them, in the same words the
world view counts one world with, and a biome that was found somewhere is on that list even when the
mod's own order has never heard of it, since the list is what was found rather than what the mod knows.

The list is divided by dimension: the overworld's biomes first, then the nether's, then the end's, and
last the ones that belong to no dimension at all, each of them under a line that names the dimension
and counts what the collection has of it, found and total. Which dimension a biome is in is not the
mod's reading but the game's own: a world generates its dimensions from the biome tags it holds, and
those are what the list is divided by, so a biome of a mod is filed under the dimension its author
tagged it into, and the void - the barrier of the end, which no world generates - is listed as
belonging to no dimension. A section the filter has emptied is left out with the biomes it would have
held, rather than standing in the panel as a heading over nothing.

Inside a section the order is the mod's own, written out by hand, from the biomes a player meets while
walking around to the ones that take a long trip or a lucky world - mushroom fields, ice spikes and
the pale garden are far down the overworld's section - because the game has no opinion on how rare a
biome is. A biome from a mod is listed with the name of the mod it comes from, after the biomes the
list knows.

The list is narrower than the screen and centred, with the room at its sides left empty for two small
buttons of the mod's own: the one on the left keeps only the biomes that were discovered, the one on
the right only the ones that are left to find, and pressing the one that is already on puts the list
back to every biome. Each carries the mark its filter is about - the check the list uses for a found
biome, and a question mark for one that is still waiting - dim while off and lit while on, and says
what it does in a tooltip.

The collection is read as two pages, and the two tabs over the list are which one: `Biomes`, which is
the list this screen has always been, and `Structures`, which no world writes down yet. The second
page is the interface of a collection still to come and nothing else: the title, the tabs, the frame
and the button that closes the screen are the same on both pages, while the line counting the
discoveries and the two filters belong to the biomes and step aside while that page is shown, since
neither has anything to count or to filter there, and where the list of biomes would be, the page says
that nothing is collected there rather than standing empty. A tab is as tall as a button of the game
and half of the strip the two of them make together, the strip is as wide as the line drawn over it -
the plate of the screen - and it is centred on the list, under that plate and resting on the frame of
the list itself: rather than a box fitted around each name, the two are the bar under the plate that
holds the two pages, and the name of each page is written in the middle of its own half of it. The bar
is stuck to the frame rather than set off from it, because it is the top of the list it names: the
pages are what the list holds, so the two edges meet and read as one line. The name of the page being
read is lit in the white of the title while the other one is dim in the grey a filter that is off is
drawn in - the same way those two filters say which of them is on. The title over the whole screen
names the collection itself rather than one of its two pages, since it is the collection that holds
both and the tabs are what name the pages.

The collection can be read in two themes, and both of them are built out of the game's own gui sprites.
The one it opens on is the mod's own: the block the list is held in is the game's own window - a band
of grey with a black line around it, a lit edge over a darker one and a single flat grey inside it -
the rows are the game's own buttons, a biome that was found the lit one and one whose reveal is still
waiting the flat dim one, and the bar counting the collection is the game's own experience bar, trough
and fill both, so how much of the world has been found is read in the one shape every player already
reads a share of progress in. Its text is read by brightness rather than by the game's own three
colours, the block drops a light shadow over the world behind it, and the gold is spent on the name of
a dimension, the tab of the page being read and the row the mouse is on. The other theme is the game's
own, which is the screen as it was before there was a theme at all - the same window drawn as one of
the game's buttons, the panel the black at 44% the game's own lists sit on, the flat bar it was, and
none of the gold. What a player chooses is written down in
`config/immersive_titles.json`, so it is the same the next time the game is started, and the button
that turns it over is the small one in the top right corner of the screen: it carries the moon of the
theme being read, says which one that is in its tooltip, and the mod still has one setting.

The screen is built out of the game's own pieces, so it matches the menus around it and follows a
resource pack that redraws them - the window around the list, the buttons the rows and the tabs are,
the bar under the count and the scroller of the list are all sprites of the game, and one resource pack
that redraws any of them redraws this screen with it: every biome is drawn as one of the game's buttons, a biome that was
found with a small green check and its name in clear white, a biome whose reveal is still waiting dimmed
with the sprite the game draws a button it will not let anyone press in and its name the dimmest text on
the screen - so what has been found is what stands out and what is left to find stays out of the way -
and the scrollbar is the one the game's own lists are scrolled with, drawn four gui pixels wide rather
than the six the game draws it in, since a bar of a list that is read rather than scrolled through is
not worth the width of a row. The rows are drawn rather than made of buttons that work, because there
is next to nothing to press on a biome: they cannot be clicked, hovered into a highlight or focused.
The row the mouse is on is held in a thin gold line in the mod's theme - which is what says which row
is being read, and with it which biome the button at the end of the row would forget - and the scrollbar
is a scrollbar: it can be taken with the mouse and dragged, and the list follows the hand exactly.

A biome that was discovered is also the one thing in the list that can be pressed. Hovering its row
draws a button at the far right end of it, in the place the name of the mod a biome comes from is
drawn at: the same button the two filters are drawn as, with a cross of the game's own font over it
in the place of their check or their question mark, and that cross turns red and the button lights up
while the mouse is on the button itself. Pressing it asks before anything happens: the game's
confirmation screen, with the biome's name and what forgetting it means. Forgetting a biome takes its reveal back
out of the world - the line is dropped from the file that world's discoveries are written to, and
from what the session remembers - so the next time the biome is entered, the full reveal plays again.
That is for a player who wants a reveal back: a biome walked into by accident, a file edited by hand,
a discovery that should not have been one. Nothing offers to forget anything in the collection the mod
list opens, whether a world is being played or not: a row of that list is a biome found in whichever
of the player's worlds it was found in, and the file to take it out of is not this screen's to pick.

What the screen draws itself are the mod's own lines. Two of them frame the list, over it and under
it, and what they are a drawing of is a needle with a thread through it: the needle stands in the
middle of the line, the thread leaves its eye and ends in a loop at either end of the line, and three
small barbs hang off the thread on the way. They are drawn in pixels, at exactly the size the screen
draws them at - 228 x 22 gui pixels over the list and 140 x 18 under it, one texture pixel to one gui
pixel - because a drawing of one-pixel threads is a drawing that cannot be scaled: a share of such a
drawing is a share of a pixel, which is a grey haze and not a thinner thread. That is also what the
drawings they replace had become: dense line art of the kind that has to be cut down to the size of
the screen, and reading, at that size, as a white blur that competed with the list it framed. The pair
carries a twentieth of their box in ink where those carried two fifths of theirs. `templates/collection_frame`
holds both drawings and the script that draws them, and each line is centred on the list rather than
stretched to its width, which is what tells the player they are looking at a collection of things
rather than at a menu.

The third line is the one inside the panel, over each section: the name of the dimension in the gold
the game writes a heading in, the count of that dimension at the far end of the row, where the name of
a biome's mod is written, and a rule drawn in the room above the row that sets the section off from
the one before it. It is drawn with the game's own font and the game's own colours rather than cut
into a texture of the mod's, because what it says is a line of the mod's language file - the name of a
dimension - and not a drawing.

The collection is a block of the screen rather than the screen itself. The list is given at most half
of the screen's height and 320 gui pixels of its width, the block it is held in is a sprite of the game
drawn eight gui pixels past it on every side - the band the game's own window keeps around what it
holds - the rows are two gui pixels shorter than they were with the same room under each of them, so
two more of the collection fit on any screen, and the
block - the title, the count, its bar, the plate, the tabs, the list, the line under it and the button
- is set in the room the list leaves over:
half of that room goes over the title and half under the button that closes the screen, so the two
margins come out the same to the pixel, and a screen of an odd height has the pixel no halving can
place taken off the list rather than handed to one of them. The same is done across the screen - the
list is centred on an even difference - so the room at its left and the room at its right are the same
on any width. The line over the list is the plate the two tabs stand under, and it is drawn at the
width the two of them make together - 228 gui pixels - so the ornament, the bar of pages under it and
the frame the bar rests on read as one thing. The line under the list is the foot of the block: it is
the drawing a player is left looking at once the list has been read, and it is drawn smaller than the
plate, 140 x 18 gui pixels. Neither is stretched to a width of its own - each is drawn at the size it
is made at, so a width and a height are one number - and the two are drawn at their own sizes because
one is the head of the block and the other its foot, rather than as the same ornament cut twice. On a
screen too small to hold even the smallest of that, the list is given everything between the two
margins, and eight gui pixels are kept over and under the block.

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
  name is shown as a much smaller title near the lower-left corner, with a shortened animation. It is
  drawn above the room the game's own HUD takes along the bottom of the screen - the hotbar and the
  row of hearts over it, 39 pixels together - so a biome name is never drawn through the hotbar.
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
