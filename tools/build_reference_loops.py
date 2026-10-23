#!/usr/bin/env python3
"""Build each stretch's low-framerate reference loop.

  tmp/keyframes/<id>_1..3.png  ->  app/src/main/assets/ref/<id>.gif (+ .webp)

The crop box is the *union* of all three keyframes, so the subject never jumps
between frames; cropping per frame makes the loop jitter. Frames ping-pong
(1,2,3,2) so three stills loop seamlessly at 3 fps.
"""
import pathlib, subprocess, sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "tmp" / "keyframes"
OUT = ROOT / "app" / "src" / "main" / "assets" / "ref"
FFMPEG = pathlib.Path.home() / ".hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg"

# portrait panel, 3:4
CANVAS = (420, 560)
FPS = 3


def subject_bbox(img: Image.Image) -> tuple[float, float, float, float]:
    """Bounding box of everything that is not the cream background."""
    small = img.convert("RGB").resize((img.width // 4, img.height // 4))
    px = small.load()
    w, h = small.size
    # background colour = most common colour along the top edge
    bg = px[w // 2, 2]
    tol = 18
    minx, miny, maxx, maxy = w, h, 0, 0
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            if abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > tol:
                minx = min(minx, x); maxx = max(maxx, x)
                miny = min(miny, y); maxy = max(maxy, y)
    if maxx <= minx or maxy <= miny:
        return (0, 0, w, h)
    return (minx * 4, miny * 4, (maxx + 1) * 4, (maxy + 1) * 4)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    ids = sorted({p.name.rsplit("_", 1)[0] for p in SRC.glob("*_1.png")})
    if not ids:
        print(f"no keyframes in {SRC}")
        return 1

    for sid in ids:
        frames = [Image.open(SRC / f"{sid}_{n}.png").convert("RGB") for n in (1, 2, 3)]
        boxes = [subject_bbox(f) for f in frames]
        # union, with padding
        x0 = min(b[0] for b in boxes); y0 = min(b[1] for b in boxes)
        x1 = max(b[2] for b in boxes); y1 = max(b[3] for b in boxes)
        pad_x = (x1 - x0) * 0.10; pad_y = (y1 - y0) * 0.06
        box = (max(0, x0 - pad_x), max(0, y0 - pad_y), min(frames[0].width, x1 + pad_x), min(frames[0].height, y1 + pad_y))

        seq = ROOT / "tmp" / f"seq_{sid}"
        seq.mkdir(parents=True, exist_ok=True)
        for f in seq.glob("*.png"):
            f.unlink()

        order = [1, 2, 3, 2]  # ping-pong
        for i, n in enumerate(order, start=1):
            img = frames[n - 1].crop(box)
            img.thumbnail(CANVAS, Image.LANCZOS)
            canvas = Image.new("RGB", CANVAS, frames[0].getpixel((4, 4)))
            canvas.paste(img, ((CANVAS[0] - img.width) // 2, (CANVAS[1] - img.height) // 2))
            canvas.save(seq / f"{i:03d}.png")

        gif = OUT / f"{sid}.gif"
        webp = OUT / f"{sid}.webp"
        subprocess.run([
            str(FFMPEG), "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(seq / "%03d.png"),
            "-vf", "split[a][b];[a]palettegen=max_colors=96[p];[b][p]paletteuse=dither=bayer",
            "-loop", "0", str(gif),
        ], check=True)
        subprocess.run([
            str(FFMPEG), "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(seq / "%03d.png"),
            "-c:v", "libwebp", "-lossless", "0", "-q", "62", "-loop", "0", "-preset", "picture", str(webp),
        ], check=True)
        print(f"{sid:16} gif {gif.stat().st_size // 1024:4} KB   webp {webp.stat().st_size // 1024:4} KB")

    total = sum(p.stat().st_size for p in OUT.iterdir())
    print(f"--- {len(ids)} loops, {total // 1024} KB total ---")
    return 0


if __name__ == "__main__":
    sys.exit(main())
