"""
Verify the stripped frames against the source render, sample by sample.

    python verify_frames.py "path/to/render.mp4" [--frames frames]

The whole deliverable must be a pure function of the source: RGB pure white on every
pixel (so no dark fringe can ever appear at any alpha) and alpha == round(ramp(luma)),
where the ramp is transparent at or below DEAD, a short blend up to KNEE and then the
untouched source value.  So this script rebuilds that expectation from the video and
compares all 187 x 1440 x 720 samples of it.

It also writes sheet.png: the animation over a dark green plate and over a
checkerboard, the fastest way to spot a halo, a rectangle or a leftover background by
eye, and it times a frame decode for the in game budget.
"""
import argparse
import os
import time

import numpy as np
from PIL import Image, ImageDraw

from strip_bg import CONTENT_T, alpha_from, luma_of, probe, video_frames

CELL, DARK = 8, (26, 40, 24)             # checkerboard cell, in game-ish dark green
SHEET = [0, 1, 2, 3, 5, 8, 11, 20, 35, 60, 95, 140, 170, 184]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("--frames", default="frames")
    ap.add_argument("--dead", type=float, default=2.0)
    ap.add_argument("--knee", type=float, default=8.0)
    args = ap.parse_args()

    src = os.path.abspath(args.src)
    out = os.path.abspath(args.frames)
    names = sorted(f for f in os.listdir(out) if f.lower().endswith(".png"))
    w, h = probe(src)

    total = lit = mismatch = bad_white = 0
    above_n = ramp_n = dead_n = 0
    worst = 0
    steady_corner = flash = None
    tiles = {}
    lo = None

    for i, rgb in enumerate(video_frames(src, w, h), start=1):
        v = luma_of(rgb)
        if v.max() <= CONTENT_T:
            continue                       # outside the written window
        if lo is None:
            lo = i
        k = i - lo
        if k >= len(names):
            break
        a = np.asarray(Image.open(os.path.join(out, names[k])))
        alpha = a[..., 3].astype(np.float32)

        expected = np.clip(np.rint(alpha_from(v, args.dead, args.knee)), 0, 255)
        diff = np.abs(alpha - expected)
        mismatch += int((diff > 0).sum())
        worst = max(worst, int(diff.max()))
        bad_white += int((a[..., :3] != 255).sum())
        above_n += int((v >= args.knee).sum())
        dead_n += int((v <= args.dead).sum())
        ramp_n += int((v > args.dead).sum() - (v >= args.knee).sum())
        total += alpha.size
        lit += int((alpha > 0).sum())

        if k == 91:
            steady_corner = (float(max(v[0:80, 0:80].max(), v[-80:, -80:].max())),
                             float(max(alpha[0:80, 0:80].max(), alpha[-80:, -80:].max())))
        if k == 11:                        # source frame 19, the blinding flash
            flash = (float(v.mean()), float(alpha.mean()),
                     float(v[0:80, 0:80].mean()), float(alpha[0:80, 0:80].mean()))
        if k in SHEET:
            tiles[k] = Image.open(os.path.join(out, names[k])).convert("RGBA")

    print("frames                   : %d (%s .. %s)" % (len(names), names[0], names[-1]))
    print("samples compared         : %d" % total)
    print("alpha != round(ramp(v))  : %d  (max diff %d)" % (mismatch, worst))
    print("RGB != pure white        : %d" % bad_white)
    print("  of which >= knee       : %d samples copied exactly" % above_n)
    print("  blend zone dead..knee  : %d samples" % ramp_n)
    print("  dropped as noise       : %d samples (<= %.0f), %.2f%% of all pixels"
          % (dead_n, args.dead, 100.0 * dead_n / total))
    print("still lit                : %d (%.2f%%)" % (lit, 100.0 * lit / total))
    print("steady frame 92 corner   : source max %.0f -> alpha max %.0f" % steady_corner)
    print("flash frame 12           : source mean %.2f -> alpha mean %.2f, corner %.2f -> %.2f"
          % flash)

    t = time.time()
    probe_set = names[::9]
    for name in probe_set:
        Image.open(os.path.join(out, name)).convert("RGBA").load()
    print("decode                   : %.1f ms/frame (%d frames)"
          % ((time.time() - t) * 1000.0 / len(probe_set), len(probe_set)))
    size = sum(os.path.getsize(os.path.join(out, f)) for f in names)
    print("frame size               : %.1f MB total, %.1f KB/frame, %.1f MB decoded as RGBA"
          % (size / 1048576.0, size / 1024.0 / len(names), w * h * 4 / 1048576.0))

    # ---- contact sheet ----
    tw, th = w // 3, h // 3
    cols = 4
    rows = (len(SHEET) + cols - 1) // cols
    sheet = Image.new("RGB", (tw * cols, th * rows), DARK)
    d = ImageDraw.Draw(sheet)
    for r in range(rows):
        for c in range(cols):
            if r < rows // 2:
                continue
            x, y = c * tw, r * th
            for yy in range(0, th, CELL):
                for xx in range(0, tw, CELL):
                    if (xx // CELL + yy // CELL) % 2:
                        d.rectangle([x + xx, y + yy, x + xx + CELL - 1, y + yy + CELL - 1],
                                    fill=(150, 150, 150))
    for n, k in enumerate(SHEET):
        if k in tiles:
            sheet.paste(tiles[k].resize((tw, th), Image.LANCZOS), ((n % cols) * tw, (n // cols) * th),
                        tiles[k].resize((tw, th), Image.LANCZOS))
    sheet_path = os.path.join(os.path.dirname(out), "sheet.png")
    sheet.save(sheet_path)
    print("contact sheet            : %s" % sheet_path)


if __name__ == "__main__":
    main()
