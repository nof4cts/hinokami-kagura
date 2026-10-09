"""
Generates the placeholder textures for Stage 1 (run from the project root):

    python3 tools/gen_textures.py

Particle textures are WHITE with the shape in the alpha channel: the particle code tints them
from the shared sun palette, and additive blending uses alpha as intensity.
Blade textures follow the UV layout in geo/item/nichirin_blade.geo.json (see docs/ASSETS.md).
Replace any of these with hand-painted versions; keep the sizes and layout.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "sunbreathing", "textures")
PARTICLE = os.path.join(ROOT, "particle")
ITEM = os.path.join(ROOT, "item")
os.makedirs(PARTICLE, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)
random.seed(7)


def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def white_alpha(size, fn):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size
            v = (y + 0.5) / size
            a = clamp(fn(u, v))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    return img


# --- value noise for organic flame edges
_grid = {}


def vnoise(x, y, seed):
    def h(ix, iy):
        k = (ix, iy, seed)
        if k not in _grid:
            _grid[k] = random.random()
        return _grid[k]

    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h(ix, iy) + (h(ix + 1, iy) - h(ix, iy)) * sx
    b = h(ix, iy + 1) + (h(ix + 1, iy + 1) - h(ix, iy + 1)) * sx
    return a + (b - a) * sy


# --- sun_flame_0..5: teardrop flame that breaks apart frame by frame
for i in range(6):
    k = i / 5.0

    def flame(u, v, k=k, i=i):
        # v: 0 top .. 1 bottom. Teardrop: wide base near bottom, pointed tip up.
        cy = 0.62
        dx = (u - 0.5)
        dy = (v - cy)
        width = 0.30 * clamp((v - 0.04) / 0.62) ** 0.85 + 0.015
        body = 1 - smooth(0.6, 1.0, abs(dx) / max(width, 1e-3))
        body *= 1 - smooth(0.28, 0.40, dy)  # rounded bottom
        n = vnoise(u * 5, v * 5 + i * 1.7, 1) * 0.6 + vnoise(u * 11, v * 11 + i * 3.1, 2) * 0.4
        erosion = smooth(0.78 - 0.5 * k, 1.02 - 0.45 * k, n + (1 - v) * 0.3 * k)
        core = 1 - smooth(0.0, 0.22, math.hypot(dx * 1.6, (v - 0.68) * 1.2))
        return body * (1 - erosion * 0.9) * (0.75 + 0.25 * core) * (1 - 0.35 * k)

    white_alpha(16, flame).save(os.path.join(PARTICLE, f"sun_flame_{i}.png"))

# --- embers: tiny hot dots
for i in range(2):
    def ember(u, v, i=i):
        d = math.hypot(u - 0.5, v - 0.5)
        return (1 - smooth(0.05, 0.42 + 0.06 * i, d)) ** (1.6 - 0.4 * i)

    white_alpha(8, ember).save(os.path.join(PARTICLE, f"sun_ember_{i}.png"))


# --- sun_ray: bright at u=0 (head), fading to u=1 (tail); thin across v
def ray(u, v):
    across = math.exp(-((v - 0.5) ** 2) / (2 * 0.16 ** 2))
    along = (1 - u) ** 1.3
    return across * along * 1.2


white_alpha(16, ray).save(os.path.join(PARTICLE, "sun_ray.png"))


# --- sun_flash: radial bloom with 6 soft spikes
def flash(u, v):
    dx, dy = u - 0.5, v - 0.5
    d = math.hypot(dx, dy)
    a = math.atan2(dy, dx)
    glow = math.exp(-(d ** 2) / (2 * 0.13 ** 2))
    halo = math.exp(-(d ** 2) / (2 * 0.28 ** 2)) * 0.35
    spikes = (abs(math.cos(a * 3)) ** 24) * math.exp(-d / 0.22) * 0.8
    return glow + halo + spikes


white_alpha(32, flash).save(os.path.join(PARTICLE, "sun_flash.png"))


# --- sun_glow: very soft puff
def glow(u, v):
    d = math.hypot(u - 0.5, v - 0.5)
    return math.exp(-(d ** 2) / (2 * 0.2 ** 2))


white_alpha(16, glow).save(os.path.join(PARTICLE, "sun_glow.png"))


# ======================================================================= blade (32x32)
def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


base = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
glowm = Image.new("RGBA", (32, 32), (0, 0, 0, 255))  # eyes render type: black = no glow
bp, gp = base.load(), glowm.load()

CRIMSON = (150, 14, 10, 255)
ORANGE = (255, 110, 18, 255)
GOLD = (255, 205, 90, 255)
WHITE_HOT = (255, 246, 225, 255)

# Blade broad face: region (0,0) 4x17 plus tip (0,17) 4x2.
# Columns: 0 = spine, 1-2 = body, 3 = cutting edge (hamon line).
for y in range(19):
    t = y / 18.0  # 0 at the tip end of the texture .. 1 near the guard (UV v grows downward)
    for x in range(4):
        if x == 0:
            c = (34, 33, 38, 255)
        elif x == 3:
            c = (88, 84, 96, 255)
        else:
            sheen = 6 if (x + y) % 5 == 0 else 0
            c = (12 + sheen, 11 + sheen, 14 + sheen, 255)
        bp[x, y] = c
        # glow: crimson near the guard, orange toward the tip, white-hot edge
        g = lerp(ORANGE, CRIMSON, t)
        if x == 3:
            g = lerp(WHITE_HOT, GOLD, t)
        elif x == 0:
            g = lerp((120, 30, 8, 255), (70, 6, 6, 255), t)
        gp[x, y] = g
# Blade thin edge strip: region (4,0) 1x17
for y in range(19):
    t = y / 18.0
    bp[4, y] = (70, 66, 76, 255)
    gp[4, y] = lerp(GOLD, ORANGE, t)

# Habaki (gold collar): region (6,0) 2x2
for y in range(2):
    for x in range(6, 8):
        bp[x, y] = (196, 150, 58, 255)
        gp[x, y] = (40, 18, 4, 255)

# Tsuba (guard) top/bottom: region (8,0) 6x6, round black guard with a dark crimson rim
for y in range(6):
    for x in range(6):
        d = math.hypot(x - 2.5, y - 2.5)
        if d <= 2.9:
            c = (20, 18, 20, 255) if d < 2.2 else (92, 18, 14, 255)
        else:
            c = (0, 0, 0, 0)
        bp[8 + x, y] = c
# Tsuba sides: region (8,6) 6x1
for x in range(6):
    bp[8 + x, 6] = (70, 16, 12, 255)

# Handle wrap: region (14,0) 4x8, black ito over deep red diamonds
for y in range(8):
    for x in range(4):
        diamond = (x + y) % 4 in (0, 1) and (x - y) % 4 in (0, 1)
        bp[14 + x, y] = (130, 20, 16, 255) if diamond else (16, 14, 16, 255)
# Handle caps: region (14,8) 2x2
for y in range(2):
    for x in range(2):
        bp[14 + x, 8 + y] = (26, 24, 28, 255)

# Pommel (kashira): region (18,0) 3x3
for y in range(3):
    for x in range(3):
        bp[18 + x, y] = (40, 36, 42, 255) if (x, y) != (1, 1) else (90, 70, 40, 255)

base.save(os.path.join(ITEM, "nichirin_blade.png"))
glowm.save(os.path.join(ITEM, "nichirin_blade_glow.png"))
print("textures written to", os.path.abspath(ROOT))
