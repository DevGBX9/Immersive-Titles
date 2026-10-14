# The lines that frame the biome collection

`collection_up_line.png` and `collection_down_line.png` - here in the mod's
`assets/immersive_titles/textures/gui` - are the two drawings the collection screen is framed with:
one drawn over the list and one under it, so that the list between them reads as a collection rather
than as a menu of things to press. The one over the list is the plate the screen's two tabs stand
under, and it is as wide as the strip the two of them make; the one under it is the foot of the
block, drawn smaller. They are original work of this project, and what they are a drawing of is a
needle with a thread through it: the needle is what the line is hung on, standing in the middle of
it, and the thread is what carries the line out of the middle to the ends of it, ending in a loop.

``` 
python templates/collection_frame/draw_ornaments.py
```

That script writes both textures and is the whole of where they come from. It draws them in pixels, at
exactly the size the screen draws them at, and what it writes is what ships: one texture pixel is one
gui pixel of the screen, with no scaling in the game and no scaling in the script.

The sizes are therefore the drawings, and they are the two numbers that decide how much of the screen
the frame of the list costs:

| file | drawn at | before |
| --- | --- | --- |
| `collection_up_line.png` | 228 x 22 | 228 x 30 |
| `collection_down_line.png` | 140 x 18 | 162 x 26 |

The width of the one over the list is not free: it is the width of the strip of the two tabs the
screen draws under it (see `TAB_STRIP_WIDTH` in the screen), so the plate and the bar of two pages are
one thing. The heights are what the drawings cost the block - 27% and 31% shorter than the drawings
they replace - and the pair is deliberately not the same size twice: one is the head of the block and
the other its foot.

## Why they are drawn where they are read, and not cut down to it

They used to be line art of a dense kind, drawn large on a sheet and cut down to the size of the
screen. That is what a drawing of thick strokes can afford: it is measured off its sheet, scaled with
an area filter, and what a fifth of a pixel of a thick stroke loses nobody sees. A drawing of
one-pixel threads cannot be treated that way at all. A thread is a pixel wide, so a share of the
drawing is a share of a pixel and nothing else - a third of a stroke, which is not a thinner thread
but a grey haze. That is the whole of what went wrong with the drawings these replace: about two
fifths of each of them was ink, and at the size the screen draws them at that ink read as a white
blur that competed with the list it framed rather than as a frame. These two carry about a twentieth
of their box in ink instead, and every stroke of them is a stroke of the screen.

## What the script draws

One motif, twice, from the same function of one size - so the head and the foot of the block are the
same thing at two sizes rather than two drawings that have to be kept alike:

- the needle: a head of six pixels with a two-pixel eye through the middle of it, a shaft under the
  head, and a point at the end of it;
- the thread: out of the eye, one dip and one rise on the way out, and a loop at the end of the line;
- three barbs along the way, each a short hook turned outwards - what a thread caught on something
  looks like, and what keeps the line from reading as a ruled curve.

Every shape is drawn from the middle of the drawing outwards, and the left half of it is the right
half in a mirror: `_inspect/check_ornaments.py` reads the two textures back and holds them to that, to
their size, and to being thin - no mark wider than the head of the needle anywhere in either of them.

## The drawing they replace

`Up-line.png`, `Down-line.png` and `cut_frames.py` are the earlier drawing and the cut that made it:
an ornament of dense line art, cut out of its sheet and scaled down to the size of the screen, which
is the drawing the two above were made to replace. They are kept here because they are the drawings
they are, and the script still cuts them - but nothing ships from them any more, and running it would
put the earlier, larger art back over the two textures the mod draws. The sizes it writes were those
drawings' own: 396 x 52 and 362 x 58, drawn at 58% and 46% of themselves.
