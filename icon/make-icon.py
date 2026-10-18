"""Draws the Litematica Turbo icon: pixel art on a 64x64 grid, scaled up without smoothing.

    python make-icon.py            -> icon.png (512x512) and icon-128.png next to this script
"""
import os
from PIL import Image, ImageDraw

N = 64
img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Background: dark rounded square, slightly lighter towards the top
for y in range(N):
    t = y / (N - 1)
    c = (int(34 - 12 * t), int(44 - 14 * t), int(66 - 18 * t), 255)
    d.line([(0, y), (N - 1, y)], fill=c)
mask = Image.new('L', (N, N), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=11, fill=255)
img.putalpha(mask)
d = ImageDraw.Draw(img)

# Speed lines behind the block
for y, x0, x1, col in [(22, 3, 13, (90, 200, 255, 255)), (30, 1, 11, (150, 225, 255, 255)),
                       (38, 4, 12, (90, 200, 255, 255)), (46, 2, 10, (60, 150, 220, 255))]:
    d.line([(x0, y), (x1, y)], fill=col)
    d.point((x0 - 1, y), fill=col[:3] + (120,))

# Schematic block (isometric cube), Litematica-like translucent blue
T, R, C, L = (32, 8), (53, 19), (32, 30), (11, 19)
B, BR, BL = (32, 54), (53, 43), (11, 43)
top, left, right = (128, 216, 255, 255), (58, 152, 224, 255), (36, 102, 186, 255)
d.polygon([T, R, C, L], fill=top)
d.polygon([L, C, B, BL], fill=left)
d.polygon([C, R, BR, B], fill=right)


def mid(a, b):
    return ((a[0] + b[0]) // 2, (a[1] + b[1]) // 2)


# Grid lines (schematic look): split each face in 2x2
grid_top, grid_side = (170, 232, 255, 255), (110, 190, 245, 255)
d.line([mid(T, L), mid(R, C)], fill=grid_top)
d.line([mid(T, R), mid(L, C)], fill=grid_top)
d.line([mid(L, BL), mid(C, B)], fill=grid_side)
d.line([mid(L, C), mid(BL, B)], fill=grid_side)
d.line([mid(C, B), mid(R, BR)], fill=(80, 150, 220, 255))
d.line([mid(C, R), mid(B, BR)], fill=(80, 150, 220, 255))

# Bright outline edges
edge = (215, 245, 255, 255)
for a, b in [(T, R), (R, C), (C, L), (L, T), (L, BL), (BL, B), (B, BR), (BR, R), (C, B)]:
    d.line([a, b], fill=edge)

# Lightning bolt over the front-right
bolt = [(47, 19), (36, 39), (44, 39), (38, 57), (58, 33), (49, 33), (56, 19)]
d.polygon(bolt, fill=(255, 214, 64, 255), outline=(200, 110, 0, 255))
d.line([(48, 20), (39, 37)], fill=(255, 244, 170, 255))  # highlight

here = os.path.dirname(os.path.abspath(__file__))
img.resize((512, 512), Image.NEAREST).save(os.path.join(here, 'icon.png'))
img.resize((128, 128), Image.NEAREST).save(os.path.join(here, 'icon-128.png'))
print('ok')
