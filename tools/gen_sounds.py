"""
Synthesises PLACEHOLDER sounds for Stage 1 so the mod is audible out of the box
(run from the project root; needs numpy and ffmpeg with libvorbis):

    python3 tools/gen_sounds.py

These are procedural stand-ins. For release quality, replace each .ogg with recorded/designed
audio of the same name (see docs/ASSETS.md for the full list and what each layer should sound like).
All files are MONO: Minecraft only positions mono sounds in 3D.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "sunbreathing", "sounds")
rng = np.random.default_rng(11)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    """One-pole low-pass; cutoff may be a scalar or per-sample array (Hz)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    a = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    s = 0.0
    for i in range(len(x)):
        s += a[i] * (x[i] - s)
        y[i] = s
    return y


def bandpass(x, centre, q=1.5):
    """State-variable band-pass with a time-varying centre frequency."""
    centre = np.broadcast_to(np.asarray(centre, dtype=float), x.shape)
    y = np.empty_like(x)
    low = band = 0.0
    damp = 1.0 / q
    for i in range(len(x)):
        f = 2 * np.sin(np.pi * min(centre[i], SR / 6) / SR)
        high = x[i] - low - damp * band
        band += f * high
        low += f * band
        y[i] = band
    return y


def env(dur, attack, decay_pow=2.0):
    t = t_axis(dur)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d = np.clip(1 - (t - attack) / max(dur - attack, 1e-4), 0, 1) ** decay_pow
    return np.where(t < attack, a, d)


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def fade(x, ms_in=2, ms_out=10):
    n_in, n_out = int(SR * ms_in / 1000), int(SR * ms_out / 1000)
    x = x.copy()
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def write(rel, x, peak=0.9):
    path = os.path.join(OUT, rel + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = fade(norm(x, peak))
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis",
                    "-q:a", "5", path], check=True)
    os.remove(wav_path)
    print("wrote", rel)


# ------------------------------------------------------------------ layers
def whoosh(dur, f0, f1, f2, q=2.2):
    t = t_axis(dur)
    k = t / dur
    centre = np.where(k < 0.4, f0 + (f1 - f0) * (k / 0.4), f1 + (f2 - f1) * ((k - 0.4) / 0.6))
    return bandpass(noise(dur), centre, q) * env(dur, dur * 0.3, 1.8)


def ring(dur, freq, decay):
    t = t_axis(dur)
    return (np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(2 * np.pi * freq * 2.76 * t)) * np.exp(-t / decay)


def thump(dur, f_start, f_end, decay):
    t = t_axis(dur)
    f = f_end + (f_start - f_end) * np.exp(-t / (decay * 0.4))
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.tanh(1.6 * np.sin(phase)) * np.exp(-t / decay)


def crackle(dur, density, seed):
    r = np.random.default_rng(seed)
    x = np.zeros(int(SR * dur))
    n = int(density * dur)
    for _ in range(n):
        i = r.integers(0, len(x) - 400)
        ln = r.integers(40, 300)
        burst = r.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4)) * r.uniform(0.3, 1.0)
        x[i:i + ln] += burst
    x = x - lowpass(x, 900)  # high-pass to keep it snappy
    return x * env(dur, 0.02, 1.2)


def flame_body(dur, cut0, cut1):
    t = t_axis(dur)
    cut = cut0 + (cut1 - cut0) * np.clip(t / (dur * 0.5), 0, 1)
    return lowpass(noise(dur), cut) * env(dur, dur * 0.18, 1.5)


def main():
    # Blade whoosh (form-specific layer for Dance): bright airy sweep + faint steel "shing"
    for i, (a, b, c) in enumerate([(500, 2600, 900), (420, 2300, 800)], start=1):
        x = whoosh(0.38, a, b, c) * 1.0 + ring(0.38, 3150 + 120 * i, 0.06) * 0.08
        write(f"form/dance_swing{i}", x)

    # Flame ignite: a "fwoomp" (rising low-passed noise) on a soft low thump
    for i, (c0, c1) in enumerate([(200, 3500), (160, 3000)], start=1):
        x = flame_body(0.55, c0, c1) + thump(0.55, 140, 60, 0.12) * 0.35 + crackle(0.55, 50, i) * 0.25
        write(f"flame/ignite{i}", x)

    # Flame crackle
    for i in range(1, 4):
        x = crackle(0.6, 70 + 15 * i, 100 + i) + flame_body(0.6, 400, 1200) * 0.25
        write(f"flame/crackle{i}", x)

    # Slash impact: sharp transient + metallic ring + body
    for i in range(1, 4):
        tr = noise(0.04) * np.exp(-t_axis(0.04) / 0.008)
        body = bandpass(noise(0.35), 1400 + 300 * i, 1.2) * np.exp(-t_axis(0.35) / 0.07)
        x = np.zeros(int(SR * 0.35))
        x[:len(tr)] += tr * 1.2
        x += body + ring(0.35, 2400 + 260 * i, 0.05) * 0.25 + thump(0.35, 220, 90, 0.06) * 0.5
        write(f"impact/slash_hit{i}", x)

    # Sub hit: deep pitch-dropping thump (felt more than heard)
    for i, (f0, f1) in enumerate([(95, 38), (85, 34)], start=1):
        write(f"impact/sub_hit{i}", thump(0.5, f0, f1, 0.16), peak=0.95)

    # Blade ignite: rising roar + crackle + high shimmer
    t = t_axis(0.9)
    shimmer = np.sin(2 * np.pi * (2200 + 600 * t) * t) * 0.05 * np.clip(t / 0.3, 0, 1) * np.exp(-t / 0.5)
    x = flame_body(0.9, 150, 4200) + crackle(0.9, 60, 7) * 0.35 + shimmer
    write("blade/ignite", x)

    # Breathing: shaped filtered noise. Inhale = brighter, rising; exhale = darker, falling.
    def breath(dur, c0, c1, peak_at):
        tt = t_axis(dur)
        k = tt / dur
        cut = c0 + (c1 - c0) * k
        shape = np.sin(np.pi * np.clip(k, 0, 1)) ** 1.5
        shape *= np.where(k < peak_at, 0.6 + 0.4 * k / peak_at, 1.0)
        return bandpass(noise(dur), cut, 0.9) * shape

    inhale = breath(1.5, 900, 1700, 0.7)
    exhale = breath(1.8, 1200, 650, 0.3) * 0.8
    loop = np.concatenate([inhale, np.zeros(int(SR * 0.25)), exhale, np.zeros(int(SR * 0.45))])
    write("breath/concentration_loop", loop, peak=0.6)
    write("breath/inhale", breath(0.9, 800, 2000, 0.8), peak=0.75)
    write("breath/exhale", breath(1.0, 1300, 600, 0.25), peak=0.7)
    gasp = np.concatenate([breath(0.18, 1500, 2200, 0.5), np.zeros(int(SR * 0.06)), breath(0.22, 1400, 2000, 0.5)])
    write("breath/fail", gasp, peak=0.75)

    # Extra swing layers for forms II-XII
    write("form/swing_heavy", whoosh(0.55, 250, 1400, 400, 1.6) + thump(0.55, 110, 50, 0.15) * 0.3)
    spin = np.zeros(int(SR * 0.9))
    for k in range(3):  # three overlapping whooshes = a whirl
        w = whoosh(0.4, 500 + 80 * k, 2300, 800)
        start = int(SR * 0.22 * k)
        spin[start:start + len(w)] += w * (1 - 0.15 * k)
    write("form/spin", spin)
    tt = t_axis(0.3)
    write("form/thrust", bandpass(noise(0.3), 3200 - 2400 * tt / 0.3, 3.0) * env(0.3, 0.02, 2.5)
          + ring(0.3, 2900, 0.07) * 0.15)
    write("form/dash", whoosh(0.35, 1200, 3800, 600, 1.4) + flame_body(0.35, 500, 2500) * 0.4)
    tr = t_axis(1.2)
    shine = sum(np.sin(2 * np.pi * f * tr) for f in (523, 659, 784, 1046)) * np.exp(-tr / 0.5) * 0.12
    write("form/radiance", flame_body(1.2, 300, 6000) * 0.8 + thump(1.2, 120, 40, 0.3) * 0.5 + shine)
    tf = t_axis(2.0)
    rise = bandpass(noise(2.0), 200 + 2500 * (tf / 2.0) ** 2, 1.2) * np.clip(tf / 1.6, 0, 1) ** 2
    boom = np.zeros_like(tf)
    b = thump(1.0, 90, 30, 0.35) + flame_body(1.0, 800, 5000) * 0.6
    boom[int(SR * 1.0):int(SR * 1.0) + len(b)] = b[:len(boom) - int(SR * 1.0)]
    write("form/finale", rise * 0.7 + boom)

    # UI
    write("ui/wheel_open", whoosh(0.28, 300, 1200, 500, 1.5) * 0.7 + ring(0.28, 880, 0.08) * 0.2, peak=0.5)
    write("ui/select", ring(0.09, 1320, 0.025) + noise(0.09) * np.exp(-t_axis(0.09) / 0.004) * 0.3, peak=0.5)


if __name__ == "__main__":
    main()
