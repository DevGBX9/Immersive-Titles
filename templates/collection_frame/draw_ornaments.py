#!/usr/bin/env python3
"""Draws the two lines that frame the biome collection: a needle with a thread through it.

The two drawings the screen frames its list with are made here, in pixels, at exactly the size the
screen draws them at - 228 x 22 gui pixels over the list and 140 x 18 under it - and they are written
straight into the mod's textures as white with an alpha, one texture pixel to one gui pixel. That is
the whole of what changed about them: the drawing before this one was a sheet of dense line art, cut
out of its background and scaled down to the size the screen draws it at, and a drawing of that kind
has to carry about two fifths of its own box in ink to survive the scaling at all - which is what made
it read as a white smear that competed with the list under it. A drawing made of one-pixel threads is
not a drawing to scale: it is drawn where it is read, and every stroke of it is a stroke of the screen
and nothing else.

What is drawn is one motif twice, and both are the same function of one size: a needle standing in the
middle of the line, with a thread through its eye and a loop at the end of the thread on either side.
The needle is the middle of the line - the head of the frame and the foot of it alike - and the two
threads are what carries the line out to the width of the list it frames. Both are mirrored exactly
about the middle of the drawing, which is what makes the line read as a thing rather than as a
pattern, and both are drawn thin: one pixel for a thread, two for the shaft of the needle, and nothing
anywhere wider than the head of the needle itself.

The sizes are the two numbers that matter and both come from the screen: the line over the list is as
wide as the strip of the two tabs under it is (see TAB_STRIP_WIDTH in the screen) and a little less
than a third shorter than the drawing it replaces, and the line under the list is drawn smaller again,
so that the pair reads as the head and the foot of one block rather than as the same ornament cut
twice. Run it from anywhere; it writes straight into the mod's assets:

    python templates/collection_frame/draw_ornaments.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

# the two drawings: the file in the mod, how wide and how tall the screen draws it, how heavy the
# thread through the needle is, and how much narrower than the head of the needle its shaft is
ORNIMENTS = [
    ("collection_up_line.png", 228, 22, 1, 2),
    ("collection_down_line.png", 140, 18, 1, 2),
]

SOURCE = Path(__file__).resolve().parent
DESTINATION = SOURCE.parents[1] / \
    "src" / "main" / "resources" / "assets" / "immersive_titles" / "textures" / "gui"

# the needle: how much of the height of the band it takes, how wide its head is as a share of that
# height, how tall the head of it - the eye and the room around the eye - is, and how many rows of the
# point at the end of it there are. The eye is what the thread goes through, so the head is the one
# part of the drawing wider than a thread, and the needle is the one part of it taller than a thread:
# it is the middle the whole line hangs on, and everything else in the drawing is a thread.
NEEDLE_SHARE = 0.60
HEAD_SHARE = 0.24
HEAD_HEIGHT = 5
POINT_HEIGHT = 3

# the loop the thread ends in, and how far it is kept off the end of the line
LOOP_RADIUS = 3
LOOP_INSET = 3

# the barbs: the little hooks of a thorn on the way out along the thread, how deep they hang, and how
# far the point of one is turned outwards - a hook rather than a dot hanging off the thread
BARBS = [0.30, 0.55, 0.78]
BARB_DEPTH = 3
BARB_FLICK = 1


def even(value):
    """`value` rounded to the nearest even number, never below two: the drawing is mirrored in half."""
    rounded = int(round(value / 2.0)) * 2

    return max(2, rounded)


def thread_points(start, end, height, lift):
    """The thread from `start` to `end`, as the points of one curve with a dip and a rise in it."""
    points = []
    span = end[0] - start[0]
    control = [(start[0] + span * 0.18, start[1] - lift),
               (start[0] + span * 0.42, start[1] + lift * 1.6),
               (start[0] + span * 0.68, start[1] - lift * 0.9)]

    steps = int(span * 4) + 1

    for step in range(steps):
        t = step / float(steps - 1)
        one = (1.0 - t) ** 3
        three = 3.0 * (1.0 - t) ** 2 * t
        three_again = 3.0 * (1.0 - t) * t * t
        cubic = t ** 3

        x = one * start[0] + three * control[0][0] + three_again * control[1][0] + cubic * control[2][0]
        y = one * start[1] + three * control[0][1] + three_again * control[1][1] + cubic * control[2][1]

        points.append((x, y))

    return points


def ornament(width, height, thread_weight, shaft_weight):
    """
    The ornament in the right half of its own box, needle and thread alike, as a list of shapes.

    The needle is what the line is hung on: the head of it at the top with a hole through the middle
    - the eye a thread goes through - the shaft of it under that, and the point it comes to at the
    bottom. The thread is the only thing that leaves the middle: it goes out of the eye, dips and
    rises once on the way, is caught by a few thorns, and ends in a loop at the end of the line.
    Everything is drawn from the middle outwards, and the left half of the drawing is what the right
    half comes to when it is looked at in a mirror.
    """
    middle = width / 2.0
    needle = int(round(height * NEEDLE_SHARE))
    top = (height - needle) // 2
    bottom = top + needle - 1
    head = even(height * HEAD_SHARE)
    shaft = even(head - shaft_weight)
    head_bottom = top + HEAD_HEIGHT - 1
    shaft_bottom = bottom - POINT_HEIGHT
    shapes = []

    # the head of the needle, and the eye of it: a hole two pixels across, through the middle of it
    shapes.append(("rect", (middle, top, middle + head / 2.0 - 1, head_bottom)))
    shapes.append(("clear", (middle - 1, top + 2, middle, head_bottom - 1)))

    # the shaft of it, and the point it comes to
    shapes.append(("rect", (middle, head_bottom + 1, middle + shaft / 2.0 - 1, shaft_bottom)))
    shapes.append(("rect", (middle, shaft_bottom + 1, middle, bottom)))

    # the thread out of the eye, to where the loop at the end of the line begins, and the loop itself
    loop_centre = width - LOOP_RADIUS - LOOP_INSET
    thread_y = top + (HEAD_HEIGHT - 1) / 2.0
    curve = thread_points((middle + head / 2.0 - 1.0, thread_y),
                          (loop_centre - LOOP_RADIUS, thread_y), height, height * 0.22)

    shapes.append(("line", (curve, thread_weight)))

    for share in BARBS:
        index = min(len(curve) - 1, int(len(curve) * share))
        x, y = curve[index]
        shapes.append(("rect", (x, y, x, y + BARB_DEPTH)))
        shapes.append(("rect", (x + BARB_FLICK, y + BARB_DEPTH, x + BARB_FLICK, y + BARB_DEPTH)))

    shapes.append(("loop", ((loop_centre, thread_y), LOOP_RADIUS, thread_weight)))

    return shapes


def draw(width, height, thread_weight, shaft_weight):
    """The ornament, mirrored exactly about the middle of its own box."""
    half = width // 2
    sheet = Image.new("L", (half, height), 0)
    pen = ImageDraw.Draw(sheet)
    corner = lambda x, y: (round(x - half), round(y))   # noqa: E731

    for shape, box in ornament(width, height, thread_weight, shaft_weight):
        if shape == "rect":
            pen.rectangle((corner(box[0], 0)[0], box[1], corner(box[2], 0)[0], box[3]), fill=255)
        elif shape == "clear":
            pen.rectangle((corner(box[0], 0)[0], box[1], corner(box[2], 0)[0], box[3]), fill=0)
        elif shape == "line":
            curve, weight = box
            pen.line([corner(x, y) for x, y in curve if x >= half], fill=255, width=weight)
        elif shape == "loop":
            (x, y), radius, weight = box
            pen.ellipse((round(x - half - radius), round(y - radius),
                         round(x - half + radius), round(y + radius)), outline=255, width=weight)

    whole = Image.new("L", (width, height), 0)
    whole.paste(sheet, (half, 0))
    whole.paste(sheet.transpose(Image.FLIP_LEFT_RIGHT), (0, 0))

    return whole


def main():
    DESTINATION.mkdir(parents=True, exist_ok=True)

    for name, width, height, thread_weight, shaft_weight in ORNIMENTS:
        ink = draw(width, height, thread_weight, shaft_weight)

        picture = Image.new("RGBA", (width, height), (255, 255, 255, 0))
        picture.putalpha(ink)

        target = DESTINATION / name
        picture.save(target, optimize=True)

        drawn = list(ink.get_flattened_data() if hasattr(ink, "get_flattened_data") else ink.getdata())
        cover = sum(drawn) / (255.0 * width * height)
        print(f"{name}: {width}x{height}, {cover:.1%} of its box drawn, {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
