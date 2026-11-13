#!/usr/bin/env python3
"""Put any word back into the word free biome title template.

`build_template.py` strips the baked lettering out of the 187 frames; this script draws a word
back into the cleared box, so the clip plays exactly like the original title reveal. The word
is white and alpha blended over the frame, and its per frame intensity follows `envelope.json`,
the curve measured on the source clip (nothing before frame 10, full from frame 10, a few
percent dimming while the flash bloom decays, then the fade out over frames 159 - 173).

The word can be given as text (a TrueType font is used) or as an image with an alpha channel,
so the caller can render it with the game font, an artist can draw it by hand, and either way
it lands where PALE GARDEN sat.

Usage:
    python apply_word.py --text "MUSHROOM FIELDS" --font C:/Windows/Fonts/arialbd.ttf --out out
    python apply_word.py --word-image my_word.png --out out
    python apply_word.py --text TEST --only 10,60,100,170 --out out   # a few frames only
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

DEFAULT_BOX = (264, 424, 432, 1508)   # y0, y1, x0, x1 - the cleared word box
DEFAULT_GLYPH_H = 110                 # cap height of PALE GARDEN in the clip
DEFAULT_CENTER = (970, 346)           # centre of the lettering (457..1463, 291..400)

FONTS = (
    r"C:\Windows\Fonts\arialbd.ttf",
    r"C:\Windows\Fonts\seguisb.ttf",
    r"C:\Windows\Fonts\impact.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
)


def load_alpha(path):
    return np.array(Image.open(path).convert("RGBA"))[..., 3].astype(np.float32)


def find_font(explicit):
    if explicit:
        return explicit
    for path in FONTS:
        if os.path.isfile(path):
            return path
    sys.exit("no usable TrueType font found, pass one with --font")


def render_text(text, font_path, glyph_h, max_w):
    """draw the text, crop to the ink and scale it to the wanted height (width capped)"""
    font = ImageFont.truetype(font_path, 400)
    box = font.getbbox(text)
    img = Image.new("L", (box[2] - box[0] + 220, box[3] - box[1] + 220), 0)
    ImageDraw.Draw(img).text((110 - box[0], 110 - box[1]), text, font=font, fill=255)

    ink = img.getbbox()
    if ink is None:
        sys.exit("the text did not draw anything")
    img = img.crop(ink)

    scale = min(glyph_h / img.height, max_w / img.width)
    size = (max(1, int(round(img.width * scale))), max(1, int(round(img.height * scale))))
    img = img.resize(size, Image.LANCZOS)
    return np.asarray(img, np.float32) / 255.0


def load_image(path, glyph_h, max_w):
    """a word image: RGBA, RGB is what gets drawn, alpha is the coverage"""
    img = Image.open(path).convert("RGBA")
    ink = img.getbbox()
    if ink is None:
        sys.exit("the word image is empty")
    img = img.crop(ink)
    scale = min(glyph_h / img.height, max_w / img.width)
    size = (max(1, int(round(img.width * scale))), max(1, int(round(img.height * scale))))
    img = img.resize(size, Image.LANCZOS)
    a = np.asarray(img, np.float32)
    return a[..., :3].mean(axis=2) / 255.0 * (a[..., 3] / 255.0)


def place(shape, word, box, center):
    """empty full frame layer with the word (coverage 0..1) centred on `center`"""
    y0, y1, x0, x1 = box
    cx, cy = center
    h, w = word.shape
    layer = np.zeros(shape, np.float32)
    ty = int(round(max(y0, min(y1 - h, cy - h / 2))))
    tx = int(round(max(x0, min(x1 - w, cx - w / 2))))
    layer[ty:ty + h, tx:tx + w] = word
    return layer


def compose(frame_alpha, word, gain):
    """alpha blend the word over the frame (RGB is white in both, so alpha is the picture)"""
    if gain <= 0.0:
        return frame_alpha
    c = word * gain
    return frame_alpha * (1.0 - c) + 255.0 * c


def save(alpha, path):
    rgb = np.full(alpha.shape + (3,), 255, np.uint8)
    out = np.clip(np.rint(alpha), 0, 255).astype(np.uint8)
    Image.fromarray(np.dstack([rgb, out]), "RGBA").save(path, optimize=True, compress_level=9)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--frames", default=os.path.join(os.path.dirname(__file__), "frames"),
                    help="folder with the word free frames")
    ap.add_argument("--out", default="out", help="folder to write the finished frames to")
    ap.add_argument("--envelope", default=os.path.join(os.path.dirname(__file__), "envelope.json"),
                    help="per frame intensity of the word")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--text", help="the word to draw")
    src.add_argument("--word-image", help="an RGBA png of the word instead of text")
    ap.add_argument("--font", help="TrueType font used for --text")
    ap.add_argument("--glyph-h", type=int, default=DEFAULT_GLYPH_H,
                    help="height the ink is scaled to, so any word sits like the original")
    ap.add_argument("--box", default=",".join(str(v) for v in DEFAULT_BOX),
                    help="cleared word box as y0,y1,x0,x1")
    ap.add_argument("--center", default=",".join(str(v) for v in DEFAULT_CENTER),
                    help="where the middle of the word goes, as x,y")
    ap.add_argument("--only", help="comma separated frame numbers, for a quick look")
    args = ap.parse_args()

    box = tuple(int(v) for v in args.box.split(","))
    center = tuple(int(v) for v in args.center.split(","))
    y0, y1, x0, x1 = box
    files = sorted(f for f in os.listdir(args.frames)
                   if f.lower().endswith(".png") and f.startswith("frame_"))
    if not files:
        sys.exit(f"no frames in {args.frames}")

    with open(args.envelope) as fh:
        gain = json.load(fh)["gain"]
    if len(gain) != len(files):
        sys.exit(f"envelope has {len(gain)} entries but there are {len(files)} frames")

    if args.text:
        word = render_text(args.text, find_font(args.font), args.glyph_h, x1 - x0)
    else:
        word = load_image(args.word_image, args.glyph_h, x1 - x0)
    layer = place((960, 1920), word, box, center)
    ys, xs = np.where(layer > 0)
    print(f"{args.text or args.word_image!r}: {int((layer > 0).sum())} px of ink, "
          f"x {xs.min()}..{xs.max()}, y {ys.min()}..{ys.max()} (box x {x0}..{x1}, y {y0}..{y1})")

    only = {int(v) for v in args.only.split(",")} if args.only else None
    os.makedirs(args.out, exist_ok=True)
    for n, f in enumerate(files):
        frame_no = n + 1
        if only and frame_no not in only:
            continue
        out = compose(load_alpha(os.path.join(args.frames, f)), layer, gain[n])
        save(out, os.path.join(args.out, f))
        print(f"\r{frame_no}/{len(files)} {f}", end="", flush=True)
    print("\ndone ->", args.out)


if __name__ == "__main__":
    main()
