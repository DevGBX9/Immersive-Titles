# The lines that frame the biome collection

`Up-line.png` and `Down-line.png` are the two drawings the collection screen is framed with: one is
drawn over the list and one under it, across its width. Together with the list between them they read
as a collection rather than as a menu of things to press. They are the screen's drawings of this kind;
the line that opens each section of the list is the mod's own as well, but it is written with the
game's own font in the game's own colours rather than cut out of a sheet, so there is nothing of it in
here.

They are original work of this project, white line art on a flat dark sheet. Both sheets are kept here so
that the texture the mod ships can always be remade from them:

```
python templates/collection_frame/cut_frames.py
```

The script writes

- `assets/immersive_titles/textures/gui/collection_up_line.png`, 396x52, and
- `assets/immersive_titles/textures/gui/collection_down_line.png`, 362x58,

and the screen draws neither of them at the whole of that: the line over the list is drawn at two
fifths of it, 152x20 gui pixels, and the one under it at a little under half, 162x26. Neither share is
a whole number of the drawing's own pixels - 52 of the up line come to 20, 58 of the down line to 26 -
so the game reads both through its area filter rather than one to one, at gui scale 1 (twenty gui
pixels out of fifty-two pixels of texture) and at gui scale 2 (forty out of the same fifty-two) alike.

The line under the list is the larger of the two because it is the foot of the collection: the one over
the list is a frame and stays out of the way of the list itself, while the drawing under it is what a
player is left looking at once the list has been read.

The size is a decision about the drawing, not about the room it is given. Both lines are drawn
**centred** over and under the list, at their own proportions, and neither is stretched to reach the
ends of the list: widening a drawing of a thing to fit a wider list does not make a longer line, it
makes a squashed one, with every ornament in it flattened by the same amount. If a line looks too
small or too large for the list, the number to change is the height it is drawn at - here and in the
screen's `UP_LINE_HEIGHT`/`DOWN_LINE_HEIGHT` - and the width follows from the drawing.

Those heights are 20 and 26 gui pixels, and they are as small as these drawings can be read at. These
are dense drawings - about two fifths of the rectangle each takes up is ink, and their finest strokes
are a hundredth of their own height - so the whole of the shape has to be given the room to resolve
back out of that box. At half of those heights - ten gui pixels out of the fifty-two the up line is
drawn from, thirteen out of the fifty-eight of the down line - the thin parts survive only as a few
percent of coverage, which is a grey haze over a bright sky, and what is left is the boldest third of
the drawing: a white smear where an ornament should be. The cost is paid by the list, which is a row
or so shorter.

What it does with a sheet is worth knowing before changing it: the sheet is not simply made
transparent, it is measured. The art is white on a sheet of one grey, so how much of a pixel is art
is how far that pixel's brightness sits between the two - the antialiasing of the drawing comes back
as the alpha of its edges, exactly as much of it as was covered. The sheet's own noise, and the ring
a JPEG leaves around every strong edge, are both a few percent of that and are dropped before
anything is scaled, so they cannot be baked into the line. Then the drawing is cut down to the
rectangle the art itself takes up and scaled, with an area filter, to the size above - and that size
is at the drawing's own proportions, so nothing is stretched on the way.
