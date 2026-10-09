"""
Synthesises TON 618's sound design (run from the project root; needs numpy + ffmpeg with libvorbis):

    python3 tools/gen_sounds.py

Every beat of the strike has its own sound, timed to common/StrikeTiming.java:
  charge (4.4 s, ends exactly on the shot) -> fire -> beam -> inbound (9.5 s, ends on impact)
  -> impact -> rumble (loop) -> collapse, plus remote UI sounds and a cut "stinger".
All mono (Minecraft only spatialises mono). Replace any file with recorded audio of the same name.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "ton618", "sounds")
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


def main():
    # ---------------- remote
    t = t_axis(0.35)
    chirp = osc_sin(np.where(t < 0.12, 900 + 900 * t / 0.12, 1800)) * env_ad(len(t), 0.03, 1.5)
    click = np.zeros_like(t); click[:200] = noise(200 / SR) * np.linspace(1, 0, 200)
    write("remote/lock", chirp * 0.7 + click * 0.5, 0.7)

    t = t_axis(0.7)
    arm = (osc_sin(np.full_like(t, 220)) + 0.5 * osc_sin(np.full_like(t, 330))) * env_ad(len(t), 0.02, 2.0)
    servo = lp(noise(0.7), 900 + 600 * np.sin(t * 30)) * env_ad(len(t), 0.1, 1.0) * 0.6
    write("remote/arm", arm + servo, 0.8)

    t = t_axis(0.3)
    write("remote/deny", np.sign(osc_sin(np.full_like(t, 110))) * env_ad(len(t), 0.01, 1.0) * 0.6, 0.6)

    t = t_axis(0.12)
    write("remote/beep", osc_sin(np.full_like(t, 1650)) * env_ad(len(t), 0.02, 0.6), 0.7)

    # ---------------- cannon charge: 4.4 s rising whine, electrical crackle, sub swell; ends on the shot
    d = 4.4
    t = t_axis(d)
    k = t / d
    pitch = 55 * (2 ** (k * 4.2))                       # 55 Hz -> ~1 kHz
    whine = osc_saw(pitch) * 0.5 + osc_saw(pitch * 1.005) * 0.5
    whine = lp(whine, 200 + 5000 * k ** 2) * (0.15 + 0.85 * k ** 1.5)
    hum = osc_sin(np.full_like(t, 41.0)) * (0.3 + 0.7 * k)
    sparks = np.zeros_like(t)
    for _ in range(int(140)):
        at = rng.uniform(0.2, d - 0.05) ** 0.85 * d ** 0.15
        ln = int(rng.integers(30, 220))
        i = int(at * SR)
        if i + ln < len(sparks):
            sparks[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 5)) * k[i]
    write("cannon/charge", whine + hum * 0.6 + hp(sparks, 1500) * 0.5, 0.9, fade=False)

    # ---------------- fire: transient + metal ring + huge sub + long noisy tail
    d = 3.5
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.05) * np.linspace(1, 0, int(0.05 * SR)), 0, 1.5)
    place(x, sub_drop(2.5, 90, 28, 0.9), 0, 1.6)
    tail = lp(noise(d), 4000 * np.exp(-t / 0.4) + 200) * np.exp(-t / 1.1)
    ring = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) for f, dec in ((1120, 0.5), (1570, 0.35), (2310, 0.25))) * 0.12
    write("cannon/fire", x + tail * 1.2 + ring, 0.98)

    # ---------------- beam: roaring hiss, fading
    d = 2.6
    t = t_axis(d)
    beam = hp(lp(noise(d), 6000), 400) * np.exp(-t / 0.9) + lp(noise(d), 300) * np.exp(-t / 1.4) * 0.8
    write("cannon/beam", beam, 0.75)

    # ---------------- inbound: 9.5 s, an enormous rising roar that ends on impact
    d = 9.5
    t = t_axis(d)
    k = t / d
    roar = lp(noise(d), 60 + 2600 * k ** 2.5, order=3) * (0.1 + 0.9 * k ** 2)
    swell = np.tanh(3 * osc_sin(28 + 30 * k ** 2)) * (0.2 + 0.8 * k ** 3)
    # rising tones a fifth apart, slowly detuning: tension
    tones = sum(osc_saw(np.full_like(t, f) * (1 + 0.6 * k ** 2)) for f in (55, 82.5, 110.3)) / 3
    tones = lp(tones, 300 + 1500 * k ** 2) * k ** 1.5 * 0.5
    write("hole/inbound", roar * 1.3 + swell + tones, 0.95, fade=False)

    # ---------------- impact: 6 s boom, sub drop, debris, tail
    d = 6.0
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.08) * np.linspace(1, 0, int(0.08 * SR)), 0, 1.8)
    place(x, sub_drop(5.0, 70, 22, 1.8), 0, 2.0)
    debris = np.zeros_like(t)
    for _ in range(600):
        at = rng.exponential(1.2)
        if at > d - 0.1: continue
        ln = int(rng.integers(80, 900))
        i = int(at * SR)
        if i + ln < len(debris):
            debris[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4)) * np.exp(-at / 2)
    tail = lp(noise(d), 1500 * np.exp(-t / 0.6) + 120, order=3) * np.exp(-t / 2.4)
    write("hole/impact", np.tanh(1.4 * (x + hp(debris, 600) * 0.6 + tail * 1.5)), 0.98)

    # ---------------- rumble: 8 s seamless loop
    d = 8.0
    t = t_axis(d)
    groan = osc_sin(34 + 6 * np.sin(2 * np.pi * t / d)) * (0.6 + 0.4 * np.sin(2 * np.pi * 2 * t / d))
    low = lp(noise(d + 1.0), 90, order=3)
    xf = int(SR * 1.0)   # crossfade the extra second into the start for a seamless loop
    low_loop = low[:len(t)].copy()
    low_loop[:xf] = low_loop[:xf] * np.linspace(0, 1, xf) + low[len(t):len(t) + xf] * np.linspace(1, 0, xf)
    write("hole/rumble", np.tanh(1.5 * (groan * 0.7 + low_loop * 3.0)), 0.9, fade=False)

    # ---------------- collapse: a sucking reverse swell, then an implosion thump
    d = 4.0
    t = t_axis(d)
    k = t / d
    suck = lp(noise(d), 100 + 4000 * k ** 3) * k ** 3
    x = suck * 1.2
    place(x, sub_drop(1.2, 50, 20, 0.5), 3.1, 1.5)
    write("hole/collapse", x, 0.95)

    # ---------------- stinger: a deep "braam" for hard cuts
    d = 2.2
    t = t_axis(d)
    braam = sum(osc_saw(np.full_like(t, f)) for f in (55, 55.4, 54.6, 110.2)) / 4
    braam = np.tanh(2.5 * lp(braam, 900 * np.exp(-t / 0.5) + 150)) * env_ad(len(t), 0.02, 2.2)
    write("cutscene/stinger", braam + sub_drop(d, 60, 35, 0.8) * 0.6, 0.95)


if __name__ == "__main__":
    main()
