#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float median(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

// Мягкость края: половина ширины перехода в физических пикселях.
// 0.5 = ровно 1 px (резко, «пиксельно»), 0.75 = 1.5 px (гладко). Больше — размытее.
const float EDGE_SOFTNESS = 0.75;

void main() {
    vec4 t = texture(Sampler0, texCoord0);

    // MSDF: медиана трёх каналов = расстояние до контура (0.5 = край буквы).
    float sd = median(t.rgb);

    // на сколько меняется sd за один физический пиксель (L2-норма: ровнее на диагоналях, чем fwidth)
    float w = max(length(vec2(dFdx(sd), dFdy(sd))), 0.0005);

    // половина ширины перехода в единицах sd; не больше 0.5, чтобы внутри буквы alpha доходил до 1
    float hw = min(w * EDGE_SOFTNESS, 0.5);
    float a = smoothstep(0.5 - hw, 0.5 + hw, sd);

    if (a <= 0.003) discard;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a);
}
