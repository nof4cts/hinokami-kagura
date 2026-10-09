#version 150

// TON 618: the orbit shots. Replaces the whole frame during the cutscene's space shots.
//
// SS-618, an orbital railgun ~55 units long, hangs high above the Earth with its barrel aimed at the
// target. Everything is raymarched from signed distance functions and lit physically:
//   octagonal barrel with panel seams and a glowing bore, twelve accelerator coils (every fourth a heavy
//   clamped housing) that light up breech-to-muzzle as it charges, twin rails, a strut lattice, a
//   receiver with a lit bridge, two capacitor banks, a caged gold reactor, four radiator fins in an X,
//   thrusters, six focusing prongs round the muzzle.
// Key light is the Sun (soft shadows), fill is earthshine from below, plus ambient occlusion and a
// Fresnel rim. Behind it: stars, a Milky Way band, the Sun with glare, and the Earth from NASA imagery
// (day, clouds, city lights on the night side, ocean glint, atmospheric scattering at the limb).
// After firing: muzzle flash, the beam to the planet, and the projectile (a tiny black hole) riding it.

#include "common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D EarthDay;    // rgb day, a clouds
uniform sampler2D EarthNight;  // rgb city lights, a ocean mask
uniform vec3 CamPos;
uniform vec3 CamFwd;
uniform vec3 CamUp;
uniform vec3 CamRight;
uniform float TanHalfFov;
uniform float Aspect;
uniform float Time;
uniform float Charge;    // 0..1
uniform float FireAge;   // seconds since the shot, < 0 before
uniform float ProjT;     // 0..1 projectile progress toward the Earth
uniform int Steps;

const float EARTH_R = 2000.0;
const vec3 EARTH_C = vec3(0.0, -1750.0, 2550.0);
const vec3 SUN_DIR = vec3(0.78, 0.52, -0.28);   // normalised in main()
const vec3 SUN_COL = vec3(1.0, 0.95, 0.88);

vec3 AXIS;    // barrel axis (toward Earth centre)
vec3 BX, BY;  // barrel frame

#define M_HULL 1.0
#define M_DARK 2.0
#define M_COIL 3.0
#define M_GOLD 4.0
#define M_CORE 5.0
#define M_RAD 6.0
#define M_NAV 7.0
#define M_BORE 8.0

// ------------------------------------------------------------------ SDF primitives
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

// ------------------------------------------------------------------ the cannon (local frame: +z = muzzle)
vec2 cannon(vec3 q) {
    // barrel
    float barrel = max(sdOct(q.xy, 2.0), abs(q.z - 6.0) - 16.0);
    float bore = max(length(q.xy) - 1.05, 17.5 - q.z);
    barrel = max(barrel, -bore);
    vec2 res = vec2(barrel, M_HULL);
    res = opU(res, vec2(max(length(q.xy) - 1.08, abs(q.z - 20.6) - 0.5), M_BORE)); // glowing throat

    // accelerator coils
    float id = clamp(floor((q.z + 6.0) / 2.3 + 0.5), 0.0, 11.0);
    float zc = id * 2.3 - 6.0;
    bool heavy = mod(id, 4.0) < 0.5;
    float ringR = heavy ? 0.7 : 0.4;
    float ringH = heavy ? 0.55 : 0.26;
    float coil = max(abs(length(q.xy) - 2.7) - ringR, abs(q.z - zc) - ringH);
    if (heavy) {
        // clamp bolts on the heavy housings
        vec2 a = rot(q.xy, 0.3926991);
        float ang = atan(a.y, a.x);
        float sector = 6.2831853 / 8.0;
        float s = mod(ang + sector * 0.5, sector) - sector * 0.5;
        vec2 b = vec2(cos(s), sin(s)) * length(a);
        coil = min(coil, sdBox(vec3(b.x - 3.45, b.y, q.z - zc), vec3(0.24, 0.24, 0.75)));
    }
    res = opU(res, vec2(coil, M_COIL));

    // rails
    float rails = sdBox(vec3(abs(q.x) - 3.1, q.y, q.z - 4.5), vec3(0.32, 0.8, 16.6));
    res = opU(res, vec2(rails, M_DARK));

    // spine lattice above and below
    float spine = sdBox(vec3(q.x, abs(q.y) - 3.15, q.z - 3.0), vec3(0.24, 0.24, 15.5));
    float sz = mod(q.z + 0.6, 1.2) - 0.6;
    float strut = sdBox(vec3(q.x, abs(q.y) - 2.6, sz), vec3(0.07, 0.55, 0.07));
    strut = max(strut, abs(q.z - 3.0) - 15.0);
    float diag = sdBox(vec3(q.x, abs(q.y) - 2.6 - sz * 0.75, sz), vec3(0.05, 0.05, 0.6));
    diag = max(diag, abs(q.z - 3.0) - 15.0);
    res = opU(res, vec2(min(spine, min(strut, diag)), M_DARK));

    // receiver + bridge
    float receiver = sdRoundBox(q - vec3(0.0, 0.0, -14.0), vec3(3.4, 3.0, 4.2), 0.35);
    res = opU(res, vec2(receiver, M_HULL));
    float bridge = sdRoundBox(q - vec3(0.0, 3.55, -13.0), vec3(1.4, 0.65, 2.0), 0.2);
    res = opU(res, vec2(bridge, M_HULL));
    float windows = sdBox(q - vec3(0.0, 3.75, -11.0), vec3(1.1, 0.16, 0.05));
    res = opU(res, vec2(windows, M_NAV));

    // capacitor banks
    float caps = sdCylZ(vec3(abs(q.x) - 4.7, q.y + 0.6, q.z + 14.0), 1.15, 3.6);
    float capRings = max(abs(length(vec2(abs(q.x) - 4.7, q.y + 0.6)) - 1.2) - 0.12,
                         abs(mod(q.z + 14.0, 1.4) - 0.7) - 0.1);
    capRings = max(capRings, abs(q.z + 14.0) - 3.5);
    res = opU(res, vec2(caps, M_DARK));
    res = opU(res, vec2(capRings, M_GOLD));

    // reactor: gold ring, glowing core, cage
    vec3 rq = q - vec3(0.0, 0.0, -21.5);
    res = opU(res, vec2(sdTorusZ(rq, 3.6, 0.55), M_GOLD));
    res = opU(res, vec2(length(rq) - 1.45, M_CORE));
    vec2 ca = rot(rq.xy, 0.7853981);
    float cage = min(sdBox(vec3(ca.x, ca.y, rq.z), vec3(3.7, 0.12, 0.12)), sdBox(vec3(ca.x, ca.y, rq.z), vec3(0.12, 3.7, 0.12)));
    cage = min(cage, sdBox(vec3(abs(rq.x) - 2.6, rq.y, rq.z), vec3(0.12, 0.12, 2.4)));
    res = opU(res, vec2(cage, M_DARK));
    float struts = sdBox(vec3(q.x, q.y, q.z + 19.0), vec3(0.5, 0.5, 1.2));
    res = opU(res, vec2(struts, M_DARK));

    // radiators: four fins in an X
    vec2 xq = rot(q.xy, 0.7853981);
    float finA = sdBox(vec3(abs(xq.x) - 6.2, xq.y, q.z + 16.0), vec3(2.9, 0.06, 3.6));
    float finB = sdBox(vec3(xq.x, abs(xq.y) - 6.2, q.z + 16.0), vec3(0.06, 2.9, 3.6));
    res = opU(res, vec2(min(finA, finB), M_RAD));

    // thrusters
    vec3 tq = vec3(abs(q.x) - 1.7, abs(q.y) - 1.7, q.z + 25.6);
    float thr = max(length(tq.xy) - (0.45 + max(-tq.z, 0.0) * 0.35), abs(tq.z) - 0.9);
    res = opU(res, vec2(thr, M_DARK));

    // six focusing prongs round the muzzle, leaning inwards
    float a6 = atan(q.y, q.x);
    float sec = 6.2831853 / 6.0;
    float s6 = mod(a6 + sec * 0.5, sec) - sec * 0.5;
    vec2 pq = vec2(cos(s6), sin(s6)) * length(q.xy);
    vec3 pp = vec3(pq.x - 3.0 + (q.z - 22.0) * 0.2, pq.y, q.z - 23.6);
    float prong = sdBox(pp, vec3(0.24, 0.32, 2.2));
    res = opU(res, vec2(prong, M_DARK));
    return res;
}

vec3 toLocal(vec3 p) {
    return vec3(dot(p, BX), dot(p, BY), dot(p, AXIS));
}

vec2 map(vec3 p) {
    return cannon(toLocal(p));
}

vec3 calcNormal(vec3 p) {
    const vec2 e = vec2(1.0, -1.0) * 0.0025;
    return normalize(e.xyy * map(p + e.xyy).x + e.yyx * map(p + e.yyx).x
                   + e.yxy * map(p + e.yxy).x + e.xxx * map(p + e.xxx).x);
}

float softShadow(vec3 ro, vec3 rd) {
    float res = 1.0;
    float t = 0.05;
    for (int i = 0; i < 40; i++) {
        float h = map(ro + rd * t).x;
        res = min(res, 10.0 * h / t);
        t += clamp(h, 0.03, 0.8);
        if (res < 0.002 || t > 40.0) break;
    }
    return clamp(res, 0.0, 1.0);
}

float ambientOcclusion(vec3 p, vec3 n) {
    float occ = 0.0;
    float w = 1.0;
    for (int i = 1; i <= 5; i++) {
        float h = 0.12 * float(i);
        occ += (h - map(p + n * h).x) * w;
        w *= 0.7;
    }
    return clamp(1.0 - 1.6 * occ, 0.0, 1.0);
}

// ------------------------------------------------------------------ backdrop
vec3 stars(vec3 dir) {
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float scale = 160.0 + float(layer) * 210.0;
        vec3 p = dir * scale;
        vec3 cell = floor(p);
        vec3 h = hash33(cell + float(layer) * 17.3);
        if (h.x > 0.955) {
            vec3 centre = cell + 0.2 + 0.6 * h;
            float d = length(p - centre);
            float b = pow(max(0.0, 1.0 - d * 2.4), 7.0) * (0.4 + 5.0 * h.y * h.y * h.y);
            col += b * blackbody(3200.0 + h.z * 9500.0);
        }
    }
    // Milky Way: a soft, dusty band around a tilted great circle
    vec3 galN = normalize(vec3(0.35, 0.82, 0.45));
    float band = exp(-pow(dot(dir, galN) / 0.16, 2.0));
    float dust = fbm3(dir * 6.0);
    float lanes = smoothstep(0.42, 0.7, fbm3(dir * 14.0 + 3.0));
    col += band * (vec3(0.12, 0.1, 0.09) * (0.5 + dust) * (1.0 - 0.7 * lanes) + vec3(0.05, 0.05, 0.08) * dust);
    return col;
}

vec2 sphereHit(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

vec3 earth(vec3 ro, vec3 rd, vec3 sun, out float hitT) {
    hitT = -1.0;
    vec2 hit = sphereHit(ro, rd, EARTH_C, EARTH_R);
    // atmosphere shell around the limb, also visible where the ray misses the planet
    vec3 oc = ro - EARTH_C;
    float closest = length(oc - rd * dot(oc, rd));
    float limb = closest - EARTH_R;
    vec3 atmoCol = vec3(0.28, 0.52, 1.0);
    if (hit.x < 0.0) {
        if (dot(-oc, rd) < 0.0) return vec3(0.0);
        vec3 pc = normalize(oc - rd * dot(oc, rd));
        float lit = smoothstep(-0.35, 0.4, dot(pc, sun));
        float glow = exp(-max(limb, 0.0) / 45.0) * lit;
        return atmoCol * glow * 1.4;
    }
    hitT = hit.x;
    vec3 p = ro + rd * hit.x;
    vec3 n = normalize(p - EARTH_C);
    // texture lookup with a slow spin
    float lon = atan(n.z, n.x) + Time * 0.004 + 2.2;
    float lat = asin(clamp(n.y, -1.0, 1.0));
    vec2 tuv = vec2(fract(lon / 6.2831853), 0.5 - lat / 3.1415927);
    vec4 day = texture(EarthDay, tuv);
    vec4 night = texture(EarthNight, tuv);

    float ndl = dot(n, sun);
    float lit = smoothstep(-0.08, 0.25, ndl);
    vec3 albedo = pow(day.rgb, vec3(2.0)) * 1.6;
    float clouds = day.a;
    vec3 surface = albedo * max(ndl, 0.0) * 2.4;
    // ocean glint
    vec3 h = normalize(sun - rd);
    surface += night.a * pow(max(dot(n, h), 0.0), 600.0) * vec3(1.0, 0.9, 0.75) * 1.6 * lit;
    // clouds on top, lit, with a faint shadow tint
    surface = mix(surface, vec3(1.0) * max(ndl, 0.0) * 2.2, clouds * 0.9);
    // city lights on the night side
    vec3 cities = pow(night.rgb, vec3(1.6)) * vec3(1.0, 0.75, 0.45) * 3.0 * (1.0 - lit) * (1.0 - clouds * 0.8);
    surface += cities;
    // atmosphere: Rayleigh-ish blue toward the limb, warm at the terminator
    float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
    float term = exp(-pow(ndl / 0.12, 2.0));
    vec3 atmo = atmoCol * fres * (0.25 + 1.4 * lit) + vec3(1.0, 0.45, 0.2) * term * 0.25;
    return surface * (1.0 - fres * 0.5) + atmo;
}

/** Distance between a ray and a segment; returns (distance, position along the segment 0..1). */
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

// ------------------------------------------------------------------ main
void main() {
    vec3 sun = normalize(SUN_DIR);
    AXIS = normalize(EARTH_C);
    BX = normalize(cross(vec3(0.0, 1.0, 0.0), AXIS));
    BY = cross(AXIS, BX);

    vec2 ndc = uv * 2.0 - 1.0;
    vec3 rd = normalize(CamFwd + CamRight * ndc.x * TanHalfFov * Aspect + CamUp * ndc.y * TanHalfFov);
    vec3 ro = CamPos;

    // ---- backdrop
    float earthT;
    vec3 col = stars(rd);
    float sd = max(dot(rd, sun), 0.0);
    col += SUN_COL * (pow(sd, 2400.0) * 400.0 + pow(sd, 90.0) * 1.2 + pow(sd, 8.0) * 0.12);
    vec3 e = earth(ro, rd, sun, earthT);
    if (earthT > 0.0) col = e; else col += e;

    // ---- cannon
    vec3 muzzle = AXIS * 26.0;
    float tHit = -1.0;
    vec2 m = vec2(0.0);
    vec2 bound = sphereHit(ro, rd, vec3(0.0), 30.0);
    if (bound.y > 0.0) {
        float t = max(bound.x, 0.0);
        for (int i = 0; i < 512; i++) {
            if (i >= Steps) break;
            vec3 p = ro + rd * t;
            m = map(p);
            if (m.x < 0.0015 * t) {
                tHit = t;
                break;
            }
            t += m.x * 0.85;
            if (t > bound.y) break;
        }
    }

    float chargeWave = Charge * 1.35;
    if (tHit > 0.0) {
        vec3 p = ro + rd * tHit;
        vec3 n = calcNormal(p);
        vec3 q = toLocal(p);
        float mat = m.y;
        float zn = clamp((q.z + 6.0) / 25.3, 0.0, 1.0); // breech 0 .. muzzle 1

        vec3 albedo = vec3(0.6, 0.61, 0.63);
        float metal = 0.2;
        float rough = 0.5;
        vec3 emit = vec3(0.0);
        if (mat == M_HULL) {
            float seam = smoothstep(0.03, 0.0, abs(mod(q.z, 2.3) - 1.15) - 1.1);
            float seam2 = smoothstep(0.03, 0.0, abs(fract(atan(q.y, q.x) * 1.273) - 0.5) - 0.48);
            albedo *= 1.0 - 0.55 * max(seam, seam2);
            albedo *= 0.92 + 0.08 * noise3(q * 3.0);
        } else if (mat == M_DARK) {
            albedo = vec3(0.07, 0.072, 0.08);
            metal = 0.8;
            rough = 0.35;
        } else if (mat == M_COIL) {
            albedo = vec3(0.16, 0.15, 0.15);
            metal = 0.9;
            rough = 0.3;
            float lit = smoothstep(zn - 0.12, zn, chargeWave) * step(0.001, Charge);
            float hum = 0.85 + 0.15 * sin(Time * 40.0 + q.z * 3.0);
            vec3 hot = mix(vec3(1.0, 0.25, 0.05), vec3(1.0, 0.85, 0.6), Charge);
            // windings: only the grooves between the bands glow, so the coils keep their machined detail
            float wind = smoothstep(0.55, 0.95, abs(sin(atan(q.y, q.x) * 24.0)));
            float cid = clamp(floor((q.z + 6.0) / 2.3 + 0.5), 0.0, 11.0);
            float face = smoothstep(0.2, 0.0, abs(length(q.xy) - 2.7) - (mod(cid, 4.0) < 0.5 ? 0.5 : 0.25));
            emit = hot * lit * hum * (0.35 + 1.4 * Charge) * (0.25 + 0.75 * wind) * (0.4 + 0.6 * face);
        } else if (mat == M_GOLD) {
            albedo = vec3(1.0, 0.72, 0.28);
            metal = 1.0;
            rough = 0.25;
        } else if (mat == M_CORE) {
            albedo = vec3(0.0);
            float pulse = 0.8 + 0.2 * sin(Time * 6.0);
            emit = vec3(1.0, 0.55, 0.18) * (3.0 + 5.0 * Charge) * pulse;
        } else if (mat == M_RAD) {
            albedo = vec3(0.24, 0.24, 0.26);
            float slat = step(0.5, fract(q.z * 2.5));
            albedo *= 0.75 + 0.25 * slat;
            emit = vec3(1.0, 0.18, 0.04) * slat * Charge * Charge * 0.35;
        } else if (mat == M_NAV) {
            albedo = vec3(0.0);
            emit = vec3(0.6, 0.85, 1.0) * 2.0;
        } else if (mat == M_BORE) {
            albedo = vec3(0.0);
            emit = mix(vec3(1.0, 0.3, 0.05), vec3(1.0, 0.9, 0.8), Charge) * (0.6 + 8.0 * Charge * Charge);
        }

        // lighting
        float ndl = max(dot(n, sun), 0.0);
        float shadow = ndl > 0.0 ? softShadow(p + n * 0.01, sun) : 0.0;
        float ao = ambientOcclusion(p, n);
        vec3 toEarth = normalize(EARTH_C - p);
        float earthshine = max(dot(n, toEarth), 0.0);
        vec3 h = normalize(sun - rd);
        float specPow = mix(256.0, 16.0, rough);
        float spec = pow(max(dot(n, h), 0.0), specPow) * (specPow + 8.0) / 25.0;
        vec3 f0 = mix(vec3(0.04), albedo, metal);
        float fres = pow(1.0 - max(dot(n, -rd), 0.0), 5.0);
        vec3 diffuse = albedo * (1.0 - metal);
        vec3 lit = SUN_COL * 3.0 * ndl * shadow * (diffuse + f0 * spec)
                 + vec3(0.18, 0.3, 0.55) * 0.55 * earthshine * ao * (diffuse + f0 * 0.3)
                 + vec3(0.02, 0.025, 0.035) * ao * (diffuse + f0)
                 + vec3(0.35, 0.5, 0.8) * fres * 0.25 * ao;
        // the reactor and coils light the hull around them
        vec3 rq = q - vec3(0.0, 0.0, -21.5);
        lit += vec3(1.0, 0.55, 0.2) * (1.2 + 3.0 * Charge) / (1.0 + dot(rq, rq) * 0.45) * diffuse * ao;
        if (Charge > 0.0) {
            float near = exp(-abs(length(q.xy) - 2.7) * 1.2) * smoothstep(zn - 0.15, zn, chargeWave);
            lit += vec3(1.0, 0.45, 0.12) * near * Charge * 2.0 * diffuse;
        }
        col = lit + emit;
    }

    // ---- the shot
    if (FireAge >= 0.0) {
        vec3 earthPoint = EARTH_C - AXIS * EARTH_R;
        float occl = tHit > 0.0 ? tHit : (earthT > 0.0 ? earthT : 1.0e9);
        vec2 rs = raySegment(ro, rd, muzzle, earthPoint);
        float along = rs.y * length(earthPoint - muzzle);
        float beamStrength = exp(-FireAge * 0.9) * 3.0 + 0.25 * exp(-FireAge * 0.15);
        float width = 0.18 + 0.35 * exp(-FireAge * 2.0) + along * 0.0006;
        float core = exp(-pow(rs.x / width, 2.0));
        float halo = exp(-rs.x / (width * 8.0)) * 0.25;
        float travelled = clamp(FireAge * 2.5, 0.0, 1.0); // beam front races toward Earth
        float reach = step(rs.y, travelled);
        vec3 beam = mix(vec3(1.0, 0.5, 0.15), vec3(1.0, 0.95, 0.9), core) * (core * 6.0 + halo) * beamStrength * reach;
        // hide the beam behind the hull, but not behind the Earth's far side
        float tb = dot(mix(muzzle, earthPoint, rs.y) - ro, rd);
        if (tb < occl + 1.0) col += beam;

        // muzzle flash
        float flashAge = FireAge;
        float flashSize = 1.6 + flashAge * 7.0;
        vec2 fm = raySegment(ro, rd, muzzle, muzzle + AXIS * 0.01);
        float flash = exp(-pow(fm.x / flashSize, 2.0)) * exp(-flashAge * 6.0) * 14.0;
        // six flare streaks across the muzzle
        vec3 toM = muzzle - ro;
        float tm = dot(toM, rd);
        vec3 off = ro + rd * tm - muzzle;
        float streak = 0.0;
        for (int k = 0; k < 3; k++) {
            float ang = float(k) * 1.0471976;
            vec3 dirk = BX * cos(ang) + BY * sin(ang);
            float across = length(off - dirk * dot(off, dirk));
            streak += exp(-across * 6.0) * exp(-abs(dot(off, dirk)) * 0.08);
        }
        flash += streak * exp(-flashAge * 4.0) * 4.0;
        col += vec3(1.0, 0.85, 0.65) * flash;

        // the projectile: a tiny black hole riding the beam, ringed by its own accretion glow
        if (ProjT > 0.0 && ProjT < 1.0) {
            vec3 P = mix(muzzle, earthPoint, ProjT);
            vec3 toP = P - ro;
            float tp = dot(toP, rd);
            if (tp > 0.0 && tp < occl) {
                float d = length(ro + rd * tp - P);
                float rad = 0.9 + tp * 0.0015;
                float ring = exp(-pow((d - rad * 1.6) / (rad * 0.35), 2.0));
                vec3 glow = vec3(1.0, 0.6, 0.25) * ring * 8.0 + vec3(1.0, 0.7, 0.4) * exp(-d / (rad * 4.0)) * 1.5;
                if (d < rad) col = vec3(0.0); else col += glow;
            }
        }
    }

    fragColor = vec4(col, 1.0);
}
