#!/usr/bin/env python3
"""Generate SalviaBrowxer launcher art.

Renders the gold/blue orbital ring app icon and writes it to the mipmap-* densities
plus a large raster brand mark in drawable-nodpi.

The in-app brand mark is drawn live in Compose (see OrbitalBrandMark.kt), so this
script only produces launcher art and the raster brand mark.
"""
from PIL import Image, ImageDraw, ImageFilter
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BLACK = (5, 5, 7, 255)
GOLD = (255, 179, 0)
GOLD_LIGHT = (255, 213, 79)
BLUE = (33, 150, 243)
BLUE_LIGHT = (79, 195, 247)


def draw_ring_icon(size=1024):
    img = Image.new("RGBA", (size, size), BLACK)
    cx = cy = size / 2
    r = size * 0.46
    stroke = int(size * 0.055)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    rings = [
        (0.96, 0.55, -24, GOLD, GOLD_LIGHT),
        (0.86, 0.48, 18, BLUE, BLUE_LIGHT),
        (0.76, 0.42, 62, GOLD, GOLD_LIGHT),
    ]
    for rx_f, ry_f, tilt, base, light in rings:
        rx, ry = r * rx_f, r * ry_f
        # Glow pass
        temp = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        td = ImageDraw.Draw(temp)
        steps = 240
        pts = []
        for i in range(steps + 1):
            t = 2 * math.pi * i / steps
            x = cx + rx * math.cos(t)
            y = cy + ry * math.sin(t)
            a = math.radians(tilt)
            xr = cx + (x - cx) * math.cos(a) - (y - cy) * math.sin(a)
            yr = cy + (x - cx) * math.sin(a) + (y - cy) * math.cos(a)
            pts.append((xr, yr))
        td.line(pts, fill=base + (255,), width=int(stroke * 1.6), joint="curve")
        glow = Image.alpha_composite(glow, temp.filter(ImageFilter.GaussianBlur(size * 0.012)))

        # Crisp ring with specular highlight arc
        crisp = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        cd = ImageDraw.Draw(crisp)
        cd.line(pts, fill=base + (255,), width=stroke, joint="curve")
        # Highlight arc (upper area of the ring)
        hpts = pts[: steps // 4]
        cd.line(hpts, fill=light + (255,), width=max(2, stroke // 2), joint="curve")
        glow = Image.alpha_composite(glow, crisp)

    # Blue nebula core
    core = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cd = ImageDraw.Draw(core)
    cd.ellipse(
        [cx - r * 0.62, cy - r * 0.62, cx + r * 0.62, cy + r * 0.62],
        fill=(10, 40, 80, 110),
    )
    core = core.filter(ImageFilter.GaussianBlur(size * 0.03))

    img = Image.alpha_composite(img, core)
    img = Image.alpha_composite(img, glow)
    # Luminous outer rim
    rim = ImageDraw.Draw(img)
    rim.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255, 36), width=max(2, size // 512))
    return img


def main():
    # Orbital icon at every launcher density
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    master = draw_ring_icon(1024)
    for name, px in densities.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        master.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
        master.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_round.png"))

    # Large brand mark for in-app use
    master.resize((512, 512), Image.LANCZOS).save(
        os.path.join(RES, "drawable-nodpi", "salvia_brand.png")
    )
    print("Brand assets generated OK")


if __name__ == "__main__":
    main()
