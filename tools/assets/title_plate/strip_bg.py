"""
Strip the black background off a rendered title animation.

    python strip_bg.py "path/to/render.mp4" [--out frames] [--dead 2] [--knee 8]

Input   : an mp4/mov render (or a directory of PNGs) of the title plate drawn on
          pure black.
Output  : RGBA PNG frames, RGB = pure white, alpha = the plate's luminance.  That
          is the format the mod consumes: it draws the frame tinted with the biome
          colour, so the frame itself must not carry colour.

Why a dead zone: the render is 8 bit 4:2:0 h264, so "black" is not exactly 0.  The
dark plateau carries +-2 of ringing and the empty corners sit at a mean of 0.13 to
0.36.  Everything at or below DEAD is therefore encoder noise and goes to fully
transparent; from DEAD the alpha ramps back up to the untouched source value at
KNEE, and from KNEE on every single sample is copied bit for bit.  The background
stops being a faint grey veil, and nothing visible is touched: no resampling, no
scaling, no colour conversion beyond the luma weights.

The content window (the frames that are not empty black) is detected from the
source and only that window is written, so the frame count matches the animation.
"""
import argparse
import os
import subprocess
import sys

import numpy as np
from PIL import Image

LUMA = np.array([0.2126, 0.7152, 0.0722], np.float32)
CONTENT_T = 4.0          # a frame is "content" once anything reaches this


def probe(src):
    out = subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries", "stream=width,height", "-of", "csv=p=0", src,
    ]).decode().strip().splitlines()[0]
    w, h = (int(v) for v in out.split(",")[:2])
    return w, h


def video_frames(src, w, h):
    """Yield RGB uint8 frames from a video file, straight from ffmpeg's stdout.

    Closing the generator early (the writer stops at the last frame of the window)
    kills ffmpeg on purpose, which is not an error.
    """
    cmd = ["ffmpeg", "-v", "error", "-i", src, "-an", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0)
    n = w * h * 3
    try:
        while True:
            buf = bytearray()
            while len(buf) < n:
                chunk = proc.stdout.read(n - len(buf))
                if not chunk:
                    break
                buf += chunk
            if len(buf) < n:
                break
            yield np.frombuffer(bytes(buf), np.uint8).reshape(h, w, 3)
        err = proc.stderr.read().decode(errors="replace")
        if proc.wait() != 0:
            raise RuntimeError("ffmpeg failed on %s: %s" % (src, err.strip()))
    except GeneratorExit:
        proc.kill()
        proc.stdout.close()
        proc.wait()
        raise


def png_frames(src, w, h):
    for name in sorted(os.listdir(src)):
        if name.lower().endswith(".png"):
            yield np.asarray(Image.open(os.path.join(src, name)).convert("RGB"))


def luma_of(rgb):
    return rgb.astype(np.float32) @ LUMA


def alpha_from(v, dead, knee):
    """dead -> 0, dead..knee -> ramp, knee..255 -> untouched source value."""
    if knee <= dead:
        return np.where(v > dead, v, 0.0).astype(np.float32)
    ramp = (knee * (v - dead) / (knee - dead)).astype(np.float32)
    return np.where(v <= dead, 0.0, np.where(v >= knee, v, ramp)).astype(np.float32)


def save(alpha, path):
    a = np.clip(np.rint(alpha), 0, 255).astype(np.uint8)
    rgb = np.full(a.shape + (3,), 255, np.uint8)
    Image.fromarray(np.dstack([rgb, a]), "RGBA").save(path, compress_level=9)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", help="video file, or a directory of PNG frames")
    ap.add_argument("--out", default="frames")
    ap.add_argument("--dead", type=float, default=2.0, help="at or below: transparent")
    ap.add_argument("--knee", type=float, default=8.0, help="from here: untouched")
    ap.add_argument("--range", default="", help="force FIRST,LAST 1-based frame numbers")
    args = ap.parse_args()

    src = os.path.abspath(args.src)
    out_dir = os.path.abspath(args.out)
    is_video = os.path.isfile(src)

    if is_video:
        w, h = probe(src)
        read = lambda: video_frames(src, w, h)
    else:
        first = sorted(f for f in os.listdir(src) if f.lower().endswith(".png"))[0]
        w, h = Image.open(os.path.join(src, first)).size
        read = lambda: png_frames(src, w, h)

    # ---- pass 1: where does the animation actually live? ----
    n = 0
    hot = []
    chroma = 0.0
    for rgb in read():
        v = luma_of(rgb)
        n += 1
        if v.max() > CONTENT_T:
            hot.append(n)
            bright = v > 40.0
            if bright.any():
                px = rgb[bright].astype(np.int16)
                chroma = max(chroma, float((px.max(axis=1) - px.min(axis=1)).max()))
    if not hot:
        sys.exit("no frame reaches %.0f, nothing to do" % CONTENT_T)
    if args.range:
        lo, hi = (int(v) for v in args.range.split(","))
    else:
        lo, hi = hot[0], hot[-1]

    print("source %dx%d, %d frames, content %d..%d (%d frames), max chroma spread %d"
          % (w, h, n, hot[0], hot[-1], hi - lo + 1, chroma))

    # ---- pass 2: write the window ----
    os.makedirs(out_dir, exist_ok=True)
    kept = 0
    for i, rgb in enumerate(read(), start=1):
        if i < lo:
            continue
        if i > hi:
            break
        save(alpha_from(luma_of(rgb), args.dead, args.knee),
             os.path.join(out_dir, "frame_%04d.png" % (i - lo + 1)))
        kept += 1
        print("\r%d/%d" % (kept, hi - lo + 1), end="", flush=True)

    size = sum(os.path.getsize(os.path.join(out_dir, f)) for f in os.listdir(out_dir))
    print("\nwrote %d frames to %s (%.1f MB, %.1f KB/frame)"
          % (kept, out_dir, size / 1048576.0, size / 1024.0 / max(kept, 1)))


if __name__ == "__main__":
    main()
