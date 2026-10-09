#version 150

// Slash ribbon vertex shader: pass-through of colour and the (u, v) ribbon coordinates.
in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 ribbon;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    ribbon = UV0;
}
