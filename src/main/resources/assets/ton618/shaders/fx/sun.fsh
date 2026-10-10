#version 150

// The Sun, torn out of its orbit and falling onto the world.
//  - the photosphere as a sphere with boiling granulation, dark spots, limb darkening, prominences
//  - a corona that floods the sky; the whole world heats from blue to orange to white as it nears
//  - heat shimmer near the horizon and around the limb
//  - after it lands: a dome of plasma half-buried in the ground, and everything near it glowing molten

#include "common.glsl"
#include "world_common.glsl"

in vec2 uv;
out vec4 fragColor;

void main() {
    vec3 rd = viewRay(uv);
    float heat = Presence * (0.15 + 0.85 * Approach * Approach);

    // heat shimmer: wobble the scene lookup, strongest low in the frame and near the sun
    vec3 toC = normalize(BodyRel);
    float nearSun = pow(max(dot(rd, toC), 0.0), 6.0);
    vec2 wob = vec2(noise3(vec3(uv * 40.0, Time * 3.0)), noise3(vec3(uv * 40.0 + 7.0, Time * 3.0))) - 0.5;
    vec2 suv = uv + wob * 0.006 * heat * (0.4 + nearSun + (1.0 - uv.y) * 0.6);
    vec3 scene = texture(SceneTex, suv).rgb;
    float sceneT = sceneDistance(suv);

    // the world bakes: blue drains, everything takes on the sun's colour, highlights blow out
    float l = luminance(scene);
    vec3 baked = scene * vec3(1.35, 0.85, 0.5) + vec3(0.32, 0.1, 0.02) * (0.3 + l);
    vec3 col = mix(scene, baked, heat * 0.85);
    if (sceneT > 1.0e8) {
        // the sky itself becomes corona
        float up = clamp(rd.y, 0.0, 1.0);
        vec3 burn = mix(vec3(1.0, 0.42, 0.1), vec3(0.45, 0.08, 0.03), up) * (0.55 + 1.2 * nearSun);
        col = mix(col, burn, smoothstep(0.0, 0.7, heat));
    }

    // after landing: ground near the impact glows molten
    if (Landed > 0.0 && sceneT < 1.0e8) {
        vec3 wp = rd * sceneT;
        float dd = length(wp - BodyRel) - Size;
        float molten = exp(-max(dd, 0.0) / (25.0 + 60.0 * Landed)) * Presence;
        float crust = 0.55 + 0.45 * noise3(wp * 0.3 + Time * 0.1);
        col += vec3(1.0, 0.32, 0.05) * molten * crust * 3.0;
    }

    // the photosphere
    vec2 hit = sphereHit(vec3(0.0), rd, BodyRel, Size);
    vec3 oc = -BodyRel;
    float closest = length(oc - rd * dot(oc, rd));
    float h = max(closest / Size - 1.0, 0.0);
    float inFront = step(0.0, dot(BodyRel, rd));
    float occluded = step(sceneT, length(BodyRel) - Size * 0.2);  // terrain in front of the limb
    vec3 pc = normalize(rd * max(dot(BodyRel, rd), 0.0) - BodyRel);
    float streamer = pow(abs(fbm3(pc * 3.0 + Time * 0.05)), 2.0);
    vec3 corona = vec3(1.0, 0.68, 0.32) * (exp(-h * 10.0) * 2.0 + exp(-h * 2.5) * (0.25 + 0.9 * streamer)) * inFront;
    col += corona * Presence * (1.0 - occluded * 0.85);

    if (hit.x > 0.0 && hit.x < sceneT) {
        vec3 n = normalize(rd * hit.x - BodyRel);
        float mu = max(dot(n, -rd), 0.0);
        float limb = pow(mu, 0.45);
        vec3 sp = n * 70.0 + Time * 0.12;
        float gran = fbm3(sp);
        float g2 = fbm3(n * 200.0 - Time * 0.3);
        float spots = smoothstep(0.68, 0.74, fbm3(n * 6.0 + 13.0));
        vec3 hot = mix(vec3(1.0, 0.42, 0.06), vec3(1.0, 0.88, 0.55), smoothstep(0.3, 0.8, gran * 0.7 + g2 * 0.5));
        vec3 surf = hot * 3.0 * (0.6 + 0.8 * gran) * mix(vec3(1.0), vec3(0.12, 0.03, 0.01), spots);
        surf *= mix(vec3(0.75, 0.32, 0.08), vec3(1.0), limb);
        // turbulence where it's being dragged down
        surf *= 1.0 + 1.2 * Approach * smoothstep(0.0, -0.8, n.y) * (0.5 + fbm3(n * 15.0 - Time));
        col = mix(col, surf, Presence);
    }

    // prominences arcing off the limb
    if (h < 0.3 && inFront > 0.5) {
        vec3 rgt = normalize(cross(rd, vec3(0.0, 1.0, 0.0)));
        vec3 upv = cross(rgt, rd);
        float a = atan(dot(pc, upv), dot(pc, rgt));
        float total = 0.0;
        for (int i = 0; i < 5; i++) {
            float c0 = hash12(vec2(float(i), 3.1)) * 6.2831853 - 3.14159;
            float span = 0.15 + 0.2 * hash12(vec2(float(i), 7.7));
            float u = (a - c0) / span;
            if (abs(u) > 1.0) continue;
            float hh = (0.05 + 0.14 * hash12(vec2(float(i), 1.9))) * sqrt(max(1.0 - u * u, 0.0));
            float fil = fbm3(vec3(u * 4.0, h * 40.0, float(i) * 3.0 - Time * 0.25));
            total += exp(-pow((h - hh) / (0.008 + 0.012 * fil), 2.0)) * smoothstep(0.25, 0.75, fil);
        }
        col += vec3(1.0, 0.3, 0.1) * total * 3.0 * Presence * (1.0 - occluded);
    }

    fragColor = vec4(col, 1.0);
}
