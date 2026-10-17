#!/usr/bin/env python3
"""The smallest lossless form of the title frames, one frame at a time.

The frames the mod ships are the same pictures `strip_bg.py` writes, recompressed with
zopfli (zopflipng). zopfli changes no pixel: it re-deflates the image stream, and drops
the colour type to the smallest one that still carries the same picture (a frame of
white and alpha becomes gray+alpha, or a palette with one entry per alpha value).
Every file about to be kept is decoded again and compared with the frame it replaces,
pixel by pixel, and a frame that does not match, or one whose new form is not smaller,
is left as it is.

    python shrink_frames.py            # dry run: totals only, writes nothing
    python shrink_frames.py --apply    # write the frames back

It needs `zopfli` and `Pillow` (pip install zopfli pillow). The run is CPU heavy - about
eleven seconds a frame on one core - so the frames are done in parallel.
"""

import io
import os
import pathlib
import sys
import time
from concurrent.futures import ProcessPoolExecutor, as_completed

from PIL import Image, ImageChops
import zopfli.png as zopfli_png

REPO = pathlib.Path(__file__).resolve().parents[2]
SRC = REPO / "src" / "main" / "resources" / "assets" / "immersive_titles" / "textures" / "title"


def optimize_frame(path_str):
    """Return (name, original bytes, new bytes or None, colour type, kept)."""
    path = pathlib.Path(path_str)
    original = path.read_bytes()
    before = Image.open(io.BytesIO(original)).convert("RGBA")
    candidate = zopfli_png.optimize(original, num_iterations=15, num_iterations_large=5)
    after = Image.open(io.BytesIO(candidate))
    mode = after.mode
    if ImageChops.difference(before, after.convert("RGBA")).getbbox() is not None:
        raise ValueError(f"pixel mismatch, nothing was written: {path.name}")
    if len(candidate) >= len(original):
        return path.name, len(original), None, None, True
    return path.name, len(original), candidate, mode, False


def main():
    apply = "--apply" in sys.argv
    files = sorted(SRC.glob("frame_*.png"))
    workers = min(12, os.cpu_count() or 4)
    start = time.time()
    results = []

    with ProcessPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(optimize_frame, str(f)): f for f in files}
        for done, future in enumerate(as_completed(futures), 1):
            name, orig, data, mode, kept = future.result()
            results.append((name, orig, len(data) if data else orig, mode, data))
            new = len(data) if data else orig
            print(f"[{done:3d}/{len(files)}] {name}: {orig/1024:6.1f}K -> {new/1024:6.1f}K"
                  f"{' (kept)' if kept else ''}", flush=True)

    orig_total = sum(r[1] for r in results)
    new_total = sum(r[2] for r in results)
    modes = {}
    kept = 0
    for name, orig, new, mode, data in results:
        if data is None:
            kept += 1
        else:
            modes[mode] = modes.get(mode, 0) + 1

    if apply:
        for name, orig, new, mode, data in results:
            if data is not None:
                (SRC / name).write_bytes(data)

    print("---")
    print(f"total: {orig_total/1048576:.2f} MB -> {new_total/1048576:.2f} MB "
          f"({new_total*100//orig_total}% of the original, saved {(orig_total-new_total)/1048576:.2f} MB)")
    print("colour types:", modes, "kept as they were:", kept)
    print(f"time {time.time()-start:.0f}s with {workers} workers -", "applied" if apply else "dry run")


if __name__ == "__main__":
    main()
