#version 150

// Every visible effect of the technique, drawn over the finished frame using its depth buffer:
//
//   Dismantle   a crescent blade of cursed energy that sweeps out from the hand in a few frames, with speed
//               streaks in its wake, leaving the cut on every surface it passed through: a white-hot seam that
//               cools to a dark line with ember edges.
//   Cleave      a net of straight cuts across the target, struck one after another.
//   Spiderweb   radial cuts and rings racing across the ground from the palm, with fractures between them.
//   Black Flash black lightning with a crimson glow, bursting from the point of impact.
//   Open        fire pouring out of an open hand; the fire forming a bow and an arrow; the arrow's flight; and
//               the blast: a raymarched pillar of flame.
//   Domain      the sky turns to blood, the land darkens, and every surface and the air itself are cut,
//               constantly, by fleeting strokes; its edge is a line of red light racing outward.
//   World-Cutting Slash
//               one cut through everything: a seam across the land and a line across the whole sky; then the
//               picture of the world itself comes apart along it, the halves sliding, the void showing between.

#include "common.glsl"
#include "world_common.glsl"

in vec2 uv;
out vec4 fragColor;

#define MAX_SLASH 48
uniform int NSlash;
uniform vec4 SlO[MAX_SLASH];   // origin (rel. camera), age s
uniform vec4 SlD[MAX_SLASH];   // direction, length
uniform vec4 SlS[MAX_SLASH];   // side axis, width
uniform vec4 SlP[MAX_SLASH];   // kind (0 dismantle, 1 cleave), speed (blocks/s), seed, intensity

uniform vec4 WebC;             // centre (rel), radius
uniform vec4 WebP;             // age s, seed, on

uniform vec4 BfC;              // point (rel), age s (< 0 off)
uniform float BfSeed;

uniform vec4 FHand;            // hand (rel), fire amount
uniform vec4 FBow;             // bow centre (rel), bow amount
uniform vec4 FDir;             // aim, draw amount
uniform vec4 FUp;              // bow up axis, flight u (< 0: not loosed)
uniform vec4 FFrom;            // arrow launch point (rel), size scale
uniform vec4 FTarget;          // impact point (rel), blast age s (< 0 none)
uniform float FRadius;
uniform float FOn;

uniform vec4 DomC;             // centre (rel), current radius
uniform vec4 DomP;             // age since open s, strength 0..1, full radius, on

uniform vec4 WsO;              // origin (rel), age since the cut s (< 0 none)
uniform vec4 WsD;              // direction (horizontal), length
uniform vec4 WsP;              // split amount (uv), seed, sky line strength, on

const float PI = 3.14159265;

// ------------------------------------------------------------------ helpers
vec3 fireColor(float k) {
    // 0 deep red .. 1 white-yellow core
    k = clamp(k, 0.0, 1.0);
    vec3 c = mix(vec3(0.5, 0.03, 0.0), vec3(1.0, 0.32, 0.02), smoothstep(0.0, 0.35, k));
    c = mix(c, vec3(1.0, 0.72, 0.25), smoothstep(0.35, 0.7, k));
    return mix(c, vec3(1.0, 0.97, 0.85), smoothstep(0.7, 1.0, k));
}

vec2 toScreen(vec3 rel) {
    vec4 c = Proj * vec4(ViewRot * rel, 1.0);
    return c.xy / max(c.w, 1.0e-4) * 0.5 + 0.5;
}

float inFront(vec3 rel) {
    return (ViewRot * rel).z < 0.0 ? 1.0 : 0.0;   // view space looks down -z
}

/** Glowing thin line: distance d in blocks, at distance t from the camera (keeps ~1px minimum width). */
float lineCore(float d, float t, float w) {
    float ww = max(w, t * 0.0012);
    return exp(-(d * d) / (ww * ww));
}

// ------------------------------------------------------------------ Dismantle / Cleave
vec3 slashes(vec3 rd, float sceneT, vec3 wp, bool hasSurface) {
    vec3 add = vec3(0.0);
    for (int i = 0; i < MAX_SLASH; i++) {
        if (i >= NSlash) break;
        vec3 O = SlO[i].xyz;
        float age = SlO[i].w;
        vec3 D = SlD[i].xyz;
        float L = SlD[i].w;
        vec3 S = SlS[i].xyz;
        float W = SlS[i].w;
        float kind = SlP[i].x;
        float speed = SlP[i].y;
        float seed = SlP[i].z;
        float inten = SlP[i].w;
        vec3 N = normalize(cross(D, S));

        if (kind < 0.5) {
            // ---- Dismantle: the sweeping blade
            float front = min(age * speed, L);
            float bladeLife = 1.0 - smoothstep(L / speed, L / speed + 0.08, age);
            if (bladeLife > 0.0) {
                float best = 1.0e9, bt = 0.0, bs = 0.0;
                vec3 prev = O + D * (front - W * 0.28) - S * W * 0.5;
                for (int k = 1; k <= 6; k++) {
                    float s = float(k) / 6.0 * 2.0 - 1.0;
                    vec3 cur = O + D * (front - W * 0.28 * (s * s)) + S * (s * W * 0.5);
                    vec3 r = raySegment(vec3(0.0), rd, prev, cur);
                    if (r.x < best) { best = r.x; bt = r.z; bs = s - (1.0 - r.y) * (2.0 / 6.0); }
                    prev = cur;
                }
                if (bt < sceneT) {
                    float taper = 1.0 - bs * bs;
                    float core = lineCore(best, bt, 0.05 * taper + 0.01);
                    float glow = exp(-best / (0.35 + bt * 0.004));
                    add += (vec3(1.0) * core * 7.0 + vec3(0.85, 0.85, 1.0) * glow * 0.6) * taper * bladeLife * inten;
                }
                // wake: speed streaks across the plane behind the blade
                float den = dot(rd, N);
                if (abs(den) > 1.0e-4) {
                    float tp = dot(O, N) / den;
                    if (tp > 0.0 && tp < sceneT) {
                        vec3 q = rd * tp - O;
                        float a = dot(q, D), s = dot(q, S);
                        if (a > 0.0 && a < front && abs(s) < W * 0.5) {
                            float lane = hash12(vec2(floor(s * 3.0), seed));
                            float streak = step(0.72, lane) * smoothstep(front - 9.0, front, a) * (1.0 - abs(s) / (W * 0.5));
                            add += vec3(0.9, 0.9, 1.0) * streak * 0.5 * bladeLife * inten;
                        }
                    }
                }
            }
            // ---- the cut left on surfaces
            if (hasSurface) {
                vec3 q = wp - O;
                float a = dot(q, D), s = dot(q, S), n = abs(dot(q, N));
                if (a > 0.0 && a < front && abs(s) < W * 0.5) {
                    float since = age - a / speed;                    // time since the blade passed here
                    float hot = exp(-since * 3.0);
                    float life = 1.0 - smoothstep(1.2, 2.6, since);
                    float px = max(fwidth(dot(q, N)) * 1.2, 0.03);
                    float edge = exp(-(n * n) / (px * px)) * life;
                    vec3 c = mix(vec3(0.75, 0.08, 0.02), vec3(1.0, 0.95, 0.9), hot);
                    add += c * edge * (1.5 + 6.0 * hot) * inten;
                }
            }
        } else {
            // ---- Cleave: a single straight cut, struck at its own moment
            float life = age < 0.0 ? 0.0 : exp(-age * 5.0);
            if (life > 0.002) {
                vec3 r = raySegment(vec3(0.0), rd, O, O + D * L);
                if (r.z < sceneT + 0.6) {
                    float draw = smoothstep(0.0, 0.04, age);
                    float along = step(r.y, draw);
                    float core = lineCore(r.x, r.z, 0.03);
                    float glow = exp(-r.x / 0.25);
                    add += (vec3(1.0) * core * 8.0 + vec3(1.0, 0.1, 0.05) * glow * 1.4) * life * along * inten;
                }
            }
        }
    }
    return add;
}

// darkening of the cut seams has to multiply the scene, so it is computed separately
float slashDarken(vec3 wp, bool hasSurface) {
    if (!hasSurface) return 1.0;
    float darken = 1.0;
    for (int i = 0; i < MAX_SLASH; i++) {
        if (i >= NSlash) break;
        if (SlP[i].x > 0.5) continue;
        vec3 O = SlO[i].xyz;
        vec3 D = SlD[i].xyz;
        vec3 S = SlS[i].xyz;
        vec3 N = normalize(cross(D, S));
        float speed = SlP[i].y;
        float front = min(SlO[i].w * speed, SlD[i].w);
        vec3 q = wp - O;
        float a = dot(q, D), s = dot(q, S), n = abs(dot(q, N));
        if (a > 0.0 && a < front && abs(s) < SlS[i].w * 0.5) {
            float since = SlO[i].w - a / speed;
            float life = 1.0 - smoothstep(1.2, 2.6, since);
            float px = max(fwidth(dot(q, N)) * 0.6, 0.015);
            darken *= 1.0 - 0.85 * exp(-(n * n) / (px * px)) * smoothstep(0.05, 0.25, since) * life;
        }
    }
    return darken;
}

// ------------------------------------------------------------------ Spiderweb
vec3 spiderweb(vec3 wp, float sceneT) {
    if (WebP.z < 0.5 || sceneT > 1.0e8) return vec3(0.0);
    vec3 q = wp - WebC.xyz;
    if (abs(q.y) > 6.0) return vec3(0.0);
    float r = length(q.xz);
    float R = WebC.w;
    if (r > R + 2.0) return vec3(0.0);
    float age = WebP.x;
    float front = R * (1.0 - pow(1.0 - clamp(age / 0.22, 0.0, 1.0), 2.0));
    float reached = smoothstep(front + 0.5, front - 0.5, r);
    float ang = atan(q.z, q.x);
    float spokes = 12.0;
    float a = ang / (2.0 * PI) * spokes + 0.5 * hash12(vec2(WebP.y, 1.0));
    float wob = (noise3(vec3(r * 0.3, a, WebP.y)) - 0.5) * 0.35;
    float spoke = abs(fract(a + wob) - 0.5) * 2.0 * PI / spokes * r;          // distance to nearest spoke (blocks)
    float ring = min(min(abs(r - 7.0), abs(r - 14.0)), abs(r - 21.0)) + (noise3(vec3(ang * 3.0, r * 0.2, 3.0)) - 0.5) * 0.6;
    float crack = abs(noise3(vec3(q.xz * 0.35, WebP.y)) - 0.5);                 // fractures between the cuts
    float d = min(spoke, ring);
    float hot = exp(-age * 1.6);
    float life = 1.0 - smoothstep(1.5, 3.0, age);
    float px = clamp(fwidth(r) * 0.8, 0.05, 0.25);
    float line = exp(-(d * d) / (px * px)) * reached * life;
    float frac = exp(-(crack * crack) / 0.0004) * reached * life * 0.35 * (1.0 - r / (R + 2.0));
    vec3 c = mix(vec3(0.85, 0.06, 0.02), vec3(1.0, 0.95, 0.9), hot);
    // the racing front glows brightest
    float frontGlow = exp(-pow((r - front) / 1.2, 2.0)) * step(age, 0.3) * 2.0;
    return c * (line * (2.0 + 6.0 * hot) + frac * 1.2) + vec3(1.0, 0.9, 0.85) * frontGlow * exp(-d * 0.8);
}

// ------------------------------------------------------------------ Black Flash
vec3 blackFlash(vec2 p, float aspect, inout float darken) {
    if (BfC.w < 0.0 || BfC.w > 0.6 || inFront(BfC.xyz) < 0.5) return vec3(0.0);
    float age = BfC.w;
    vec2 c = toScreen(BfC.xyz);
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float th = atan(d.y, d.x);
    float dist = length(BfC.xyz);
    float scale = clamp(6.0 / max(dist, 1.0), 0.15, 1.5);
    float reach = (0.06 + 0.34 * smoothstep(0.0, 0.07, age)) * scale * (0.7 + 0.6 * hash12(vec2(BfSeed, 9.0)));
    // flicker: lightning strobes on and off
    float strobe = 0.35 + 0.65 * step(0.25, hash12(vec2(floor(age * 40.0), BfSeed)));
    float bolts = 0.0, rim = 0.0;
    for (int k = 0; k < 9; k++) {
        float a0 = hash12(vec2(float(k), BfSeed)) * 2.0 * PI;
        float jag = (fbm3(vec3(r * 26.0 / scale, float(k) * 7.0, BfSeed + floor(age * 30.0))) - 0.5) * 0.16 * scale;
        jag += (noise3(vec3(r * 90.0 / scale, float(k) * 3.0, floor(age * 30.0))) - 0.5) * 0.025 * scale;
        float ang = mod(th - a0 + PI, 2.0 * PI) - PI;
        float da = abs(ang * r - jag * smoothstep(0.0, 0.05 * scale, r)) + step(PI * 0.5, abs(ang)) * 10.0;
        float w = 0.004 * scale * (1.0 - r / reach);
        bolts = max(bolts, exp(-(da * da) / (w * w + 1.0e-7)) * step(r, reach));
        rim = max(rim, exp(-da / (w * 3.0 + 1.0e-5)) * step(r, reach));
    }
    float life = 1.0 - smoothstep(0.25, 0.6, age);
    darken *= 1.0 - 0.95 * bolts * strobe * life;
    float halo = exp(-r / (0.05 * scale)) * 0.8 + exp(-r / (0.2 * scale)) * 0.25;
    vec3 red = vec3(1.0, 0.05, 0.08);
    return red * (rim * 4.0 * strobe + halo * (1.0 - age * 1.6)) * life;
}

// ------------------------------------------------------------------ Open
/** A fiery tube along a polyline sampled from the bow arc. */
float bowDistance(vec3 rd, out float tAt) {
    vec3 c = FBow.xyz;
    vec3 up = FUp.xyz;
    vec3 fwd = FDir.xyz;
    float R = 1.15 * FFrom.w;
    float best = 1.0e9;
    tAt = 1.0e9;
    vec3 prev = c + up * R * sin(-1.15) - fwd * R * 0.38 * (1.0 - cos(-1.15) - 0.6);
    for (int k = 1; k <= 10; k++) {
        float th = mix(-1.15, 1.15, float(k) / 10.0);
        vec3 cur = c + up * R * sin(th) + fwd * R * 0.38 * cos(th);
        vec3 r = raySegment(vec3(0.0), rd, prev, cur);
        if (r.x < best) { best = r.x; tAt = r.z; }
        prev = cur;
    }
    return best;
}

vec3 fuga(vec3 rd, float sceneT, vec3 wp, bool hasSurface) {
    if (FOn < 0.5) return vec3(0.0);
    vec3 col = vec3(0.0);
    float T = Time;
    float sc = FFrom.w;

    // fire pouring from the open hand: a small raymarched flame
    if (FHand.w > 0.001) {
        vec3 c = FHand.xyz;
        float R = (0.16 + 0.3 * FHand.w) * sc;
        vec2 h = sphereHit(vec3(0.0), rd, c + vec3(0.0, R * 0.6, 0.0), R * 1.8);
        if (h.y > 0.0) {
            float t0 = max(h.x, 0.0), t1 = min(h.y, sceneT);
            float dt = (t1 - t0) / 18.0;
            vec3 acc = vec3(0.0);
            for (int i = 0; i < 18; i++) {
                vec3 p = rd * (t0 + dt * (float(i) + 0.5)) - c;
                vec3 pn = p / R;
                float rise = clamp(pn.y * 0.5 + 0.5, 0.0, 1.0);
                float shape = 1.0 - length(vec3(pn.x, (pn.y - 0.5) * 0.55, pn.z)) / (1.1 - 0.5 * rise);
                float n = fbm3(pn * 2.4 - vec3(0.0, T * 3.5, 0.0));
                float dens = clamp(shape + (n - 0.5) * 1.2, 0.0, 1.0);
                acc += fireColor(dens * 1.3) * dens * dens * 2.2 * dt / R;
            }
            col += acc * FHand.w;
        }
    }

    // the bow of fire
    if (FBow.w > 0.001) {
        float tb;
        float d = bowDistance(rd, tb);
        if (tb < sceneT) {
            float flick = 0.6 + 0.4 * noise3(vec3(tb * 4.0, T * 9.0, 1.0));
            col += fireColor(0.85) * exp(-(d * d) / (0.01 * sc * sc)) * 5.0 * FBow.w
                 + fireColor(0.5) * exp(-d / (0.18 * sc)) * flick * 1.4 * FBow.w;
        }
        // the string, drawn back to the nock
        vec3 up = FUp.xyz, fwd = FDir.xyz;
        float R = 1.15 * sc;
        vec3 top = FBow.xyz + up * R * sin(1.15) + fwd * R * 0.38 * cos(1.15);
        vec3 bot = FBow.xyz - up * R * sin(1.15) + fwd * R * 0.38 * cos(1.15);
        vec3 nock = FBow.xyz - fwd * R * (0.1 + 0.75 * FDir.w);
        vec3 s1 = raySegment(vec3(0.0), rd, top, nock), s2 = raySegment(vec3(0.0), rd, bot, nock);
        float ds = min(s1.x, s2.x);
        if (min(s1.z, s2.z) < sceneT) col += fireColor(0.9) * exp(-(ds * ds) / (0.0015 * sc * sc)) * 3.0 * FBow.w;
        // the arrow, nocked: a lance of fire
        if (FDir.w > 0.01 && FUp.w < 0.0) {
            vec3 tip = FBow.xyz + fwd * R * 0.95;
            vec3 a = raySegment(vec3(0.0), rd, nock, tip);
            if (a.z < sceneT) {
                float w = (0.05 + 0.05 * (1.0 - a.y)) * sc;
                float lick = fbm3(vec3(a.y * 9.0 - T * 6.0, a.x / sc * 6.0, 4.0));
                col += fireColor(1.0) * exp(-(a.x * a.x) / (w * w)) * 8.0 * FDir.w
                     + fireColor(0.6) * exp(-a.x / (0.25 * sc)) * (0.6 + lick) * 1.5 * FDir.w;
            }
        }
    }

    // the arrow in flight, and its burning wake
    if (FUp.w >= 0.0 && FUp.w <= 1.0) {
        vec3 head = mix(FFrom.xyz, FTarget.xyz, FUp.w);
        vec3 tail = FFrom.xyz;
        vec3 a = raySegment(vec3(0.0), rd, tail, head);
        if (a.z < sceneT + 2.0) {
            float w = (0.12 + 0.55 * (1.0 - a.y)) * sc;
            float lick = fbm3(vec3(a.y * 14.0 - T * 12.0, a.x * 2.0, 9.0));
            float wake = smoothstep(0.0, 1.0, a.y);
            col += fireColor(1.0) * exp(-(a.x * a.x) / (0.03 * sc * sc)) * 10.0 * pow(a.y, 6.0)
                 + fireColor(0.45 + 0.5 * a.y) * exp(-a.x / w) * (0.5 + lick) * 2.2 * wake;
        }
        vec3 hs = raySegment(vec3(0.0), rd, head, head + FDir.xyz * 0.5);
        col += vec3(1.0, 0.95, 0.85) * exp(-hs.x / (0.6 * sc)) * 3.0;
    }

    // the blast: a pillar of fire
    float bage = FTarget.w;
    if (bage >= 0.0) {
        float Rb = FRadius;
        float grow = 1.0 - pow(1.0 - clamp(bage / 0.6, 0.0, 1.0), 3.0);
        float rad = Rb * (0.35 + 0.85 * grow);
        float H = Rb * (1.0 + 4.5 * (1.0 - pow(1.0 - clamp(bage / 2.5, 0.0, 1.0), 2.0)));
        float fade = 1.0 - smoothstep(4.0, 8.0, bage);
        vec3 c = FTarget.xyz;
        // bounding cylinder
        vec3 ro = -c;
        vec2 dxz = rd.xz;
        float A = dot(dxz, dxz), B = dot(ro.xz, dxz), C = dot(ro.xz, ro.xz) - rad * rad * 1.6;
        float disc = B * B - A * C;
        if (disc > 0.0 && fade > 0.0) {
            float sq = sqrt(disc);
            float t0 = max((-B - sq) / A, 0.0), t1 = min((-B + sq) / A, sceneT);
            if (t1 > t0) {
                int steps = Steps;
                float dt = (t1 - t0) / float(steps);
                vec3 acc = vec3(0.0);
                float trans = 1.0;
                float jit = hash12(gl_FragCoord.xy + T);
                for (int i = 0; i < 160; i++) {
                    if (i >= steps || trans < 0.02) break;
                    vec3 p = ro + rd * (t0 + dt * (float(i) + jit));
                    float y = p.y + rad * 0.25;
                    if (y < -rad * 0.3 || y > H) continue;
                    float hy = clamp(y / H, 0.0, 1.0);
                    // the column swells into a mushroom head near the top
                    float width = rad * (0.75 + 0.25 * sin(hy * 3.0) + 0.9 * smoothstep(0.7, 1.0, hy) * grow);
                    float rr = length(p.xz) / width;
                    float n = fbm3(p / Rb * 2.2 - vec3(0.0, T * 1.6 + bage * 0.8, 0.0));
                    float dens = clamp((1.0 - rr) * 1.6 + (n - 0.5) * 1.4, 0.0, 1.0) * smoothstep(H, H * 0.82, y);
                    if (dens <= 0.0) continue;
                    float heat = clamp((1.0 - rr) * 1.3 - hy * 0.35 + n * 0.3 - bage * 0.06, 0.0, 1.0);
                    float smoke = smoothstep(0.55, 1.0, hy) * smoothstep(1.0, 3.5, bage);
                    vec3 e = fireColor(heat) * (0.6 + 3.5 * heat * heat) * (1.0 - smoke * 0.9);
                    float a = dens * dt / Rb * 2.5;
                    acc += e * a * trans * 2.2;
                    trans *= exp(-a * (0.6 + smoke * 1.5));
                }
                col = col * trans + acc * fade;
            }
        }
        // the shock: a ring of light racing across the ground, and the glowing crater
        if (hasSurface) {
            vec3 q = wp - c;
            float rq = length(q.xz);
            float ringR = bage * 90.0;
            float ring = exp(-pow((rq - ringR) / (3.0 + bage * 6.0), 2.0)) * exp(-bage * 1.2) * step(abs(q.y), 20.0);
            col += vec3(1.0, 0.55, 0.2) * ring * 3.0;
            float glow = exp(-max(length(q) - Rb, 0.0) / (Rb * 0.4)) * fade;
            col += fireColor(0.45) * glow * 1.5;
        }
        // flash
        col += vec3(1.0, 0.75, 0.4) * exp(-bage * 5.0) * 2.0;
    }
    return col;
}

// ------------------------------------------------------------------ Domain
vec3 domainCuts(vec3 wp, float t) {
    // strokes that flicker into existence all over every surface, a different set every few frames
    vec3 total = vec3(0.0);
    for (int layer = 0; layer < 2; layer++) {
        float cs = layer == 0 ? 2.6 : 6.5;
        vec3 cell = floor(wp / cs);
        vec3 f = wp / cs - cell;
        float slot = floor(Time * (14.0 - float(layer) * 5.0) + hash13(cell) * 7.0);
        float pick = hash13(cell + slot * 1.37);
        if (pick < 0.55) continue;
        vec3 dir = normalize(hash33(cell + slot) - 0.5);
        vec3 pt = hash33(cell * 1.7 + slot) * 0.6 + 0.2;
        vec3 v = f - pt;
        float d = length(v - dir * dot(v, dir)) * cs;
        float along = abs(dot(v, dir));
        float life = fract(Time * (14.0 - float(layer) * 5.0) + hash13(cell) * 7.0);
        float l = exp(-(d * d) / (0.004 + t * 1.0e-5)) * smoothstep(0.55, 0.2, along) * (1.0 - life);
        total += mix(vec3(1.0, 0.2, 0.15), vec3(1.0, 0.95, 0.95), 1.0 - life) * l * (layer == 0 ? 2.5 : 4.0);
    }
    return total;
}

vec3 domainAir(vec2 p, float aspect) {
    // cuts in the air itself, in screen space: fast, thin, everywhere
    vec3 total = vec3(0.0);
    for (int layer = 0; layer < 2; layer++) {
        vec2 g = p * vec2(aspect, 1.0) * (layer == 0 ? 7.0 : 3.0);
        vec2 cell = floor(g);
        vec2 f = g - cell;
        float rate = layer == 0 ? 18.0 : 9.0;
        float ph = Time * rate + hash12(cell) * 9.0;
        float slot = floor(ph);
        if (hash12(cell + slot * 3.1) < 0.7) continue;
        float ang = hash12(cell + slot) * PI;
        vec2 dir = vec2(cos(ang), sin(ang));
        vec2 v = f - vec2(0.5) - (hash12(cell * 1.3 + slot) - 0.5) * 0.4;
        float d = abs(v.x * dir.y - v.y * dir.x);
        float along = abs(dot(v, dir));
        float life = fract(ph);
        float w = layer == 0 ? 0.012 : 0.006;
        total += vec3(1.0, 0.92, 0.92) * exp(-(d * d) / (w * w)) * smoothstep(0.75, 0.2, along) * pow(1.0 - life, 2.0) * 0.9;
    }
    return total;
}

// ------------------------------------------------------------------ World-Cutting Slash
vec3 wsNormal() {
    return normalize(vec3(-WsD.z, 0.0, WsD.x));
}

float wsSide(vec3 p) {
    return dot(p - WsO.xyz, wsNormal());
}

void main() {
    vec2 res = vec2(textureSize(SceneTex, 0));
    float aspect = res.x / res.y;
    vec3 rd = viewRay(uv);

    // ---- the world comes apart: the picture on each side of the cut slides away from it
    vec2 suv = uv;
    bool gap = false;
    float gapEdge = 0.0;
    if (WsP.w > 0.5 && WsP.x > 0.0) {
        float t0 = sceneDistance(uv);
        vec3 p0 = rd * min(t0, 3000.0);
        float side = sign(wsSide(p0));
        // screen direction of the plane's normal, near the cut ahead of the camera
        vec3 anchor = WsO.xyz + WsD.xyz * 40.0;
        vec2 s0 = toScreen(anchor), s1 = toScreen(anchor + wsNormal() * 4.0);
        vec2 sd = s1 - s0;
        sd = length(sd) > 1.0e-5 ? normalize(sd) : vec2(1.0, 0.0);
        suv = uv - sd * side * WsP.x;
        float t1 = sceneDistance(suv);
        vec3 p1 = normalize(InvViewRot * (vec4(InvProj * vec4(suv * 2.0 - 1.0, 1.0, 1.0)).xyz)) * min(t1, 3000.0);
        float side1 = sign(wsSide(p1));
        if (side1 != side) gap = true;
        gapEdge = 1.0 - smoothstep(0.0, 6.0, abs(wsSide(p1)));
    }

    vec3 scene = texture(SceneTex, suv).rgb;
    float sceneT = sceneDistance(suv);
    vec3 rds = normalize(InvViewRot * (vec4(InvProj * vec4(suv * 2.0 - 1.0, 1.0, 1.0)).xyz));
    bool hasSurface = sceneT < 1.0e8;
    vec3 wp = rds * sceneT;
    vec3 col = scene;

    // ---- Domain: the world turns
    if (DomP.w > 0.5 && DomP.y > 0.0) {
        float s = DomP.y;
        float camIn = 1.0 - smoothstep(DomC.w - 4.0, DomC.w + 4.0, length(DomC.xyz));
        float inside = hasSurface ? 1.0 - smoothstep(DomC.w - 1.5, DomC.w + 1.5, length(wp - DomC.xyz)) : camIn;
        float l = luminance(col);
        vec3 graded = mix(vec3(l), col, 0.35) * vec3(1.15, 0.38, 0.32) * 0.75;
        if (!hasSurface) {
            // the sky becomes a wound: crimson at the horizon, black above, slow black bands
            float up = clamp(rd.y, -0.2, 1.0);
            float bands = fbm3(vec3(rd.xz / max(rd.y + 0.35, 0.05) * 0.6, Time * 0.03));
            vec3 sky = mix(vec3(0.75, 0.05, 0.03), vec3(0.06, 0.0, 0.0), smoothstep(-0.05, 0.6, up));
            sky *= 0.55 + 0.6 * bands;
            // scars across the sky: long, still, faintly glowing
            float scar = 0.0;
            for (int k = 0; k < 4; k++) {
                vec3 nk = normalize(hash33(vec3(float(k), DomP.z, 3.0)) - 0.5);
                scar += exp(-pow(dot(rd, nk) / 0.0025, 2.0)) * step(0.0, rd.y + 0.05);
            }
            graded = sky + vec3(1.0, 0.3, 0.25) * scar * 0.8;
        }
        col = mix(col, graded, inside * s);
        if (hasSurface) col += domainCuts(wp, sceneT) * inside * s;
        col += domainAir(uv, aspect) * camIn * s * 0.6;
        // the edge: a racing line of red light on the ground
        if (hasSurface) {
            float e = abs(length(wp.xz - DomC.xz) - DomC.w);
            col += vec3(1.0, 0.1, 0.05) * exp(-e * e / 2.0) * s * (DomC.w < DomP.z - 0.5 ? 3.0 : 0.8);
        }
    }

    // ---- Dismantle / Cleave
    col *= slashDarken(wp, hasSurface);
    col += slashes(rds, sceneT, wp, hasSurface);

    // ---- Spiderweb
    col += spiderweb(wp, sceneT);

    // ---- Black Flash
    float bfDark = 1.0;
    vec3 bf = blackFlash(uv, aspect, bfDark);
    col = col * bfDark + bf;

    // ---- Open
    col += fuga(rds, sceneT, wp, hasSurface);

    // ---- World-Cutting Slash
    if (WsP.w > 0.5 && WsO.w >= 0.0) {
        float age = WsO.w;
        vec3 N = wsNormal();
        float front = min(WsD.w, age * 2500.0);
        float hot = exp(-age * 0.8);
        // the seam across the land
        if (hasSurface) {
            vec3 q = wp - WsO.xyz;
            float a = dot(q, WsD.xyz), n = abs(dot(q, N));
            if (a > 0.0 && a < front) {
                float px = max(fwidth(dot(q, N)) * 1.5, 0.05);
                float w = px + age * 0.25;
                float seam = exp(-(n * n) / (w * w));
                col = col * (1.0 - 0.9 * exp(-(n * n) / (w * w * 0.2)) * smoothstep(0.1, 0.6, age));
                col += mix(vec3(1.0, 0.2, 0.1), vec3(1.0), hot) * seam * (2.0 + 10.0 * hot);
                // a shock racing outward from the seam on both sides
                float sh = exp(-pow((n - age * 70.0) / (2.0 + age * 8.0), 2.0)) * exp(-age * 1.1);
                col += vec3(1.0, 0.85, 0.75) * sh * 0.9;
            }
        }
        // the line across the sky: the plane meets the heavens along a great circle
        if (!hasSurface) {
            float d = abs(dot(rds, N));
            float ahead = smoothstep(-0.2, 0.2, dot(rds, WsD.xyz) + 0.6);
            float width = 0.0015 + 0.002 * hot;
            col += (vec3(1.0) * exp(-(d * d) / (width * width)) * 8.0 + vec3(0.9, 0.85, 1.0) * exp(-d / 0.03) * 0.6)
                   * ahead * WsP.z * (0.5 + 0.5 * hot);
        }
        // the split: the void between the halves
        if (gap) {
            vec3 voidc = vec3(0.0) + vec3(0.35, 0.05, 0.6) * 0.15 * fbm3(vec3(uv * 40.0, Time));
            col = voidc + vec3(1.0) * gapEdge * 3.0;
        }
        col += vec3(1.0) * exp(-age * 10.0) * 0.6 * WsP.z;   // the flash of the cut
    }

    fragColor = vec4(col, 1.0);
}
