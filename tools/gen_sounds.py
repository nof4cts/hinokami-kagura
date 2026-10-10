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

    # ---------------- cannon charge: 4.2 s rising whine, electrical crackle, sub swell; ends on the shot (H_FIRE)
    d = 4.2
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

    # ---------------- inbound: 7.6 s, an enormous rising roar that ends on impact (H_APPEAR -> H_IMPACT)
    d = 7.6
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

    extra()


def formant(x, centres, q=6.0):
    """Crude vowel colouring: sum of resonant band-passes (two one-pole stages each side)."""
    out = np.zeros_like(x)
    for f, g in centres:
        out += (lp(x, f * 1.15) - lp(x, f / 1.15)) * g
    return out


def extra():
    # ---------------- whoosh for space cuts: noise swell with a falling doppler sweep
    d = 1.8
    t = t_axis(d)
    k = t / d
    env = np.sin(np.pi * np.clip(k * 1.3, 0, 1)) ** 2
    sweep = 300 + 5000 * np.exp(-((k - 0.45) ** 2) / 0.02)
    write("cutscene/whoosh", lp(noise(d), sweep, order=2) * env + osc_sin(80 - 40 * k) * env * 0.3, 0.8)

    # ---------------- impact-frame hit: crack, metallic ring, sub thump, short reversed air
    d = 1.1
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.012) * 2.0, 0.0)
    place(x, hp(noise(0.06), 3000) * np.linspace(1, 0, int(0.06 * SR)), 0.0, 1.2)
    place(x, sub_drop(0.9, 140, 40, 0.18), 0.0, 1.4)
    ring = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) for f, dec in ((2600, 0.18), (3900, 0.12), (5200, 0.08))) * 0.25
    write("impact/hit", np.tanh(1.8 * (x + ring)), 0.98)

    # ---------------- star pierced: a searing sizzle that hits a wall of plasma, deep boom under it
    d = 3.2
    t = t_axis(d)
    sizzle = hp(noise(d), 2500) * (0.5 + 0.5 * np.sin(2 * np.pi * 37 * t)) * np.exp(-t / 0.9)
    tone = osc_saw(np.full_like(t, 1800) * (1 - 0.3 * t / d)) * np.exp(-t / 0.5) * 0.25
    x = sizzle * 0.8 + tone
    place(x, sub_drop(3.0, 80, 24, 1.1), 0.0, 1.8)
    place(x, lp(noise(2.5), 800, order=2) * np.exp(-t_axis(2.5) / 0.8), 0.05, 1.6)
    write("star/pierce", np.tanh(1.3 * x), 0.95)

    # ---------------- star collapse: 2.5 s of rising suction, then the implosion (boom lands at 2.5 s)
    d = 6.0
    t = t_axis(d)
    k = np.clip(t / 2.5, 0, 1)
    suck = lp(noise(d), 80 + 6000 * k ** 4, order=2) * k ** 3 * (t < 2.5)
    whine = osc_sin(60 * 2 ** (k * 4)) * k ** 4 * (t < 2.5) * 0.4
    x = suck * 1.4 + whine
    place(x, noise(0.03) * 2.5, 2.5)
    place(x, sub_drop(3.4, 90, 18, 1.4), 2.5, 2.4)
    tail = lp(noise(3.5), 2500 * np.exp(-t_axis(3.5) / 0.4) + 100, order=2) * np.exp(-t_axis(3.5) / 1.2)
    place(x, tail, 2.5, 1.5)
    write("star/collapse", np.tanh(1.4 * x), 0.98)

    # ---------------- the Sun torn loose: a tearing, groaning roar with crackling plasma
    d = 4.5
    t = t_axis(d)
    k = t / d
    groan = np.tanh(2.5 * osc_saw(38 + 12 * np.sin(2 * np.pi * 0.7 * t) + 20 * k)) * 0.5
    groan = lp(groan, 400 + 600 * k)
    crack = np.zeros_like(t)
    for _ in range(260):
        at = rng.uniform(0, d - 0.1)
        ln = int(rng.integers(40, 600))
        i = int(at * SR)
        crack[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 5))
    roar = lp(noise(d), 1200 + 1500 * np.sin(np.pi * k), order=2)
    x = groan + hp(crack, 1200) * 0.35 + roar * 0.9
    place(x, sub_drop(2.0, 70, 25, 0.8), 0.0, 1.5)
    write("sun/tear", np.tanh(1.2 * x) * env_ad(len(t), 0.02, 1.2), 0.95)

    # ---------------- the Sun inbound: 7.5 s, a firestorm roaring louder and brighter until impact
    d = 7.5
    t = t_axis(d)
    k = t / d
    fire = lp(noise(d), 200 + 3500 * k ** 2, order=2) * (0.15 + 0.85 * k ** 2)
    flutter = (0.7 + 0.3 * np.sin(2 * np.pi * (6 + 10 * k) * t))
    sub = np.tanh(2.5 * osc_sin(24 + 26 * k ** 2)) * (0.2 + 0.8 * k ** 3)
    crackle = np.zeros_like(t)
    for _ in range(900):
        at = d * rng.uniform(0, 1) ** 0.6
        ln = int(rng.integers(20, 200))
        i = int(at * SR)
        if i + ln < len(crackle):
            crackle[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 4)) * (at / d)
    write("sun/inbound", fire * flutter * 1.4 + sub + hp(crackle, 2000) * 0.25, 0.95, fade=False)

    # ---------------- the Sun's impact: the biggest sound in the mod
    d = 8.0
    t = t_axis(d)
    x = np.zeros_like(t)
    place(x, noise(0.1) * np.linspace(1, 0, int(0.1 * SR)), 0, 2.0)
    place(x, sub_drop(7.0, 60, 16, 2.4), 0, 2.4)
    roar = lp(noise(d), 2500 * np.exp(-t / 1.2) + 150, order=3) * np.exp(-t / 3.5)
    blaze = hp(lp(noise(d), 5000), 800) * np.exp(-t / 2.0) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))
    write("sun/impact", np.tanh(1.5 * (x + roar * 1.8 + blaze * 0.5)), 0.98)

    # ---------------- heavenly choir: an 8 s swelling chord sung on "ah", with shimmering air on top
    d = 8.0
    t = t_axis(d)
    k = t / d
    chord = [110.0, 164.8, 220.0, 246.9, 329.6, 440.0]      # A, E, A, B, E, A: open, radiant
    voices = np.zeros_like(t)
    for f in chord:
        for det in (-0.004, 0.0, 0.0045):
            vib = 1 + 0.004 * np.sin(2 * np.pi * (5.0 + rng.uniform(-0.6, 0.6)) * t + rng.uniform(0, 6.28))
            voices += osc_saw(np.full_like(t, f * (1 + det)) * vib)
    voices = formant(voices, [(800, 1.0), (1150, 0.6), (2900, 0.25)])
    air = hp(lp(noise(d), 9000), 4500) * 0.08
    env = np.clip(k / 0.35, 0, 1) ** 2 * np.clip((1 - k) / 0.25, 0, 1)
    bells = sum(osc_sin(np.full_like(t, f)) * np.exp(-((t - at) % d) / 1.5) * (t > at)
                for f, at in ((1760, 1.2), (2637, 2.6), (2217, 4.0))) * 0.06
    write("angel/choir", (voices * 0.25 + air + bells) * env, 0.85, fade=False)

    # ---------------- the blade moving: a deep rushing whoosh with fire licking along it
    d = 1.6
    t = t_axis(d)
    k = t / d
    env = np.sin(np.pi * k) ** 1.5
    whoosh = lp(noise(d), 200 + 1800 * env, order=2) * env
    fire = hp(noise(d), 1500) * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t)) * env * 0.25
    write("angel/swing", whoosh * 1.4 + fire + osc_sin(55 + 30 * env) * env * 0.4, 0.9)

    # ---------------- the slash: a blade ring, the crack of the world splitting, and the land tearing open
    d = 7.0
    t = t_axis(d)
    x = np.zeros_like(t)
    shing = sum(osc_sin(np.full_like(t, f)) * np.exp(-t / dec) for f, dec in ((3150, 0.9), (4720, 0.6), (6300, 0.4))) * 0.25
    place(x, noise(0.04) * 2.4, 0.0)
    place(x, sub_drop(6.0, 75, 15, 2.0), 0.0, 2.4)
    rip = np.zeros_like(t)
    for _ in range(900):
        at = rng.exponential(1.6)
        if at > d - 0.2: continue
        ln = int(rng.integers(100, 1600))
        i = int(at * SR)
        if i + ln < len(rip):
            rip[i:i + ln] += rng.uniform(-1, 1, ln) * np.exp(-np.arange(ln) / (ln / 3)) * np.exp(-at / 2.5)
    rumble = lp(noise(d), 160, order=3) * np.exp(-t / 3.0) * 4
    write("angel/slash", np.tanh(1.5 * (x + shing + lp(rip, 1800) * 0.9 + rumble)), 0.98)


if __name__ == "__main__":
    main()
