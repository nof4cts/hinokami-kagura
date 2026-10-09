"""
Offline test of the orbit shot shader (orbit.fsh) with the real Earth textures.

    python3 tools/render_orbit.py out.png --shot wide|barrel|muzzle|fire [--charge 0.6] [--fire -1] [--proj 0]
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


EARTH_C = np.array([0.0, -1750.0, 2550.0])
AXIS = norm(EARTH_C)
BX = norm(np.cross([0, 1, 0], AXIS))
BY = np.cross(AXIS, BX)


def shot(name):
    """Camera presets, matching CutsceneShots.java."""
    if name == "wide":
        pos = AXIS * -18 + BX * 46 + BY * 16
        look = AXIS * 2
    elif name == "barrel":
        pos = AXIS * -6 + BX * 8.5 + BY * 5.5
        look = AXIS * 22
    elif name == "muzzle":
        pos = AXIS * 40 + BX * 10 + BY * -4
        look = AXIS * 18
    else:  # fire: looking down the beam toward Earth from beside the breech
        pos = AXIS * -30 + BX * 12 + BY * 9
        look = AXIS * 120
    fwd = norm(look - pos)
    right = norm(np.cross(fwd, [0, 1, 0]))
    up = np.cross(right, fwd)
    return pos, fwd, up, right


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--shot", default="wide")
    ap.add_argument("--charge", type=float, default=0.0)
    ap.add_argument("--fire", type=float, default=-1.0)
    ap.add_argument("--proj", type=float, default=0.0)
    ap.add_argument("--fov", type=float, default=45)
    ap.add_argument("--w", type=int, default=960)
    ap.add_argument("--h", type=int, default=540)
    a = ap.parse_args()

    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=load("fullscreen.vsh"), fragment_shader=load("orbit.fsh"))
    vao = ctx.vertex_array(prog, [])

    def tex(path):
        im = Image.open(os.path.join(ROOT, "textures", "space", path)).convert("RGBA")
        t = ctx.texture(im.size, 4, im.tobytes())
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        return t

    day, night = tex("earth_day_clouds.png"), tex("earth_night_ocean.png")
    day.use(0)
    night.use(1)
    pos, fwd, up, right = shot(a.shot)
    vals = {"EarthDay": 0, "EarthNight": 1, "CamPos": tuple(pos), "CamFwd": tuple(fwd), "CamUp": tuple(up),
            "CamRight": tuple(right), "TanHalfFov": math.tan(math.radians(a.fov) / 2), "Aspect": a.w / a.h,
            "Time": 12.0, "Charge": a.charge, "FireAge": a.fire, "ProjT": a.proj, "Steps": 180}
    for k, v in vals.items():
        if k in prog:
            prog[k].value = v
    out = ctx.texture((a.w, a.h), 4, dtype="f4")
    fbo = ctx.framebuffer([out])
    fbo.use()
    vao.render(moderngl.TRIANGLES, vertices=3)
    img = np.frombuffer(out.read(), dtype=np.float32).reshape(a.h, a.w, 4)[::-1, :, :3]
    # quick display tonemap (the game uses composite.fsh)
    m = img.max(axis=-1, keepdims=True)
    mapped = np.where(m > 0.8, 0.8 + 0.2 * (1 - np.exp(-(m - 0.8) / 0.2)), m)
    img = img * (mapped / np.maximum(m, 1e-6))
    Image.fromarray((np.clip(img, 0, 1) ** (1 / 1.0) * 255).astype(np.uint8)).save(a.out)
    print("saved", a.out)


if __name__ == "__main__":
    main()
