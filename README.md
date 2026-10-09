# Wanderer's Titles

[![Dev Builds](https://github.com/DevGBX9/Wanderer-s-Titles-/actions/workflows/gradle.yml/badge.svg)](https://github.com/DevGBX9/Wanderer-s-Titles-/actions/workflows/gradle.yml)
[![License](https://img.shields.io/github/license/DevGBX9/Wanderer-s-Titles-.svg)](https://www.gnu.org/licenses/lgpl-3.0.html)

Displays cinematic biome titles when entering a biome, with a large first-visit title and a compact title on later visits.

> **Status: work in progress.**
> This project is still the prepared mod template: the cinematic biome title system is
> **experimental and not implemented yet**, so there is nothing to see in game at the moment.

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
| **26.3** | enabled - active development target for the biome title feature |
| **1.20.1** | enabled - core project, builds the shared template code without the feature |
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
