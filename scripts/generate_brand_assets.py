#!/usr/bin/env python3
"""Generate SalviaBrowxer launcher assets from the transparent official emblem."""

from PIL import Image
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
LOGO_PATH = os.path.join(RES, "drawable-nodpi", "salvia_official_logo.webp")
LAUNCHER_BACKGROUND = (8, 14, 25, 255)
RESAMPLING = getattr(Image, "Resampling", Image)


def contain(logo, size, fill_fraction):
    """Scale a square logo into a centered transparent canvas without cropping it."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mark = logo.copy()
    mark.thumbnail(
        (int(size * fill_fraction), int(size * fill_fraction)),
        RESAMPLING.LANCZOS,
    )
    canvas.alpha_composite(mark, ((size - mark.width) // 2, (size - mark.height) // 2))
    return canvas


def launcher_icon(logo, size):
    """Legacy launcher PNGs use a dark tile; adaptive icons use a transparent foreground."""
    tile = Image.new("RGBA", (size, size), LAUNCHER_BACKGROUND)
    tile.alpha_composite(contain(logo, size, 0.88))
    return tile.convert("RGB")


def main():
    logo = Image.open(LOGO_PATH).convert("RGBA")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        icon = launcher_icon(logo, px)
        icon.save(os.path.join(folder, "ic_launcher.png"), optimize=True)
        icon.save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)

    drawable_folder = os.path.join(RES, "drawable-nodpi")
    os.makedirs(drawable_folder, exist_ok=True)
    contain(logo, 512, 0.625).save(
        os.path.join(drawable_folder, "salvia_launcher_foreground.png"),
        optimize=True,
    )
    contain(logo, 512, 0.625).save(
        os.path.join(drawable_folder, "salvia_brand_foreground.png"),
        optimize=True,
    )
    contain(logo, 512, 0.96).save(
        os.path.join(drawable_folder, "salvia_brand.png"),
        optimize=True,
    )
    print("Official Salvia launcher and brand assets generated.")


if __name__ == "__main__":
    main()
