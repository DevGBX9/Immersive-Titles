# title_plate - the biome title reveal, with no word baked in

The ornamental plate and its reveal animation, rendered clean and stripped of every
last bit of background. This is the base the mod animates and writes the biome name
on top of; `templates/biome_title/` holds the earlier attempt, where the word had to
be erased out of an existing render, and stays only for comparison.

| | |
|---|---|
| frames | `frames/frame_0001.png` .. `frame_0187.png` (187) |
| size | 1440 x 720, 30 fps, 6.23 s |
| format | RGBA PNG, RGB = pure white, alpha = the plate's luminance |
| on disk | 7.7 MB, 42.3 KB per frame |
| source | `theanimation.mp4` (201 frames, 30 fps); frame N here is source frame N+8 |

The render itself lives outside the repo, so the paths below are where it sat when the
frame set was written; point `strip_bg.py` at wherever it is now to regenerate.

## The format

The plate is white and achromatic - measured over every bright pixel of the source,
the widest channel spread is 8 of 255, which is compression damage rather than
colour. So the colour channels carry nothing and are written as pure white, and all
of the signal lives in alpha. The mod draws the frame tinted with the biome colour,
and since RGB is white there is no such thing as a dark fringe at any alpha: the
classic halo around semi transparent edges cannot happen.

## Why the background is not simply `alpha = luminance`

The source is 8 bit 4:2:0 h264, so its "black" is not 0. The dark plateau carries
1 to 2 of ringing - 88% of all pixels of the clip sit at or below 2 - and empty
corners measure a mean of 0.13 to 0.36. Copied straight into alpha that becomes a
grey veil over the game, and one that flickers, because the ringing changes every
frame.

`strip_bg.py` therefore applies a dead zone:

```
alpha = 0                                    luma <= DEAD   (2)
alpha = KNEE * (luma - DEAD) / (KNEE - DEAD) DEAD < luma < KNEE (8)
alpha = luma                                 luma >= KNEE
```

At or above `KNEE` every sample is copied bit for bit, so everything that is visible
is lossless; only 1 to 3% opacity noise under `DEAD` disappears, and the short ramp
in between keeps the falloff smooth instead of clipping it. Nothing is resampled,
nothing is scaled, no colour conversion happens beyond the luma weights.

## Regenerating and checking

```
python strip_bg.py "C:/Users/GBX/Desktop/theanimation.mp4"        # writes frames/
python verify_frames.py "C:/Users/GBX/Desktop/theanimation.mp4"   # numbers + sheet.png
python -m http.server 8731 --bind 127.0.0.1                        # then open preview.html
```

`verify_frames.py` rebuilds the expectation from the video and compares all
187 x 1440 x 720 = 193,881,600 samples of the frame set against it:

```
alpha != round(ramp(v))  : 0  (max diff 0)
RGB != pure white        : 0
  of which >= knee       : 18,745,964 samples copied exactly
  dropped as noise       : 171,321,610 samples (<= 2), 88.36% of all pixels
still lit                : 22,557,606 (11.63%)
steady frame 92 corner   : source max 1 -> alpha max 0
flash frame 12           : source mean 58.17 -> alpha mean 58.17
```

So the frame set is a pure, checkable function of the render: 88.36% of all pixels
become fully transparent, the rest is the render itself.

Beyond that:

* nothing survives more than 40 px away from the plate: pooling the frames into
  40x40 cells and keeping the cells whose 40 px neighbourhood holds no content at
  all, the highest alpha over every such cell of every one of the 187 frames is
  exactly 0,
* there is no flicker left in the background: through the steady stretch (frames 49
  to 133) the mean inter frame difference is 0.01 to 0.06 of 255, produced only by
  the drifting sparkles,
* the opening flash (frames 4 to 15) lights the entire frame radially and is copied
  exactly as rendered (`source mean 58.17 -> alpha mean 58.17`), so there is no far
  band to test on those frames. A washed out full frame there is the animation, not
  a leftover background. A rectangle would look different: the 40 px block map of
  frame 13 is a smooth centred ellipse decaying to the corners.

`sheet.png` and `proof.png` are the by eye checks: `sheet.png` shows 14 frames over
dark green and over a checkerboard, `proof.png` shows key frames over white, where a
leftover black plate would be impossible to miss, plus a 1:1 crop of a plate edge and
the same crop as pure alpha.

## Where a name goes

The plate is symmetric and leaves its middle empty. The box below is the area the word
was cleared from in the 1920 x 960 render of `templates/biome_title/`, scaled by 0.75,
which is the exact scale factor between the two renders:

| | |
|---|---|
| word box | x 324 .. 1131, y 198 .. 318 (1440 x 720) |
| box centre | (727.5, 258) |
| ink height | 82-84 px, to match the 110 px of the 1920 x 960 render |

The new frames confirm the clearing: in a 90 frame average of the steady stretch the
solid ornament occupies rows 147 to 196 and rows 342 to 430, so the box top lands
exactly on the gap, and the whole interior carries nothing above 27 of 255 - drifting
sparkle trails only.

**The box is not the middle of the plate.** It is where the original word happened to
sit, and that word sat ten pixels right of the middle of the 1920 x 960 render and
about twelve above the middle of the room between the ornaments - the box centre is
the box's own centre, not the plate's. Measured on these frames, the plate's middle is
column **720** and row **269.5**: the middle stroke of each ornament is drawn at
720.00 and 720.39 down the frames, and the room the two leave between them is rows 197
to 342. That is where a name is centred (`NAME_CENTER_X` / `NAME_CENTER_Y` in
`TitleOverlay`), and a name drawn around the box centre instead lands 7.5 pixels right
of the plate's middle and 11.5 pixels above the middle of its room.

## The in game budget

Playback is 30 fps, so 33 ms per frame, and the frame set is 7.7 MB on disk with a
42 KB average frame that decodes in ~10.6 ms. The one thing to avoid is uploading all
of it: 187 frames of 1440 x 720 RGBA is 4.0 MB each, 774 MB of texture. The plan
that keeps the quality identical and the frame time untouched is to keep the PNGs as
they are and decode ahead: a worker thread holds two or three decoded frames, which
is 8-12 MB, and the renderer only ever draws an already decoded texture, so the draw
path never blocks on IO or on zlib. Any tiling or pre scaling that packs the set into
one texture would mean giving up resolution, and there is no need for it.
