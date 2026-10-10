#!/usr/bin/env python3
"""Rebuild the word free biome title template from a word baked frame sequence.

Scene assumptions (that is what this clip is): a black plate, white lettering inside one fixed
box, two white ornaments above and below it, drifting sparkles, and a short blinding flash at
the start whose light floods the whole frame.

Per frame:

  1. a smooth estimate of the background is built from the frame itself: a wide normalised blur
     that leaves the lettering out and then rejects whatever ends up brighter than its own
     estimate, round after round. Being a wide blur of the actual frame it follows the flash
     dome and the afterglow across the whole box instead of only interpolating them inwards
     from the edges, so the repaired area does not show up as a rectangle while the flash is
     fading - that is what a purely boundary based fill does there;
  2. the lettering mask: pixels that are ink bright in most frames of the steady window (thin
     serif strokes stay, drifting sparkles drop out) plus whatever is brighter than the
     background estimate nearby (that is the lettering and the bloom around it), minus the two
     ornaments, which are never touched;
  3. only the masked pixels are rewritten (with the estimate) while the rest of the frame is
     pinned back to its own values, so the flash, the ornaments and every sparkle stay exactly
     as shot;
  4. the patch is feathered back in.

Usage:
    python build_template.py --input <word baked frames> --output frames
    python build_template.py --input <frames> --output out --only 15,22,100
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

DEFAULT_BOX = (264, 424, 432, 1508)   # y0, y1, x0, x1
INK_T = 30                            # "ink" brightness
PERSIST = 0.55                        # share of the steady window a pixel counts as ink in
CORE_DILATE = 12                      # skirt around the lettering that is always masked
ZONE_GROW = 34                        # how far from the letters the per frame test may reach
DEV_T = 3.0                           # how much brighter than the background a pixel must be
BG_SIGMA = 48.0                       # width of the background estimate, in pixels
BG_T = 3.0                            # sigma clipping: no longer background past this deviation
BG_ITERS = 3
ROI_PAD = 150                         # context around the repaired area used for the estimate
# The ornaments of the template (y0,y1,x0,x1, a few px of margin). The repair must never reach
# them: they are part of the frame we keep as shot, and the word box has enough clearance that
# nothing about the lettering needs to get that close.
DEFAULT_PROTECT = ((184, 258, 708, 1219), (447, 572, 583, 1329))
SCALES = (7.0, 5.0, 3.5, 2.5, 1.6, 1.0)
PASSES = 3
FEATHER = 3


# ----------------------------------------------------------------- helpers

def load_alpha(path):
    return np.array(Image.open(path).convert("RGBA"))[..., 3].astype(np.float32)


def blur1d(v, r):
    if r < 1:
        return v
    k = 2 * r + 1
    pad = np.concatenate([np.full(r, v[0], np.float32), v, np.full(r, v[-1], np.float32)])
    c = np.concatenate([[0.0], np.cumsum(pad, dtype=np.float64)])
    return ((c[k:] - c[:-k]) / k).astype(np.float32)


def box_blur(a, r):
    if r < 1:
        return a
    k = 2 * r + 1
    pad = np.zeros((a.shape[0] + 2 * r, a.shape[1]), np.float32)
    pad[r:r + a.shape[0]] = a
    c = np.cumsum(pad, axis=0)
    c = np.vstack([np.zeros((1, a.shape[1]), np.float32), c])
    out = (c[k:] - c[:-k]) / k
    pad = np.zeros((out.shape[0], out.shape[1] + 2 * r), np.float32)
    pad[:, r:r + out.shape[1]] = out
    c = np.cumsum(pad, axis=1)
    c = np.hstack([np.zeros((out.shape[0], 1), np.float32), c])
    return (c[:, k:] - c[:, :-k]) / k


def gauss(a, s):
    r = max(1, int(round(s * 0.7)))
    return box_blur(box_blur(a, r), r)


def _shift(m, d, axis):
    if d == 0:
        return m
    n = np.zeros_like(m)
    if axis == 0:
        if d > 0:
            n[d:] = m[:-d]
        else:
            n[:d] = m[-d:]
    else:
        if d > 0:
            n[:, d:] = m[:, :-d]
        else:
            n[:, :d] = m[:, -d:]
    return n


def dilate(m, r):
    if r < 1:
        return m
    out = np.zeros_like(m)
    for d in range(-r, r + 1):
        out |= _shift(m, d, 0)
    tmp = out
    out = np.zeros_like(m)
    for d in range(-r, r + 1):
        out |= _shift(tmp, d, 1)
    return out


def grow_box(box, pad, shape):
    y0, y1, x0, x1 = box
    h, w = shape
    return (max(0, y0 - pad), min(h, y1 + pad), max(0, x0 - pad), min(w, x1 + pad))


# ----------------------------------------------------------------- background estimate

def local_background(a, core, box):
    """Smooth estimate of what the frame looks like without the lettering.

    A wide normalised blur of the frame computed with the lettering excluded; after the first
    round anything that stands out brighter than the estimate - the lettering, the bloom around
    it, the ornaments, the sparkles - is dropped from the average as well, so bright details do
    not bleed into it. Light that floods the whole frame (the flash dome, the afterglow) is
    followed across the box, while a black plate stays black.
    """
    roi = grow_box(box, ROI_PAD, a.shape)
    ry0, ry1, rx0, rx1 = roi
    sub = a[ry0:ry1, rx0:rx1]
    w = (~core[ry0:ry1, rx0:rx1]).astype(np.float32)
    bg = np.zeros_like(sub)
    for _ in range(BG_ITERS):
        bg = gauss(sub * w, BG_SIGMA) / np.maximum(gauss(w, BG_SIGMA), 1e-3)
        w = w * ((sub - bg) <= BG_T).astype(np.float32)
    return bg, roi


# ----------------------------------------------------------------- lettering mask

def static_core(files, box, window):
    """lettering mask: ink bright in most frames of the steady window"""
    y0, y1, x0, x1 = box
    count = np.zeros((y1 - y0, x1 - x0), np.int32)
    n = 0
    for f in window:
        a = load_alpha(files[f])
        count += (a[y0:y1, x0:x1] > INK_T).astype(np.int32)
        n += 1
    shape = load_alpha(files[0]).shape
    full = np.zeros(shape, bool)
    full[y0:y1, x0:x1] = count >= max(8, int(round(PERSIST * n)))
    return dilate(full, CORE_DILATE)


def erase(path, core, zone_box, protect):
    a = load_alpha(path)
    bg, roi = local_background(a, core, zone_box)
    ry0, ry1, rx0, rx1 = roi
    bg_full = np.zeros_like(a)
    bg_full[ry0:ry1, rx0:rx1] = bg

    zone = dilate(core, ZONE_GROW)
    m = (core | ((a - bg_full > DEV_T) & zone)) & ~protect
    m = dilate(m, 1) & ~protect

    u = np.where(m, bg_full, a)
    for s in SCALES:
        for _ in range(PASSES):
            u = np.where(m, gauss(u, s), a)

    w = np.clip(box_blur(box_blur(m.astype(np.float32), FEATHER), FEATHER), 0, 1)
    return a * (1.0 - w) + u * w


def save(out, path):
    rgb = np.full(out.shape + (3,), 255, np.uint8)
    alpha = np.clip(np.rint(out), 0, 255).astype(np.uint8)
    Image.fromarray(np.dstack([rgb, alpha]), "RGBA").save(path)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input", required=True, help="folder holding the word baked frames")
    ap.add_argument("--output", default="frames", help="folder to write the template into")
    ap.add_argument("--box", default=",".join(str(v) for v in DEFAULT_BOX),
                    help="word box as y0,y1,x0,x1")
    ap.add_argument("--window", default="55,151",
                    help="steady window (start,end) used to learn the lettering mask")
    ap.add_argument("--only", help="comma separated frame numbers, for a quick look")
    ap.add_argument("--protect", default=";".join(",".join(str(v) for v in b)
                                                  for b in DEFAULT_PROTECT),
                    help="boxes (y0,y1,x0,x1, semicolon separated) that are never repaired")
    args = ap.parse_args()

    box = tuple(int(v) for v in args.box.split(","))
    ws, we = (int(v) for v in args.window.split(","))
    if not os.path.isdir(args.input):
        sys.exit(f"no such input folder: {args.input}")
    files = [os.path.join(args.input, f) for f in sorted(os.listdir(args.input))
             if f.lower().endswith(".png")]
    if not files:
        sys.exit(f"no png frames in {args.input}")
    os.makedirs(args.output, exist_ok=True)

    core = static_core(files, box, range(ws, min(we, len(files))))
    ys, xs = np.where(core)
    shape = load_alpha(files[0]).shape
    zone_box = grow_box((ys.min(), ys.max() + 1, xs.min(), xs.max() + 1), ZONE_GROW + 1, shape)
    protect = np.zeros(shape, bool)
    for spec in args.protect.split(";"):
        py0, py1, px0, px1 = (int(v) for v in spec.split(","))
        protect[py0:py1, px0:px1] = True
    only = {int(v) for v in args.only.split(",")} if args.only else None
    print(f"{len(files)} frames, ink mask {int(core.sum())} px over box {box}, "
          f"lettering {xs.min()}..{xs.max()} x {ys.min()}..{ys.max()}")

    for n, f in enumerate(files):
        if only and n + 1 not in only:
            continue
        save(erase(f, core, zone_box, protect), os.path.join(args.output, os.path.basename(f)))
        print(f"\r{n + 1}/{len(files)} {os.path.basename(f)}", end="", flush=True)
    print("\ndone")


if __name__ == "__main__":
    main()
