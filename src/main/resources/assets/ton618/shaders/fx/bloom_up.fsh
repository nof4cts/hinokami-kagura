#version 150

// Bloom upsample: 3x3 tent filter of the smaller level, added onto this level's downsample.
in vec2 uv;
out vec4 fragColor;

uniform sampler2D Low;
uniform sampler2D Cur;
uniform vec2 LowTexel;
uniform float Radius;
uniform float LowWeight; // <1 keeps the widest levels from fogging the whole frame

void main() {
    vec2 r = LowTexel * Radius;
    vec3 s = texture(Low, uv).rgb * 4.0;
    s += (texture(Low, uv + vec2(-r.x, 0)).rgb + texture(Low, uv + vec2(r.x, 0)).rgb
        + texture(Low, uv + vec2(0, -r.y)).rgb + texture(Low, uv + vec2(0, r.y)).rgb) * 2.0;
    s += texture(Low, uv + vec2(-r.x, -r.y)).rgb + texture(Low, uv + vec2(r.x, -r.y)).rgb
       + texture(Low, uv + vec2(-r.x, r.y)).rgb + texture(Low, uv + vec2(r.x, r.y)).rgb;
    fragColor = vec4(texture(Cur, uv).rgb + s / 16.0 * LowWeight, 1.0);
}
