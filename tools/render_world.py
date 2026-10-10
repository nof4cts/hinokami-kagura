"""
Offline test of the in-world passes (sun.fsh, angel.fsh, blackhole.fsh) over a synthetic landscape.

    python3 tools/render_world.py out.png --pass sun|angel --shot <name> [--t 0..1]

The landscape is a procedural heightfield (rolling hills, a lake, a sky gradient) rendered in numpy with a
matching depth buffer, so the passes see the same inputs they get in game (scene colour + depth + matrices).
"""
import argparse
import math
import os
import re

import moderngl
import numpy as np
from PIL import Image

FX = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ton618", "shaders", "fx")
NEAR, FAR = 0.05, 1600.0


def load(name):
    src = open(os.path.join(FX, name)).read()
    return re.sub(r'#include\s+"([^"]+)"', lambda m: open(os.path.join(FX, m.group(1))).read(), src)


def norm(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v, axis=-1, keepdims=True)


def perspective(fov, aspect):
    f = 1 / math.tan(math.radians(fov) / 2)
    return np.array([[f / aspect, 0, 0, 0], [0, f, 0, 0],
                     [0, 0, (FAR + NEAR) / (NEAR - FAR), 2 * FAR * NEAR / (NEAR - FAR)], [0, 0, -1, 0]])


def height(x, z):
    return (6 * np.sin(x * 0.011) * np.cos(z * 0.013) + 3 * np.sin(x * 0.037 + z * 0.021)
            + 14 * np.sin(x * 0.0021 + 1.3) * np.sin(z * 0.0027) + 1.5 * np.sin(x * 0.17) * np.sin(z * 0.19))


def landscape(cam, rd):
    """numpy raymarch of the heightfield; returns colour (h,w,3) and distance (inf = sky)."""
    t = np.full(rd.shape[:2], 0.5)
    hit = np.zeros(rd.shape[:2], bool)
    for _ in range(220):
        p = cam + rd * t[..., None]
        d = p[..., 1] - height(p[..., 0], p[..., 2])
        hit |= d < 0.02 * t
        t = np.where(hit, t, t + np.maximum(d * 0.5, 0.05 * t * 0.02 + 0.2))
        t = np.minimum(t, 3000)
    hit &= t < 1500
    p = cam + rd * t[..., None]
    e = 0.5
    nx = height(p[..., 0] - e, p[..., 2]) - height(p[..., 0] + e, p[..., 2])
    nz = height(p[..., 0], p[..., 2] - e) - height(p[..., 0], p[..., 2] + e)
    n = norm(np.stack([nx, np.full_like(nx, 2 * e), nz], -1))
    sun = norm(np.array([0.4, 0.7, 0.3]))
    dif = np.clip((n * sun).sum(-1), 0, 1)[..., None]
    grass = np.array([0.18, 0.32, 0.1])
    rock = np.array([0.35, 0.33, 0.3])
    steep = np.clip((1 - n[..., 1:2]) * 4, 0, 1)
    alb = grass * (1 - steep) + rock * steep
    checker = ((np.floor(p[..., 0]) + np.floor(p[..., 2])) % 2)[..., None] * 0.06 + 0.94  # block grid hint
    col = alb * checker * (0.25 + 1.1 * dif) * np.array([1.0, 0.97, 0.9])
    fog = 1 - np.exp(-t / 900)[..., None]
    sky = (np.array([0.35, 0.55, 0.95]) * (1 - np.clip(rd[..., 1:2], 0, 1)) + np.array([0.15, 0.3, 0.8]) * np.clip(rd[..., 1:2], 0, 1)) * 0.9
    col = col * (1 - fog) + np.array([0.6, 0.72, 0.9]) * fog
    col = np.where(hit[..., None], col, sky)
    return col.astype(np.float32), np.where(hit, t, np.inf)


def look_basis(fwd):
    fwd = norm(fwd)
    right = norm(np.cross(fwd, [0, 1, 0]))
    up = np.cross(right, fwd)
    return fwd, right, up


def ease(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3 - 2 * t)


def shot(name, t):
    """returns cam pos (world), look-at (world), fov, uniforms (world-space body pos under key 'Body')"""
    ground = lambda x, z: float(height(np.array(x), np.array(z)))
    if name == "sun_far":       # sun falling from the sky, seen across the land
        cam = np.array([0, ground(0, 0) + 30, 0.0])
        body = np.array([200, 1400 - 900 * t, 900.0])
        return cam, body * 0.5 + cam * 0.5 + np.array([0, -60, 0]), 70, dict(Body=body, Size=260.0, Presence=1.0, Approach=0.3 + 0.6 * t, Landed=0.0)
    if name == "sun_landed":
        cam = np.array([-300, ground(-300, -200) + 60, -200.0])
        body = np.array([150, ground(150, 600) + 40, 600.0])
        return cam, body, 75, dict(Body=body, Size=260.0, Presence=1.0, Approach=1.0, Landed=0.2 + 0.8 * t)
    if name.startswith("angel"):
        body = np.array([0, ground(0, 300) + 10, 300.0])
        face = norm([0.0, 0, -1.0])
        sword = dict(angel_raise=0.0, angel_mid=0.55, angel_slash=1.0, angel_after=1.0, angel_low=0.0)[name]
        age = dict(angel_raise=-1.0, angel_mid=-0.3, angel_slash=0.15, angel_after=2.5, angel_low=-1.0)[name]
        cam = np.array([-160, ground(-160, -80) + 25, -80.0])
        look = body + np.array([0, 140, 0])
        if name == "angel_low":
            cam = np.array([60, ground(60, 120) + 6, 120.0])
            look = body + np.array([0, 170, 0])
        if name == "angel_after":
            cam = np.array([-260, ground(-260, 200) + 120, 200.0])
            look = np.array([0, 0, 0.0])
        u = dict(Body=body, Size=200.0, Presence=1.0, Approach=1.0, Landed=0.0, FaceDir=tuple(face), SwordT=sword,
                 SlashAge=age, WingBeat=t, Lean=0.2 if sword >= 1 else -0.1, SwordGlow=1.5, FissureA=body + np.array([0, 0, -900]), FissureB=body + np.array([0, 0, 900]), FissureW=14.0)
        return cam, look, 70, u
    raise SystemExit("unknown shot " + name)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--pass", dest="fx", default="sun")
    ap.add_argument("--shot", default="sun_far")
    ap.add_argument("--t", type=float, default=0.5)
    ap.add_argument("--w", type=int, default=800)
    ap.add_argument("--h", type=int, default=450)
    a = ap.parse_args()

    cam, look, fov, u = shot(a.shot, a.t)
    fwd, right, up = look_basis(look - cam)
    aspect = a.w / a.h
    P = perspective(fov, aspect)
    view = np.stack([right, up, -fwd])  # world -> view rows
    ys, xs = np.mgrid[0:a.h, 0:a.w]
    px = ((xs + 0.5) / a.w * 2 - 1) * math.tan(math.radians(fov) / 2) * aspect
    py = (1 - (ys + 0.5) / a.h * 2) * math.tan(math.radians(fov) / 2)
    rd = norm(fwd * 1 + right * px[..., None] + up * py[..., None])
    col, dist = landscape(cam, rd)
    # depth buffer: view-space z of the hit, through the projection
    vz = -(dist * (rd @ fwd))
    ndc = np.where(np.isfinite(dist), (P[2, 2] * vz + P[2, 3]) / -vz, 1.0)
    depth = np.clip(ndc * 0.5 + 0.5, 0, 1).astype(np.float32)
    depth[~np.isfinite(dist)] = 1.0

    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    prog = ctx.program(vertex_shader=load("fullscreen.vsh"), fragment_shader=load(a.fx + ".fsh"))
    vao = ctx.vertex_array(prog, [])
    scene = ctx.texture((a.w, a.h), 3, col[::-1].copy().tobytes(), dtype="f4")
    dtex = ctx.texture((a.w, a.h), 1, depth[::-1].copy().tobytes(), dtype="f4")
    scene.use(0)
    dtex.use(1)
    vals = {"SceneTex": 0, "DepthTex": 1, "Proj": tuple(P.T.flatten()), "InvProj": tuple(np.linalg.inv(P).T.flatten()),
            "ViewRot": tuple(view.T.flatten()), "InvViewRot": tuple(view.flatten()), "Time": 12.0, "Steps": 160}
    for k, v in u.items():
        if k == "Body":
            vals["BodyRel"] = tuple(v - cam)
        elif k in ("FissureA", "FissureB"):
            vals[k] = tuple(v - cam)
        else:
            vals[k] = v
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
