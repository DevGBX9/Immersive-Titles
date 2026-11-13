# Build and release

## Requirements

| what | version | why |
| --- | --- | --- |
| JDK | **25** | `common.gradle` compiles the 26.x projects with `JavaVersion.VERSION_25` |
| Gradle | the wrapper (`./gradlew`) | the plugins and the preprocessor are pinned by it |
| Fabric Loader | 0.19.5 (build), `>=0.11.4` (declared) | `gradle.properties` / `fabric.mod.json` |
| Fabric API | `0.160.5+26.3`, `0.160.0+26.2`, `0.155.3+26.1.2` (build); `>=0.160.5`, `>=0.160.0`, `>=0.155.3` (declared) | required at runtime by the HUD and client events the titles use |
| Mod Menu | `21.0.0` (26.3), `20.0.3` (26.2), `18.0.2` (26.1.2) | **compile only**: the mod works without it |

The mod is built with JDK 25 and the build itself is expected to run on CI; nothing about the project
needs a local toolchain to be read, checked or edited.

## The version matrix

Which Minecraft versions exist as Gradle projects is decided in three places that have to agree:

1. **`settings.json`** — `versions` is the enabled build matrix, `disabled_versions` is kept for
   later. `settings.gradle` includes one Gradle subproject per enabled entry.
2. **`build.gradle`** — the `preprocess` block creates one node per version (`createNode`) and links
   them into a chain, so that the sources of a parent are preprocessed into its children. 26.3 is the
   root and 26.2 and 26.1.2 hang off it, one under the other, so the sources are written for 26.3
   with no `//#if` guard around the client code, and the two children are compiled from exactly those
   sources: the guards that do exist (`WandererTitlesClient`, `ModMenuIntegration`) mean "26.1.2 and
   newer", never "26.3 only", so no part of the mod is left out of any of the three versions.
3. **`versions/<version>/gradle.properties`** — per-version metadata: `minecraft_version`,
   `minecraft_dependency`, `game_versions` (what the release is published for),
   `fabric_api_version`, `modmenu_version`. `versions/mainProject` names the core project
   (`26.3`), whose sources are the root `src/` folder.

`common.gradle` is the build of every version project: Loom, the preprocessor, yamlang (the language
files), the license plugin, the jar, and the Fabric API / Mod Menu dependencies that are only added
for `mcVersion >= 260102`, i.e. for the three enabled 26.x projects.

### Re-enabling an older version

The steps are also written next to the commented nodes in `build.gradle`:

1. add the version to `versions` in `settings.json` and remove it from `disabled_versions`;
2. uncomment its `createNode(...)` line and its `link(...)` lines;
3. make sure it hangs off an **enabled** parent (a disabled version cannot be a parent);
4. check `versions/<version>/gradle.properties` and `versions/mainProject` if the core project changes;
5. port or guard the 26.x client code in `src/` with `//#if MC >= ...` blocks — the core sources are
   compiled as they are, so an older child needs the guards to keep compiling.

## Gradle tasks

```bash
./gradlew build                 # every enabled version project
./gradlew :26.3:build           # one version
./gradlew :26.2:build           # one version
./gradlew :26.1.2:build         # one version
./gradlew buildAndGather        # build, then copy the mod jars into build/libs of the root
```

The license plugin runs `licenseFormatMain` before `classes`, so a Java file without the MIT header in
`HEADER.txt` is rewritten during the build. The jar carries `LICENSE`, and its name is
`immersive_titles-v<mod_version>.jar` (the Minecraft version is the build matrix's business, not the
player's).

## GitHub workflows

| workflow | when | what it does |
| --- | --- | --- |
| `.github/workflows/gradle.yml` — **Dev Builds** | push to `*.gradle`, `gradle.properties`, `src/**`, `versions/**`, `.github/**`; every pull request | calls `build.yml` with the default target (all enabled versions) |
| `.github/workflows/build.yml` | called by the two above | JDK 25 (Temurin), Gradle cache, prints the version matrix into the run summary, runs `./gradlew build` (or `:26.3:build` for a target), uploads `build-artifacts` and the collected `mod-jars`, logs a notice per built jar; a `summary` job downloads the artifacts and renders `.github/workflows/scripts/summary.py` |
| `.github/workflows/dev-jars.yml` — **Dev jars** | after every completed **Dev Builds** run on `main` | downloads that run's `mod-jars` artifact, renames each jar to `<name>-mc<version>.jar` and attaches them all to the `dev-latest` prerelease, so each version has a public download link. It cannot be a job of `build.yml`: an asset of a release is a plain link, while an artifact of a run needs a signed-in GitHub account |
| `.github/workflows/matrix_prep.yml` | called by `release.yml` | runs `scripts/matrix.py` to turn `settings.json` into a job matrix |
| `.github/workflows/release.yml` — **Release** | a published release, or a manual dispatch with a release tag | validates the tag, builds with `release: true`, then publishes each version project's jar as an asset of that release (`Kira-NT/mc-publish`). The Modrinth and CurseForge blocks are commented out, so only GitHub is targeted today |

The Modrinth/CurseForge publication is one uncommented block each in `release.yml` plus the matching
secrets; the changelog, file name and SHA-256 that go with them are already generated by the job.

## JitPack

`jitpack.yml` selects Java 25 before the build, which is what the 26.x sources need.
