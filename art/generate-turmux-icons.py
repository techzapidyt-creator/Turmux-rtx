#!/usr/bin/env python3
"""Generate the Turmux RTX launcher icons and TV banner.

Usage: python3 art/generate-turmux-icons.py
"""

import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

ACCENT = (0, 255, 156, 255)
ACCENT_DIM = (0, 200, 83, 255)
BG_TOP = (12, 36, 28)
BG_BOTTOM = (5, 7, 10)

SCALE = 1024
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def radial_background(size):
    img = Image.new("RGBA", (size, size), BG_BOTTOM + (255,))
    draw = ImageDraw.Draw(img)
    cx, cy = size * 0.40, size * 0.32
    steps = 96
    for i in range(steps, 0, -1):
        t = i / steps
        radius = size * 0.92 * t
        mix = (1 - t) ** 1.6
        color = tuple(
            int(BG_BOTTOM[c] + (BG_TOP[c] - BG_BOTTOM[c]) * mix) for c in range(3)
        )
        draw.ellipse(
            [cx - radius, cy - radius, cx + radius, cy + radius],
            fill=color + (255,),
        )
    # Faint grid, so the icon reads as a terminal panel.
    grid = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(grid)
    line = max(1, size // 340)
    step = size // 9
    for x in range(0, size + step, step):
        gdraw.line([(x, 0), (x, size)], fill=(0, 255, 156, 14), width=line)
    for y in range(0, size + step, step):
        gdraw.line([(0, y), (size, y)], fill=(0, 255, 156, 14), width=line)
    return Image.alpha_composite(img, grid)


def glyph(size):
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    s = size / 108.0

    def pt(x, y):
        return (x * s, y * s)

    chevron = [pt(34, 38), pt(41, 38), pt(54, 54), pt(41, 70), pt(34, 70), pt(47, 54)]
    draw.polygon(chevron, fill=ACCENT)
    draw.rounded_rectangle(
        [pt(58, 61), pt(76, 67)], radius=1.5 * s, fill=ACCENT
    )
    draw.rounded_rectangle(
        [pt(34, 70), pt(46, 73)], radius=1.0 * s, fill=ACCENT_DIM
    )
    return layer


def compose(size, round_icon):
    canvas = radial_background(size)
    glow = glyph(size).filter(ImageFilter.GaussianBlur(size * 0.045))
    glow.putalpha(glow.split()[3].point(lambda a: int(a * 0.85)))
    canvas = Image.alpha_composite(canvas, glow)
    canvas = Image.alpha_composite(canvas, glyph(size))

    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    if round_icon:
        mdraw.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        mdraw.rounded_rectangle(
            [0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255
        )
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask)
    return out


# Minimal 5x7 pixel font, so the banner needs no system fonts.
PIXEL_FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10001", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    " ": ["00000"] * 7,
}


def pixel_text(draw, origin, text, scale, color):
    x0, y0 = origin
    for index, char in enumerate(text):
        rows = PIXEL_FONT.get(char, PIXEL_FONT[" "])
        for row, bits in enumerate(rows):
            for col, bit in enumerate(bits):
                if bit == "1":
                    x = x0 + (index * 6 + col) * scale
                    y = y0 + row * scale
                    draw.rectangle([x, y, x + scale - 1, y + scale - 1], fill=color)


def banner():
    width, height = 320, 180
    img = Image.new("RGBA", (width, height), BG_BOTTOM + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(height):
        t = y / height
        color = tuple(int(BG_TOP[c] * (1 - t) + BG_BOTTOM[c] * t) for c in range(3))
        draw.line([(0, y), (width, y)], fill=color + (255,))

    logo = glyph(110).resize((110, 110), Image.LANCZOS)
    img.alpha_composite(logo, (18, 35))

    pixel_text(draw, (152, 52), "TURMUX", 4, (255, 255, 255, 255))
    pixel_text(draw, (152, 88), "RTX", 4, ACCENT)
    pixel_text(draw, (154, 134), "TERMINAL", 2, (143, 163, 176, 255))
    img.convert("RGB").save(os.path.join(RES, "drawable", "banner.png"))


def main():
    for density, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        compose(SCALE, False).resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher.png")
        )
        compose(SCALE, True).resize((size, size), Image.LANCZOS).save(
            os.path.join(folder, "ic_launcher_round.png")
        )
    banner()
    print("Generated Turmux RTX launcher icons and banner.")


if __name__ == "__main__":
    main()
