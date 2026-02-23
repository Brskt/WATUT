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

    float xs = resolution[0];
    float ys = resolution[1];
    float xmid = xs / 2;
    float ymid = ys / 2;

    vec2 uv = mix(InCropMin, InCropMax, texCoord0);
    vec2 tex_offset = 1.0 / textureSize(InSampler, 0); // size of a single texel
    vec4 result = sampleGui(uv);
    if (blurLevel != 0) {
        vec3 accumPremul = vec3(0.0);
        float accumAlpha = 0.0;
        float weights[5];
        if (blurLevel == 1) {
            weights = float[](0.45, 0.1, 0.1, 0.05, 0.02);
        } else {
            weights = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
        }
        int blurRange = 4;
        for (int i = -blurRange; i <= blurRange; ++i) {
            vec4 s = sampleGui(uv + vec2(0.0, tex_offset.y * float(i)));
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

    vec2 pixelCoord = gl_FragCoord.xy;
    //col.a = texCoord0.y;
    float cutoff = radius;
    //cutoff = 32;
    //int cutoff2 = 32;
    int cutoff2 = 16;
    float dist = distance(pixelCoord, vec2(xmid, ymid));
    //vec4 color = vec4(result, 1.0);
    if (cutoff != -1) {
        if (dist > cutoff) {
            //TODO: there might be a problem with my strat of using the same texture to render back onto itself, try enabling discard below to see the weirdness outside of the circle
            //if i discard, the texture data remains because its already in the texture
            //for now its ok cause im forcing all other pixels to be alpha 0
            //discard;
            //color.a = min(color.a, 1 - ((dist - cutoff) / cutoff2));
            //avoid doing the alpha calculation where theres no point, saves on performance a lot
            if (dist > cutoff + cutoff2) {
                result.a = 0;
            } else {
                result.a = min(result.a, 1 - ((dist - cutoff) / cutoff2));
            }
        }
    }
    /*if (result.a <= 0.0) {
        discard;
    }*/
    //col.a = 0.32;
    /*if (fragColor.a <= 0.0) {
        discard;
    }*/
    //fragColor = color;
    fragColor = result;
}
