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

vec4 sampleGui(vec2 uv) {
    vec4 s = texture(InSampler, uv);
    // GUI-only capture targets may preserve RGB but lose alpha; recover alpha for visible pixels.
    if (s.a <= 0.0 && dot(s.rgb, vec3(1.0)) > 0.0) {
        s.a = 1.0;
    }
    return s;
}

void main() {
    // Remap texCoord0 from [0,1] to the crop region of the source texture
    vec2 uv = mix(InCropMin, InCropMax, texCoord0);

    vec2 tex_offset = 1.0 / textureSize(InSampler, 0); // size of a single texel
    vec4 result = sampleGui(uv);
    float weights[5];
    if (blurLevel != 0) {
        vec3 accumPremul = vec3(0.0);
        float accumAlpha = 0.0;
        if (blurLevel == 1) {
            weights = float[](0.45, 0.1, 0.1, 0.05, 0.02);
        } else {
            weights = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
        }
        int blurRange = 4;
        /*float weights[3] = float[](0.294117, 0.235294, 0.117647);
        int blurRange = 2;*/
        for (int i = -blurRange; i <= blurRange; ++i) {
            vec4 s = sampleGui(uv + vec2(tex_offset.x * float(i), 0.0));
            float w = weights[abs(i)];
            accumPremul += s.rgb * s.a * w;
            accumAlpha += s.a * w;
        }
        if (accumAlpha > 0.0) {
            result.rgb = accumPremul / accumAlpha;
            result.a = accumAlpha;
        } else {
            result = vec4(0.0);
        }
    }
    if (result.a <= 0.0) {
        discard;
    }
    fragColor = result;
}
