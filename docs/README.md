# Documentation

| file | what it covers |
| --- | --- |
| [`architecture.md`](architecture.md) | what the mod does and how the pieces fit: titles, place detection, structures, per-world memory, the collection screen |
| [`build-and-release.md`](build-and-release.md) | the version matrix, the Gradle layout, the GitHub workflows and what each one produces |
| [`asset-pipeline.md`](asset-pipeline.md) | the frame set the reveal plays, the drawings that frame the collection, and the tools that made them |
| [`version-support.md`](version-support.md) | the version matrix, the package map ordered by how much of the game each package touches, and how to enable an older version |
| [`../tools/README.md`](../tools/README.md) | the checks and previews, what each one holds to what, and how to run them |

## Repository map

```
.
├── .github/                  workflows (build, release) and issue templates
├── docs/                     these notes
├── gradle/, gradlew(.bat)    the Gradle wrapper
├── src/main/java/            the mod: com.devgx9.wanderers_titles
│   └── …/client/             entrypoints, title/, collection/, catalog/, world/
├── src/main/resources/       fabric.mod.json and the assets (lang, sounds, textures, gui sprites)
├── tools/                    checks, previews, and the tools that made the shipped art
├── versions/                 one Gradle project per Minecraft version (only 26.3 is enabled)
├── build.gradle              the version matrix (preprocessor nodes)
├── common.gradle             what every version project is made of
├── settings.gradle           reads settings.json and includes the enabled versions
├── settings.json             the enabled and the disabled version lists
├── gradle.properties         mod id, name, version, loader version
└── HEADER.txt, LICENSE       the MIT header the license plugin applies, and the license
```

A file's home follows from what it is: Java that ships goes under `src/main/java`, anything the game
loads goes under `src/main/resources`, anything that only *reads* the project goes under `tools/`,
and anything that only explains it goes under `docs/`. `versions/` and the Gradle files at the root
are where the preprocessor plugin expects them, so they stay where they are.
