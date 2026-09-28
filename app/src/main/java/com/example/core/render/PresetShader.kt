package com.example.core.render

/**
 * The develop pipeline on the GPU, in Lightroom's order: white balance, exposure, tone, local contrast,
 * tone curves, colour mixer, vibrance/saturation, colour grading, calibration, B&W, detail, vignette, grain.
 * [CpuRenderer] runs the same steps in Kotlin; keep the two in step when changing either.
 */
object PresetShader {
    const val VERTEX = """#version 300 es
in vec2 aPos;
out vec2 vUv;
void main() {
    // Framebuffer row 0 samples bitmap row 0, so glReadPixels hands back rows in bitmap order.
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;
in vec2 vUv;
out vec4 outColor;

uniform sampler2D uSrc;      // photo, with mipmaps (blurred levels for local contrast and detail)
uniform sampler2D uLut;      // 256x1, tone curves per channel
uniform vec2 uSize;          // output size in pixels
uniform float uSmallLod;
uniform float uLargeLod;

uniform vec3 uWb;
uniform float uExposure;
uniform float uContrast, uHighlights, uShadows, uWhites, uBlacks;
uniform float uVibrance, uSaturation;
uniform vec3 uHsl[8];
uniform vec4 uGrading[4];
uniform float uBalance, uBlending;
uniform vec2 uCalib[3];
uniform float uShadowTint;
uniform int uMono;
uniform float uGray[8];
uniform float uTexture, uClarity, uDehaze;
uniform float uSharpen, uMasking, uNoise;
uniform float uVignette, uVigMid, uVigFeather, uVigRound;
uniform float uGrain, uGrainSize;

const float CENTERS[8] = float[8](0.0, 30.0/360.0, 60.0/360.0, 120.0/360.0, 180.0/360.0, 240.0/360.0, 270.0/360.0, 300.0/360.0);

vec3 toLinear(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c)); }
vec3 toSrgb(vec3 c) { c = max(c, 0.0); return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c)); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 rgb2hsv(vec3 c) {
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float d = mx - mn;
    float h = 0.0;
    if (d > 1e-6) {
        if (mx == c.r) h = mod((c.g - c.b) / d, 6.0);
        else if (mx == c.g) h = (c.b - c.r) / d + 2.0;
        else h = (c.r - c.g) / d + 4.0;
        h /= 6.0;
    }
    float s = mx > 1e-6 ? d / mx : 0.0;
    return vec3(h, s, mx);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

float hueDist(float a, float b) { float d = abs(a - b); return min(d, 1.0 - d); }

// Triangle weights between neighbouring centres: they always add up to 1 around the wheel.
float band(float h, int i) {
    float c = CENTERS[i];
    float prev = CENTERS[(i + 7) % 8];
    float next = CENTERS[(i + 1) % 8];
    float wl = hueDist(c, prev);
    float wr = hueDist(c, next);
    float d = h - c;
    d = d - floor(d + 0.5);
    return d < 0.0 ? max(0.0, 1.0 + d / wl) : max(0.0, 1.0 - d / wr);
}

float band3(float h, int i) {
    float d = h - float(i) / 3.0;
    d = d - floor(d + 0.5);
    return max(0.0, 1.0 - abs(d) * 3.0);
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec3 src = texture(uSrc, vUv).rgb;
    vec3 small = textureLod(uSrc, vUv, uSmallLod).rgb;
    vec3 large = textureLod(uSrc, vUv, uLargeLod).rgb;

    vec3 c = toLinear(src) * uWb * exp2(uExposure);
    c = toSrgb(c);

    float L = luma(c);
    float gain = exp2(uExposure);
    float Ls = luma(toSrgb(toLinear(small) * uWb * gain));
    float Ll = luma(toSrgb(toLinear(large) * uWb * gain));

    float t = L;
    t += uHighlights * 0.25 * smoothstep(0.45, 0.95, L);
    t += uShadows * 0.25 * (1.0 - smoothstep(0.05, 0.6, L));
    t += uWhites * 0.15 * smoothstep(0.65, 1.0, L);
    t += uBlacks * 0.15 * (1.0 - smoothstep(0.0, 0.35, L));
    t = 0.5 + (t - 0.5) * (1.0 + uContrast * 0.6);
    t += uClarity * 0.6 * (L - Ll) * 4.0 * L * (1.0 - L);
    t += uTexture * 0.8 * (L - Ls);
    float haze = uDehaze * 0.08;
    t = (t - haze) / (1.0 - haze);
    c = L > 1e-4 ? c * (t / L) : c + (t - L);
    c = clamp(c, 0.0, 1.0);

    c = vec3(texture(uLut, vec2(c.r * (255.0 / 256.0) + 0.5 / 256.0, 0.5)).r,
             texture(uLut, vec2(c.g * (255.0 / 256.0) + 0.5 / 256.0, 0.5)).g,
             texture(uLut, vec2(c.b * (255.0 / 256.0) + 0.5 / 256.0, 0.5)).b);

    vec3 hsv = rgb2hsv(c);
    float sat0 = hsv.y;
    float hueShift = 0.0, satDelta = 0.0, lumDelta = 0.0, grayDelta = 0.0;
    for (int i = 0; i < 8; i++) {
        float w = band(hsv.x, i);
        hueShift += w * uHsl[i].x;
        satDelta += w * uHsl[i].y;
        lumDelta += w * uHsl[i].z;
        grayDelta += w * uGray[i];
    }
    for (int i = 0; i < 3; i++) {
        float w = band3(hsv.x, i);
        hueShift += w * uCalib[i].x;
        satDelta += w * uCalib[i].y * 0.5;
    }
    hsv.x = fract(hsv.x + hueShift);
    hsv.y *= 1.0 + satDelta;
    hsv.y *= 1.0 + uVibrance * (1.0 - hsv.y);
    hsv.y *= 1.0 + uSaturation + uDehaze * 0.2;
    hsv.y = clamp(hsv.y, 0.0, 1.0);
    c = hsv2rgb(hsv);
    c *= 1.0 + lumDelta * sat0;

    float L2 = luma(c);
    float center = 0.5 - uBalance * 0.25;
    float width = mix(0.08, 0.45, uBlending);
    float wh = smoothstep(center - width, center + width, L2);
    float ws = 1.0 - wh;
    float wm = max(0.0, 1.0 - abs(L2 - center) / (width + 0.25));
    c += uGrading[0].rgb * ws + uGrading[1].rgb * wm + uGrading[2].rgb * wh + uGrading[3].rgb;
    c *= 1.0 + uGrading[0].a * ws + uGrading[1].a * wm + uGrading[2].a * wh + uGrading[3].a;
    c.g -= uShadowTint * 0.08 * (1.0 - L2);

    if (uMono == 1) c = vec3(luma(c) + grayDelta * sat0);

    float detail = L - Ls;
    float edge = uMasking <= 0.0 ? 1.0 : smoothstep(0.0, uMasking * 0.08, abs(detail));
    c += uSharpen * detail * edge * 1.5;
    c -= uNoise * 0.5 * detail * (1.0 - edge);

    vec2 p = (vUv - 0.5) * 2.0;
    p.x *= mix(1.0, uSize.x / uSize.y, clamp(uVigRound * 0.5 + 0.5, 0.0, 1.0));
    float dist = length(p) / 1.4142;
    float v = smoothstep(uVigMid - uVigFeather * 0.5, uVigMid + uVigFeather * 0.5 + 0.001, dist);
    c = uVignette < 0.0 ? c * (1.0 + uVignette * v) : mix(c, vec3(1.0), uVignette * v);

    vec2 cell = floor(vUv * uSize / (1.0 + uGrainSize * 3.0));
    c += (hash(cell) - 0.5) * uGrain * 0.12;

    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}
