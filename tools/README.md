# Development tools

Everything in this folder is a script that reads the project and reports on it, or that draws what
the game draws so it can be looked at. None of it takes part in the build: the mod is built from
`src/` by Gradle and the GitHub workflow, and these scripts only read sources, textures and the
Minecraft client jar.

## Layout

| folder | what lives there |
| --- | --- |
| `checks/` | static checks: each one holds a fact written into the sources to the source it is read from, and fails loudly when the two disagree |
| `previews/` | pages that draw the titles and the collection screen the way the mod draws them, plus the measurements the drawing is built on |
| `previews/mock/` | the layout mocks of the collection screen |
| `assets/` | the tools that produced, and can regenerate, the art the mod ships |

## Running

Run everything from the repository root: the checks read `src/...`, `gradle.properties` and
`settings.json` by relative path.

```bash
python tools/checks/check_java_shape.py
python tools/previews/preview_title.py
```

Dependencies, only for the scripts that need them:

| package / tool | used by |
| --- | --- |
| `Pillow` | the previews, `check_ornaments`, `check_forget_button`, and the asset tools |
| `numpy` | `tools/assets/*` (the frame and drawing tools) |
| `zopfli` | `tools/assets/title_plate/shrink_frames.py` |
| `ffmpeg` / `ffprobe` on `PATH` | `tools/assets/title_plate/strip_bg.py` and `verify_frames.py` |

```bash
pip install pillow numpy zopfli
```

A few scripts read the Minecraft client jar out of the local loom cache
(`~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar`) and say so at the top of the file. They
need a development environment that has resolved the game at least once; the others run on a bare
checkout.

## Checks

| script | what it holds to what |
| --- | --- |
| `check_java_shape.py` | every Java source is structurally balanced |
| `check_lang_keys.py` | the language file and the keys the sources ask for are the same set |
| `check_structures.py` | the structures the collection lists, against the game the build is against |
| `check_dimension_groups.py` | the dimensions the collection divides its list by, against the game's own tags |
| `check_place_rule.py` | the rule the automatic titles follow, against a table of walks |
| `check_discovery_paths.py` | the file a world writes its discoveries to is the file the collection reads |
| `check_structure_visits.py` | how a structure is counted as a visit, and what its end is |
| `check_gaze_reveal.py` | the look trigger: a structure the line of sight of a player reaches from outside |
| `check_forget_reveal_again.py` | the path a forgotten discovery takes back to being discovered again |
| `check_title_names.py` | what the titles make of the names the game gives the biomes |
| `check_block_balance.py` | where the collection screen sets its block, against the numbers it is built of |
| `check_gui_sprites.py` | the gui sprites the screen names, against the files it reads them from |
| `check_menu_background.py` | the collection is drawn over the game's own menu background |
| `check_ornaments.py` | the two drawings that frame the list, against the screen that draws them |
| `check_filter_faces.py` | the two filter buttons: which filter is on, and which of them the mouse is on |
| `check_forget_button.py` | the button a row offers to forget a discovery with, against the picture it was asked from |
| `check_screen_switch.py` | the mod changes screens the way the game changes its own |

## Previews and measurements

| script | what it draws or measures |
| --- | --- |
| `previews/preview_font.py` | the game's own font, read out of the client jar; shared by the previews |
| `previews/preview_title.py` | the two titles as the mod draws them, plus their measurements |
| `previews/preview_collection.py` | the parts of the collection screen that were changed, as the game draws them |
| `previews/measure_plate_box.py` | the hollow middle of the plate, measured on the real frames |
| `previews/measure_small_title.py` | the small title of the reference picture the corner title is shaped after |
| `previews/mock/build_mock.py` | the layout mock of the collection screen, as one self-contained page |
| `previews/mock/build_frames.py` | the collection block drawn in the game's own window sprite, three ways |

The pages these write are ignored by git (see `.gitignore`); the frames they read are the ones the
mod ships.
