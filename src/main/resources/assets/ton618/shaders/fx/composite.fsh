#version 150

// Final pass, written back over Minecraft's frame:
// punch-in zoom blur -> shockwave refraction -> chromatic aberration -> bloom -> god rays -> exposure ->
// highlight roll-off -> flash with burned-in afterimage -> colour grade -> vignette -> grain -> IMPACT FRAMES.
//
// Impact frames are the anime/manga "hit" frames: for a few frames at a time the picture is redrawn as
// pure graphic shapes (inked silhouettes, inverted negatives, two-tone crimson, halftone), with speed lines
// converging on the point of impact and the image torn into slices. ScreenFx sequences the modes, switching
// every couple of frames, so a hit reads as a stutter of stark images before the flash and the shockwave.

#include "common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D Hdr;
uniform sampler2D Bloom;
uniform vec2 ScreenSize;
uniform float Time;
uniform float BloomStrength;
uniform float Exposure;
uniform float Aberration;     // 0..1
uniform vec4 Shockwave;       // centre uv (xy), radius in screen heights (z), strength (w)
uniform vec4 Flash;           // colour (rgb), amount (a)
uniform float Afterimage;     // 0..1 how much of the flash is "burned in"
uniform float Desaturate;     // 0..1
uniform float Vignette;       // 0..1
uniform float Grain;          // 0..1
uniform vec2 Shake;           // uv offset
uniform vec4 Impact;          // mode (x), amount (y), centre uv (zw)
uniform float ImpactSeed;     // changes with every impact sub-frame
uniform vec3 Accent;          // impact frame accent colour
uniform float Punch;          // radial zoom blur toward the impact centre, 0..1
uniform vec4 GodRays;         // light source uv (xy), strength (z), 0 = off
uniform vec3 GodRayColor;

/** Leaves the normal 0..0.8 range of the game untouched and rolls HDR highlights off to white. */
vec3 rolloff(vec3 c) {
    float m = max(c.r, max(c.g, c.b));
    if (m <= 0.8) return c;
    float mapped = 0.8 + 0.2 * (1.0 - exp(-(m - 0.8) / 0.2));
    vec3 scaled = c * (mapped / m);
    float bleach = clamp((m - 1.6) * 0.06, 0.0, 0.7);
    return mix(scaled, vec3(mapped), bleach);
}

float lumAt(vec2 p) {
    return luminance(rolloff(texture(Hdr, p).rgb + texture(Bloom, p).rgb * BloomStrength));
}

/** Speed lines converging on c: thin dark/bright wedges, density by angle bucket, clear near the centre. */
float speedLines(vec2 p, vec2 c, float aspect, float seed) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float a = atan(d.y, d.x) / 6.2831853 + 0.5;
    float n = 220.0;
    float cell = floor(a * n);
    float h = hash12(vec2(cell, seed));
    float w = 0.15 + 0.6 * hash12(vec2(cell, seed + 7.0));
    float f = abs(fract(a * n) - 0.5) * 2.0;
    float line = step(0.55, h) * smoothstep(w, w * 0.6, f);
    float start = 0.18 + 0.35 * hash12(vec2(cell, seed + 3.0));
    return line * smoothstep(start, start + 0.12, r);
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = uv + Shake;
    float mode = Impact.x;
    float amt = Impact.y;
    vec2 ic = Impact.zw;

    // Impact tear: the image splits into horizontal slices that jump sideways
    if (mode > 0.5 && amt > 0.0) {
        float band = floor(p.y * 18.0 + hash12(vec2(ImpactSeed, 1.0)) * 4.0);
        float jump = hash12(vec2(band, ImpactSeed)) - 0.5;
        p.x += jump * step(0.62, abs(jump) * 2.0) * 0.05 * amt;
    }

    // Shockwave: a ring that refracts the image outward as it expands.
    if (Shockwave.w > 0.0) {
        vec2 d = (p - Shockwave.xy) * vec2(aspect, 1.0);
        float len = length(d);
        float ring = exp(-pow((len - Shockwave.z) / 0.035, 2.0));
        p -= normalize(d + 1.0e-5) / vec2(aspect, 1.0) * ring * Shockwave.w * 0.045;
    }

    // Chromatic aberration grows toward the edges; punch-in zoom blur smears everything toward the hit.
    vec2 off = (p - 0.5) * Aberration * 0.012;
    vec3 col = vec3(0.0);
    vec3 bloom = vec3(0.0);
    int taps = Punch > 0.001 ? 10 : 1;
    for (int i = 0; i < 10; i++) {
        if (i >= taps) break;
        float k = float(i) / 9.0;
        vec2 q = mix(p, ic, k * Punch * 0.22);
        vec3 c;
        c.r = texture(Hdr, q + off).r;
        c.g = texture(Hdr, q).g;
        c.b = texture(Hdr, q - off).b;
        col += c;
        bloom += texture(Bloom, q).rgb;
    }
    col /= float(taps);
    bloom /= float(taps);
    col += bloom * BloomStrength;

    // God rays: radial blur of the bright (bloom) image toward the light source
    if (GodRays.z > 0.0) {
        vec2 dir = (GodRays.xy - p) / 28.0;
        vec2 q = p + dir * hash12(gl_FragCoord.xy + Time) ;   // jitter hides banding
        float w = 1.0;
        vec3 rays = vec3(0.0);
        for (int i = 0; i < 28; i++) {
            rays += texture(Bloom, q).rgb * w;
            w *= 0.955;
            q += dir;
        }
        col += rays / 28.0 * GodRays.z * GodRayColor;
    }

    col *= Exposure;
    col = rolloff(col);

    if (Flash.a > 0.0) {
        col = mix(col, Flash.rgb, clamp(Flash.a, 0.0, 1.0));
    }
    if (Afterimage > 0.0) {
        vec3 burned = vec3(1.0) - col * vec3(0.55, 0.75, 1.0);
        col = mix(col, col * 0.55 + burned * 0.25, Afterimage * 0.6);
    }

    float l = luminance(col);
    col = mix(col, vec3(l) * vec3(1.02, 0.98, 0.95), Desaturate);

    vec2 vq = (uv - 0.5) * vec2(aspect, 1.0);
    float vig = 1.0 - Vignette * smoothstep(0.35, 1.1, length(vq));
    col *= vig;

    if (Grain > 0.0) {
        float g = hash12(gl_FragCoord.xy + fract(Time * 13.37) * 371.0) - 0.5;
        col += g * 0.06 * Grain * (0.4 + 0.6 * (1.0 - l));
    }

    // ---------------------------------------------------------------- impact frames
    if (mode > 0.5 && amt > 0.0) {
        vec2 px = 1.5 / ScreenSize;
        float lc = lumAt(p);
        float gx = lumAt(p + vec2(px.x, 0.0)) - lumAt(p - vec2(px.x, 0.0));
        float gy = lumAt(p + vec2(0.0, px.y)) - lumAt(p - vec2(0.0, px.y));
        float edge = smoothstep(0.04, 0.14, length(vec2(gx, gy)));
        float r = length((p - ic) * vec2(aspect, 1.0));
        // adaptive threshold: everything near the hit tends to white-hot, the edges of frame to ink
        // adaptive threshold from the frame's own brightness (mean + half a deviation), so the subject
        // separates from its background whether it's noon, night, or a sky full of fire
        float sum = 0.0, sum2 = 0.0;
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 7; i++) {
                float s = lumAt(vec2((float(i) + 0.5) / 7.0, (float(j) + 0.5) / 5.0));
                sum += s;
                sum2 += s * s;
            }
        }
        float mean = sum / 35.0;
        float sd = sqrt(max(sum2 / 35.0 - mean * mean, 0.0));
        float th = clamp(mean + 0.5 * sd, 0.08, 0.92) - 0.05 * exp(-r * 3.0);
        float lit = smoothstep(th - 0.012, th + 0.012, lc);
        float lines = speedLines(p, ic, aspect, ImpactSeed);
        // a burst of light at the centre that the shapes are cut against
        float core = exp(-r * 6.0);
        vec3 f;
        if (mode < 1.5) {
            // INK: white paper, black silhouettes and outlines, black speed lines
            float ink = max(1.0 - lit, edge);
            f = vec3(1.0 - ink);
            f = mix(f, vec3(0.0), lines * (1.0 - core));
        } else if (mode < 2.5) {
            // NEGATIVE: black void, glowing white shapes, accent-rimmed outlines, white lines
            f = vec3(lit) + Accent * edge * 1.2;
            f = mix(f, vec3(1.0), lines * 0.9);
            f += vec3(1.0) * core * 0.25;
        } else if (mode < 3.5) {
            // TWO-TONE: accent and black, white-hot core
            f = mix(vec3(0.02, 0.0, 0.0), Accent, lit);
            f = mix(f, vec3(0.0), max(edge, lines) * 0.9);
            f = mix(f, vec3(1.0, 0.97, 0.9), smoothstep(0.75, 0.95, lc) + core * 0.5);
        } else if (mode < 4.5) {
            // HALFTONE: manga screen-tone dots sized by brightness, on black, with accent ink
            vec2 g = gl_FragCoord.xy / 6.0;
            mat2 rot = mat2(0.7071, -0.7071, 0.7071, 0.7071);
            vec2 cell = fract(rot * g) - 0.5;
            float dotR = sqrt(clamp(lc, 0.0, 1.0)) * 0.62;
            float dotM = smoothstep(dotR + 0.06, dotR - 0.06, length(cell));
            f = mix(vec3(0.0), Accent * 1.2, dotM);
            f = mix(f, vec3(1.0), edge * 0.8 + lines * 0.6);
        } else {
            // WHITE-OUT: everything burns to white except the darkest silhouettes, hard black outline
            f = vec3(smoothstep(0.03, 0.12, lc));
            f = mix(f, vec3(0.0), edge);
            f = mix(f, Accent, lines * 0.7);
        }
        col = mix(col, clamp(f, 0.0, 1.0), clamp(amt, 0.0, 1.0));
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
