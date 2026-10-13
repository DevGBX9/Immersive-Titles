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

- `assets/immersive_titles/textures/gui/collection_up_line.png`, 548x72, and
- `assets/immersive_titles/textures/gui/collection_down_line.png`, 500x80,

and the screen draws each of them at half of that - 274x36 gui pixels over the list, 250x40 under it
- so that a game at gui scale 2 draws them one to one and a game at gui scale 1 reads them through an
area filter.

The size is a decision about the drawing, not about the room it is given. Both lines are drawn
**centred** over and under the list, at their own proportions, and neither is stretched to reach the
ends of the list: widening a drawing of a thing to fit a wider list does not make a longer line, it
makes a squashed one, with every ornament in it flattened by the same amount. If a line looks too
small or too large for the list, the number to change is the height it is drawn at - here and in the
screen's `UP_LINE_HEIGHT`/`DOWN_LINE_HEIGHT` - and the width follows from the drawing.

Those heights are 36 and 40 gui pixels, and they are large on purpose. These are dense drawings -
about two fifths of the rectangle each takes up is ink, and their finest strokes are a hundredth of
their own height - so the whole of the shape has to be given the room to resolve back out of that
box. At twenty pixels tall the thin parts survive only as a few percent of coverage, which is a grey
haze over a bright sky, and what is left is the boldest third of the drawing: a white smear where an
ornament should be. The cost is paid by the list, which is a row or so shorter.

What it does with a sheet is worth knowing before changing it: the sheet is not simply made
transparent, it is measured. The art is white on a sheet of one grey, so how much of a pixel is art
is how far that pixel's brightness sits between the two - the antialiasing of the drawing comes back
as the alpha of its edges, exactly as much of it as was covered. The sheet's own noise, and the ring
a JPEG leaves around every strong edge, are both a few percent of that and are dropped before
anything is scaled, so they cannot be baked into the line. Then the drawing is cut down to the
rectangle the art itself takes up and scaled, with an area filter, to the size above - and that size
is at the drawing's own proportions, so nothing is stretched on the way.
