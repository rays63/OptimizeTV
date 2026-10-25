"""Renders the Google TV-style boot screen: wordmark over a slowly circling, pulsing glow.

python -I render.py <out_dir>   -> frame_000.png .. frame_NNN.png (one loop) + bootlogo.bmp (frame 0)
"""
import math
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1280, 720
FPS = 15
LOOP_S = 6
BG = (8, 10, 16)
# Google blue, red, yellow, green
COLORS = [(66, 133, 244), (234, 67, 53), (251, 188, 5), (52, 168, 83)]
FONT = "C:/Windows/Fonts/segoeuisl.ttf"  # Segoe UI Semilight, closest to Google Sans here
TV_FONT = "C:/Windows/Fonts/segoeuil.ttf"


def glow(t):
    """Four blurred colour blobs orbiting the centre; drawn small then scaled up (cheap blur)."""
    s = 4
    g = Image.new("RGB", (W // s, H // s), BG)
    d = ImageDraw.Draw(g)
    cx, cy = W / s / 2, H / s / 2
    pulse = 0.75 + 0.25 * math.sin(2 * math.pi * t / LOOP_S)
    for i, c in enumerate(COLORS):
        a = 2 * math.pi * (t / LOOP_S + i / 4)
        x, y = cx + 46 * math.cos(a), cy + 18 * math.sin(a)
        r = 42 * pulse
        col = tuple(int(BG[k] + (c[k] - BG[k]) * 0.55 * pulse) for k in range(3))
        d.ellipse([x - r * 1.5, y - r, x + r * 1.5, y + r], fill=col)
    g = g.filter(ImageFilter.GaussianBlur(26))
    return g.resize((W, H), Image.BICUBIC)


def wordmark(img):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(FONT, 118)
    ft = ImageFont.truetype(TV_FONT, 118)
    letters = list(zip("Google", [0, 1, 2, 0, 3, 1]))
    gap = 30
    widths = [d.textlength(ch, font=f) for ch, _ in letters]
    tv_w = d.textlength("TV", font=ft)
    total = sum(widths) + gap + tv_w
    x = (W - total) / 2
    y = H / 2 - 8
    for (ch, ci), w in zip(letters, widths):
        d.text((x, y), ch, font=f, fill=COLORS[ci], anchor="ls")
        x += w
    d.text((x + gap, y), "TV", font=ft, fill=(232, 234, 237), anchor="ls")


def frame(t):
    img = glow(t)
    wordmark(img)
    return img


def save_bmp32(img, path):
    """1280x720 32-bit BGRA bottom-up BMP, the exact layout of the stock bootlogo.bmp."""
    px = img.convert("RGB").transpose(Image.FLIP_TOP_BOTTOM).tobytes()
    data = bytearray(W * H * 4)
    data[0::4], data[1::4], data[2::4] = px[2::3], px[1::3], px[0::3]
    data[3::4] = b"\xff" * (W * H)
    header = b"BM" + struct.pack("<IHHI", 54 + len(data), 0, 0, 54)
    header += struct.pack("<IiiHHIIiiII", 40, W, H, 1, 32, 0, len(data), 2835, 2835, 0, 0)
    with open(path, "wb") as fh:
        fh.write(header + data)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    n = FPS * LOOP_S
    for i in range(n):
        im = frame(i / FPS)
        im.save(os.path.join(out, "frame_%03d.png" % i))
        if i == 0:
            save_bmp32(im, os.path.join(out, "bootlogo.bmp"))
    print("frames", n)
