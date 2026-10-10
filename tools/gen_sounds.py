"""
Synthesises every sound of the technique from scratch (run from the project root; needs numpy + ffmpeg):

    python3 tools/gen_sounds.py

Nothing is sampled from anything: cuts are built from noise transients and inharmonic metal rings, fire from
filtered noise, impacts from pitched sub drops. All mono so Minecraft can place them in the world.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "shrine", "sounds")
rng = np.random.default_rng(618)


def t_axis(d):
    return np.arange(int(SR * d)) / SR


def noise(d):
    return rng.uniform(-1, 1, int(SR * d))


def onepole(x, cutoff):
    cutoff = np.broadcast_to(np.asarray(cutoff, float), x.shape)
    a = 1 - np.exp(-2 * np.pi * np.clip(cutoff, 1, SR / 2.2) / SR)
    y = np.empty_like(x)
    s = 0.0
    for i in range(len(x)):
        s += a[i] * (x[i] - s)
        y[i] = s
    return y


def lp(x, cutoff, order=2):
    for _ in range(order):
        x = onepole(x, cutoff)
    return x


def hp(x, cutoff):
    return x - onepole(x, cutoff)


def osc_saw(freq):
    phase = np.cumsum(np.broadcast_to(freq, freq.shape) / SR)
    return 2 * (phase % 1.0) - 1


def osc_sin(freq):
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def env_ad(n, a, d_pow=2.0):
    t = np.linspace(0, 1, n)
    out = np.where(t < a, t / max(a, 1e-6), (1 - (t - a) / max(1 - a, 1e-6)) ** d_pow)
    return np.clip(out, 0, 1)


def fades(x, i_ms=3, o_ms=30):
    x = x.copy()
    ni, no = int(SR * i_ms / 1000), int(SR * o_ms / 1000)
    if ni: x[:ni] *= np.linspace(0, 1, ni)
    if no: x[-no:] *= np.linspace(1, 0, no)
    return x


def write(rel, x, peak=0.95, fade=True):
    path = os.path.join(OUT, rel + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.nan_to_num(x)
    m = np.max(np.abs(x)) or 1.0
    x = x / m * peak
    if fade:
        x = fades(x)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        wp = f.name
    with wave.open(wp, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wp, "-ac", "1", "-c:a", "libvorbis", "-q:a", "6", path], check=True)
    os.remove(wp)
    print("wrote", rel, f"{len(x) / SR:.2f}s")


def sub_drop(d, f0, f1, decay):
    t = t_axis(d)
    f = f1 + (f0 - f1) * np.exp(-t / (decay * 0.5))
    return np.tanh(2.0 * osc_sin(f)) * np.exp(-t / decay)


def place(dst, src, at_s, gain=1.0):
    i = int(at_s * SR)
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += src[:n] * gain




def slice_(d=0.5, pitch=1.0, bright=1.0):
    """The cut: a razor 'shing' -- a bright noise transient, an inharmonic metallic ring, a torn-air tail."""
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, hp(noise(0.012), 4000) * 2.2 * bright, 0.0)
    ring = sum(osc_sin(np.full_like(t, f * pitch)) * np.exp(-t / dec) * g
               for f, dec, g in ((3800, 0.16, 1.0), (5650, 0.11, 0.7), (7300, 0.07, 0.5), (2450, 0.22, 0.4)))
    air = hp(lp(noise(d), 9000), 1500) * np.exp(-t / 0.06) * 0.9
    sweep = osc_sin(2600 * pitch * np.exp(-t * 9) + 300) * np.exp(-t / 0.05) * 0.4
    return x + ring * 0.35 + air + sweep


def thud(d, f0=90, f1=30, decay=0.4, g=1.0):
    return sub_drop(d, f0, f1, decay) * g


def main():
    # ---------------- Dismantle: one cut, and the air splitting behind it
    d = 0.9
    x = slice_(d)
    place(x, thud(0.6, 120, 45, 0.12, 0.6), 0.0)
    write("dismantle", np.tanh(1.5 * x), 0.95)

    # ---------------- Barrage: a storm of cuts, 16 in 1.6 s, each slightly different
    d = 2.2
    x = np.zeros(int(SR * d))
    for i in range(16):
        s = slice_(0.4, pitch=0.85 + 0.35 * rng.random(), bright=0.7 + 0.5 * rng.random())
        place(x, s, 0.15 + i * 0.1 + rng.uniform(-0.01, 0.01), 0.75)
    t = t_axis(d)
    x += lp(noise(d), 1800) * np.clip(t / 0.2, 0, 1) * np.exp(-np.maximum(t - 1.7, 0) / 0.2) * 0.35
    write("barrage", np.tanh(1.3 * x), 0.95)

    # ---------------- Cleave: a dozen cuts in a heartbeat, then the weight of it
    d = 1.4
    x = np.zeros(int(SR * d))
    for i in range(12):
        place(x, slice_(0.35, pitch=0.7 + 0.5 * rng.random(), bright=0.6), 0.02 + i * 0.012, 0.55)
    place(x, thud(1.2, 140, 35, 0.3, 1.3), 0.03)
    write("cleave", np.tanh(1.6 * x), 0.95)

    # ---------------- Spiderweb: a palm on the ground, a crack racing out, the ground collapsing
    d = 2.6
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.02) * 2.0, 0.0)
    place(x, thud(2.2, 110, 22, 0.6, 2.0), 0.0)
    crack = np.zeros_like(t)
    for _ in range(500):
        at = rng.exponential(0.35)
        if at > d - 0.1: continue
        ln = int(rng.integers(60, 700))
        i = int(at * SR)
        if i + ln < len(crack):
            crack[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4)) * np.exp(-at / 0.8)
    rumble = lp(noise(d), 180, order=3) * np.exp(-t / 0.9) * 3
    write("spiderweb", np.tanh(1.4 * (x + hp(crack, 900) * 0.7 + rumble)), 0.97)

    # ---------------- Black Flash: crackling discharge, a hollow boom, and the world snapping back
    d = 2.0
    t = t_axis(d)
    x = np.zeros_like(t)
    zap = np.sign(np.sin(2 * np.pi * np.cumsum(220 + 900 * rng.random(len(t))) / SR)) * np.exp(-t / 0.18)
    zap = hp(zap, 1200) * (0.5 + 0.5 * (rng.random(len(t)) > 0.6))
    place(x, noise(0.015) * 2.5, 0.0)
    place(x, thud(1.8, 160, 28, 0.35, 2.2), 0.0)
    rev = lp(noise(0.35), 3000) * np.linspace(0, 1, int(0.35 * SR)) ** 3
    y = np.zeros_like(t)
    place(y, rev, 0.0)
    write("black_flash", np.tanh(1.6 * (x + zap * 0.6 + y * 0.4)), 0.97)

    # ---------------- Open: ignition -- fire blooming out of a hand
    d = 2.2
    t = t_axis(d)
    k = np.clip(t / 0.4, 0, 1)
    roar = lp(noise(d), 400 + 3000 * k, order=2) * k * np.exp(-np.maximum(t - 0.6, 0) / 0.9)
    flutter = 0.7 + 0.3 * np.sin(2 * np.pi * 11 * t)
    crackle = np.zeros_like(t)
    for _ in range(300):
        at = rng.uniform(0.05, d - 0.05)
        ln = int(rng.integers(20, 160))
        i = int(at * SR)
        crackle[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4))
    write("fuga/ignite", roar * flutter * 1.4 + hp(crackle, 2500) * 0.25 + osc_sin(70 + 0 * t) * k * 0.3 * np.exp(-t / 1.2), 0.85)

    # ---------------- Open: the draw -- a tightening, rising roar of fire and creaking tension (1.6 s)
    d = 1.7
    t = t_axis(d)
    k = t / d
    fire = lp(noise(d), 300 + 3500 * k ** 2, order=2) * (0.3 + 0.7 * k)
    tone = osc_saw(80 * 2 ** (k * 1.5)) * k ** 2 * 0.25
    write("fuga/draw", fire * (0.75 + 0.25 * np.sin(2 * np.pi * (8 + 12 * k) * t)) + lp(tone, 900), 0.85, fade=False)

    # ---------------- Open: release -- a 'thwump' of a bowstring of fire and a shrieking tear of air
    d = 1.6
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, thud(1.2, 180, 45, 0.15, 1.5), 0.0)
    shriek = hp(lp(noise(d), 9000), 1800) * np.exp(-t / 0.25) * 1.2
    whoosh = lp(noise(d), 3000 * np.exp(-t / 0.3) + 300, order=2) * np.exp(-t / 0.5)
    write("fuga/release", np.tanh(1.4 * (x + shriek + whoosh * 1.5)), 0.95)

    # ---------------- Open: impact -- the pillar of fire: a vast boom and a firestorm
    d = 8.0
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.1) * np.linspace(1, 0, int(0.1 * SR)) * 2, 0.0)
    place(x, sub_drop(7.0, 70, 16, 2.0), 0.0, 2.4)
    storm = lp(noise(d), 2400 * np.exp(-t / 1.4) + 200, order=3) * np.exp(-t / 3.2) * 2
    blaze = hp(lp(noise(d), 6000), 900) * np.exp(-t / 2.2) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t))
    write("fuga/impact", np.tanh(1.5 * (x + storm + blaze * 0.6)), 0.98)

    # ---------------- Domain: the hand sign -- a temple bell struck in a cavern, a low drone beneath
    d = 3.0
    t = t_axis(d)
    bell = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) * g
               for f, dec, g in ((196, 2.2, 1.0), (196 * 2.76, 1.4, 0.5), (196 * 5.4, 0.8, 0.3), (196 * 8.93, 0.5, 0.2)))
    drone = lp(osc_saw(np.full_like(t, 49)) + osc_saw(np.full_like(t, 49.3)), 300) * np.clip(t / 1.5, 0, 1) * 0.4
    write("domain/sign", bell * 0.6 + drone, 0.85)

    # ---------------- Domain: it opens -- an inhaling roar, a hit, and the storm of cuts beginning
    d = 5.0
    t = t_axis(d)
    x = np.zeros_like(t)
    inhale = lp(noise(1.0), 200 + 5000 * np.linspace(0, 1, int(SR * 1.0)) ** 3) * np.linspace(0, 1, int(SR * 1.0)) ** 2
    place(x, inhale, 0.0, 1.4)
    place(x, noise(0.05) * 2.2, 1.0)
    place(x, sub_drop(3.8, 80, 18, 1.6), 1.0, 2.4)
    braam = sum(osc_saw(np.full_like(t, f)) for f in (41.2, 41.6, 61.7, 82.4)) / 4
    braam = np.tanh(2.5 * lp(braam, 700 * np.exp(-np.maximum(t - 1.0, 0) / 0.8) + 120)) * (t > 1.0) * np.exp(-np.maximum(t - 1.0, 0) / 2.0)
    for i in range(30):
        place(x, slice_(0.3, 0.8 + 0.6 * rng.random(), 0.6), 1.1 + rng.exponential(0.9), 0.35)
    write("domain/open", np.tanh(1.4 * (x + braam * 0.8)), 0.97)

    # ---------------- Domain: the shredding -- 3 s of ceaseless cutting (re-triggered every 3 s)
    d = 3.0
    x = np.zeros(int(SR * d))
    for i in range(80):
        place(x, slice_(0.25, 0.7 + 0.8 * rng.random(), 0.4 + 0.6 * rng.random()), rng.uniform(0, d - 0.25), 0.3)
    t = t_axis(d)
    x += lp(noise(d), 700, order=2) * 0.5 * (0.7 + 0.3 * np.sin(2 * np.pi * 0.5 * t))
    write("domain/shred", np.tanh(1.2 * x), 0.8)

    # ---------------- World-Cutting Slash: a hand sign -- a sharp clap and a held, ringing note
    d = 1.0
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, hp(noise(0.02), 800) * 2.0, 0.0)
    ring = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / 0.5) for f in (880, 1320.5, 1761)) * 0.15
    write("world/sign", np.tanh(1.3 * (x + ring)), 0.9)

    # ---------------- World-Cutting Slash: the cut -- silence torn by one impossibly clean, enormous 'shing'
    d = 5.0
    t = t_axis(d)
    x = np.zeros_like(t)
    big = slice_(3.0, pitch=0.55, bright=2.0)
    place(x, big, 0.0, 1.4)
    place(x, sub_drop(4.5, 60, 14, 1.8), 0.02, 2.6)
    tear = hp(lp(noise(d), 7000), 500) * np.exp(-t / 1.2) * 0.8
    write("world/slash", np.tanh(1.5 * (x + tear)), 0.98)

    # ---------------- World-Cutting Slash: the world comes apart -- a grinding, cavernous rending
    d = 6.0
    t = t_axis(d)
    grind = lp(noise(d), 260, order=3) * 4 * np.exp(-t / 2.5)
    groan = np.tanh(2 * osc_saw(30 + 8 * np.sin(2 * np.pi * 0.4 * t))) * np.exp(-t / 3) * 0.5
    rocks = np.zeros_like(t)
    for _ in range(700):
        at = rng.exponential(1.4)
        if at > d - 0.2: continue
        ln = int(rng.integers(100, 1400))
        i = int(at * SR)
        if i + ln < len(rocks):
            rocks[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 3)) * np.exp(-at / 2)
    write("world/split", np.tanh(1.4 * (grind + lp(groan, 300) + lp(rocks, 2000) * 0.8)), 0.97)

    # ---------------- impact frame hit
    d = 1.1
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.012) * 2.0, 0.0)
    place(x, hp(noise(0.06), 3000) * np.linspace(1, 0, int(0.06 * SR)), 0.0, 1.2)
    place(x, sub_drop(0.9, 140, 40, 0.18), 0.0, 1.4)
    ring = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) for f, dec in ((2600, 0.18), (3900, 0.12), (5200, 0.08))) * 0.25
    write("impact", np.tanh(1.8 * (x + ring)), 0.98)

    # ---------------- title card: a deep 'dong' and a breath of air
    d = 2.0
    t = t_axis(d)
    dong = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) for f, dec in ((73.4, 1.4), (146.8, 0.9), (220.5, 0.6))) * 0.5
    write("card", dong + lp(noise(d), 1200) * np.exp(-t / 0.4) * 0.2 + sub_drop(d, 70, 40, 0.5) * 0.5, 0.9)

    # ---------------- deny
    t = t_axis(0.18)
    write("deny", np.sign(osc_sin(np.full_like(t, 140))) * env_ad(len(t), 0.01, 1.0) * 0.5, 0.5)


if __name__ == "__main__":
    main()
