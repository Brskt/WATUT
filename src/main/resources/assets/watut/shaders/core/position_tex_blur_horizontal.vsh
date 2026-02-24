#version 150

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize0;
};

out vec2 texCoord0;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * vec2(2.0, 2.0) + vec2(-1.0, -1.0), 0.2, 1.0);
    texCoord0 = uv;
}
