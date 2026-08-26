#!/usr/bin/env python3
"""Draws the draugr app icon and writes every size both platforms need.

The mark is the app's own visual language rather than a picture of an emulator: black ground,
cyan bracket corners from `BracketPanel`, and a dagaz rune — two triangles meeting, the Old
Norse letter that opens "draugr" — with the same magenta/cyan chromatic offset `GlitchText`
uses. It has to survive being 48px on a launcher, so it stays to four strokes and no detail.

    python3 tools/generate-icons.py
"""

from __future__ import annotations

import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent

BACKGROUND = (0, 0, 0, 255)
CYAN = (0, 255, 255, 255)
MAGENTA = (232, 121, 249, 255)
ACID = (34, 222, 128, 255)

# Android's adaptive icon crops to the centre, so the mark lives inside this fraction of the
# canvas. The same inset looks right on iOS, which does not crop at all.
SAFE = 0.62


def draw_mark(size: int, inset: float, with_brackets: bool) -> Image.Image:
    image = Image.new("RGBA", (size, size), BACKGROUND)
    draw = ImageDraw.Draw(image)

    stroke = max(2, round(size * 0.035))
    margin = size * (1 - inset) / 2
    box = (margin, margin, size - margin, size - margin)
    left, top, right, bottom = box
    width = right - left
    height = bottom - top

    def rune(offset_x: float, colour, thickness: int) -> None:
        # Dagaz: an hourglass on its side, two triangles meeting at the centre.
        cx = left + width / 2 + offset_x
        cy = top + height / 2
        half_w = width * 0.34
        half_h = height * 0.34
        points = [
            ((cx - half_w, cy - half_h), (cx, cy)),
            ((cx - half_w, cy + half_h), (cx, cy)),
            ((cx + half_w, cy - half_h), (cx, cy)),
            ((cx + half_w, cy + half_h), (cx, cy)),
            ((cx - half_w, cy - half_h), (cx - half_w, cy + half_h)),
            ((cx + half_w, cy - half_h), (cx + half_w, cy + half_h)),
        ]
        for start, end in points:
            draw.line([start, end], fill=colour, width=thickness)

    # Chromatic aberration, drawn first so the cyan reads on top.
    shift = max(1, round(size * 0.012))
    rune(-shift, MAGENTA, stroke)
    rune(shift, ACID, stroke)
    rune(0, CYAN, stroke)

    if with_brackets:
        arm = width * 0.28
        bracket = max(2, round(size * 0.028))
        corners = [
            [(left, top), (left + arm, top)],
            [(left, top), (left, top + arm)],
            [(right - arm, top), (right, top)],
            [(right, top), (right, top + arm)],
            [(left, bottom - arm), (left, bottom)],
            [(left, bottom), (left + arm, bottom)],
            [(right - arm, bottom), (right, bottom)],
            [(right, bottom - arm), (right, bottom)],
        ]
        for start, end in corners:
            draw.line([start, end], fill=CYAN, width=bracket)

    return image


def write(image: Image.Image, path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    print(f"{path.relative_to(ROOT)}  {image.width}x{image.height}")


def main() -> None:
    android_res = ROOT / "composeApp/src/androidMain/res"

    # Legacy launcher icons: the full mark, brackets included.
    for density, size in {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }.items():
        icon = draw_mark(size, SAFE, with_brackets=True)
        write(icon, android_res / f"mipmap-{density}/ic_launcher.png")
        write(icon, android_res / f"mipmap-{density}/ic_launcher_round.png")

    # Adaptive icon layers. The foreground is 108dp with the mark inside the 66dp safe zone, so
    # it draws without brackets: whatever the launcher's mask is, the rune survives it.
    for density, size in {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
        "xxxhdpi": 432,
    }.items():
        foreground = draw_mark(size, SAFE * 0.66, with_brackets=False)
        write(foreground, android_res / f"mipmap-{density}/ic_launcher_foreground.png")

    # iOS wants one 1024 square with no transparency.
    ios_icon = draw_mark(1024, SAFE, with_brackets=True).convert("RGB")
    write(ios_icon, ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-1024.png")

    # The iOS launch screen scales its image to the screen width, whatever size the asset is,
    # so the only way to make the mark smaller is to draw it inside more black. All three scales
    # are filled: with just 1x, iOS stretched a 512px asset across a 3x screen.
    launch = ROOT / "iosApp/iosApp/Assets.xcassets/LaunchMark.imageset"
    for scale, size in ((1, 400), (2, 800), (3, 1200)):
        write(draw_mark(size, SAFE * 0.45, with_brackets=True), launch / f"mark-{scale}x.png")
    write(draw_mark(1024, SAFE, with_brackets=True), ROOT / "docs/icon.png")


if __name__ == "__main__":
    main()
