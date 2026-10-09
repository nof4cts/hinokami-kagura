"""Draws the 32x32 remote item texture (run from project root)."""
from PIL import Image
import os
W = 32
im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
p = im.load()
def put(x, y, c):
    if 0 <= x < W and 0 <= y < W: p[x, y] = c
# diagonal handheld: body runs from bottom-left to top-right
import math
for y in range(W):
    for x in range(W):
        u = (x - (31 - y)) / math.sqrt(2)   # across
        v = (x + (31 - y)) / math.sqrt(2)   # along: bottom-left (0) -> top-right (44)
        if abs(u) <= 0.75 and 39 < v <= 43.5:   # antenna
            put(x, y, (255, 70, 50, 255) if v > 42.3 else (78, 78, 86, 255))
            continue
        if abs(u) <= 5.2 and 9 <= v <= 39:
            edge = abs(u) > 4.2 or v < 10 or v > 38
            shade = 34 + int(10 * (1 - abs(u) / 5.2))
            c = (shade, shade + 1, shade + 4, 255)
            if edge: c = (18, 18, 22, 255)
            if abs(u) <= 3.2 and 27 <= v <= 35:      # screen
                glow = 1 - abs(v - 31) / 5
                c = (int(200 + 55 * glow), int(110 + 60 * glow), 30, 255)
                if abs(u) < 0.6 or abs(v - 31) < 0.6: c = (255, 236, 200, 255)  # crosshair
            if abs(u) <= 1.6 and 16 <= v <= 19.5:    # fire button
                c = (215, 40, 32, 255) if v > 16.6 else (255, 110, 90, 255)
            if 12 <= v <= 13 and abs(u) <= 3.5:      # grip ridge
                c = (60, 60, 66, 255)
            put(x, y, c)
os.makedirs("src/main/resources/assets/ton618/textures/item", exist_ok=True)
im.save("src/main/resources/assets/ton618/textures/item/remote.png")
im.resize((256, 256), Image.NEAREST).save(os.environ.get("PREVIEW", "/dev/null") if os.environ.get("PREVIEW") else "/tmp/remote_preview.png")
