# The lines that frame the biome collection

`Up-line.png` and `Down-line.png` are the two drawings the collection screen is framed with: one is
drawn over the list and one under it, across its width. Together with the list between them they read
as a collection rather than as a menu of things to press, which is the one place in that screen that
is the mod's own drawing rather than the game's own widgets.

They are original work of this project, white line art on a flat dark sheet. Both sheets are kept here so
that the texture the mod ships can always be remade from them:

```
python templates/collection_frame/cut_frames.py
```

The script writes

- `assets/immersive_titles/textures/gui/collection_up_line.png`, 880x44, and
- `assets/immersive_titles/textures/gui/collection_down_line.png`, 880x52,

and the screen draws each of them 22 or 26 gui pixels tall - half of what is written, so that a game
at gui scale 2 draws them one to one and a game at gui scale 1 reads them through an area filter.

What it does with a sheet is worth knowing before changing it: the sheet is not simply made
transparent, it is measured. The art is white on a sheet of one grey, so how much of a pixel is art
is how far that pixel's brightness sits between the two - the antialiasing of the drawing comes back
as the alpha of its edges, exactly as much of it as was covered. The sheet's own noise, and the ring
a JPEG leaves around every strong edge, are both a few percent of that and are dropped before
anything is scaled, so they cannot be baked into the line. Then the drawing is cut down to the
rectangle the art itself takes up and scaled, with an area filter, to the size above.
