"""
Offline test harness: runs the mod's REAL GLSL (assets/ton618/shaders/fx) through the same pass
order as PostFx.java, on a software GL context, against a synthetic Minecraft-like frame.
Lets the shaders be compiled, debugged and judged by eye without launching the game.

    pip install moderngl numpy pillow
    python3 tools/render_test.py out.png [--dist 160] [--rs 12] [--yaw 0] [--pitch 0] [--w 960] [--h 540]
"""
import argparse
import math
import os
import re

import moderngl
import numpy as np
from PIL import Image

FX = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ton618", "shaders", "fx")


def load(name):
    src = open(os.path.join(FX, name)).read()
    return re.sub(r'#include\s+"([^"]+)"', lambda m: open(os.path.join(FX, m.group(1))).read(), src)


def perspective(fov_deg, aspect, near, far):
    f = 1.0 / math.tan(math.radians(fov_deg) / 2)
    m = np.zeros((4, 4), dtype=np.float32)
    m[0, 0] = f / aspect
    m[1, 1] = f
    m[2, 2] = (far + near) / (near - far)
    m[2, 3] = 2 * far * near / (near - far)
    m[3, 2] = -1
    return m


def view_rotation(yaw, pitch):
    """World->view rotation for a Minecraft camera (yaw 0 looks +Z, pitch + looks down)."""
    y, p = math.radians(yaw), math.radians(pitch)
    fwd = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    # view space: x right, y up, -z forward
    return np.array([right, up, -fwd], dtype=np.float32), fwd


def synthetic_scene(w, h, proj, view, cam_y):
    """A plausible daytime frame: sky gradient, distant hills, grassy ground with a block grid."""
    inv_proj = np.linalg.inv(proj)
    inv_view = view.T
    ys, xs = np.mgrid[0:h, 0:w]
    ndc = np.stack([(xs + 0.5) / w * 2 - 1, (ys + 0.5) / h * 2 - 1, np.ones_like(xs, dtype=float), np.ones_like(xs, dtype=float)], -1)
    c = ndc @ inv_proj.T
    d = c[..., :3] / c[..., 3:4]
    d = d @ inv_view.T
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    col = np.zeros((h, w, 3))
    depth = np.ones((h, w))
    sky_t = np.clip(d[..., 1], 0, 1)[..., None]
    sky = (1 - sky_t) * np.array([0.72, 0.82, 0.98]) + sky_t * np.array([0.36, 0.55, 0.92])
    col[:] = sky
    # hills on the horizon
    ang = np.arctan2(d[..., 0], d[..., 2])
    hill = 0.035 + 0.025 * np.sin(ang * 5) + 0.015 * np.sin(ang * 13 + 1)
    is_hill = (d[..., 1] < hill) & (d[..., 1] > 0)
    col[is_hill] = [0.38, 0.52, 0.42]
    # ground plane at y=0
    ground = d[..., 1] < 0
    t = np.where(ground, -cam_y / np.minimum(d[..., 1], -1e-4), 0)
    hit = d * t[..., None] + np.array([0, cam_y, 0])
    gx, gz = np.floor(hit[..., 0]), np.floor(hit[..., 2])
    checker = ((gx + gz) % 2)[..., None]
    grass = np.array([0.36, 0.58, 0.24]) * (0.9 + 0.1 * checker)
    fog = np.clip(t / 700, 0, 1)[..., None]
    gcol = grass * (1 - fog) + np.array([0.7, 0.8, 0.95]) * fog
    col = np.where(ground[..., None], gcol, col)
    # depth for the ground
    view_pos = (hit - np.array([0, cam_y, 0])) @ view.T
    clip = np.concatenate([view_pos, np.ones((h, w, 1))], -1) @ proj.T
    z = clip[..., 2] / clip[..., 3] * 0.5 + 0.5
    depth = np.where(ground & (t < 1000), z, 1.0)
    return col.astype(np.float32), depth.astype(np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--dist", type=float, default=160)
    ap.add_argument("--rs", type=float, default=12)
    ap.add_argument("--height", type=float, default=40, help="hole height above camera")
    ap.add_argument("--yaw", type=float, default=0)
    ap.add_argument("--pitch", type=float, default=-8)
    ap.add_argument("--tilt", type=float, default=14)
    ap.add_argument("--w", type=int, default=960)
    ap.add_argument("--h", type=int, default=540)
    ap.add_argument("--steps", type=int, default=280)
    ap.add_argument("--time", type=float, default=3.0)
    ap.add_argument("--flash", type=float, default=0.0)
    ap.add_argument("--shock", type=float, default=0.0)
    ap.add_argument("--bloom", type=float, default=0.35)
    ap.add_argument("--space", type=float, default=0.0)
    ap.add_argument("--gain", type=float, default=1.0)
    ap.add_argument("--eclipse", type=float, default=0.0)
    a = ap.parse_args()

    W, H = a.w, a.h
    ctx = moderngl.create_standalone_context(backend="egl", require=330)
    vao = ctx.vertex_array(ctx.program(vertex_shader=load("fullscreen.vsh"),
                                       fragment_shader="#version 150\nout vec4 o;void main(){o=vec4(0);}"), [])

    def prog(frag):
        p = ctx.program(vertex_shader=load("fullscreen.vsh"), fragment_shader=load(frag))
        return p, ctx.vertex_array(p, [])

    proj = perspective(70, W / H, 0.05, 768)
    view, fwd = view_rotation(a.yaw, a.pitch)
    cam_y = 70.0
    scene, depth = synthetic_scene(W, H, proj, view, cam_y)

    scene_tex = ctx.texture((W, H), 3, scene.tobytes(), dtype="f4")
    depth_tex = ctx.texture((W, H), 1, depth.tobytes(), dtype="f4")
    for t in (scene_tex, depth_tex):
        t.filter = (moderngl.LINEAR, moderngl.LINEAR)
        t.repeat_x = t.repeat_y = False

    def setu(p, name, val):
        if name in p:
            p[name].value = val

    # --- black hole pass
    bh, bh_vao = prog("blackhole.fsh")
    hdr = ctx.texture((W, H), 4, dtype="f2")
    hdr.filter = (moderngl.LINEAR, moderngl.LINEAR)
    hdr.repeat_x = hdr.repeat_y = False
    fbo = ctx.framebuffer([hdr])
    hole_rel = fwd * a.dist + np.array([0, a.height, 0])
    tilt = math.radians(a.tilt)
    n = np.array([0, math.cos(tilt), math.sin(tilt)])
    dx = np.array([1.0, 0, 0])
    dz = np.cross(dx, n)
    basis = np.stack([dx, n, dz], 1).astype(np.float32)  # columns
    scene_tex.use(0)
    depth_tex.use(1)
    setu(bh, "SceneTex", 0)
    setu(bh, "DepthTex", 1)
    setu(bh, "Proj", tuple(proj.T.flatten()))
    setu(bh, "InvProj", tuple(np.linalg.inv(proj).T.flatten()))
    setu(bh, "ViewRot", tuple(view.T.flatten()))
    setu(bh, "InvViewRot", tuple(view.flatten()))
    setu(bh, "HoleRel", tuple(hole_rel))
    setu(bh, "Rs", a.rs)
    setu(bh, "DiskBasis", tuple(basis.T.flatten()))
    setu(bh, "Time", a.time)
    setu(bh, "Steps", a.steps)
    setu(bh, "Presence", 1.0)
    setu(bh, "Jets", 1.0)
    setu(bh, "DiskGain", a.gain)
    setu(bh, "HasHole", 1.0)
    setu(bh, "SpaceMix", a.space)
    setu(bh, "Eclipse", a.eclipse)
    fbo.use()
    bh_vao.render(moderngl.TRIANGLES, vertices=3)

    # --- bloom chain
    down, down_vao = prog("bloom_down.fsh")
    up, up_vao = prog("bloom_up.fsh")
    levels = []
    w, h = W, H
    for i in range(6):
        w, h = max(1, w // 2), max(1, h // 2)
        t = ctx.texture((w, h), 4, dtype="f2"); t.filter = (moderngl.LINEAR, moderngl.LINEAR); t.repeat_x = t.repeat_y = False
        levels.append((t, ctx.framebuffer([t]), w, h))
    src, sw, sh = hdr, W, H
    for i, (t, f, w, h) in enumerate(levels):
        src.use(0)
        setu(down, "Src", 0); setu(down, "SrcTexel", (1 / sw, 1 / sh)); setu(down, "Prefilter", 1.0 if i == 0 else 0.0)
        setu(down, "Threshold", 1.4)
        f.use(); ctx.viewport = (0, 0, w, h); down_vao.render(moderngl.TRIANGLES, vertices=3)
        src, sw, sh = t, w, h
    ups = []
    for i in range(len(levels) - 2, -1, -1):
        t, f, w, h = levels[i]
        o = ctx.texture((w, h), 4, dtype="f2"); o.filter = (moderngl.LINEAR, moderngl.LINEAR); o.repeat_x = o.repeat_y = False
        of = ctx.framebuffer([o])
        low = levels[i + 1] if not ups else ups[-1]
        low[0].use(0); t.use(1)
        setu(up, "Low", 0); setu(up, "Cur", 1); setu(up, "LowTexel", (1 / low[2], 1 / low[3])); setu(up, "Radius", 1.0); setu(up, "LowWeight", 0.55)
        of.use(); ctx.viewport = (0, 0, w, h); up_vao.render(moderngl.TRIANGLES, vertices=3)
        ups.append((o, of, w, h))

    # --- composite
    comp, comp_vao = prog("composite.fsh")
    out = ctx.texture((W, H), 4, dtype="f1")
    of = ctx.framebuffer([out])
    hdr.use(0); ups[-1][0].use(1)
    setu(comp, "Hdr", 0); setu(comp, "Bloom", 1); setu(comp, "ScreenSize", (W, H)); setu(comp, "Time", a.time)
    setu(comp, "BloomStrength", a.bloom); setu(comp, "Exposure", 1.0); setu(comp, "Aberration", 0.35)
    setu(comp, "Shockwave", (0.5, 0.5, 0.3, a.shock)); setu(comp, "Flash", (1, 0.96, 0.9, a.flash))
    setu(comp, "Afterimage", 0.0); setu(comp, "Desaturate", 0.1); setu(comp, "Vignette", 0.35); setu(comp, "Grain", 0.5)
    setu(comp, "Shake", (0.0, 0.0))
    of.use(); ctx.viewport = (0, 0, W, H); comp_vao.render(moderngl.TRIANGLES, vertices=3)
    img = np.frombuffer(out.read(), dtype=np.uint8).reshape(H, W, 4)[::-1, :, :3]
    Image.fromarray(img).save(a.out)
    print("saved", a.out)


if __name__ == "__main__":
    main()
