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

const float weight[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

vec4 sampleGui(vec2 uv) {
    vec4 s = texture(InSampler, uv);
    if (s.a <= 0.0 && dot(s.rgb, vec3(1.0)) > 0.0) {
        s.a = 1.0;
    }
    return s;
}

void main() {
    // Remap texCoord0 from [0,1] to the crop region of the source texture
    vec2 uv = mix(InCropMin, InCropMax, texCoord0);
    vec4 color = sampleGui(uv);

    float r = radius;
    float x,y,xx,yy,rr=r*r,dx,dy,w,w0;
    float xs = resolution[0];
    float ys = resolution[1];
    vec2 texCoord0Pixels = vec2(texCoord0.x * xs, texCoord0.y * ys);
    vec2 pixelCoord = gl_FragCoord.xy;
    float xmid = xs / 2;
    float ymid = ys / 2;
    w0=0.3780/pow(r,1.975);
    vec2 p;
    vec2 pos = uv;
    vec4 col=vec4(0.0,0.0,0.0,0.0);
    for (dx=1.0/xs, x=-r, p.x=(pos.x)+(x*dx); x<=r; x++, p.x+=dx) {
        xx=x*x;
        for (dy=1.0/ys, y=-r, p.y=(pos.y)+(y*dy); y<=r; y++, p.y+=dy) {
            yy=y*y;
            if (xx+yy<=rr) {
                w=w0*exp((-xx-yy)/(2.0*rr));
                vec4 s = sampleGui(p);
                col += vec4(s.rgb * s.a, s.a) * w;
            }
        }
    }
    if (r == 0) {
        col = sampleGui(uv);
    } else if (col.a > 0.0) {
        col.rgb /= col.a;
    }
    //col.a = texCoord0.y;
    int cutoff = 128;
    int cutoff2 = 64;
    float dist = distance(pixelCoord, vec2(xmid, ymid));
    if (dist > cutoff) {
        //discard;
        col.a = min(col.a, 1 - ((dist - cutoff) / cutoff2));
    }
    //col.a = 0.32;
    if (col.a <= 0.0) {
        discard;
    }
    fragColor = col/* * ColorModulator*/;
}
