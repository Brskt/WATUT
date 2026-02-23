#version 150

in vec4 Position;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize0;
};

out vec2 texCoord0;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy * OutSize, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    texCoord0 = Position.xy;
}
