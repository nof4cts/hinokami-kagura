"""
Offline test of the composite pass's impact frames: feeds a PNG in as the HDR frame and renders each mode.

    python3 tools/render_impact.py in.png out.png [cx cy]
"""
import os
import re
import sys

import moderngl
import numpy as np
from PIL import Image, ImageFilter

FX = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "shrine", "shaders", "fx")


def load(name):
    src = open(os.path.join(FX, name)).read()
    return re.sub(r'#include\s+"([^"]+)"', lambda m: open(os.path.join(FX, m.group(1))).read(), src)


def main():
    src, out = sys.argv[1], sys.argv[2]
    cx, cy = (float(sys.argv[3]), float(sys.argv[4])) if len(sys.argv) > 4 else (0.5, 0.5)
    im = Image.open(src).convert("RGB")
    w, h = im.size
    a = np.asarray(im).astype(np.float32) / 255
    hdr = a * (1 + 3 * np.clip(a.max(-1, keepdims=True) - 0.75, 0, 1) * 4)
    blur = np.asarray(im.filter(ImageFilter.GaussianBlur(18))).astype(np.float32) / 255
    bloom = np.clip(blur - 0.6, 0, None) * 3
    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=load("fullscreen.vsh"), fragment_shader=load("composite.fsh"))
    vao = ctx.vertex_array(prog, [])
    t0 = ctx.texture((w, h), 3, hdr[::-1].copy().tobytes(), dtype="f4")
    t1 = ctx.texture((w, h), 3, bloom[::-1].astype(np.float32).copy().tobytes(), dtype="f4")
    for t in (t0, t1):
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
    t0.use(0)
    t1.use(1)
    tiles = []
    for mode in range(0, 6):
        vals = dict(Hdr=0, Bloom=1, ScreenSize=(w, h), Time=3.0, BloomStrength=0.35, Exposure=1.0, Aberration=0.3,
                    Shockwave=(0.5, 0.5, 0, 0), Flash=(1, 1, 1, 0), Afterimage=0.0, Desaturate=0.0, Vignette=0.3,
                    Grain=0.2, Shake=(0, 0), Impact=(mode, 1.0, cx, 1 - cy), ImpactSeed=float(mode * 13 + 1),
                    Accent=(1.0, 0.12, 0.05), Punch=0.6 if mode else 0.0, GodRays=(cx, 1 - cy, 0.0, 0), GodRayColor=(1, 1, 1))
        for k, v in vals.items():
            if k in prog:
                prog[k].value = v
        o = ctx.texture((w, h), 4, dtype="f4")
        fbo = ctx.framebuffer([o])
        fbo.use()
        vao.render(moderngl.TRIANGLES, vertices=3)
        img = np.frombuffer(o.read(), dtype=np.float32).reshape(h, w, 4)[::-1, :, :3]
        tiles.append(Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)))
    grid = Image.new("RGB", (w * 2, h * 3))
    for i, t in enumerate(tiles):
        grid.paste(t, ((i % 2) * w, (i // 2) * h))
    grid.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
