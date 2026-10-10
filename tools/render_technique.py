"""
Offline test of technique.fsh over the synthetic landscape from render_world.py.

    python3 tools/render_technique.py out.png --fx dismantle|dismantle_after|cleave|web|flash|fuga_hand|fuga_bow|
                                              fuga_draw|fuga_flight|fuga_blast|fuga_late|domain|ws|ws_split
"""
import argparse
import math
import os
import sys

import moderngl
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import render_world as rw  # noqa: E402

MAXS = 48


def norm(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def slash(origin, d, roll, L, W, age, kind=0, speed=900.0, seed=1.0, inten=1.0):
    d = norm(d)
    right = norm(np.cross(d, [0, 1, 0]))
    up = np.cross(right, d)
    r = math.radians(roll)
    side = right * math.cos(r) + up * math.sin(r)
    return (np.append(origin, age), np.append(d, L), np.append(side, W), (kind, speed, seed, inten))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--fx", default="dismantle")
    ap.add_argument("--w", type=int, default=800)
    ap.add_argument("--h", type=int, default=450)
    a = ap.parse_args()
    g = lambda x, z: float(rw.height(np.array(x), np.array(z)))

    cam = np.array([0.0, g(0, 0) + 1.8, 0.0])
    look = cam + np.array([0, -0.15, 1.0])
    fov = 70
    U = {}
    slashes = []
    fx = a.fx
    if fx.startswith("dismantle"):
        age = 0.014 if fx == "dismantle" else 0.5
        cam = np.array([-6.0, g(-6, 0) + 3.5, -4.0])
        look = np.array([0.0, g(0, 20), 20.0])
        o = np.array([0.0, g(0, 0) + 1.6, 0.0])
        slashes.append(slash(o, [0.05, -0.05, 1.0], 30, 48, 16, age))
        slashes.append(slash(o, [0.2, -0.08, 1.0], 150, 48, 16, age - 0.08, seed=2))
    if fx == "cleave":
        tgt = np.array([0.0, g(0, 6) + 1.0, 6.0])
        rng = np.random.default_rng(3)
        for i in range(14):
            d = norm(rng.normal(size=3))
            c = tgt + rng.normal(size=3) * np.array([0.3, 0.6, 0.3])
            slashes.append(slash(c - d * 1.2, d, 0, 2.4, 0, 0.05 - i * 0.012, kind=1))
        look = tgt
    if fx == "web":
        c = np.array([0.0, g(0, 10), 10.0])
        cam = np.array([-14.0, g(-14, -4) + 9, -4.0])
        look = c
        U.update(WebC=tuple(c) + (24.0,), WebP=(0.6, 4.0, 1.0, 0.0))
    if fx == "flash":
        p = np.array([0.0, g(0, 3) + 1.4, 3.0])
        look = p
        U.update(BfC=tuple(p) + (0.11,), BfSeed=5.0)
    if fx.startswith("fuga"):
        eye = np.array([0.0, g(0, 0) + 1.62, 0.0])
        aim = norm([0.1, -0.08, 1.0])
        right = norm(np.cross(aim, [0, 1, 0]))
        up = np.cross(right, aim)
        hand = eye + aim * 0.55 + right * 0.3 - up * 0.3
        bow = eye + aim * 0.7 - up * 0.2
        tgt = np.array([10.0, g(10, 90), 90.0])
        cam = eye + right * 2.6 + aim * 0.5 + up * 0.2
        look = eye + aim * 1.2
        st = dict(fuga_hand=(1, 0, 0, -1, -1), fuga_bow=(0.4, 1, 0, -1, -1), fuga_draw=(0.2, 1, 1, -1, -1),
                  fuga_flight=(0, 0, 0, 0.55, -1), fuga_blast=(0, 0, 0, -1, 0.9), fuga_late=(0, 0, 0, -1, 3.5))[fx]
        if fx == "fuga_hand":
            cam = eye + right * 1.0 + aim * 1.2 - up * 0.1
            look = hand
        if fx == "fuga_flight":
            head = eye + (tgt - eye) * 0.55
            cam = head - aim * 7 + up * 1.8 + right * 1.0
            look = head + aim * 10
        if fx in ("fuga_blast", "fuga_late"):
            cam = tgt - aim * 120 + right * 40 + np.array([0, 25, 0])
            look = tgt + np.array([0, 50, 0])
            fov = 75
        U.update(FHand=tuple(hand) + (st[0],), FBow=tuple(bow) + (st[1],), FDir=tuple(aim) + (st[2],),
                 FUp=tuple(up) + (st[3],), FFrom=tuple(bow) + (1.0,), FTarget=tuple(tgt) + (st[4],), FRadius=34.0, FOn=1.0)
    if fx == "domain":
        c = np.array([0.0, g(0, 0), 0.0])
        cam = np.array([-30.0, g(-30, -40) + 25, -40.0])
        look = np.array([0.0, g(0, 30) + 2, 30.0])
        U.update(DomC=tuple(c) + (110.0,), DomP=(3.0, 1.0, 110.0, 1.0))
    if fx.startswith("ws"):
        o = np.array([0.0, g(0, 0), 0.0])
        d = norm([0.0, 0, 1.0])
        cam = o - d * 8 + np.array([0.6, 3.0, 0])
        look = o + d * 200 + np.array([0, 10, 0])
        fov = 75
        U.update(WsO=tuple(o) + (0.25 if fx == "ws" else 1.2,), WsD=tuple(d) + (1000.0,),
                 WsP=(0.0 if fx == "ws" else 0.025, 3.0, 1.0, 1.0))

    fwd, right, up = rw.look_basis(look - cam)
    aspect = a.w / a.h
    P = rw.perspective(fov, aspect)
    view = np.stack([right, up, -fwd])
    ys, xs = np.mgrid[0:a.h, 0:a.w]
    px = ((xs + 0.5) / a.w * 2 - 1) * math.tan(math.radians(fov) / 2) * aspect
    py = (1 - (ys + 0.5) / a.h * 2) * math.tan(math.radians(fov) / 2)
    rd = rw.norm(fwd * 1 + right * px[..., None] + up * py[..., None])
    col, dist = rw.landscape(cam, rd)
    vz = -(dist * (rd @ fwd))
    with np.errstate(all="ignore"):
        ndc = np.where(np.isfinite(dist), (P[2, 2] * vz + P[2, 3]) / -vz, 1.0)
    depth = np.clip(ndc * 0.5 + 0.5, 0, 1).astype(np.float32)
    depth[~np.isfinite(dist)] = 1.0

    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=rw.load("fullscreen.vsh"), fragment_shader=rw.load("technique.fsh"))
    vao = ctx.vertex_array(prog, [])
    scene = ctx.texture((a.w, a.h), 3, col[::-1].copy().tobytes(), dtype="f4")
    dtex = ctx.texture((a.w, a.h), 1, depth[::-1].copy().tobytes(), dtype="f4")
    scene.use(0)
    dtex.use(1)
    vals = {"SceneTex": 0, "DepthTex": 1, "Proj": tuple(P.T.flatten()), "InvProj": tuple(np.linalg.inv(P).T.flatten()),
            "ViewRot": tuple(view.T.flatten()), "InvViewRot": tuple(view.flatten()), "Time": 12.0, "Steps": 64,
            "NSlash": len(slashes)}
    rel = lambda v4: (v4[0] - cam[0], v4[1] - cam[1], v4[2] - cam[2], v4[3])
    for k, v in U.items():
        if k in ("WebC", "BfC", "FHand", "FBow", "FFrom", "FTarget", "DomC", "WsO"):
            v = rel(v)
        vals[k] = v
    for k, v in vals.items():
        if k in prog:
            prog[k].value = v
    if slashes:
        for name, idx in (("SlO", 0), ("SlD", 1), ("SlS", 2), ("SlP", 3)):
            arr = []
            for s in slashes:
                v = s[idx]
                if idx == 0:
                    v = (v[0] - cam[0], v[1] - cam[1], v[2] - cam[2], v[3])
                arr.append(tuple(v))
            while len(arr) < MAXS:
                arr.append((0, 0, 0, 0))
            prog[name].value = arr
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
