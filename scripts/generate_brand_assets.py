#!/usr/bin/env python3
"""Generate SalviaBrowxer brand assets.

Renders:
- The silver signature wordmark "Salar Farzaneh" with the long sweep underline
  (vector-like strokes, silver metallic gradient on black).
- The gold/blue orbital ring app icon.

Outputs land in app/src/main/res/drawable-nodpi and mipmap-* densities.
"""
from PIL import Image, ImageDraw, ImageFilter
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BLACK = (5, 5, 7, 255)
SILVER_STOPS = [
    (0.00, (154, 160, 174)),
    (0.18, (245, 247, 250)),
    (0.36, (232, 234, 237)),
    (0.52, (248, 250, 253)),
    (0.70, (192, 197, 206)),
    (0.86, (245, 247, 250)),
    (1.00, (154, 160, 174)),
]
GOLD = (255, 179, 0)
GOLD_LIGHT = (255, 213, 79)
BLUE = (33, 150, 243)
BLUE_LIGHT = (79, 195, 247)


def silver_gradient(width):
    grad = Image.new("RGB", (width, 1))
    px = grad.load()
    for x in range(width):
        t = x / max(1, width - 1)
        for i in range(len(SILVER_STOPS) - 1):
            t0, c0 = SILVER_STOPS[i]
            t1, c1 = SILVER_STOPS[i + 1]
            if t0 <= t <= t1:
                f = (t - t0) / (t1 - t0) if t1 > t0 else 0
                px[x, 0] = tuple(int(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
                break
    return grad


def cubic(p0, p1, p2, p3, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def stroke_points(w, h):
    """All polylines of the signature, in unit coordinates scaled to (w, h)."""
    P = lambda x, y: (x * w, y * h)
    lines = []

    # "Salar"
    lines.append(cubic(P(0.035, 0.62), P(0.050, 0.18), P(0.115, 0.10), P(0.145, 0.30)))
    lines.append(cubic(P(0.145, 0.30), P(0.165, 0.44), P(0.135, 0.60), P(0.105, 0.64)))
    lines.append(cubic(P(0.105, 0.64), P(0.075, 0.675), P(0.055, 0.60), P(0.085, 0.52)))
    lines.append(cubic(P(0.160, 0.44), P(0.200, 0.38), P(0.235, 0.42), P(0.215, 0.50)))
    lines.append(cubic(P(0.215, 0.50), P(0.200, 0.565), P(0.165, 0.60), P(0.185, 0.635)))
    lines.append([P(0.245, 0.40), P(0.238, 0.60)])
    lines.append(cubic(P(0.260, 0.44), P(0.300, 0.385), P(0.325, 0.44), P(0.300, 0.52)))
    lines.append(cubic(P(0.300, 0.52), P(0.285, 0.575), P(0.260, 0.60), P(0.285, 0.635)))
    lines.append([P(0.345, 0.40), P(0.340, 0.595)])
    lines.append(cubic(P(0.360, 0.44), P(0.400, 0.385), P(0.425, 0.44), P(0.400, 0.52)))
    lines.append(cubic(P(0.400, 0.52), P(0.385, 0.575), P(0.360, 0.60), P(0.385, 0.635)))
    lines += [
        [P(0.415, 0.40), P(0.445, 0.40)],
        [P(0.445, 0.40), P(0.438, 0.56)],
        cubic(P(0.438, 0.56), P(0.435, 0.60), P(0.455, 0.615), P(0.475, 0.595)),
    ]

    # "Farzaneh"
    lines.append(cubic(P(0.520, 0.22), P(0.565, 0.16), P(0.615, 0.17), P(0.635, 0.22)))
    lines.append(cubic(P(0.505, 0.42), P(0.560, 0.30), P(0.625, 0.26), P(0.665, 0.29)))
    lines.append([P(0.545, 0.35), P(0.600, 0.40)])
    lines.append([P(0.655, 0.44), P(0.630, 0.53)])
    lines.append(cubic(P(0.630, 0.53), P(0.622, 0.565), P(0.645, 0.575), P(0.665, 0.555)))
    lines.append(cubic(P(0.685, 0.40), P(0.725, 0.375), P(0.745, 0.43), P(0.715, 0.50)))
    lines.append(cubic(P(0.715, 0.50), P(0.700, 0.545), P(0.675, 0.585), P(0.700, 0.615)))
    lines.append([P(0.765, 0.40), P(0.735, 0.545)])
    lines.append(cubic(P(0.735, 0.545), P(0.728, 0.58), P(0.750, 0.59), P(0.770, 0.565)))
    lines.append([P(0.790, 0.40), P(0.785, 0.50)])
    lines.append(cubic(P(0.785, 0.50), P(0.783, 0.545), P(0.805, 0.555), P(0.830, 0.53)))
    lines.append(cubic(P(0.850, 0.42), P(0.845, 0.48), P(0.845, 0.53), P(0.860, 0.55)))
    lines.append(cubic(P(0.845, 0.465), P(0.870, 0.44), P(0.895, 0.445), P(0.885, 0.50)))
    lines.append([P(0.885, 0.50), P(0.875, 0.56)])

    # Underline flourish + F loop + comet sweep
    lines.append(cubic(P(0.30, 0.70), P(0.52, 0.86), P(0.72, 0.84), P(0.88, 0.68)))
    lines.append(cubic(P(0.88, 0.68), P(0.94, 0.615), P(0.965, 0.565), P(0.985, 0.50)))
    lines.append(cubic(P(0.50, 0.42), P(0.545, 0.50), P(0.60, 0.585), P(0.66, 0.68)))
    lines.append(cubic(P(0.885, 0.60), P(0.93, 0.545), P(0.965, 0.50), P(1.0, 0.455)))
    return lines


def draw_signature(width=1600):
    h = int(width * 0.36)
    img = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    stroke_layer = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(stroke_layer)
    thickness = max(3, int(width * 0.011))

    for line in stroke_points(width, h):
        d.line(line, fill=(255, 255, 255, 255), width=thickness, joint="curve")
        for pt in (line[0], line[-1]):
            r = thickness / 2
            d.ellipse([pt[0] - r, pt[1] - r, pt[0] + r, pt[1] + r], fill=(255, 255, 255, 255))

    # Metallic gradient clipped to strokes
    grad = silver_gradient(width).resize((width, h))
    alpha = stroke_layer.split()[3]
    signature = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    signature.paste(grad, (0, 0), alpha)

    # Soft glow underlay
    glow = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    glow_alpha = alpha.filter(ImageFilter.GaussianBlur(width * 0.004))
    glow.paste(Image.new("RGBA", (width, h), (255, 255, 255, 40)), (0, 0), glow_alpha)

    out = Image.alpha_composite(glow, signature)
    return out


def draw_ring_icon(size=1024):
    img = Image.new("RGBA", (size, size), BLACK)
    cx = cy = size / 2
    r = size * 0.46
    stroke = int(size * 0.055)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)

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
    # Signature wordmark (in-app branding)
    sig = draw_signature(1600)
    sig_path = os.path.join(RES, "drawable-nodpi", "salvia_signature.png")
    os.makedirs(os.path.dirname(sig_path), exist_ok=True)
    sig.save(sig_path)

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
