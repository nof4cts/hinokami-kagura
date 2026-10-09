#version 150

// TON 618: the black hole itself, raymarched per pixel on top of the rendered world.
//
// Each pixel's view ray is integrated through the hole's gravity (a Schwarzschild photon path in
// the standard "bend by -1.5 h^2 r / |r|^5" form, units of the horizon radius Rs). Along the way it
//   - crosses the accretion disk (thin, turbulent, Keplerian, Doppler-beamed and gravitationally
//     redshifted, coloured by black-body temperature), possibly several times: that is what draws
//     the disk's far side bent up over the top and the thin photon ring,
//   - passes through the polar jets,
//   - is either swallowed by the horizon (black), stopped by terrain in front, or escapes. Escaped rays
//     look up the WORLD at the screen position of their bent direction, so the landscape and sky
//     behind the hole are genuinely lensed; directions that leave the screen fall back to deep space.
// Output is HDR (values well above 1 for the disk and jets) for the bloom and grading passes.

#include "common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D SceneTex;
uniform sampler2D DepthTex;
uniform mat4 Proj;
uniform mat4 InvProj;
uniform mat3 ViewRot;     // world -> view
uniform mat3 InvViewRot;  // view -> world
uniform vec3 HoleRel;     // hole centre minus camera, in blocks
uniform float Rs;         // horizon radius in blocks
uniform mat3 DiskBasis;   // columns: disk X, disk normal, disk Z (world space)
uniform float Time;       // seconds
uniform int Steps;
uniform float Presence;   // 0..1 overall fade
uniform float Jets;       // jet strength
uniform float DiskGain;   // disk brightness multiplier
uniform float HasHole;
uniform float Eclipse;    // 0..1 the world dims and cools around the hole as it closes in
uniform float SpaceMix;   // 0 = lensed world behind the hole, 1 = deep space (night sky / orbit shots)

const float R_BOUND = 24.0;  // integration sphere (Rs units)
const float R_IN = 3.0;      // innermost stable circular orbit = 3 Rs
const float R_OUT = 11.5;

vec3 viewRay(vec2 p) {
    vec4 c = InvProj * vec4(p * 2.0 - 1.0, 1.0, 1.0);
    return normalize(InvViewRot * (c.xyz / c.w));
}

float sceneDistance(vec2 p) {
    float d = texture(DepthTex, p).r;
    if (d >= 0.99999) return 1.0e9; // sky: infinitely far
    vec4 v = InvProj * vec4(p * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return length(v.xyz / v.w);
}

/**
 * Screen position the bent ray now points at. Off-screen directions clamp to the nearest edge
 * (their bending is tiny there, so the smear is invisible); directions behind the camera report 0.
 */
vec2 projectDir(vec3 worldDir, vec2 fallback, out float valid) {
    vec3 v = ViewRot * worldDir;
    vec4 clip = Proj * vec4(v * 64.0, 1.0);
    if (clip.w <= 0.0) {
        valid = 0.0;
        return fallback;
    }
    valid = 1.0;
    return clamp(clip.xy / clip.w * 0.5 + 0.5, vec2(0.001), vec2(0.999));
}

/** Deep space for lensed directions that leave the screen: stars plus faint warm nebulosity. */
vec3 space(vec3 dir) {
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float scale = 120.0 + float(layer) * 170.0;
        vec3 p = dir * scale;
        vec3 cell = floor(p);
        vec3 h = hash33(cell + float(layer) * 31.7);
        if (h.x > 0.965) {
            vec3 centre = cell + 0.25 + 0.5 * h;
            float d = length(p - centre);
            float b = pow(max(0.0, 1.0 - d * 2.2), 6.0) * (0.5 + 4.0 * h.y * h.y);
            col += b * blackbody(3500.0 + h.z * 9000.0);
        }
    }
    float n = fbm3(dir * 3.0);
    float n2 = fbm3(dir * 7.0 + 4.0);
    col += vec3(0.10, 0.045, 0.03) * pow(n, 3.0) * 1.6 + vec3(0.03, 0.035, 0.07) * pow(n2, 4.0) * 1.4;
    return col;
}

/** Thin-disk emission at crossing point q (radius rr), seen along ray direction vdir. rgb = light, a = opacity. */
vec4 disk(vec3 q, float rr, vec3 vdir, vec3 dx, vec3 n, vec3 dz) {
    float x = dot(q, dx);
    float z = dot(q, dz);
    float phi = atan(z, x);
    // Keplerian rotation: inner gas laps the outer gas. Sample noise in the co-rotating frame.
    float omega = 1.6 * pow(rr, -1.5);
    float a = phi + Time * omega;
    float lr = log(rr);
    vec3 sp = vec3(cos(a) * 2.2, sin(a) * 2.2, lr * 5.5);
    float turb = fbm3(sp + vec3(0.0, 0.0, Time * 0.05));
    // Spiral streaks: high-frequency noise stretched along the orbit, sharpened into lanes.
    float lanes = fbm3(vec3(cos(a + lr * 2.6) * 9.0, sin(a + lr * 2.6) * 9.0, lr * 34.0));
    lanes = smoothstep(0.25, 0.75, lanes);
    float body = smoothstep(R_IN * 0.95, R_IN * 1.12, rr) * (1.0 - smoothstep(R_OUT * 0.45, R_OUT, rr));
    // A hot, dense inner rim where gas piles up before plunging in.
    float rim = exp(-pow((rr - R_IN * 1.12) / 0.45, 2.0));
    float dens = body * (0.15 + 1.6 * pow(turb, 1.7) * (0.35 + 0.9 * lanes)) + rim * 0.9;

    // Novikov-Thorne-like temperature profile, peaking just outside the inner edge.
    float k = R_IN / rr;
    float prof = pow(k, 0.75) * pow(max(1.0 - sqrt(k * 0.93), 0.0), 0.25) * 2.4;

    // Relativistic Doppler beaming (the approaching side blazes) and gravitational redshift.
    vec3 orbit = normalize(cross(n, q));
    float beta = clamp(sqrt(0.5 / max(rr - 1.0, 0.1)), 0.0, 0.7);
    float cosT = dot(orbit, -normalize(vdir));
    float gamma = inversesqrt(1.0 - beta * beta);
    float g = 1.0 / (gamma * (1.0 - beta * cosT)) * sqrt(max(1.0 - 1.0 / rr, 0.02));

    float temp = 1300.0 + 3700.0 * prof * pow(g, 1.4);
    vec3 col = blackbody(temp);
    float intensity = prof * g * g * g * 2.2 * DiskGain;
    float alpha = clamp(dens * 1.35, 0.0, 0.97);
    return vec4(col * intensity * dens, alpha);
}

/** Relativistic polar jets: narrow, flickering blue-white columns along the disk axis. */
vec3 jet(vec3 p, vec3 n) {
    float ax = dot(p, n);
    float h = abs(ax);
    if (h < 1.1 || h > R_BOUND) return vec3(0.0);
    float rho = length(p - n * ax);
    float w = 0.16 + h * 0.055;
    float core = exp(-(rho * rho) / (w * w));
    float knots = 0.45 + 0.55 * noise3(vec3(h * 1.4 - Time * 9.0 * sign(ax), rho * 3.0, sign(ax)));
    float fall = exp(-h / 11.0) * smoothstep(1.1, 2.6, h);
    return mix(vec3(0.45, 0.62, 1.0), vec3(1.0), core * core) * core * fall * knots * Jets * 0.55;
}

/** The world loses light toward the hole: strongest right around it, a little everywhere. */
vec3 dimmed(vec3 c, vec3 rd) {
    if (Eclipse <= 0.0) return c;
    float dist = length(HoleRel);
    float ang = acos(clamp(dot(rd, HoleRel / max(dist, 1.0e-3)), -1.0, 1.0));
    float radius = asin(clamp(R_BOUND * Rs / max(dist, 1.0e-3), 0.0, 1.0));
    float local = 1.0 - smoothstep(radius * 0.6, radius * 3.5 + 0.2, ang);
    float k = Eclipse * (0.35 + 0.55 * local);
    float l = luminance(c);
    vec3 cold = mix(c, vec3(l) * vec3(0.78, 0.86, 1.05), 0.6 * Eclipse);
    return cold * (1.0 - k);
}

void main() {
    vec3 rd = viewRay(uv);
    vec3 scene = texture(SceneTex, uv).rgb;
    if (HasHole < 0.5 || Presence <= 0.001) {
        fragColor = vec4(scene, 1.0);
        return;
    }
    scene = dimmed(scene, rd);
    vec3 ro = -HoleRel / Rs;

    // Only pixels whose ray enters the integration sphere do any work.
    float b = dot(ro, rd);
    float c = dot(ro, ro) - R_BOUND * R_BOUND;
    float disc = b * b - c;
    if (disc < 0.0) {
        fragColor = vec4(scene, 1.0);
        return;
    }
    float sq = sqrt(disc);
    float t1 = -b + sq;
    float tStart = max(-b - sq, 0.0);
    float sceneT = sceneDistance(uv) / Rs;
    if (t1 < 0.0 || sceneT < tStart) {
        fragColor = vec4(scene, 1.0);
        return;
    }

    vec3 dx = DiskBasis[0];
    vec3 n = DiskBasis[1];
    vec3 dz = DiskBasis[2];

    // Small per-pixel jitter on the first step breaks up banding in the disk and jets.
    float jitter = hash12(gl_FragCoord.xy + fract(Time) * 61.0);
    vec3 p = ro + rd * tStart;
    vec3 v = rd;
    vec3 hv = cross(p, v);
    float h2 = dot(hv, hv);
    float traveled = tStart;

    vec3 emit = vec3(0.0);
    float trans = 1.0;
    int fate = 0; // 0 escaped, 1 swallowed, 2 reached terrain, 3 fully absorbed by disk
    for (int i = 0; i < 1024; i++) {
        if (i >= Steps) break;
        float r = length(p);
        if (r < 1.0) {
            fate = 1;
            break;
        }
        // Small steps near the hole (photon sphere at 1.5 Rs), long strides far away.
        float dt = clamp(0.055 * r * min(1.0, r / 2.2), 0.012, 1.1);
        if (i == 0) dt *= 0.25 + jitter;

        vec3 acc = -1.5 * h2 * p / pow(r, 5.0);
        vec3 vn = v + acc * dt;
        vec3 pn = p + vn * dt;

        float y0 = dot(p, n);
        float y1 = dot(pn, n);
        if (y0 * y1 < 0.0) {
            vec3 q = mix(p, pn, y0 / (y0 - y1));
            float rr = length(q);
            if (rr > R_IN * 0.9 && rr < R_OUT) {
                vec4 d = disk(q, rr, vn, dx, n, dz);
                emit += trans * d.rgb;
                trans *= 1.0 - d.a;
            }
        }
        if (Jets > 0.0) emit += trans * jet(pn, n) * dt;

        traveled += length(vn) * dt;
        p = pn;
        v = vn;
        if (traveled > sceneT) {
            fate = 2;
            break;
        }
        if (trans < 0.01) {
            fate = 3;
            break;
        }
        if (r > R_BOUND && dot(p, v) > 0.0) break;
    }

    vec3 background;
    if (fate == 1 || fate == 3) {
        background = vec3(0.0);
    } else if (fate == 2) {
        background = scene;
    } else {
        vec3 dir = normalize(v);
        float valid;
        vec2 suv = projectDir(dir, uv, valid);
        vec3 world = dimmed(texture(SceneTex, suv).rgb, dir);
        background = SpaceMix > 0.001 ? mix(world, space(dir) + world * 0.35, SpaceMix) : world;
    }

    vec3 result = background * trans + emit;
    fragColor = vec4(mix(scene, result, Presence), 1.0);
}
