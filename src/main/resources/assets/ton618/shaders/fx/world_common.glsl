// Shared inputs for the in-world passes (sun.fsh, angel.fsh): the rendered frame, its depth, and the camera.

uniform sampler2D SceneTex;
uniform sampler2D DepthTex;
uniform mat4 Proj;
uniform mat4 InvProj;
uniform mat3 ViewRot;     // world -> view
uniform mat3 InvViewRot;  // view -> world
uniform float Time;
uniform int Steps;
uniform vec3 BodyRel;     // body centre minus camera (blocks)
uniform float Size;       // body radius (sun) or height (angel), blocks
uniform float Presence;   // 0..1 overall fade
uniform float Approach;   // 0..1 descent progress
uniform float Landed;     // 0..1 time since impact (eased), 0 before

vec3 viewRay(vec2 p) {
    vec4 c = InvProj * vec4(p * 2.0 - 1.0, 1.0, 1.0);
    return normalize(InvViewRot * (c.xyz / c.w));
}

/** Distance from the camera to the scene along the pixel's ray (1e9 for sky). */
float sceneDistance(vec2 p) {
    float d = texture(DepthTex, p).r;
    if (d >= 0.99999) return 1.0e9;
    vec4 v = InvProj * vec4(p * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return length(v.xyz / v.w);
}

vec2 sphereHit(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

/** Closest distance between the ray and a segment; returns (distance, segment param, ray t). */
vec3 raySegment(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ba = b - a;
    vec3 oa = ro - a;
    float baba = dot(ba, ba);
    float bard = dot(ba, rd);
    float denom = baba - bard * bard;
    float t = denom > 1.0e-6 ? (dot(ba, oa) - bard * dot(rd, oa)) / denom : 0.0;
    t = clamp(t, 0.0, 1.0);
    vec3 pa = a + ba * t;
    float s = max(dot(pa - ro, rd), 0.0);
    return vec3(length(ro + rd * s - pa), t, s);
}
