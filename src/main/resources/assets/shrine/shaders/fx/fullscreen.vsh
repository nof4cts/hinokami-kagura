#version 150

// One oversized triangle covering the screen; no vertex buffers needed (gl_VertexID 0..2).
out vec2 uv;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
