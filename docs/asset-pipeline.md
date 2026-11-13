# Art pipeline

Everything the mod draws that is not text was produced outside the build and is committed under
`src/main/resources/assets/immersive_titles`. The tools that produced it, and the notes on what each
one did, live in `tools/assets/`.

## The reveal frame set

`src/main/resources/assets/immersive_titles/textures/title/frame_0001.png` … `frame_0187.png`

| | |
| --- | --- |
| frames | 187, at 1440 x 720, 30 fps (6.23 s) |
| format | RGBA, RGB is pure white, alpha carries the plate's luminance |
| size on disk | ≈5.2 MB in total (≈28.5 KB a frame, losslessly recompressed) |
| source | `theanimation.mp4` (201 frames, 30 fps); frame N of the set is source frame N+8 |

The frames are white-and-alpha on purpose: the plate glows over the world instead of covering it with
a black rectangle, and since RGB carries no colour there is no dark fringe at any alpha. The
background of the render (88% of every frame) is not copied into alpha as-is — the render's "black"
carries encoder ringing, so `tools/assets/title_plate/strip_bg.py` applies a dead zone
(`DEAD = 2`, `KNEE = 8`) and copies every visible sample bit for bit.

`tools/assets/title_plate/README.md` has the numbers and the proof:

```bash
python tools/assets/title_plate/strip_bg.py "path/to/theanimation.mp4"     # writes frames/
python tools/assets/title_plate/verify_frames.py "path/to/theanimation.mp4" # compares every sample
python tools/assets/title_plate/shrink_frames.py --apply                    # lossless recompress
```

`verify_frames.py` rebuilds the expectation from the video and compares all 187 x 1440 x 720 samples;
`shrink_frames.py` recompresses each frame to a palette PNG and refuses to write a file whose pixels
differ. `preview.html` in the same folder shows the set over dark green and white.

### Where the name goes

The plate is symmetric and leaves its middle empty; the name is drawn there at draw time, never baked
into the frames.

| | |
| --- | --- |
| plate middle | column **720**, row **269.5** (measured on the frames) |
| room between the ornaments | rows 197 … 342 |
| the room a name may fill in the mod | `NAME_ROOM_WIDTH` 807 x `NAME_ROOM_HEIGHT` 120, capped at 0.94 / 0.70 of it |
| rows cropped off in the draw | `FRAME_CROP` = 9 at the top and the bottom (a bright rind from the flash) |

`tools/previews/measure_plate_box.py` re-measures the empty middle, and
`tools/previews/preview_title.py` draws a name in it exactly as `TitleOverlay` does.

## `tools/assets/biome_title/` — the earlier template

The first attempt at a reusable plate: a word-free version of the original 1920 x 960 clip, with the
`PALE GARDEN` lettering erased and its box left empty, so a name could be laid over it (1920 x 960,
word box x 432 … 1508, y 264 … 424, `apply_word.py`, `build_template.py`, `envelope.json` with the
per-frame gain of the original word). It is kept for comparison; the mod ships the `title_plate` set
above, which never had a word in it.

## The lines that frame the collection

`src/main/resources/assets/immersive_titles/textures/gui/collection_up_line.png` (228 x 22) and
`collection_down_line.png` (140 x 18) are drawn by

```bash
python tools/assets/collection_frame/draw_ornaments.py
```

at exactly the size the screen draws them at: one texture pixel is one gui pixel, no scaling in the
game and none in the script. The drawing is a needle with a thread through it, mirrored left to right,
and the width of the upper one is the width of the strip of the screen's two tabs, so the plate and
the bar of two pages are one thing. `tools/checks/check_ornaments.py` reads the two textures back and
holds them to their size, their thinness and that mirror.

`tools/assets/collection_frame/cut_frames.py` and the two sheets beside it (`Up-line.png`,
`Down-line.png`) are the earlier, denser drawing and the cut that made it. Nothing ships from them
any more; they are kept because they are the drawings the two above were made to replace.

## Gui sprites

The sprites under `textures/gui/sprites/` (buttons, the window, the experience bar, the social
interactions background) are copied from the *Reimagined GUI - Dark* resource pack with the
permission of its author; `textures/gui/sprites/CREDITS.md` is the record of that. They are read by
`BiomeCollectionScreen` exactly as the game reads its own sprites, nine slices and `.mcmeta` files
included, and `tools/checks/check_gui_sprites.py` holds the names the screen asks for to the files
that are there.

## What guards all of this

| check | what it holds |
| --- | --- |
| `tools/checks/check_ornaments.py` | the two framing drawings: size, thinness, mirror |
| `tools/checks/check_gui_sprites.py` | the sprite names in the screen against the shipped files and their hashes |
| `tools/checks/check_menu_background.py` | the collection is drawn over the game's own menu background |
| `tools/checks/check_title_names.py` | the names of every biome, drawn in the plate with the game's font |
| `tools/checks/check_filter_faces.py`, `check_forget_button.py` | what the screen draws over the game's own buttons and row controls |
