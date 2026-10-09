#version 150

// Bloom downsample: 13-tap filter (Jimenez, "Next Generation Post Processing in Call of Duty").
// On the first level (Prefilter = 1) it also keeps only the light ABOVE the threshold, with a soft knee,
// so ordinary daylight scenery doesn't bloom but the disk, jets and flashes do.

in vec2 uv;
out vec4 fragColor;

uniform sampler2D Src;
uniform vec2 SrcTexel;
uniform float Prefilter;
uniform float Threshold;

vec3 tap(vec2 o) {
    return texture(Src, uv + o * SrcTexel).rgb;
}

void main() {
    vec3 a = tap(vec2(-2, -2)), b = tap(vec2(0, -2)), c = tap(vec2(2, -2));
    vec3 d = tap(vec2(-1, -1)), e = tap(vec2(1, -1));
    vec3 f = tap(vec2(-2, 0)), g = tap(vec2(0, 0)), h = tap(vec2(2, 0));
    vec3 i = tap(vec2(-1, 1)), j = tap(vec2(1, 1));
    vec3 k = tap(vec2(-2, 2)), l = tap(vec2(0, 2)), m = tap(vec2(2, 2));
    vec3 col = (d + e + i + j) * 0.125
             + (a + b + g + f) * 0.03125 + (b + c + h + g) * 0.03125
             + (f + g + l + k) * 0.03125 + (g + h + m + l) * 0.03125;

    if (Prefilter > 0.5) {
        float br = max(col.r, max(col.g, col.b));
        float knee = Threshold * 0.5;
        float soft = clamp(br - Threshold + knee, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 1.0e-4);
        float contrib = max(soft, br - Threshold) / max(br, 1.0e-4);
        col *= contrib;
        col = min(col, vec3(60.0)); // tame single-pixel fireflies
    }
    fragColor = vec4(col, 1.0);
}
