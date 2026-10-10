# Biome title template (word free)

`frames/` holds the 187 frame cinematic biome title reveal **with the baked word removed**, so the
same reveal can be used for any biome name. The clip is the one the mod plays for `PALE GARDEN`;
only the lettering is gone, and only inside the box listed below.

| | |
| --- | --- |
| frames | `frame_0001.png` .. `frame_0187.png` (187 files) |
| size | 1920 x 960 |
| channels | RGBA, RGB is always white, alpha carries the intensity |
| frame rate | 30 fps, so the clip runs 6.23 s |
| total size | 14.7 MB (lossless PNG, alpha only) |
| first frames | completely empty, the clip starts on black |

## Geometry

All coordinates are in the 1920x960 frame space, top left origin.

| element | x | y |
| --- | --- | --- |
| top ornament | 712 .. 1215 | 188 .. 254 |
| **word box** (the cleared area) | 432 .. 1508 | 264 .. 424 |
| ... where the lettering was | 457 .. 1463 | 291 .. 400 (cap height 110) |
| bottom ornament | 587 .. 1325 | 451 .. 568 |

Put a new word inside the **word box**: it is 1076 x 160 (centre 970, 346), keeps ~10 px of
clearance from the lettering, and leaves the ornaments alone. A word that fills the box sits
exactly where `PALE GARDEN` sat; the name of the original title is 1007 x 110 at cap height 110.

## Timeline (frame numbers, 1 based)

| frames | what is on screen |
| --- | --- |
| 1 .. 9 | black plate, the first sparkles drift in |
| 10 .. 11 | the blinding flash, the whole frame is saturated |
| 12 .. ~40 | the afterglow dome fades, the ornaments and the word are on the black plate |
| ~40 .. 158 | title held, sparkles drifting |
| 159 .. 173 | the word fades out (the ornaments stay) |
| 174 .. 187 | only the ornaments and the sparkles, then the frame fades to black |

The word's own intensity per frame is in `envelope.json` (measured on the source clip, 187 values
normalised to the peak at frame 11): 0 through frame 9, 0.79 on frame 10, 1.0 from frame 11,
slightly dimming to 0.95 while the afterglow decays, then 0.88, 0.74, 0.62, 0.50, 0.40, 0.32,
0.25, 0.20, 0.15, 0.11, 0.08, 0.08, 0.05, 0.03 over frames 159 .. 173 and 0 after that.

## How `frames/` was produced

`build_template.py` rebuilds it from the word baked frame sequence. Per frame:

1. the harmonic continuation of the frame around the word box is computed in closed form (sum of
   two sine series solving Laplace's equation) - smooth, and it never overshoots the boundary, so a
   black plate stays black and a bright flash frame stays a smooth bright dome;
2. the lettering mask is built: pixels that are ink bright in most frames of the steady window
   (thin serif strokes stay, drifting sparkles drop out) plus whatever is brighter than that
   continuation nearby, which adds the bloom that hugs the letters in the reveal frames;
3. only the masked pixels are rewritten, by diffusing the harmonic seed inwards while the rest of
   the frame is pinned back to its original values, so the flash streaks, the ornaments and every
   sparkle outside the box stay exactly as shot;
4. the patch is feathered back in over a couple of pixels.

```bash
python build_template.py --input <folder with the word baked frames> --output frames
```

It needs `numpy` and `Pillow`. `--box` overrides the y0,y1,x0,x1 word box and `--window` the steady
window used to learn the lettering mask, if a different source clip is used.

What came out of it, on all 187 frames:

* outside the word box plus a 47 px band every frame is **bit identical** to the source, so the
  ornaments, the sparkles and the flash streaks are exactly as shot;
* inside the box the only thing that changed is the lettering and the bloom hugging it (the
  removal mask is everywhere within ~40 px of the letters, never further), the min composite over
  the steady window - which is what would show a ghost - peaks at **1/255** over the lettering and
  2/255 anywhere in the box;
* in the flash frames the cleared box is a smooth dome built from the frame around it, within a few
  percent of the source while the screen is saturated anyway, and in the last frames the box plate
  is within 2/255 of the plate around it - both far below anything visible.

## Putting a word in

In an editor: drop the frames in as a sequence and lay any word over the word box.

With the script here, which also applies the fade of the source clip:

```bash
python apply_word.py --text "MUSHROOM FIELDS" --font C:/Windows/Fonts/arialbd.ttf --out out
python apply_word.py --word-image my_word.png --out out          # pre rendered RGBA word
python apply_word.py --text TEST --only 10,60,100,170 --out out  # a few frames only
```

`--text` renders the word with a TrueType font, `--word-image` takes any RGBA png, and both are
scaled so the ink is `--glyph-h` (default 110) tall - width capped to the box - and centred on
`--center` (default 970,346), so any word lands where the original one sat. The word is drawn white
and alpha blended over the frame with the per frame gain from `envelope.json`; it needs `numpy` and
`Pillow`.

In the mod: the frames are the word free half of a biome title. Draw the biome name over the box
each frame with alpha `gain[i]` from `envelope.json` and the frames supply the entrance, the
ornaments and the exit.
