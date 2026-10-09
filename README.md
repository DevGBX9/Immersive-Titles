# Wanderer's Titles

[![Dev Builds](https://github.com/DevGBX9/Wanderer-s-Titles-/actions/workflows/gradle.yml/badge.svg)](https://github.com/DevGBX9/Wanderer-s-Titles-/actions/workflows/gradle.yml)
[![License](https://img.shields.io/github/license/DevGBX9/Wanderer-s-Titles-.svg)](https://www.gnu.org/licenses/lgpl-3.0.html)

Displays cinematic biome titles when entering a biome, with a large first-visit title and a compact title on later visits.

> **Status: work in progress.**
> Two title reveals are implemented and playable on 26.3: the cinematic reveal and a programmatic
> corner title. The biome triggered titles described below are still **not implemented yet**.

## What works right now

Both reveals live in the **Wanderer's Titles** category of the controls screen, so both can be
rebound there.

| Key | Title | Behaviour |
| --- | --- | --- |
| `T` | cinematic reveal | Plays the 187 frame clip as a full screen overlay, transparent over the world, in real time for about 6.2 seconds, then removes itself. `T` is also the vanilla chat key, so rebind one of the two if the reveal should run without opening the chat. |
| `K` | corner title | Draws `pale garden` with the game font in bold in the lower left corner. It fades in, holds and fades out in about 2.9 seconds, and it uses no texture at all. |

## What the mod is meant to do

Wanderer's Titles is planned as a client side mod that treats every biome like a discovered location:

- **First discovery of a biome** - a large cinematic title card is presented in the middle of the screen,
  with elegant ornaments and its own multi step animation: an entrance, a slight overshoot, a settle,
  a short readable hold and an animated exit. It should feel like a location reveal instead of an
  ordinary HUD notification.
- **Later visits** - when the player leaves an already discovered biome and enters it again, the biome
  name is shown as a much smaller title near the lower-left corner, with a shortened animation.
- **Triggering** - a title is shown when the player's biome actually changes, not continuously while
  standing inside the same biome. Leaving and re-entering a biome triggers it again.
- `minecraft:plains` is planned as the first experimental biome. The architecture is meant to be
  extended to all biomes later.

The intended visual language of the title card is a near-black composition with bright white uppercase
display type and symmetrical, thin ornaments above and below the biome name. All fonts and decorative
assets shipped with the mod are original work of this project.

## Minecraft versions

| Minecraft | Status |
| --- | --- |
| **26.3** | enabled - core project and active development target for the Wanderer's Titles feature |
| **1.20.1** | disabled for now, configuration kept so it can be re-enabled later |
| all other versions | disabled for now, configuration kept so they can be re-enabled later |

- `settings.json` contains the enabled build matrix (`versions`) and the versions that are currently
  disabled (`disabled_versions`).
- `build.gradle` contains the preprocessor node definitions, including the commented out definitions of
  the disabled versions, together with the steps needed to re-enable one.
- `versions/mainProject` names the core project, i.e. the version whose sources are the root `src/` folder
  and from which the other enabled versions are derived.

## Building

The authoritative build environment is GitHub Actions: every push is built there through the
[Dev Builds workflow](https://github.com/DevGBX9/Wanderer-s-Titles-/actions/workflows/gradle.yml).
The build matrix only contains the enabled versions listed above, and the enabled/disabled matrix is
printed into the run summary of every build.

`./gradlew build` is the command the workflow runs for every enabled version.

## License

Licensed under the [GNU Lesser General Public License v3.0](LICENSE).

The project is based on [fallen's fabric mod template](https://github.com/Fallen-Breath/fabric-mod-template),
thanks to Fallen_Breath for it.
