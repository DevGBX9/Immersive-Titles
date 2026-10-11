# Immersive Titles

[![Dev Builds](https://github.com/DevGBX9/Wanderers-Titles/actions/workflows/gradle.yml/badge.svg)](https://github.com/DevGBX9/Wanderers-Titles/actions/workflows/gradle.yml)
[![License](https://img.shields.io/github/license/DevGBX9/Wanderers-Titles.svg)](https://www.gnu.org/licenses/lgpl-3.0.html)

Displays cinematic biome titles when entering a biome, with a large first-visit title and a compact title on later visits.

> **Status: work in progress.**
> Both reveals are implemented and playable on 26.3, including the biome trigger, the biome name in
> the title card and the first visit / later visit behaviour.

## What works right now

Both title reveals live in the **Immersive Titles** category of the controls screen, so both can be
rebound there.

| Key | Title | Behaviour |
| --- | --- | --- |
| `T` | cinematic reveal | Plays the 187 frame clip at its native resolution as a full screen overlay, transparent over the world, in real time for about 6.2 seconds, then removes itself. Only the frames still in use are held in video memory. `T` is also the vanilla chat key, so rebind one of the two if the reveal should run without opening the chat. |
| `K` | corner title | Draws the name of the biome the player is in with the game font in bold, two and a half times the normal size, a little above the lower left corner. It fades in, holds and fades out in about 2.9 seconds, and it uses no texture at all. |

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
for everything already seen, while another world still has its own first visits to hand out. The
client keeps that in `config/wanderers_titles/discovered`, one plain text file per world with one
biome id per line, written as a biome is discovered. Deleting a line gives that single biome its
reveal again, and deleting the folder starts over everywhere.

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

`./gradlew build` is the command the workflow runs for every enabled version.

## License

Licensed under the [GNU Lesser General Public License v3.0](LICENSE).

The project is based on [fallen's fabric mod template](https://github.com/Fallen-Breath/fabric-mod-template),
thanks to Fallen_Breath for it.
