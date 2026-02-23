#version 150

uniform sampler2D InSampler;

layout(std140) uniform BlurParams {
    vec2 resolution;
    float radius;
    float blurLevel;
    vec2 InCropMin;
    vec2 InCropMax;
};

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Remap texCoord0 from [0,1] to the crop region of the source texture
    vec2 uv = mix(InCropMin, InCropMax, texCoord0);

    vec2 tex_offset = 1.0 / textureSize(InSampler, 0); // size of a single texel
    vec4 result = texture(InSampler, uv);
    float weights[5];
    if (blurLevel != 0) {
        result.rgb = vec3(0);
        if (blurLevel == 1) {
            weights = float[](0.45, 0.1, 0.1, 0.05, 0.02);
        } else {
            weights = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
        }
        int blurRange = 4;
        /*float weights[3] = float[](0.294117, 0.235294, 0.117647);
        int blurRange = 2;*/
        for (int i = -blurRange; i <= blurRange; ++i) {
            result.rgb += texture(InSampler, uv + vec2(tex_offset.x * float(i), 0.0)).rgb * weights[abs(i)];
        }
    }
    // GUI-only capture targets may preserve RGB but lose alpha; recover alpha for visible pixels.
    if (result.a <= 0.0 && dot(result.rgb, vec3(1.0)) > 0.0) {
        result.a = 1.0;
    }
    if (result.a <= 0.0) {
        discard;
    }
    fragColor = result;
}
