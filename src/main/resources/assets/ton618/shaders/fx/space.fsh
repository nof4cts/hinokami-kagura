#version 150

// TON 618: every off-world shot of every strike, rendered here (it replaces the whole frame).
//
// Scene 0  CANNON   SS-618 hangs in the intergalactic void, far outside the Milky Way, aimed at the Sun inside
//                   it. Raymarched hardware with GGX lighting, reflections, AO, panel work, greebles, nav lights.
//                   The galaxy behind it is a real volume: bulge, logarithmic spiral arms, star-forming knots, dust.
// Scene 1  CROSSING after the shot: the camera rides the projectile across intergalactic space, into the disk,
//                   through the star field to the Sun (Progress 0..1).
// Scene 2  EARTH    the Earth from orbit (4K NASA imagery, single-scattering atmosphere, cloud shadows, terrain
//                   relief, ocean glint, city lights). Per strike: the projectile falling in (Kind 0), the Sun bearing
//                   down on the planet (Kind 1), the heavens opening and the archangel descending (Kind 2).
// Scene 3  SUN      the Sun up close as something tears it out of its orbit (Kind 1).
//
// Units: scene 0 in cannon units (barrel ~55 long), the galaxy as a background volume in its own units;
// scene 2 in Earth units (radius 2000). Output is HDR for the bloom/composite passes.

#include "common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D EarthDay;    // rgb day, a clouds
uniform sampler2D EarthNight;  // rgb city lights, a ocean mask
uniform sampler2D EarthHeight; // r elevation
uniform vec3 CamPos;
uniform vec3 CamFwd;
uniform vec3 CamUp;
uniform vec3 CamRight;
uniform float TanHalfFov;
uniform float Aspect;
uniform float Time;
uniform int Steps;
uniform int Scene;
uniform int Kind;
uniform float Charge;    // cannon charge 0..1
uniform float FireAge;   // seconds since the shot, < 0 before
uniform float Progress;  // per-scene 0..1 (crossing, sun approach, angel descent, sun tearing)

const float PI = 3.14159265;

// ================================================================== galaxy (shared by scenes 0, 1)
// Galaxy units: disk radius ~16. In scene 0 the galaxy sits far away along a fixed direction.
const float GAL_SCALE = 9000.0;               // cannon units per galaxy unit
const vec3 GAL_C = vec3(-30000.0, 22000.0, 150000.0);
vec3 GN, GX, GZ;                              // galaxy normal + in-plane axes
vec3 SUN_G;                                   // the Sun, in galaxy units, relative to the galactic centre

void galaxyFrame() {
    GN = normalize(vec3(0.25, 1.0, -0.55));
    GX = normalize(cross(GN, vec3(0.0, 0.0, 1.0)));
    GZ = cross(GX, GN);
    float a = 2.35;
    SUN_G = (GX * cos(a) + GZ * sin(a)) * 8.5;
}

/** Galaxy emission/absorption density at p (galaxy units). rgb = emission, a = dust. */
vec4 galaxyDensity(vec3 p, float detail) {
    float y = dot(p, GN);
    vec3 pd = p - GN * y;
    float r = length(pd);
    float th = atan(dot(pd, GZ), dot(pd, GX));
    // logarithmic two-armed spiral with a little winding noise
    float lr = log(max(r, 0.05));
    float wind = th - lr * 2.6 + 0.4 * noise3(p * 0.6);
    float arm = pow(0.5 + 0.5 * cos(2.0 * wind), 3.0);
    float arm2 = pow(0.5 + 0.5 * cos(2.0 * wind + 1.9), 6.0) * 0.45;   // fainter spurs
    float n = detail > 0.5 ? fbm3(p * 1.7) : noise3(p * 1.7);
    float disk = exp(-r / 5.0) * exp(-abs(y) / (0.18 + 0.02 * r)) * smoothstep(17.0, 13.0, r);
    float bulge = exp(-length(vec3(r, y * 1.7, 0.0)) / 0.75) * 5.0;
    float arms = disk * (0.25 + 2.4 * (arm + arm2) * (0.55 + 0.9 * n));
    // HII regions: pink knots strung along the arms
    float knots = detail > 0.5 ? pow(noise3(p * 6.0), 6.0) * 30.0 * arm * disk : 0.0;
    vec3 em = vec3(1.0, 0.82, 0.58) * bulge
            + vec3(0.62, 0.74, 1.0) * arms * 1.8
            + vec3(1.0, 0.33, 0.55) * knots * 0.35;
    // dust lanes trail the arms on their inner edge, very thin
    float lane = pow(0.5 + 0.5 * cos(2.0 * wind - 0.55), 8.0);
    float dust = lane * exp(-abs(y) / 0.06) * exp(-r / 6.0) * smoothstep(1.2, 3.0, r) * (0.5 + n) * 9.0;
    return vec4(em, dust);
}

/** Integrates the galaxy volume along a ray (galaxy units). */
vec3 galaxy(vec3 ro, vec3 rd, int steps, out float transmittance) {
    transmittance = 1.0;
    // bound by a flattened sphere
    vec3 oc = ro;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - 18.0 * 18.0;
    float h = b * b - c;
    if (h < 0.0) return vec3(0.0);
    h = sqrt(h);
    float t0 = max(-b - h, 0.0), t1 = -b + h;
    if (t1 <= 0.0) return vec3(0.0);
    vec3 acc = vec3(0.0);
    float dt = (t1 - t0) / float(steps);
    float t = t0 + dt * hash12(gl_FragCoord.xy);
    for (int i = 0; i < 160; i++) {
        if (i >= steps) break;
        vec4 d = galaxyDensity(ro + rd * t, 1.0);
        acc += d.rgb * transmittance * dt * 0.09;
        transmittance *= exp(-d.a * dt * 0.35);
        t += dt;
    }
    return acc;
}

/** Distant field stars and far galaxies. */
vec3 stars(vec3 dir, float density) {
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float scale = 150.0 + float(layer) * 230.0;
        vec3 p = dir * scale;
        vec3 cell = floor(p);
        vec3 h = hash33(cell + float(layer) * 17.3);
        if (h.x > 1.0 - 0.045 * density) {
            vec3 centre = cell + 0.2 + 0.6 * h;
            float d = length(p - centre);
            float bright = pow(max(0.0, 1.0 - d * 2.3), 7.0) * (0.4 + 5.0 * h.y * h.y * h.y);
            col += bright * blackbody(3200.0 + h.z * 9500.0);
        }
    }
    // a few tiny far galaxies
    vec3 gp = dir * 40.0;
    vec3 gc = floor(gp);
    vec3 gh = hash33(gc + 7.7);
    if (gh.x > 0.985) {
        vec3 q = gp - gc - 0.5;
        float e = length(q * vec3(1.0, 3.0, 1.0));
        col += vec3(0.9, 0.85, 1.0) * exp(-e * 18.0) * 0.6;
    }
    return col;
}

// ================================================================== cannon SDF (scene 0)
vec3 AXIS, BX, BY;

#define M_HULL 1.0
#define M_DARK 2.0
#define M_COIL 3.0
#define M_GOLD 4.0
#define M_CORE 5.0
#define M_RAD 6.0
#define M_WIN 7.0
#define M_BORE 8.0
#define M_CHROME 9.0

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    return sdBox(p, b - r) - r;
}

float sdOct(vec2 p, float r) {
    const vec3 k = vec3(-0.9238795325, 0.3826834323, 0.4142135623);
    p = abs(p);
    p -= 2.0 * min(dot(vec2(k.x, k.y), p), 0.0) * vec2(k.x, k.y);
    p -= 2.0 * min(dot(vec2(-k.x, k.y), p), 0.0) * vec2(-k.x, k.y);
    p -= vec2(clamp(p.x, -k.z * r, k.z * r), r);
    return length(p) * sign(p.y);
}

float sdCylZ(vec3 p, float r, float h) {
    vec2 d = abs(vec2(length(p.xy), p.z)) - vec2(r, h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float sdTorusZ(vec3 p, float R, float r) {
    return length(vec2(length(p.xy) - R, p.z)) - r;
}

vec2 rot(vec2 p, float a) {
    float c = cos(a), s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

vec2 opU(vec2 a, vec2 b) {
    return a.x < b.x ? a : b;
}

/** Fold space into one sector of n around the z axis. */
vec2 sector(vec2 p, float n) {
    float sec = 2.0 * PI / n;
    float a = atan(p.y, p.x);
    a = mod(a + sec * 0.5, sec) - sec * 0.5;
    return vec2(cos(a), sin(a)) * length(p);
}

vec2 cannon(vec3 q) {
    // ---- barrel: octagonal, with recessed panel bands and a bored muzzle
    float barrel = max(sdOct(q.xy, 2.0), abs(q.z - 6.0) - 16.0);
    float bands = max(abs(mod(q.z, 2.3) - 1.15) - 1.08, 0.0);
    barrel += 0.03 * smoothstep(0.0, 0.02, bands);
    float bore = max(length(q.xy) - 1.05, 17.5 - q.z);
    barrel = max(barrel, -bore);
    vec2 res = vec2(barrel, M_HULL);
    res = opU(res, vec2(max(length(q.xy) - 1.08, abs(q.z - 20.6) - 0.5), M_BORE));
    // chrome muzzle brake with vents
    float brake = max(abs(length(q.xy) - 2.25) - 0.35, abs(q.z - 21.4) - 0.9);
    vec2 sq = sector(q.xy, 8.0);
    float vents = sdBox(vec3(sq.x - 2.3, sq.y, q.z - 21.4), vec3(0.6, 0.18, 0.55));
    res = opU(res, vec2(max(brake, -vents), M_CHROME));

    // ---- accelerator coils (twelve; every fourth a heavy clamped housing)
    float id = clamp(floor((q.z + 6.0) / 2.3 + 0.5), 0.0, 11.0);
    float zc = id * 2.3 - 6.0;
    bool heavy = mod(id, 4.0) < 0.5;
    float ringR = heavy ? 0.7 : 0.4;
    float ringH = heavy ? 0.55 : 0.26;
    float coil = max(abs(length(q.xy) - 2.7) - ringR, abs(q.z - zc) - ringH);
    if (heavy) {
        vec2 b = sector(rot(q.xy, 0.3926991), 8.0);
        coil = min(coil, sdBox(vec3(b.x - 3.45, b.y, q.z - zc), vec3(0.24, 0.24, 0.75)));
        // bolt heads on the clamps
        coil = min(coil, length(vec3(b.x - 3.72, b.y, abs(q.z - zc) - 0.45)) - 0.09);
    }
    res = opU(res, vec2(coil, M_COIL));

    // ---- rails with conduit bundles
    float rails = sdBox(vec3(abs(q.x) - 3.1, q.y, q.z - 4.5), vec3(0.32, 0.8, 16.6));
    float conduit = length(vec2(abs(q.x) - 3.55, abs(q.y) - 0.45)) - 0.11;
    conduit = max(conduit, abs(q.z - 4.5) - 16.0);
    res = opU(res, vec2(rails, M_DARK));
    res = opU(res, vec2(conduit, M_GOLD));

    // ---- truss spine above and below
    float spine = sdBox(vec3(q.x, abs(q.y) - 3.15, q.z - 3.0), vec3(0.24, 0.24, 15.5));
    float sz = mod(q.z + 0.6, 1.2) - 0.6;
    float strut = sdBox(vec3(q.x, abs(q.y) - 2.6, sz), vec3(0.07, 0.55, 0.07));
    strut = max(strut, abs(q.z - 3.0) - 15.0);
    float diag = sdBox(vec3(q.x, abs(q.y) - 2.6 - sz * 0.75, sz), vec3(0.05, 0.05, 0.6));
    diag = max(diag, abs(q.z - 3.0) - 15.0);
    res = opU(res, vec2(min(spine, min(strut, diag)), M_DARK));

    // ---- receiver block with greebles, lit window rows and the bridge
    vec3 rq = q - vec3(0.0, 0.0, -14.0);
    float receiver = sdRoundBox(rq, vec3(3.4, 3.0, 4.2), 0.35);
    // greeble field on top and sides: hashed boxes on a grid
    vec2 gcell = floor(rq.xz / 0.7);
    vec3 gh = hash33(vec3(gcell, 3.0));
    vec3 gq = vec3(mod(rq.x, 0.7) - 0.35, rq.y - 3.0, mod(rq.z, 0.7) - 0.35);
    float greeb = sdBox(gq - vec3(0.0, gh.y * 0.12, 0.0), vec3(0.12 + 0.18 * gh.x, 0.05 + 0.15 * gh.y, 0.12 + 0.18 * gh.z));
    greeb = max(greeb, sdBox(rq - vec3(0.0, 3.0, 0.0), vec3(3.0, 0.5, 3.8)));
    receiver = min(receiver, greeb);
    res = opU(res, vec2(receiver, M_HULL));
    float bridge = sdRoundBox(q - vec3(0.0, 3.55, -13.0), vec3(1.4, 0.65, 2.0), 0.2);
    res = opU(res, vec2(bridge, M_HULL));
    float bridgeWin = sdBox(q - vec3(0.0, 3.75, -11.0), vec3(1.1, 0.16, 0.05));
    float sideWin = sdBox(vec3(abs(rq.x) - 3.42, rq.y - 0.8, rq.z), vec3(0.03, 0.12, 3.6));
    sideWin = min(sideWin, sdBox(vec3(abs(rq.x) - 3.42, rq.y + 0.4, rq.z), vec3(0.03, 0.12, 3.6)));
    res = opU(res, vec2(min(bridgeWin, sideWin), M_WIN));
    // antenna mast and dish on the bridge
    float mast = max(length(q.xz - vec2(0.8, -14.2)) - 0.06, abs(q.y - 5.4) - 1.2);
    float dish = max(abs(length(q - vec3(0.8, 6.8, -14.2)) - 0.8) - 0.04, q.y - 6.6);
    res = opU(res, vec2(min(mast, dish), M_DARK));

    // ---- capacitor banks
    float caps = sdCylZ(vec3(abs(q.x) - 4.7, q.y + 0.6, q.z + 14.0), 1.15, 3.6);
    float capRings = max(abs(length(vec2(abs(q.x) - 4.7, q.y + 0.6)) - 1.2) - 0.12,
                         abs(mod(q.z + 14.0, 1.4) - 0.7) - 0.1);
    capRings = max(capRings, abs(q.z + 14.0) - 3.5);
    res = opU(res, vec2(caps, M_DARK));
    res = opU(res, vec2(capRings, M_GOLD));

    // ---- reactor: gold ring, glowing core, cage
    vec3 cq = q - vec3(0.0, 0.0, -21.5);
    res = opU(res, vec2(sdTorusZ(cq, 3.6, 0.55), M_GOLD));
    res = opU(res, vec2(length(cq) - 1.45, M_CORE));
    vec2 ca = rot(cq.xy, 0.7853981);
    float cage = min(sdBox(vec3(ca.x, ca.y, cq.z), vec3(3.7, 0.12, 0.12)), sdBox(vec3(ca.x, ca.y, cq.z), vec3(0.12, 3.7, 0.12)));
    cage = min(cage, sdBox(vec3(abs(cq.x) - 2.6, cq.y, cq.z), vec3(0.12, 0.12, 2.4)));
    res = opU(res, vec2(cage, M_DARK));
    res = opU(res, vec2(sdBox(vec3(q.x, q.y, q.z + 19.0), vec3(0.5, 0.5, 1.2)), M_DARK));

    // ---- radiators: four slatted fins in an X
    vec2 xq = rot(q.xy, 0.7853981);
    float finA = sdBox(vec3(abs(xq.x) - 6.2, xq.y, q.z + 16.0), vec3(2.9, 0.06, 3.6));
    float finB = sdBox(vec3(xq.x, abs(xq.y) - 6.2, q.z + 16.0), vec3(0.06, 2.9, 3.6));
    float finFrame = min(sdBox(vec3(abs(xq.x) - 6.2, xq.y, q.z + 16.0), vec3(3.0, 0.12, 0.08)),
                         sdBox(vec3(xq.x, abs(xq.y) - 6.2, q.z + 16.0), vec3(0.12, 3.0, 0.08)));
    res = opU(res, vec2(min(finA, finB), M_RAD));
    res = opU(res, vec2(finFrame, M_DARK));

    // ---- thrusters
    vec3 tq = vec3(abs(q.x) - 1.7, abs(q.y) - 1.7, q.z + 25.6);
    float thr = max(length(tq.xy) - (0.45 + max(-tq.z, 0.0) * 0.35), abs(tq.z) - 0.9);
    res = opU(res, vec2(thr, M_DARK));

    // ---- six focusing prongs round the muzzle
    vec2 pq = sector(q.xy, 6.0);
    vec3 pp = vec3(pq.x - 3.0 + (q.z - 22.0) * 0.2, pq.y, q.z - 23.6);
    res = opU(res, vec2(sdBox(pp, vec3(0.24, 0.32, 2.2)), M_DARK));
    return res;
}

vec3 toLocal(vec3 p) {
    return vec3(dot(p, BX), dot(p, BY), dot(p, AXIS));
}

vec2 map(vec3 p) {
    return cannon(toLocal(p));
}

vec3 calcNormal(vec3 p, float t) {
    float e = 0.0012 * max(1.0, t * 0.08);
    vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * map(p + k.xyy * e).x + k.yyx * map(p + k.yyx * e).x
                   + k.yxy * map(p + k.yxy * e).x + k.xxx * map(p + k.xxx * e).x);
}

float softShadow(vec3 ro, vec3 rd) {
    float res = 1.0;
    float t = 0.05;
    for (int i = 0; i < 48; i++) {
        float h = map(ro + rd * t).x;
        res = min(res, 12.0 * h / t);
        t += clamp(h, 0.03, 0.9);
        if (res < 0.002 || t > 50.0) break;
    }
    return clamp(res, 0.0, 1.0);
}

float ambientOcclusion(vec3 p, vec3 n) {
    float occ = 0.0;
    float w = 1.0;
    for (int i = 1; i <= 6; i++) {
        float h = 0.1 * float(i);
        occ += (h - map(p + n * h).x) * w;
        w *= 0.72;
    }
    return clamp(1.0 - 1.5 * occ, 0.0, 1.0);
}

/** GGX specular term (normalised, includes the geometry term approximation). */
float ggx(vec3 n, vec3 v, vec3 l, float rough) {
    vec3 h = normalize(v + l);
    float a = rough * rough;
    float a2 = a * a;
    float nh = max(dot(n, h), 0.0);
    float d = nh * nh * (a2 - 1.0) + 1.0;
    float D = a2 / (PI * d * d);
    float nv = max(dot(n, v), 0.001), nl = max(dot(n, l), 0.0);
    float k = (rough + 1.0) * (rough + 1.0) / 8.0;
    float G = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
    return D * G / (4.0 * nv * max(nl, 0.001));
}

/** Cheap environment for reflections: field stars plus the galaxy's glow. */
vec3 envCheap(vec3 dir, vec3 galDir) {
    float g = max(dot(dir, galDir), 0.0);
    return vec3(0.9, 0.82, 0.7) * pow(g, 12.0) * 1.2 + vec3(0.4, 0.45, 0.6) * pow(g, 3.0) * 0.15 + vec3(0.004, 0.005, 0.008);
}

vec2 sphereHit(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

vec2 raySegment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ba = b - a;
    vec3 oa = ro - a;
    float baba = dot(ba, ba);
    float bard = dot(ba, rd);
    float baoa = dot(ba, oa);
    float rdoa = dot(rd, oa);
    float denom = baba - bard * bard;
    float t = denom > 1.0e-6 ? (baoa - bard * rdoa) / denom : 0.0;
    t = clamp(t, 0.0, 1.0);
    vec3 pa = a + ba * t;
    float s = max(dot(pa - ro, rd), 0.0);
    return vec2(length(ro + rd * s - pa), t);
}

vec3 sceneCannon(vec3 ro, vec3 rd) {
    vec3 sunC = GAL_C + SUN_G * GAL_SCALE;
    AXIS = normalize(sunC);
    BX = normalize(cross(vec3(0.0, 1.0, 0.0), AXIS));
    BY = cross(AXIS, BX);
    vec3 galDir = normalize(GAL_C);

    // backdrop: the galaxy as a volume, far stars behind it
    float trans;
    vec3 gro = (ro - GAL_C) / GAL_SCALE;
    vec3 col = galaxy(gro, rd, Steps / 3 + 24, trans) * 1.4;
    col += stars(rd, 0.6) * trans;

    // the cannon
    float tHit = -1.0;
    vec2 m = vec2(0.0);
    vec2 bound = sphereHit(ro, rd, vec3(0.0), 32.0);
    if (bound.y > 0.0) {
        float t = max(bound.x, 0.0);
        for (int i = 0; i < 512; i++) {
            if (i >= Steps) break;
            m = map(ro + rd * t);
            if (m.x < 0.0008 * t) {
                tHit = t;
                break;
            }
            t += m.x * 0.85;
            if (t > bound.y) break;
        }
    }

    vec3 muzzle = AXIS * 26.0;
    float chargeWave = Charge * 1.35;
    if (tHit > 0.0) {
        vec3 p = ro + rd * tHit;
        vec3 n = calcNormal(p, tHit);
        vec3 q = toLocal(p);
        float mat = m.y;
        float zn = clamp((q.z + 6.0) / 25.3, 0.0, 1.0);
        vec3 v = -rd;

        vec3 albedo = vec3(0.62, 0.63, 0.66);
        float metal = 0.3;
        float rough = 0.45;
        vec3 emit = vec3(0.0);
        if (mat == M_HULL) {
            // panel grid with per-panel tint and grime, hazard stripes near the muzzle
            vec2 pc = vec2(floor(q.z / 1.15), floor(atan(q.y, q.x) * 2.546));
            float panel = hash12(pc);
            albedo *= 0.82 + 0.22 * panel;
            float seamZ = smoothstep(0.035, 0.0, abs(mod(q.z, 1.15) - 0.575) - 0.55);
            float seamA = smoothstep(0.03, 0.0, abs(fract(atan(q.y, q.x) * 2.546) - 0.5) - 0.48);
            albedo *= 1.0 - 0.6 * max(seamZ, seamA);
            albedo *= 0.9 + 0.1 * fbm3(q * 4.0);
            rough = 0.35 + 0.25 * panel;
            if (q.z > 15.5 && q.z < 17.3 && length(q.xy) > 1.6) {
                float stripe = step(0.5, fract((q.z + atan(q.y, q.x) * 0.4) * 2.5));
                albedo = mix(vec3(0.05), vec3(0.95, 0.65, 0.05), stripe);
            }
        } else if (mat == M_DARK) {
            albedo = vec3(0.06, 0.062, 0.07);
            metal = 0.85;
            rough = 0.32;
        } else if (mat == M_CHROME) {
            albedo = vec3(0.92, 0.93, 0.95);
            metal = 1.0;
            rough = 0.12;
        } else if (mat == M_COIL) {
            albedo = vec3(0.5, 0.27, 0.14); // copper windings
            metal = 1.0;
            rough = 0.38;
            float lit = smoothstep(zn - 0.12, zn, chargeWave) * step(0.001, Charge);
            float hum = 0.85 + 0.15 * sin(Time * 40.0 + q.z * 3.0);
            vec3 hot = mix(vec3(1.0, 0.3, 0.06), vec3(0.55, 0.75, 1.0), Charge);
            float wind = smoothstep(0.9, 0.99, abs(sin(atan(q.y, q.x) * 24.0)));
            float cid = clamp(floor((q.z + 6.0) / 2.3 + 0.5), 0.0, 11.0);
            float face = smoothstep(0.2, 0.0, abs(length(q.xy) - 2.7) - (mod(cid, 4.0) < 0.5 ? 0.5 : 0.25));
            emit = hot * lit * hum * (0.4 + 2.2 * Charge) * (0.08 + 0.92 * wind) * face;
        } else if (mat == M_GOLD) {
            albedo = vec3(1.0, 0.74, 0.32);
            metal = 1.0;
            rough = 0.22;
        } else if (mat == M_CORE) {
            albedo = vec3(0.0);
            float pulse = 0.8 + 0.2 * sin(Time * 6.0);
            emit = mix(vec3(1.0, 0.55, 0.18), vec3(0.7, 0.85, 1.0), Charge) * (3.0 + 6.0 * Charge) * pulse;
        } else if (mat == M_RAD) {
            albedo = vec3(0.2, 0.2, 0.22);
            float slat = step(0.5, fract(q.z * 2.5));
            albedo *= 0.7 + 0.3 * slat;
            rough = 0.6;
            emit = vec3(1.0, 0.18, 0.04) * slat * Charge * Charge * 0.35;
        } else if (mat == M_WIN) {
            albedo = vec3(0.0);
            vec2 wc = floor(vec2(q.z * 3.0, q.y * 2.0));
            float on = step(0.35, hash12(wc));
            emit = vec3(1.0, 0.86, 0.6) * on * 2.2;
        } else if (mat == M_BORE) {
            albedo = vec3(0.0);
            emit = mix(vec3(1.0, 0.3, 0.05), vec3(0.85, 0.92, 1.0), Charge) * (0.6 + 9.0 * Charge * Charge);
        }

        // lights: the galaxy as a broad warm key, a cool rim from the far side, the work lights on the hull
        vec3 keyDir = galDir;
        vec3 keyCol = vec3(1.0, 0.9, 0.76) * 4.0;
        vec3 rimDir = normalize(-galDir + BY * 0.6);
        vec3 rimCol = vec3(0.35, 0.5, 0.9) * 0.9;
        float ao = ambientOcclusion(p, n);
        float sh = dot(n, keyDir) > 0.0 ? softShadow(p + n * 0.01, keyDir) : 0.0;
        vec3 f0 = mix(vec3(0.04), albedo, metal);
        vec3 diffuse = albedo * (1.0 - metal);
        float nv = max(dot(n, v), 0.0);
        vec3 fres = f0 + (1.0 - f0) * pow(1.0 - nv, 5.0);

        vec3 lit = keyCol * max(dot(n, keyDir), 0.0) * sh * (diffuse / PI * 3.0 + fres * ggx(n, v, keyDir, rough));
        lit += rimCol * max(dot(n, rimDir), 0.0) * ao * (diffuse / PI * 3.0 + fres * ggx(n, v, rimDir, rough));
        // floodlights mounted on the receiver, pointing along the barrel
        vec3 flood = normalize(AXIS * 0.8 + BY * 0.6);
        float floodMask = smoothstep(-8.0, 0.0, q.z) * smoothstep(24.0, 10.0, q.z);
        lit += vec3(1.0, 0.95, 0.85) * 1.6 * floodMask * max(dot(n, flood), 0.0) * ao * (diffuse + fres * 0.3);
        // reflections of the environment
        vec3 refl = reflect(rd, n);
        lit += envCheap(refl, galDir) * fres * (1.0 - rough) * ao * 3.0;
        lit += diffuse * vec3(0.012, 0.014, 0.02) * ao;
        // the reactor and the charging coils light the hull around them
        vec3 cq = q - vec3(0.0, 0.0, -21.5);
        lit += vec3(1.0, 0.55, 0.2) * (1.2 + 3.0 * Charge) / (1.0 + dot(cq, cq) * 0.45) * diffuse * ao;
        if (Charge > 0.0) {
            float near = exp(-abs(length(q.xy) - 2.7) * 1.2) * smoothstep(zn - 0.15, zn, chargeWave);
            lit += mix(vec3(1.0, 0.45, 0.12), vec3(0.6, 0.8, 1.0), Charge) * near * Charge * 2.0 * diffuse;
        }
        if (FireAge >= 0.0) {
            vec3 dm = p - muzzle;
            lit += vec3(1.0, 0.9, 0.8) * 30.0 * exp(-FireAge * 4.0) / (1.0 + dot(dm, dm) * 0.2) * (diffuse + fres);
        }
        col = lit + emit;
    }

    // navigation lights: red port, green starboard, blinking white strobes (glows so they read at any distance)
    vec3 navs[4];
    vec2 tipL = rot(vec2(-9.1, 0.0), -0.7853981);
    vec2 tipR = rot(vec2(9.1, 0.0), -0.7853981);
    navs[0] = BX * tipL.x + BY * tipL.y - AXIS * 16.0;
    navs[1] = BX * tipR.x + BY * tipR.y - AXIS * 16.0;
    navs[2] = BY * 7.0 - AXIS * 14.2;
    navs[3] = AXIS * 25.4 + BY * 3.3;
    vec3 navCol[4];
    navCol[0] = vec3(1.0, 0.1, 0.05);
    navCol[1] = vec3(0.1, 1.0, 0.3);
    navCol[2] = vec3(1.0);
    navCol[3] = vec3(1.0, 0.2, 0.1);
    for (int i = 0; i < 4; i++) {
        float blink = i < 2 ? 1.0 : step(0.85, fract(Time * 0.8 + float(i) * 0.37));
        vec3 lp = navs[i];
        float tl = dot(lp - ro, rd);
        if (tl < 0.0 || (tHit > 0.0 && tl > tHit + 0.3)) continue;
        float d = length(ro + rd * tl - lp);
        col += navCol[i] * blink * (exp(-d * 9.0 / max(tl * 0.02, 0.05)) * 6.0 + exp(-d * 1.5) * 0.15);
    }

    // the shot: muzzle flash and the beam racing toward the galaxy
    if (FireAge >= 0.0) {
        vec3 far = muzzle + AXIS * 1.0e6;
        vec2 rs = raySegment(ro, rd, muzzle, far);
        float along = rs.y * 1.0e6;
        float occl = tHit > 0.0 ? tHit : 1.0e9;
        float front = FireAge * 2.0e5;   // the beam front, leaving at ludicrous speed
        float reach = smoothstep(front, front * 0.9, along);
        float width = 0.2 + 0.4 * exp(-FireAge * 2.0) + along * 0.00002;
        float core = exp(-pow(rs.x / width, 2.0));
        float halo = exp(-rs.x / (width * 10.0)) * 0.3;
        float fade = exp(-FireAge * 0.5) * 2.5 + 0.4;
        vec3 beam = mix(vec3(0.5, 0.7, 1.0), vec3(1.0), core) * (core * 7.0 + halo) * fade * reach;
        float tb = dot(mix(muzzle, far, rs.y) - ro, rd);
        if (tb < occl + 1.0) col += beam;

        float flashSize = 1.6 + FireAge * 8.0;
        vec2 fm = raySegment(ro, rd, muzzle, muzzle + AXIS * 0.01);
        float flash = exp(-pow(fm.x / flashSize, 2.0)) * exp(-FireAge * 6.0) * 16.0;
        vec3 off = ro + rd * dot(muzzle - ro, rd) - muzzle;
        float streak = 0.0;
        for (int k = 0; k < 3; k++) {
            float ang = float(k) * 1.0471976;
            vec3 dirk = BX * cos(ang) + BY * sin(ang);
            float across = length(off - dirk * dot(off, dirk));
            streak += exp(-across * 6.0) * exp(-abs(dot(off, dirk)) * 0.06);
        }
        flash += streak * exp(-FireAge * 4.0) * 5.0;
        // expanding shock ring of plasma round the muzzle
        float ringR = FireAge * 22.0;
        vec3 rp = ro + rd * dot(muzzle - ro, rd) - muzzle;
        float ring = exp(-pow((length(rp - AXIS * dot(rp, AXIS)) - ringR) / (0.6 + ringR * 0.08), 2.0)) * exp(-FireAge * 2.5);
        col += vec3(1.0, 0.85, 0.7) * flash + vec3(0.6, 0.75, 1.0) * ring * 3.0;
    }
    return col;
}

// ================================================================== scene 1: the crossing
vec3 sceneCrossing(vec3 rd) {
    // Camera path in galaxy units: from far outside, closing on the Sun exponentially.
    vec3 start = SUN_G + normalize(-GAL_C / GAL_SCALE - SUN_G) * 60.0;
    float p = Progress;
    float dist = 60.0 * pow(0.0005, p);              // 60 -> 0.03 galaxy units from the Sun
    vec3 dirToSun = normalize(SUN_G - start);
    vec3 ro = SUN_G - dirToSun * dist;
    // the camera looks along its travel; CamFwd etc. are given relative to that direction in Java
    vec3 fwd = dirToSun;
    vec3 right = normalize(cross(fwd, GN));
    vec3 up = cross(right, fwd);
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 d = normalize(fwd + right * ndc.x * TanHalfFov * Aspect + up * ndc.y * TanHalfFov);

    float trans;
    vec3 col = galaxy(ro, d, Steps / 3 + 32, trans) * 1.5;
    col += stars(d, 1.0) * trans;

    // warp streaks: velocity on a log scale peaks mid-journey
    float speed = sin(PI * p);
    vec2 sp = ndc * vec2(Aspect, 1.0);
    float ang = atan(sp.y, sp.x);
    float rad = length(sp);
    float lane = hash12(vec2(floor(ang * 180.0), 7.0));
    float streak = step(0.93, lane) * smoothstep(0.05, 0.8, rad) * fract(rad * 1.3 - Time * 3.0 * (0.5 + lane));
    col += vec3(0.75, 0.85, 1.0) * pow(streak, 6.0) * speed * 3.0 * smoothstep(0.1, 0.9, rad);

    // the Sun ahead, swelling from a point into a star
    float sunAng = acos(clamp(dot(d, dirToSun), -1.0, 1.0));
    float sunSize = 0.0003 / max(dist, 1.0e-4);
    col += vec3(1.0, 0.92, 0.8) * (exp(-sunAng / max(sunSize * 3.0, 1.0e-5)) * 40.0 + exp(-sunAng / max(sunSize * 40.0, 1.0e-4)) * 2.0);

    if (Kind == 0) {
        // riding the beam: a white-hot line from below the lens converging on the star ahead
        vec2 a = vec2(0.0, -1.3), b = vec2(0.0, 0.0);
        vec2 pa = sp - a, ba = b - a;
        float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
        float dl = length(pa - ba * h);
        float wdt = mix(0.06, 0.002, h);
        col += vec3(0.75, 0.88, 1.0) * (exp(-pow(dl / wdt, 2.0)) * 6.0 + exp(-dl / (wdt * 6.0)) * 0.5);
    } else {
        // the newborn hole ahead of us, falling home: a black core and its bright ring
        vec2 pc = sp - vec2(0.0, -0.08);
        float pr = length(pc);
        float ring = exp(-pow((pr - 0.03) / 0.006, 2.0));
        col = mix(col, vec3(0.0), smoothstep(0.022, 0.017, pr));
        col += vec3(1.0, 0.55, 0.2) * ring * 6.0 + vec3(1.0, 0.6, 0.3) * exp(-pr / 0.05) * 0.4;
    }
    return col;
}

// ================================================================== a black hole in space (lensed, with a disk)
/**
 * Bends the ray round a Schwarzschild hole at c (horizon radius rs), collecting accretion-disk light where
 * the bent path crosses the disk plane (normal dn). rd is replaced by the escaping direction.
 * Returns emission (rgb) and transmittance (a: 0 = swallowed by the horizon).
 */
vec4 lensHole(vec3 ro, inout vec3 rd, vec3 c, float rs, vec3 dn, float diskAmt, float spin) {
    float R = 40.0;                                   // influence radius, in rs
    vec3 p = (ro - c) / rs;
    vec2 b = sphereHit(p, rd, vec3(0.0), R);
    if (b.y < 0.0) return vec4(0.0, 0.0, 0.0, 1.0);
    p += rd * max(b.x, 0.0);
    vec3 v = rd;
    vec3 h = cross(p, v);
    float h2 = dot(h, h);
    vec3 emit = vec3(0.0);
    float tr = 1.0;
    vec3 dx = normalize(cross(dn, abs(dn.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 dz = cross(dn, dx);
    for (int i = 0; i < 150; i++) {
        float r = length(p);
        if (r < 1.0) { tr = 0.0; break; }
        if (r > R * 1.02 && dot(p, v) > 0.0) break;
        float dt = clamp(0.06 * r, 0.02, 2.0);
        vec3 a = -1.5 * h2 * p / pow(r, 5.0);
        vec3 pn = p + (v + a * dt * 0.5) * dt;
        v += a * dt;
        float s0 = dot(p, dn), s1 = dot(pn, dn);
        if (diskAmt > 0.0 && s0 * s1 < 0.0) {
            vec3 q = mix(p, pn, s0 / (s0 - s1));
            float rr = length(q);
            if (rr > 2.6 && rr < 16.0) {
                float ang = atan(dot(q, dz), dot(q, dx));
                float swirl = fbm3(vec3(rr * 1.2, ang * 3.0 - spin * 2.0 / pow(rr, 1.5) - log(rr) * 4.0, spin * 0.05));
                float prof = smoothstep(2.6, 3.4, rr) * smoothstep(16.0, 6.0, rr) * pow(3.0 / rr, 1.6);
                vec3 orb = normalize(cross(dn, q));
                float beta = sqrt(0.5 / rr);
                float dop = 1.0 / (1.0 - beta * dot(orb, -normalize(v)) * 0.85);
                float g = pow(dop, 3.0);
                float temp = (1700.0 + 5200.0 * prof) * dop;
                float dens = prof * (0.45 + 1.1 * swirl * swirl);
                vec3 e = blackbody(temp) * dens * g * 2.4 * diskAmt;
                emit += e * tr;
                tr *= 1.0 - clamp(dens * 0.9 * diskAmt, 0.0, 0.85);
            }
        }
        p = pn;
    }
    // thin photon ring right at the shadow edge
    rd = normalize(v);
    return vec4(emit, tr);
}

// ================================================================== scene 2: the Earth
const float ER = 2000.0;
const vec3 EARTH_C = vec3(0.0, -1750.0, 2550.0);
const float ATM_H = 40.0;
const vec3 BETA_R = vec3(0.0185, 0.043, 0.105);
const float BETA_M = 0.067;
const float HR = 2.5;
const float HM = 0.38;

vec2 earthUV(vec3 n) {
    // tilt the globe so the strike lands at temperate latitudes instead of near the pole
    float c = cos(-0.6), s = sin(-0.6);
    n = vec3(n.x, c * n.y - s * n.z, s * n.y + c * n.z);
    float lon = atan(n.z, n.x) + Time * 0.004 + 2.2;
    float lat = asin(clamp(n.y, -1.0, 1.0));
    return vec2(fract(lon / (2.0 * PI)), 0.5 - lat / PI);
}

/** Optical depth along a ray from p toward the light, through the atmosphere. */
vec2 lightDepth(vec3 p, vec3 l) {
    vec2 hit = sphereHit(p, l, EARTH_C, ER + ATM_H);
    float len = max(hit.y, 0.0);
    float ds = len / 6.0;
    vec2 od = vec2(0.0);
    for (int i = 0; i < 6; i++) {
        vec3 s = p + l * (ds * (float(i) + 0.5));
        float hgt = length(s - EARTH_C) - ER;
        if (hgt < 0.0) return vec2(1.0e4);
        od += vec2(exp(-hgt / HR), exp(-hgt / HM)) * ds;
    }
    return od;
}

/** Single-scattering atmosphere between ro and ro+rd*tMax; returns in-scatter, writes transmittance. */
vec3 atmosphere(vec3 ro, vec3 rd, float tMax, vec3 sun, vec3 sunCol, out vec3 trans) {
    trans = vec3(1.0);
    vec2 hit = sphereHit(ro, rd, EARTH_C, ER + ATM_H);
    if (hit.y < 0.0) return vec3(0.0);
    float t0 = max(hit.x, 0.0), t1 = min(hit.y, tMax);
    if (t1 <= t0) return vec3(0.0);
    float mu = dot(rd, sun);
    float phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float g = 0.76;
    float phaseM = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    vec3 sumR = vec3(0.0), sumM = vec3(0.0);
    vec2 od = vec2(0.0);
    float ds = (t1 - t0) / 16.0;
    for (int i = 0; i < 16; i++) {
        vec3 s = ro + rd * (t0 + ds * (float(i) + 0.5));
        float hgt = length(s - EARTH_C) - ER;
        vec2 dens = vec2(exp(-hgt / HR), exp(-hgt / HM)) * ds;
        od += dens;
        vec2 ld = lightDepth(s, sun);
        vec3 tau = BETA_R * (od.x + ld.x) + BETA_M * 1.1 * (od.y + ld.y);
        vec3 att = exp(-tau);
        sumR += dens.x * att;
        sumM += dens.y * att;
    }
    trans = exp(-(BETA_R * od.x + BETA_M * 1.1 * od.y));
    return sunCol * (sumR * BETA_R * phaseR + sumM * BETA_M * phaseM) * 4.5;
}

vec3 PART_AT = vec3(0.0);   // where the clouds part (angel), unused when PART_R == 0
float PART_R = 0.0;

vec3 earthSurface(vec3 p, vec3 rd, vec3 sun, vec3 sunCol, float heat) {
    vec3 n = normalize(p - EARTH_C);
    vec2 tuv = earthUV(n);
    vec4 day = texture(EarthDay, tuv);
    vec4 night = texture(EarthNight, tuv);
    // terrain relief: bump from the elevation map
    vec2 px = vec2(1.0 / 4096.0, 1.0 / 2048.0);
    float h0 = texture(EarthHeight, tuv).r;
    float hx = texture(EarthHeight, tuv + vec2(px.x, 0.0)).r;
    float hy = texture(EarthHeight, tuv + vec2(0.0, px.y)).r;
    vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), n));
    vec3 north = cross(n, east);
    vec3 bn = normalize(n - (east * (hx - h0) - north * (hy - h0)) * 3.5);

    float ocean = night.a;
    vec3 albedo = pow(day.rgb, vec3(2.2)) * 1.25;
    float ndl = dot(bn, sun);
    vec3 trans;
    vec2 ld = lightDepth(p + n * 0.5, sun);
    vec3 sunAtt = exp(-(BETA_R * ld.x + BETA_M * 1.1 * ld.y));
    // cloud shadow: sample the cloud layer offset toward the sun
    vec3 sp = normalize(n + sun * 0.004);
    float cloudShadow = texture(EarthDay, earthUV(sp)).a;
    vec3 light = sunCol * 0.42 * sunAtt * max(ndl, 0.0) * (1.0 - 0.7 * cloudShadow);
    vec3 surf = albedo * light * 1.0;
    // ocean: GGX glint with Fresnel
    if (ocean > 0.5) {
        vec3 v = -rd;
        float spec = ggx(n, v, sun, 0.12) * max(dot(n, sun), 0.0);
        float f = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        surf += sunCol * 0.42 * sunAtt * spec * f * 0.6 * (1.0 - cloudShadow);
        surf += vec3(0.01, 0.02, 0.04) * f;
    }
    // clouds: lit, with a soft terminator and a little silver lining
    float cloud = day.a;
    if (PART_R > 0.0) {
        float dd = length(p - PART_AT);
        cloud *= smoothstep(PART_R * 0.7, PART_R * 1.3, dd + 25.0 * noise3(p * 0.02));
        // a bright swirling rim where the clouds are pushed apart
        surf += vec3(1.0, 0.9, 0.7) * 0.25 * exp(-pow((dd - PART_R) / (PART_R * 0.15), 2.0));
    }
    float cl = smoothstep(-0.12, 0.25, dot(n, sun));
    vec3 cloudCol = sunCol * 0.3 * sunAtt * cl * (0.85 + 0.15 * pow(1.0 - max(dot(n, -rd), 0.0), 2.0));
    surf = mix(surf, cloudCol * 0.92, cloud * 0.95);
    // city lights on the night side
    float dark = 1.0 - smoothstep(-0.15, 0.05, dot(n, sun));
    surf += night.rgb * night.rgb * vec3(1.0, 0.8, 0.55) * 2.8 * dark * (1.0 - cloud * 0.75);
    // the Sun strike: the day side glows as it cooks
    surf += vec3(1.0, 0.35, 0.08) * heat * heat * max(ndl, 0.0) * (0.5 + 0.5 * noise3(n * 60.0 + Time * 0.2)) * 3.0;
    return surf;
}

/** The Sun as a body: granulation, limb darkening, spots, prominences, corona. Returns colour, writes hit t. */
vec3 sunBody(vec3 ro, vec3 rd, vec3 c, float r, float tear, vec3 tearDir, out float tHit) {
    tHit = -1.0;
    vec3 col = vec3(0.0);
    vec2 hit = sphereHit(ro, rd, c, r);
    vec3 oc = ro - c;
    float closest = length(oc - rd * dot(oc, rd));
    float alongOk = step(0.0, -dot(oc, rd));
    // corona and streamers
    float h = max(closest / r - 1.0, 0.0);
    vec3 pc = normalize(ro + rd * max(-dot(oc, rd), 0.0) - c);
    float streamer = pow(abs(fbm3(pc * 3.0 + Time * 0.03)), 2.0);
    col += vec3(1.0, 0.72, 0.42) * (exp(-h * 12.0) * 1.6 + exp(-h * 7.0) * (0.1 + 0.45 * streamer)) * alongOk;
    // the tidal tail as it's pulled apart: plasma streaming toward tearDir
    if (tear > 0.0) {
        float along = dot(pc, tearDir);
        float tail = smoothstep(0.2, 1.0, along) * exp(-h / (0.4 + 3.0 * tear)) * tear;
        col += vec3(1.0, 0.55, 0.2) * tail * (1.5 + 2.0 * fbm3(pc * 8.0 - Time * 0.3)) * alongOk;
    }
    if (hit.x > 0.0) {
        tHit = hit.x;
        vec3 n = normalize(ro + rd * hit.x - c);
        float mu = max(dot(n, -rd), 0.0);
        float limb = pow(mu, 0.45);
        float gran = fbm3(n * 70.0 + Time * 0.15);
        float cells = 0.6 + 0.8 * gran;
        float spots = smoothstep(0.68, 0.74, fbm3(n * 6.0 + 13.0));
        float faculae = smoothstep(0.6, 0.68, fbm3(n * 6.0 + 13.0)) * (1.0 - spots);
        float g2 = fbm3(n * 180.0 - Time * 0.25);
        vec3 hot = mix(vec3(1.0, 0.42, 0.06), vec3(1.0, 0.86, 0.5), smoothstep(0.3, 0.8, gran * 0.7 + g2 * 0.5));
        vec3 base = hot * (2.2 + 0.8 * faculae) * mix(vec3(1.0), vec3(0.12, 0.03, 0.01), spots);
        vec3 s = base * cells * mix(vec3(0.75, 0.32, 0.08), vec3(1.0), limb);
        // turbulence from the tearing
        s *= 1.0 + tear * 1.5 * smoothstep(0.3, 1.0, dot(n, tearDir)) * (0.5 + fbm3(n * 20.0 - Time));
        col = s;
    }
    return col;
}

/** Prominence loops above the limb (cheap: noisy arcs near the edge of the disc). */
vec3 prominences(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float tc = -dot(oc, rd);
    if (tc < 0.0) return vec3(0.0);
    vec3 p = ro + rd * tc - c;
    float h = length(p) / r - 1.0;
    if (h < 0.0 || h > 0.25) return vec3(0.0);
    vec3 dir = normalize(p);
    float a = atan(dir.y, dir.x);
    // a few arches: each is a half-sine in height over a span of angle
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        float c0 = hash12(vec2(float(i), 3.1)) * 6.2831853 - 3.14159;
        float span = 0.12 + 0.18 * hash12(vec2(float(i), 7.7));
        float u = (a - c0) / span;
        if (abs(u) > 1.0) continue;
        float hh = (0.05 + 0.12 * hash12(vec2(float(i), 1.9))) * sqrt(max(1.0 - u * u, 0.0));
        float fil = fbm3(vec3(u * 4.0, h * 40.0, float(i) * 3.0 - Time * 0.2));
        total += exp(-pow((h - hh) / (0.008 + 0.01 * fil), 2.0)) * smoothstep(0.25, 0.75, fil);
    }
    return vec3(1.0, 0.32, 0.12) * total * 3.0;
}

/** The archangel at Earth scale: a winged figure of light descending a pillar from the heavens. */
vec3 heavenlyDescent(vec3 ro, vec3 rd, vec3 target, vec3 up, float prog) {
    vec3 col = vec3(0.0);
    // the pillar: a column of white-gold light from deep space down to the target
    vec3 top = target + up * 4000.0;
    vec2 rs = raySegment(ro, rd, target, top);
    float w = 3.0 + 9.0 * prog;
    float core = exp(-pow(rs.x / w, 2.0));
    float halo = exp(-rs.x / (w * 8.0));
    float open = smoothstep(0.0, 0.25, prog);
    float shimmer = 0.85 + 0.15 * sin(Time * 9.0 + rs.y * 40.0);
    col += vec3(1.0, 0.9, 0.7) * (core * 2.5 + halo * 0.25) * open * shimmer;
    // the figure, a glyph of light on the pillar: a body, two great wings, a halo, a burning sword
    vec3 top2 = target + up * 2600.0;
    vec3 pos = mix(top2, target + up * 260.0, smoothstep(0.05, 1.0, prog));
    vec3 toP = pos - ro;
    float tp = dot(toP, rd);
    if (tp > 0.0) {
        vec3 right = normalize(cross(up, normalize(toP)));
        vec3 local = ro + rd * tp - pos;
        float s = 90.0 + 60.0 * prog;           // scale in Earth units
        vec2 q = vec2(dot(local, right), dot(local, up)) / s;
        float body = length(vec2(q.x * 3.0, q.y + 0.2)) - 0.9;
        float wingL = length((q - vec2(-1.1, 0.35)) * vec2(0.7, 1.8)) - 0.9;
        float wingR = length((q - vec2(1.1, 0.35)) * vec2(0.7, 1.8)) - 0.9;
        float feathers = 0.5 + 0.5 * sin(atan(q.y - 0.1, abs(q.x)) * 24.0);
        float wings = min(wingL, wingR);
        float halo = abs(length(q - vec2(0.0, 1.05)) - 0.22) - 0.03;
        float sword = max(abs(q.x - 0.55 + (q.y - 0.4) * 0.15) - 0.03, abs(q.y - 0.9) - 0.9);
        float g = exp(-max(body, 0.0) * 10.0) + exp(-max(wings, 0.0) * 6.0) * (0.55 + 0.45 * feathers)
                + exp(-max(halo, 0.0) * 40.0) * 1.5;
        col += vec3(1.0, 0.93, 0.8) * g * 3.5 * open;
        col += vec3(1.0, 0.45, 0.1) * exp(-max(sword, 0.0) * 30.0) * 6.0 * open;
    }
    return col;
}

vec3 sceneEarth(vec3 ro, vec3 rd) {
    vec3 sun = normalize(vec3(0.78, 0.52, -0.28));
    vec3 sunCol = vec3(1.0, 0.97, 0.92) * 3.2;
    vec3 col = stars(rd, 1.0);
    float sd = max(dot(rd, sun), 0.0);
    float heat = 0.0;

    // Kind 1: the Sun closes in on the Earth
    float sunT = -1.0;
    vec3 sunPos = vec3(0.0);
    float sunR = 0.0;
    if (Kind == 1) {
        vec3 earthToSun = sun;
        float d = mix(70000.0, 6800.0, pow(Progress, 1.3));
        sunR = 3500.0;
        sunPos = EARTH_C + earthToSun * d;
        heat = smoothstep(0.55, 1.0, Progress);
        sunCol *= 1.0 + 6.0 * pow(Progress, 3.0);
        vec3 sb = sunBody(ro, rd, sunPos, sunR, Progress, -earthToSun, sunT);
        col = sunT > 0.0 ? sb : col + sb;
        col += prominences(ro, rd, sunPos, sunR);
    } else {
        col += vec3(1.0, 0.95, 0.88) * (pow(sd, 2400.0) * 500.0 + pow(sd, 120.0) * 1.0 + pow(sd, 8.0) * 0.08);
    }

    vec3 target = EARTH_C + normalize(vec3(0.05, 1.0, -0.62)) * ER;
    vec3 up = normalize(target - EARTH_C);
    if (Kind == 2) {
        PART_AT = target;
        PART_R = 260.0 * smoothstep(0.05, 0.7, Progress);
    }

    vec2 hit = sphereHit(ro, rd, EARTH_C, ER);
    float tEarth = hit.x > 0.0 ? hit.x : 1.0e9;
    if (sunT > 0.0 && sunT < tEarth) tEarth = 1.0e9;
    vec3 trans;
    vec3 scat = atmosphere(ro, rd, tEarth, sun, sunCol, trans);
    if (tEarth < 1.0e8) {
        vec3 p = ro + rd * tEarth;
        col = earthSurface(p, rd, sun, sunCol, heat) * trans + scat;
    } else {
        col = col * trans + scat;
    }


    if (Kind == 0 && FireAge >= 0.0) {
        // the projectile falls in from deep space, trailing plasma as it hits the atmosphere
        float p = clamp(Progress, 0.0, 1.0);
        vec3 from = target + normalize(up * 1.0 + vec3(0.3, 0.2, -0.5)) * 2600.0;
        vec3 P = mix(from, target, p * p);
        vec3 toP = P - ro;
        float tp = dot(toP, rd);
        if (tp > 0.0 && tp < tEarth + 1.0) {
            float d = length(ro + rd * tp - P);
            float rad = 4.0 + tp * 0.002;
            float alt = length(P - EARTH_C) - ER;
            float entry = smoothstep(ATM_H * 1.5, 0.0, alt);
            vec2 tr = raySegment(ro, rd, P, mix(from, P, 0.85));
            col += vec3(1.0, 0.6, 0.3) * exp(-tr.x / (rad * (1.0 + 2.0 * entry))) * (0.5 + 4.0 * entry);
            vec3 d2 = rd;
            vec4 L = lensHole(ro, d2, P, rad * 1.1, normalize(up + vec3(0.2, 0.0, 0.3)), 1.0, Time * 3.0);
            col = col * L.a + L.rgb * 1.5;
            // the atmosphere below it begins to glow
            col += vec3(1.0, 0.5, 0.2) * entry * exp(-length(ro + rd * tEarth - target) / 120.0) * 4.0 * step(tEarth, 1.0e8);
        }
    }
    if (Kind == 2) {
        // clouds part in a ring around the target as the heavens open
        col += heavenlyDescent(ro, rd, target, up, Progress);
    }
    return col;
}


// ================================================================== scene 4: the pierced star becomes the hole
// Star at the origin (radius 1000). The cannon's beam arrives along BEAM_DIR, runs it through, and the star
// collapses: swell, implode, flash, and out of the light a black hole spins up its disk from the star's remains.
// Then a second pulse of the beam drives the newborn hole out toward the Earth.
const vec3 BEAM_DIR = vec3(0.6, -0.25, 0.76);        // direction the beam travels (normalised in use)
const vec3 LAUNCH_DIR = vec3(-0.35, -0.2, -0.91);

vec3 scenePierce(vec3 ro, vec3 rd) {
    float P = Progress;
    vec3 bd = normalize(BEAM_DIR);
    vec3 col = stars(rd, 0.9);
    // phases
    float pierce = smoothstep(0.02, 0.08, P);                    // beam through the star
    float unrest = smoothstep(0.08, 0.5, P);                     // the star convulses
    float implode = smoothstep(0.48, 0.6, P);                    // collapse
    float flash = exp(-max(P - 0.6, 0.0) * 30.0) * step(0.585, P);
    float born = smoothstep(0.6, 0.8, P);                        // the hole and its disk
    float launch = max(P - 0.86, 0.0) / 0.14;                    // driven out
    float r = 1000.0 * (1.0 + 0.06 * unrest * sin(P * 90.0) * (1.0 - implode)) * mix(1.0, 0.0, pow(implode, 1.6));

    float rs = 170.0;
    float tStar = -1.0;
    vec3 hc = normalize(LAUNCH_DIR) * 40000.0 * launch * launch * launch;

    // the star (until it is gone)
    if (r > 2.0) {
        float tS;
        vec3 sb = sunBody(ro, rd, vec3(0.0), r, unrest * 0.6, -bd, tS);
        // hotter and bluer as it destabilises
        sb *= mix(vec3(1.0), vec3(0.75, 0.9, 1.6), unrest) * (1.0 + 2.5 * implode);
        if (tS > 0.0) {
            tStar = tS;
            vec3 n = normalize(ro + rd * tS);
            // the wound where the beam entered: white-hot, rippling outward across the surface
            float entry = max(dot(n, -bd), 0.0);
            float wound = exp(-(1.0 - entry) * 60.0) * 25.0 + exp(-(1.0 - entry) * 8.0) * 2.0 * (0.6 + 0.4 * sin((1.0 - entry) * 80.0 - P * 160.0));
            sb += vec3(0.8, 0.9, 1.0) * wound * pierce;
            col = sb;
        } else {
            col += sb;
        }
        col += prominences(ro, rd, vec3(0.0), r) * (1.0 + 3.0 * unrest);
    }

    // the hole: lensing bends whatever is behind it
    if (born > 0.0) {
        vec3 d2 = rd;
        vec4 L = lensHole(ro, d2, hc, rs * born, normalize(vec3(0.15, 1.0, 0.25)), born, Time * 3.0 + P * 40.0);
        vec3 bg = stars(d2, 0.9);
        float tg;
        // remnant glow of the star seen through the lens
        vec3 rem = sunBody(ro, d2, vec3(0.0), max(r, 1.0), 0.0, -bd, tg) * step(2.0, r);
        col = mix(col, (bg + rem) * L.a + L.rgb, born);
    }

    // the beam: from behind the star along bd, through it, and the jet of star-stuff blown out the far side
    if (pierce > 0.0) {
        vec3 a = -bd * 1.0e6, b = bd * 1.0e6;
        vec2 bs = raySegment(ro, rd, a, b);
        float along = (bs.y - 0.5) * 2.0e6;                        // position along the beam from the star
        float front = mix(-2.0e4, 1.0e6, pierce);
        float lit = step(along, front);
        float w = 14.0 + 30.0 * exp(-P * 10.0);
        float beamI = exp(-pow(bs.x / w, 2.0)) * 9.0 + exp(-bs.x / (w * 8.0)) * 0.6;
        float fade = 1.0 - smoothstep(0.55, 0.62, P);              // the beam dies as the star collapses
        // hidden while inside the star body
        float tb = dot(mix(a, b, bs.y) - ro, rd);
        float hidden = (tStar > 0.0 && tb > tStar) ? 0.15 : 1.0;
        col += vec3(0.75, 0.88, 1.0) * beamI * lit * fade * hidden;
        // exit jet: a cone of plasma flung out behind the star
        if (along > 0.0 && r > 2.0) {
            float cone = bs.x / (along * 0.18 + 60.0);
            float jet = exp(-cone * cone * 2.0) * exp(-along / (3500.0 * pierce + 1.0)) * (0.6 + 0.8 * fbm3(vec3(along * 0.002, bs.x * 0.01, Time)));
            col += vec3(1.0, 0.6, 0.25) * jet * 3.0 * unrest * (1.0 - implode);
        }
    }

    // the second pulse that launches the hole
    if (launch > 0.0) {
        vec2 ls = raySegment(ro, rd, hc - normalize(LAUNCH_DIR) * 1.0e6, hc);
        col += vec3(0.75, 0.88, 1.0) * (exp(-pow(ls.x / 18.0, 2.0)) * 8.0 + exp(-ls.x / 200.0) * 0.4) * exp(-launch * 3.0);
    }

    // the collapse: a white flash and a shock shell racing outward
    vec3 oc = ro;
    float closest = length(oc - rd * dot(oc, rd));
    col += vec3(1.0, 0.97, 0.95) * flash * (exp(-closest / 2500.0) * 40.0 + 3.0);
    float shellR = max(P - 0.6, 0.0) * 30000.0;
    if (shellR > 0.0) {
        float shell = exp(-pow((closest - shellR) / (200.0 + shellR * 0.05), 2.0)) * exp(-(P - 0.6) * 6.0);
        col += mix(vec3(0.6, 0.8, 1.0), vec3(1.0, 0.7, 0.4), clamp(closest / 6000.0, 0.0, 1.0)) * shell * 4.0;
    }
    return col;
}

// ================================================================== scene 3: the Sun, torn out of place
vec3 sceneSun(vec3 ro, vec3 rd) {
    vec3 c = vec3(0.0);
    float r = 1000.0;
    vec3 tearDir = normalize(vec3(0.2, -0.3, -1.0));
    float t;
    vec3 col = stars(rd, 0.8);
    vec3 s = sunBody(ro, rd, c, r, Progress, tearDir, t);
    col = t > 0.0 ? s : col + s;
    col += prominences(ro, rd, c, r) * (1.0 + 3.0 * Progress);
    return col;
}

void main() {
    galaxyFrame();
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 rd = normalize(CamFwd + CamRight * ndc.x * TanHalfFov * Aspect + CamUp * ndc.y * TanHalfFov);
    vec3 col;
    if (Scene == 0) col = sceneCannon(CamPos, rd);
    else if (Scene == 1) col = sceneCrossing(rd);
    else if (Scene == 2) col = sceneEarth(CamPos, rd);
    else if (Scene == 3) col = sceneSun(CamPos, rd);
    else col = scenePierce(CamPos, rd);
    fragColor = vec4(max(col, vec3(0.0)), 1.0);
}
