#version 150

// The Archangel. A colossal figure of light raymarched over the world:
//   robed body, head and halo, two great wings of layered feathers (slowly beating), and a sword of fire
//   that it raises and brings down along the line of the strike.
// Around it: a column of heavenly light from above, a burning slash trail, and after the blow the fissure:
//   a glowing molten line through the land with a curtain of fire rising from it.
//
// Model space: feet at y=0, head at y~9, facing +z, units = Size / 10 blocks.

#include "common.glsl"
#include "world_common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform vec3 FaceDir;     // world direction the angel faces (horizontal)
uniform float SwordT;     // 0 sword raised high .. 1 brought all the way down
uniform float SlashAge;   // seconds since the blow, < 0 before
uniform vec3 FissureA;    // fissure ends, relative to camera
uniform vec3 FissureB;
uniform float FissureW;   // half width in blocks
uniform float WingBeat;   // 0..1 phase driver
uniform float Lean;       // radians, + leans forward into the blow
uniform float SwordGlow;  // fire intensity of the blade, ~0.5 .. 2

const float PI = 3.14159265;

vec3 MX, MY, MZ;          // model axes in world space
float UNIT;               // blocks per model unit

#define A_BODY 1.0
#define A_ROBE 2.0
#define A_WING 3.0
#define A_GOLD 4.0
#define A_SKIN 5.0
#define A_BLADE 6.0

float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

float sdRoundCone(vec3 p, vec3 a, vec3 b, float r1, float r2) {
    vec3 ba = b - a;
    float l2 = dot(ba, ba);
    float rr = r1 - r2;
    float a2 = l2 - rr * rr;
    float il2 = 1.0 / l2;
    vec3 pa = p - a;
    float y = dot(pa, ba);
    float z = y - l2;
    vec3 xv = pa * l2 - ba * y;
    float x2 = dot(xv, xv);
    float y2 = y * y * l2;
    float z2 = z * z * l2;
    float k = sign(rr) * rr * rr * x2;
    if (sign(z) * a2 * z2 > k) return sqrt(x2 + z2) * il2 - r2;
    if (sign(y) * a2 * y2 < k) return sqrt(x2 + y2) * il2 - r1;
    return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
}

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / k1;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec2 opU(vec2 a, vec2 b) {
    return a.x < b.x ? a : b;
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotZ(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

// sword arm pose: shoulder fixed, hand sweeps from high overhead to low in front
vec3 SHOULDER = vec3(1.15, 7.55, 0.0);
vec3 handPos() {
    float a = mix(-2.35, 0.95, SwordT);          // angle of the arm in the y/z plane (0 = straight forward)
    vec3 dir = normalize(vec3(-0.15, -sin(a) * 1.0, cos(a)));
    return SHOULDER + dir * 3.0;
}

vec3 swordDir() {
    float a = mix(-2.6, 1.1, SwordT);
    return normalize(vec3(-0.1, -sin(a), cos(a)));
}

/** One wing, built from layered feathers fanning out from the shoulder blade. side = +1 / -1. */
float wing(vec3 p, float side) {
    p.x *= side;
    vec3 root = vec3(0.55, 7.3, -0.7);
    vec3 q = p - root;
    // slow, heavy beat: the whole wing lifts and the tip lags behind (fluid, not rigid)
    float ph = WingBeat * 2.0 * PI;
    float beat = sin(ph) * 0.22;
    q = rotZ(-0.45 + beat) * rotY(0.42 + 0.06 * cos(ph)) * q;
    float lag = sin(ph - 0.9) * 0.12 * smoothstep(2.0, 9.0, q.x);
    q = rotZ(lag) * q;
    if (length(q - vec3(5.0, -1.0, -0.6)) > 10.5) return length(q - vec3(5.0, -1.0, -0.6)) - 10.0;
    float d = 1.0e9;
    // arm of the wing (bone)
    d = min(d, sdCapsule(q, vec3(0.0), vec3(4.2, 2.6, -0.6), 0.3));
    d = min(d, sdCapsule(q, vec3(4.2, 2.6, -0.6), vec3(9.0, 1.8, -1.0), 0.2));
    // coverts: a soft scalloped shield along the bone
    d = smin(d, sdEllipsoid(q - vec3(4.3, 1.7, -0.65), vec3(4.9, 1.6, 0.26)), 0.6);
    // secondaries (short, hanging) and primaries (long, sweeping out) in two overlapping layers
    for (int i = 0; i < 14; i++) {
        float f = float(i) / 13.0;
        float jit = hash12(vec2(float(i), side * 3.0)) - 0.5;
        vec3 base = mix(vec3(1.0, 1.8, -0.55), vec3(8.9, 1.8, -1.0), f) + vec3(0.0, 0.0, (i % 2 == 0) ? 0.12 : -0.12);
        float ang = mix(-1.45, -0.05, pow(f, 1.3)) + jit * 0.06 + sin(ph - 1.5 - f * 2.0) * 0.05;
        float len = mix(4.2, 7.6, smoothstep(0.2, 1.0, f)) * (0.95 + 0.1 * jit);
        vec3 fq = rotZ(ang) * (q - base);
        // gently curved, tapered vane
        float fx = clamp(fq.x, 0.0, len);
        fq.z += 0.04 * fx * fx / len;
        float taper = mix(0.5, 0.18, clamp(fq.x / len, 0.0, 1.0));
        d = min(d, sdEllipsoid(fq - vec3(len * 0.5, 0.0, 0.0), vec3(len * 0.5, taper, 0.07)));
    }
    return d;
}

vec2 angel(vec3 p) {
    // body: torso, hips, neck, head
    float torso = sdRoundCone(p, vec3(0.0, 5.4, 0.0), vec3(0.0, 7.6, 0.0), 0.85, 1.05);
    torso = smin(torso, sdEllipsoid(p - vec3(0.0, 7.55, 0.0), vec3(1.45, 0.55, 0.7)), 0.4); // shoulders
    float neck = sdCapsule(p, vec3(0.0, 7.9, 0.0), vec3(0.0, 8.5, 0.05), 0.3);
    float head = sdEllipsoid(p - vec3(0.0, 9.0, 0.08), vec3(0.48, 0.6, 0.52));
    float body = smin(smin(torso, neck, 0.2), head, 0.15);
    vec2 res = vec2(body, A_SKIN);

    // robe: a flared cone with flowing folds, from the waist to below the feet
    vec3 rp = p - vec3(0.0, 5.6, 0.0);
    float t = clamp(-rp.y / 6.2, 0.0, 1.0);
    float radius = mix(0.95, 2.4, t * t * 0.6 + t * 0.4);
    float folds = sin(atan(rp.z, rp.x) * 9.0 + rp.y * 0.6 + Time * 0.6) * 0.12 * t;
    float robe = max(length(rp.xz) - radius - folds, abs(rp.y + 3.1) - 3.1);
    res = opU(res, vec2(robe, A_ROBE));
    // gold belt
    res = opU(res, vec2(max(abs(length(p.xz) - 1.0) - 0.1, abs(p.y - 5.6) - 0.16), A_GOLD));

    // arms: left hangs open-handed, right holds the sword
    vec3 hand = handPos();
    vec3 elbow = mix(SHOULDER, hand, 0.5) + vec3(0.25, 0.0, -0.2);
    float armR = min(sdCapsule(p, SHOULDER, elbow, 0.34), sdCapsule(p, elbow, hand, 0.28));
    float armL = min(sdCapsule(p, vec3(-1.15, 7.55, 0.0), vec3(-1.5, 5.6, 0.4), 0.33),
                     sdCapsule(p, vec3(-1.5, 5.6, 0.4), vec3(-1.4, 4.2, 1.1), 0.27));
    res = opU(res, vec2(min(armR, armL), A_ROBE));

    // halo
    vec3 hp = p - vec3(0.0, 10.1, -0.35);
    res = opU(res, vec2(length(vec2(length(hp.xz) - 0.75, hp.y)) - 0.07, A_GOLD));

    // wings
    res = opU(res, vec2(min(wing(p, 1.0), wing(p, -1.0)), A_WING));

    // sword: crossguard + long blade
    vec3 sd = swordDir();
    vec3 tip = hand + sd * 9.5;
    float blade = sdCapsule(p, hand + sd * 0.6, tip, 0.17);
    vec3 side = normalize(cross(sd, vec3(1.0, 0.0, 0.0)));
    float guard = sdCapsule(p, hand + sd * 0.5 - side * 0.7, hand + sd * 0.5 + side * 0.7, 0.1);
    res = opU(res, vec2(blade, A_BLADE));
    res = opU(res, vec2(guard, A_GOLD));
    return res;
}

const vec3 HIP = vec3(0.0, 4.6, 0.0);

vec3 toModel(vec3 worldRel) {
    // worldRel is relative to the angel's feet; undo the body's lean about the hips
    vec3 m = vec3(dot(worldRel, MX), dot(worldRel, MY), dot(worldRel, MZ)) / UNIT;
    return rotX(-Lean) * (m - HIP) + HIP;
}

/** Model point -> world offset from the camera (applies the lean). */
vec3 fromModel(vec3 m) {
    m = rotX(Lean) * (m - HIP) + HIP;
    return BodyRel + (MX * m.x + MY * m.y + MZ * m.z) * UNIT;
}

vec2 mapW(vec3 w) {
    vec2 r = angel(toModel(w - BodyRel));
    return vec2(r.x * UNIT, r.y);
}

vec3 normalW(vec3 w) {
    float e = UNIT * 0.02;
    vec2 k = vec2(1.0, -1.0);
    return normalize(k.xyy * mapW(w + k.xyy * e).x + k.yyx * mapW(w + k.yyx * e).x
                   + k.yxy * mapW(w + k.yxy * e).x + k.xxx * mapW(w + k.xxx * e).x);
}

void main() {
    MZ = normalize(vec3(FaceDir.x, 0.0, FaceDir.z));
    MY = vec3(0.0, 1.0, 0.0);
    MX = cross(MY, MZ);
    UNIT = Size / 10.0;

    vec3 rd = viewRay(uv);
    vec3 scene = texture(SceneTex, uv).rgb;
    float sceneT = sceneDistance(uv);
    vec3 col = scene;

    // heaven's light: the land brightens and warms under the opening sky
    float holy = Presence * (0.3 + 0.7 * Approach);
    col = mix(col, col * vec3(1.15, 1.08, 0.95) + vec3(0.05, 0.045, 0.03), holy * 0.6);

    // the column of light it descends through
    vec3 centre = BodyRel + MY * Size * 0.55;
    vec3 col0 = raySegment(vec3(0.0), rd, centre + MY * 3000.0, centre - MY * Size * 0.4);
    if (col0.z < sceneT) {
        float w = Size * (0.9 + 0.5 * Approach);
        float shaft = exp(-pow(col0.x / w, 2.0)) * 0.35 + exp(-col0.x / (w * 3.0)) * 0.12;
        float rays = 0.6 + 0.4 * noise3(vec3(col0.y * 30.0, Time * 0.5, 0.0));
        col += vec3(1.0, 0.93, 0.78) * shaft * rays * Presence;
    }

    // the figure
    vec2 b = sphereHit(vec3(0.0), rd, centre, Size * 1.25);
    float tHit = -1.0;
    vec2 m = vec2(0.0);
    float minD = 1.0e9;
    if (b.y > 0.0 && Presence > 0.01) {
        float t = max(b.x, 0.0);
        for (int i = 0; i < 160; i++) {
            if (i >= Steps) break;
            m = mapW(rd * t);
            if (t < sceneT) minD = min(minD, m.x / (m.y == A_WING ? 1.0 : 1.0));
            if (m.x < 0.0015 * t) {
                tHit = t;
                break;
            }
            t += m.x * 0.9;
            if (t > b.y) break;
        }
    }
    // soft glow around the whole figure (it radiates)
    if (minD < 1.0e8) {
        col += vec3(1.0, 0.9, 0.7) * exp(-max(minD, 0.0) / (UNIT * 0.22)) * smoothstep(UNIT * 0.8, UNIT * 0.2, minD) * 0.6 * Presence;   // tight rim halo
    }
    {   // broad radiance: analytic, from the body's axis and the wing span, so it is smooth
        vec3 ax = raySegment(vec3(0.0), rd, BodyRel + MY * UNIT * 1.0, BodyRel + MY * UNIT * 10.0);
        vec3 sp = raySegment(vec3(0.0), rd, BodyRel + (MY * 8.5 - MX * 9.0 - MZ * 1.0) * UNIT, BodyRel + (MY * 8.5 + MX * 9.0 - MZ * 1.0) * UNIT);
        float g = exp(-ax.x / (UNIT * 2.2)) * step(ax.z, sceneT) + 0.6 * exp(-sp.x / (UNIT * 3.0)) * step(sp.z, sceneT);
        col += vec3(1.0, 0.92, 0.75) * g * 0.35 * Presence;
    }
    if (tHit > 0.0 && tHit < sceneT) {
        vec3 w = rd * tHit;
        vec3 n = normalW(w);
        vec3 v = -rd;
        float fres = pow(1.0 - max(dot(n, v), 0.0), 2.5);
        vec3 lightDir = normalize(vec3(0.2, 1.0, 0.3));
        float dif = 0.55 + 0.45 * dot(n, lightDir);
        vec3 c;
        if (m.y == A_BLADE) {
            float fire = fbm3(toModel(w - BodyRel) * 3.0 - vec3(0.0, Time * 4.0, 0.0));
            c = mix(vec3(1.0, 0.5, 0.1), vec3(1.0, 0.95, 0.85), fire) * 9.0 * SwordGlow;
        } else if (m.y == A_GOLD) {
            c = vec3(1.0, 0.78, 0.35) * (1.8 + 2.0 * fres) * dif;
        } else if (m.y == A_WING) {
            vec3 mp = toModel(w - BodyRel);
            float barbs = 0.93 + 0.07 * noise3(mp * vec3(14.0, 3.0, 14.0));
            float back = pow(max(dot(rd, lightDir), 0.0), 3.0);   // light through the vanes
            vec3 base = mix(vec3(1.0, 0.98, 0.95), vec3(1.0, 0.9, 0.72), smoothstep(6.0, 13.0, abs(mp.x)));
            c = base * (0.85 + 0.7 * dif) * barbs + vec3(1.0, 0.88, 0.62) * (fres * 1.8 + back * 0.5);
        } else if (m.y == A_ROBE) {
            c = vec3(0.96, 0.94, 0.9) * 1.25 * dif + vec3(1.0, 0.9, 0.7) * fres * 1.6;
        } else {
            c = vec3(1.0, 0.88, 0.75) * 1.4 * dif + vec3(1.0, 0.9, 0.7) * fres * 2.0;
        }
        col = mix(col, c, Presence);
    }

    // the sword's fire: a glow along the blade whatever is in front
    vec3 hand = handPos();
    vec3 sd = swordDir();
    vec3 hw = fromModel(hand);
    vec3 tw = fromModel(hand + sd * 9.5);
    vec3 bs = raySegment(vec3(0.0), rd, hw, tw);
    if (bs.z < sceneT) {
        float flick = 0.7 + 0.3 * noise3(vec3(bs.y * 12.0, Time * 6.0, 0.0));
        col += vec3(1.0, 0.45, 0.08) * (exp(-bs.x / (UNIT * 0.6)) * 2.2 + exp(-bs.x / (UNIT * 2.5)) * 0.35) * flick * SwordGlow * Presence;
    }

    // the slash trail: an arc of fire swept by the blade tip, lingering a moment after the blow
    if (SlashAge > -0.7 && SlashAge < 1.5) {
        float fade = SlashAge < 0.0 ? 1.0 : exp(-SlashAge * 2.5);
        vec3 shoulderW = fromModel(SHOULDER);
        float R = 12.0 * UNIT;
        // plane of the swing: spanned by up and forward
        vec3 n = MX;
        float denom = dot(rd, n);
        if (abs(denom) > 1.0e-3) {
            float t = dot(shoulderW, n) / denom;
            if (t > 0.0 && t < sceneT) {
                vec3 q = rd * t - shoulderW;
                float ang = atan(-dot(q, MY), dot(q, MZ));
                float a0 = mix(-2.6, 1.1, 0.0), a1 = mix(-2.6, 1.1, SwordT);
                float behind = a1 - ang;                                   // radians behind the blade
                float inArc = smoothstep(a0 - 0.05, a0 + 0.25, ang) * smoothstep(-0.03, 0.06, behind);
                float tail = exp(-max(behind, 0.0) * 1.6);
                float wdt = UNIT * (0.5 + 1.4 * clamp(behind, 0.0, 1.5));
                float ring = exp(-pow((length(q) - R) / wdt, 2.0)) * tail;
                // the blade sweeps the whole length: inner part of the arc fainter
                ring += exp(-pow((length(q) - R * 0.62) / (wdt * 2.2), 2.0)) * tail * 0.25 * step(length(q), R);
                float fl = 0.55 + 0.45 * fbm3(vec3(ang * 9.0, length(q) / UNIT * 0.4, Time * 4.0));
                col += mix(vec3(1.0, 0.4, 0.06), vec3(1.0, 0.92, 0.7), ring) * ring * inArc * fl * 3.5 * fade * Presence;
            }
        }
    }

    // the fissure: a molten seam through the land, and a curtain of fire rising from it
    if (SlashAge >= 0.0) {
        vec3 ab = FissureB - FissureA;
        float abLen = length(ab.xz);
        vec2 dirXZ = ab.xz / max(abLen, 1.0e-3);
        float spread = clamp(SlashAge * 0.9, 0.0, 1.0);   // the cut races out from the centre
        if (sceneT < 1.0e8) {
            vec3 wp = rd * sceneT;
            vec2 rel = wp.xz - (FissureA.xz + FissureB.xz) * 0.5;
            float along = dot(rel, dirXZ);
            float across = abs(rel.x * dirXZ.y - rel.y * dirXZ.x);
            float halfLen = abLen * 0.5 * spread;
            float inLen = smoothstep(halfLen, halfLen - 20.0, abs(along));
            float taper = sqrt(max(1.0 - pow(along / max(abLen * 0.5, 1.0), 2.0), 0.0));
            float edge = exp(-max(across - FissureW * taper, 0.0) / 6.0) * inLen;
            float crack = 0.6 + 0.4 * noise3(vec3(along * 0.2, across * 0.5, Time * 0.8));
            col += vec3(1.0, 0.35, 0.05) * edge * crack * 2.6 * Presence;
        }
        // fire curtain: the vertical plane through the fissure
        vec3 pn = normalize(vec3(-dirXZ.y, 0.0, dirXZ.x));
        float den = dot(rd, pn);
        if (abs(den) > 1.0e-3) {
            float t = dot((FissureA + FissureB) * 0.5, pn) / den;
            if (t > 0.0 && t < sceneT) {
                vec3 q = rd * t - (FissureA + FissureB) * 0.5;
                float along = dot(q.xz, dirXZ);
                float height = q.y + 20.0;
                float curtainH = 160.0 * exp(-SlashAge * 0.35) * spread;
                float inLen = smoothstep(abLen * 0.5 * spread, abLen * 0.5 * spread - 30.0, abs(along));
                float f = fbm3(vec3(along * 0.03, height * 0.02 - Time * 1.5, 0.0));
                float flame = smoothstep(curtainH, 0.0, height) * step(0.0, height) * smoothstep(0.3, 0.8, f + 0.2);
                col += mix(vec3(1.0, 0.3, 0.04), vec3(1.0, 0.8, 0.45), f) * flame * inLen * 1.6 * Presence;
            }
        }
    }

    fragColor = vec4(col, 1.0);
}
