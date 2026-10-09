#version 150

// Final pass, written back over Minecraft's frame:
// shockwave refraction -> chromatic aberration -> bloom -> exposure -> highlight roll-off ->
// flash with burned-in afterimage -> colour grade (desaturation, warm/cold split) -> vignette -> grain.

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

/** Leaves the normal 0..0.8 range of the game untouched and rolls HDR highlights off to white. */
vec3 rolloff(vec3 c) {
    float m = max(c.r, max(c.g, c.b));
    if (m <= 0.8) return c;
    float mapped = 0.8 + 0.2 * (1.0 - exp(-(m - 0.8) / 0.2));
    vec3 scaled = c * (mapped / m);
    // very hot light bleaches toward white, like an overexposed camera
    float bleach = clamp((m - 1.6) * 0.06, 0.0, 0.7);
    return mix(scaled, vec3(mapped), bleach);
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = uv + Shake;

    // Shockwave: a ring that refracts the image outward as it expands.
    if (Shockwave.w > 0.0) {
        vec2 d = (p - Shockwave.xy) * vec2(aspect, 1.0);
        float len = length(d);
        float ring = exp(-pow((len - Shockwave.z) / 0.035, 2.0));
        p -= normalize(d + 1.0e-5) / vec2(aspect, 1.0) * ring * Shockwave.w * 0.045;
    }

    // Chromatic aberration grows toward the edges.
    vec2 off = (p - 0.5) * Aberration * 0.012;
    vec3 col;
    col.r = texture(Hdr, p + off).r;
    col.g = texture(Hdr, p).g;
    col.b = texture(Hdr, p - off).b;

    vec3 bloom = texture(Bloom, p).rgb;
    col += bloom * BloomStrength;
    col *= Exposure;
    col = rolloff(col);

    // Flash: blinding white that leaves a negative-tinted afterimage as it fades.
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

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
