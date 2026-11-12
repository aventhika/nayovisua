#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const vec3 TINT = vec3(16.0 / 255.0, 16.0 / 255.0, 18.0 / 255.0); // #101012
const float BORDER_WIDTH = 2.0; // физ. пиксели

// 24 заранее посчитанных точки (спираль Фибоначчи): x, y, вес. Никакой тригонометрии в цикле.
const int TAPS = 24;
const vec3 TAP[TAPS] = vec3[](
    vec3(0.14434, 0.00000, 0.95919),
    vec3(-0.18434, 0.16887, 0.88250),
    vec3(0.02822, -0.32151, 0.81194),
    vec3(0.23235, 0.30306, 0.74702),
    vec3(-0.42639, -0.07542, 0.68729),
    vec3(0.40392, -0.25694, 0.63234),
    vec3(-0.13510, 0.50257, 0.58178),
    vec3(-0.25765, -0.49610, 0.53526),
    vec3(0.55901, 0.20415, 0.49246),
    vec3(-0.58155, 0.24006, 0.45309),
    vec3(0.28035, -0.59909, 0.41686),
    vec3(0.20717, 0.66049, 0.38353),
    vec3(-0.62441, -0.36186, 0.35287),
    vec3(0.73251, -0.16104, 0.32465),
    vec3(-0.44704, 0.63586, 0.29869),
    vec3(-0.10328, -0.79697, 0.27481),
    vec3(0.63401, 0.53435, 0.25284),
    vec3(-0.85318, 0.03528, 0.23262),
    vec3(0.62233, -0.61930, 0.21402),
    vec3(-0.04164, 0.90043, 0.19691),
    vec3(-0.59215, -0.70959, 0.18117),
    vec3(0.93803, 0.12621, 0.16668),
    vec3(-0.79479, 0.55300, 0.15335),
    vec3(0.21718, -0.96540, 0.14109)
);

void main() {
    float rawBlur      = vertexColor.r * 255.0;
    bool  invert       = rawBlur > 127.5;                 // флаг: рисуем только углы
    float blurRadius   = invert ? rawBlur - 128.0 : rawBlur;
    float cornerRadius = vertexColor.g * 255.0;
    float tintAlpha    = vertexColor.b;
    float borderAlpha  = vertexColor.a;

    vec2 size = 1.0 / max(fwidth(texCoord0), vec2(1e-6));

    vec2 p = (texCoord0 - 0.5) * size;
    vec2 q = abs(p) - (0.5 * size - cornerRadius);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;

    float shape = clamp(0.5 - d, 0.0, 1.0);
    float coverage = invert ? 1.0 - shape : shape;
    if (coverage <= 0.0) discard;

    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 center = gl_FragCoord.xy * texel;

    vec3 blurred;
    if (tintAlpha > 0.995) {
        // фон почти полностью закрашен — размытие всё равно не видно
        blurred = TINT;
    } else if (blurRadius < 0.5) {
        blurred = texture(Sampler0, center).rgb;
    } else {
        // случайный поворот на пиксель: 24 точек хватает, шум скрывается под тонировкой
        float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
        float ang = ign * 6.2831853;
        vec2 rot = vec2(cos(ang), sin(ang));

        // плотная тонировка (окно меню): размытие почти не видно, берём каждую 3-ю точку (8 вместо 24)
        int stride = tintAlpha > 0.85 ? 3 : 1;

        vec3 sum = vec3(0.0);
        float wsum = 0.0;
        for (int i = 0; i < TAPS; i += stride) {
            vec2 o = TAP[i].xy;
            vec2 r = vec2(o.x * rot.x - o.y * rot.y, o.x * rot.y + o.y * rot.x);
            sum += texture(Sampler0, center + r * blurRadius * texel).rgb * TAP[i].z;
            wsum += TAP[i].z;
        }
        blurred = sum / wsum;
    }

    vec3 color = mix(blurred, TINT, tintAlpha);

    if (!invert) {
        float inner = clamp(0.5 - (d + BORDER_WIDTH), 0.0, 1.0);
        float borderMask = max(shape - inner, 0.0);
        color = mix(color, vec3(1.0), borderMask * borderAlpha);
    }

    fragColor = vec4(color, coverage);
}
