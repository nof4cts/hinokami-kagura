#version 150

// Slash ribbon fragment shader.
//   ribbon.x (u): 0 = tail of the trail .. 1 = blade head
//   ribbon.y (v): 0 = inner edge .. 1 = outer cutting edge
// Output is meant for additive blending (src_alpha, one): colour * alpha is added to the scene,
// which is what makes overlapping slashes and flames bloom toward white.

in vec4 vertexColor;
in vec2 ribbon;

uniform vec4 ColorModulator;
uniform float GameTime; // fraction of a 20-minute day; *1200 = seconds

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    float u = ribbon.x;
    float v = ribbon.y;
    float t = GameTime * 1200.0;

    // Across the ribbon: soft inner falloff rising to a hard bright cutting edge.
    float body = smoothstep(0.0, 0.9, v);
    float edge = pow(smoothstep(0.55, 0.97, v), 3.0) * (1.0 - smoothstep(0.97, 1.0, v) * 0.6);

    // Flame licks: two octaves of noise scrolling backwards along the trail, eating into the inner side.
    float n = noise(vec2(u * 10.0 - t * 7.0, v * 4.0 + t * 1.7)) * 0.65
            + noise(vec2(u * 23.0 - t * 13.0, v * 9.0 - t * 2.3)) * 0.35;
    float flame = smoothstep(0.35 - v * 0.5, 0.85, n);

    // Along the trail: bright at the head, burning out toward the tail.
    float along = pow(u, 1.4);

    float intensity = (body * 0.55 + flame * body * 0.45) * along + edge * along * 1.2;
    float alpha = clamp(intensity, 0.0, 1.0) * vertexColor.a;

    // Palette: base tint -> gold -> white-hot at the cutting edge near the head.
    vec3 gold = vec3(1.0, 0.78, 0.30);
    vec3 whiteHot = vec3(1.0, 0.97, 0.90);
    vec3 col = mix(vertexColor.rgb, gold, clamp(body * flame * 0.6, 0.0, 1.0));
    col = mix(col, whiteHot, clamp(edge * along * 1.3, 0.0, 1.0));

    fragColor = vec4(col, alpha) * ColorModulator;
    if (fragColor.a < 0.004) {
        discard;
    }
}
