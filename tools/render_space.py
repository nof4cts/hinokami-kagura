"""
Offline test of the space shader (space.fsh) with the real textures, one shot per call.

    python3 tools/render_space.py out.png --shot <name> [--t <0..1 within the shot>]

Shot names and camera presets mirror client/cinematic/Shots.java.
"""
import argparse
import math
import os
import re

import moderngl
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ton618")
FX = os.path.join(ROOT, "shaders", "fx")


def load(name):
    src = open(os.path.join(FX, name)).read()
    return re.sub(r'#include\s+"([^"]+)"', lambda m: open(os.path.join(FX, m.group(1))).read(), src)


def norm(v):
    v = np.array(v, dtype=float)
    return v / np.linalg.norm(v)


# galaxy frame (mirror of space.fsh galaxyFrame / SpaceFrame.java)
GAL_SCALE = 9000.0
GAL_C = np.array([-30000.0, 22000.0, 150000.0])
GN = norm([0.25, 1.0, -0.55])
GX = norm(np.cross(GN, [0, 0, 1]))
GZ = np.cross(GX, GN)
SUN_G = (GX * math.cos(2.35) + GZ * math.sin(2.35)) * 8.5
AXIS = norm(GAL_C + SUN_G * GAL_SCALE)
BX = norm(np.cross([0, 1, 0], AXIS))
BY = np.cross(AXIS, BX)
EARTH_C = np.array([0.0, -1750.0, 2550.0])


def lerp(a, b, t):
    return a + (b - a) * t


def ease(t):
    t = min(max(t, 0), 1)
    return t * t * (3 - 2 * t)


def C(a, x, y):
    return AXIS * a + BX * x + BY * y


def shot(name, t):
    """returns scene, pos, look, fov, extra uniforms"""
    u = ease(t)
    if name == "cannon_wide":
        return 0, lerp(C(-52, 24, 11), C(-44, 20, 9), u), C(6, 0, 0), 55, dict(Charge=0.15 * t)
    if name == "cannon_barrel":
        return 0, lerp(C(-6, 8.5, 5.5), C(12, 7.5, 5.0), u), C(22 + 8 * u, 0, 0), 52, dict(Charge=0.3 + 0.7 * t)
    if name == "cannon_behind":
        return 0, C(-42, 9, 7), C(200, 0, 0), 55, dict(Charge=1.0, FireAge=0.05 + 1.2 * t)
    if name == "crossing":
        return 1, np.zeros(3), np.array([0, 0, 1.0]), 70, dict(Progress=t)
    target = EARTH_C + norm([0.05, 1.0, -0.62]) * 2000
    up = norm(target - EARTH_C)
    if name == "earth_projectile":
        side = norm(np.cross(up, [1, 0, 0]))
        pos = target + up * lerp(3200, 1600, u) + side * 1400
        return 2, pos, target, 45, dict(Kind=0, FireAge=1.0, Progress=t)
    if name == "sun_close":
        return 3, np.array([0, 300, -3400.0]) + np.array([300, 0, 0]) * u, np.array([0, 0, 0.0]), 55, dict(Kind=1, Progress=t)
    if name == "sun_approach":
        sun = norm([0.78, 0.52, -0.28])
        side = norm(np.cross(sun, [0, 1, 0]))
        pos = EARTH_C + norm(side * 1.0 - sun * 0.55 + np.array([0, 0.25, 0])) * lerp(9000, 7000, u)
        return 2, pos, EARTH_C + sun * 2600, 62, dict(Kind=1, Progress=lerp(0.05, 0.85, t))
    if name == "angel_orbit":
        side = norm(np.cross(up, [1, 0, 0]))
        pos = target + up * lerp(2600, 1700, u) + side * 1700
        return 2, pos, target + up * 900, 55, dict(Kind=2, Progress=t)
    if name == "pierce_wide":
        bd = norm([0.6, -0.25, 0.76])
        side = norm(np.cross(bd, [0, 1, 0]))
        pos = side * lerp(-7800, -7000, u) + np.array([0, 1400, 0]) - bd * 2500
        return 4, pos, bd * 600, 55, dict(Progress=t)
    if name == "pierce_behind":
        bd = norm([0.6, -0.25, 0.76])
        pos = -bd * 9000 + np.array([0, 600, 0])
        return 4, pos, np.zeros(3), 50, dict(Progress=t)
    raise SystemExit("unknown shot " + name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--shot", default="cannon_wide")
    ap.add_argument("--t", type=float, default=0.5)
    ap.add_argument("--w", type=int, default=960)
    ap.add_argument("--h", type=int, default=540)
    ap.add_argument("--steps", type=int, default=200)
    a = ap.parse_args()

    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=load("fullscreen.vsh"), fragment_shader=load("space.fsh"))
    vao = ctx.vertex_array(prog, [])

    def tex(path, comps):
        im = Image.open(os.path.join(ROOT, "textures", "space", path)).convert("RGBA" if comps == 4 else "L")
        t = ctx.texture(im.size, comps, im.tobytes())
        t.build_mipmaps()
        t.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR)
        return t

    tex("earth_day_clouds.png", 4).use(0)
    tex("earth_night_ocean.png", 4).use(1)
    tex("earth_height.png", 1).use(2)
    scene, pos, look, fov, extra = shot(a.shot, a.t)
    fwd = norm(look - pos)
    right = norm(np.cross(fwd, [0, 1, 0]))
    up = np.cross(right, fwd)
    vals = {"EarthDay": 0, "EarthNight": 1, "EarthHeight": 2, "CamPos": tuple(pos), "CamFwd": tuple(fwd),
            "CamUp": tuple(up), "CamRight": tuple(right), "TanHalfFov": math.tan(math.radians(fov) / 2),
            "Aspect": a.w / a.h, "Time": 12.0, "Steps": a.steps, "Scene": scene, "Kind": 0,
            "Charge": 0.0, "FireAge": -1.0, "Progress": 0.0}
    vals.update(extra)
    for k, v in vals.items():
        if k in prog:
            prog[k].value = v
    out = ctx.texture((a.w, a.h), 4, dtype="f4")
    fbo = ctx.framebuffer([out])
    fbo.use()
    vao.render(moderngl.TRIANGLES, vertices=3)
    img = np.frombuffer(out.read(), dtype=np.float32).reshape(a.h, a.w, 4)[::-1, :, :3]
    m = img.max(axis=-1, keepdims=True)
    mapped = np.where(m > 0.8, 0.8 + 0.2 * (1 - np.exp(-(m - 0.8) / 0.2)), m)
    img = img * (mapped / np.maximum(m, 1e-6))
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(a.out)
    print("saved", a.out)


if __name__ == "__main__":
    main()
