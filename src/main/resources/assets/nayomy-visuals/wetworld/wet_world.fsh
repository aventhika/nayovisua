#version 330 core
// Wet World: SSR-отражения + затемнение + рябь. Логика исходного шейдера сохранена,
// но параметры качества идут uniform'ами, а дорогая трассировка пропускается там, где результат не виден.
in vec2 uv;
out vec4 fragColor;

uniform sampler2D uScene, uDepth;
uniform mat4 uInvProjection, uProjection, uViewRot; // uViewRot: view -> world (только поворот)
uniform vec3 uCamera;
uniform vec2 uResolution;
uniform vec3 uSkyTop, uSkyHorizon, uSunDir, uSunColor;
uniform float uStrength, uDarkening, uPhase, uRippleOn, uSkyLight, uSunPower;
uniform float uFogStart, uFogEnd, uMaxDist, uStepSize;
uniform int uQuality, uMaxSteps, uBinarySteps;

float getDither(vec2 coord) {
    return fract(52.9829189 * fract(dot(coord, vec2(0.06711056, 0.00583715))));
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = rot * p * 2.02 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

vec3 viewPosFromRawDepth(vec2 p, float d) {
    vec4 v = uInvProjection * vec4(p * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    return v.xyz / max(abs(v.w), 0.00001);
}

float depthAt(vec2 p) {
    return texture(uDepth, clamp(p, vec2(0.0001), vec2(0.9999))).r;
}

float viewZFromDepth(float d) {
    float clipZ = d * 2.0 - 1.0;
    float vz = uInvProjection[2][2] * clipZ + uInvProjection[3][2];
    float vw = uInvProjection[2][3] * clipZ + uInvProjection[3][3];
    return vz / max(abs(vw), 0.00001);
}

vec3 normalFromDepth(vec2 p, vec3 viewPos) {
    vec2 texel = 1.0 / max(uResolution, vec2(1.0));
    vec3 pL = viewPosFromRawDepth(p - vec2(texel.x, 0.0), depthAt(p - vec2(texel.x, 0.0)));
    vec3 pR = viewPosFromRawDepth(p + vec2(texel.x, 0.0), depthAt(p + vec2(texel.x, 0.0)));
    vec3 pD = viewPosFromRawDepth(p - vec2(0.0, texel.y), depthAt(p - vec2(0.0, texel.y)));
    vec3 pU = viewPosFromRawDepth(p + vec2(0.0, texel.y), depthAt(p + vec2(0.0, texel.y)));

    vec3 vL = viewPos - pL, vR = pR - viewPos, vD = viewPos - pD, vU = pU - viewPos;
    vec3 dX = (abs(vL.z) < abs(vR.z)) ? vL : vR;
    vec3 dY = (abs(vD.z) < abs(vU.z)) ? vD : vU;

    vec3 n = cross(dX, dY);
    float len = length(n);
    n = len > 1e-6 ? n / len : normalize(-viewPos);
    return dot(n, viewPos) > 0.0 ? -n : n;
}

// Рябь: две бегущие волны + круги от капель (+ мелкая волна на высоком качестве).
vec2 waterWaves(vec2 p, float t) {
    vec2 dN = vec2(0.0);

    float w1 = sin(p.x * 2.5 + p.y * 1.8 + t * 3.2);
    float w2 = cos(p.x * -2.2 + p.y * 3.1 - t * 2.6);
    dN += vec2(w1 + w2, -w1 + w2) * 0.025;

    vec2 grid = fract(p * 2.5) - 0.5;
    vec2 id = floor(p * 2.5);
    float dropTime = fract(t * 1.6 + hash21(id));
    float dropDist = length(grid);
    float ring = sin(dropDist * 32.0 - dropTime * 16.0) * exp(-dropDist * 4.5) * (1.0 - dropTime);
    dN += (grid / max(dropDist, 0.001)) * ring * 0.030;

    if (uQuality >= 2) {
        float w3 = sin(p.x * 6.5 - p.y * 4.8 + t * 4.2);
        float w4 = cos(p.x * 5.8 + p.y * 7.2 - t * 3.5);
        dN += vec2(w3, w4) * 0.012;
    }
    return dN;
}

void main() {
    vec4 original = texture(uScene, uv);
    float depthVal = texture(uDepth, uv).r;
    if (depthVal >= 0.999999) { fragColor = original; return; }

    vec3 viewPos = viewPosFromRawDepth(uv, depthVal);
    float dist = length(viewPos);

    // Туман дальности: далёкие блоки не мокнут. Рано выходим — это большая часть пикселей на больших дистанциях.
    float fogFactor = clamp((dist - uFogStart) / max(uFogEnd - uFogStart, 0.001), 0.0, 1.0);
    fogFactor = fogFactor * fogFactor * (3.0 - 2.0 * fogFactor);
    if (fogFactor >= 0.999) { fragColor = original; return; }

    vec3 V = viewPos / max(dist, 0.00001);
    mat3 viewRot = mat3(uViewRot);
    vec3 worldPos = uCamera + viewRot * viewPos;

    vec3 N_view = normalFromDepth(uv, viewPos);
    vec3 N_world = normalize(viewRot * N_view);

    float isGround = smoothstep(0.35, 0.85, clamp(N_world.y, 0.0, 1.0));
    float wetness = 0.45 + 0.55 * isGround;

    vec3 N = N_view;
    if (uRippleOn > 0.5 && isGround > 0.05) {
        vec2 dN = waterWaves(worldPos.xz, uPhase);
        vec3 Nw = normalize(vec3(-dN.x * isGround * 0.45, 1.0, -dN.y * isGround * 0.45));
        N = normalize(transpose(viewRot) * Nw);
        if (dot(N, viewPos) > 0.0) N = -N;
    }

    float nearFade = smoothstep(0.35, 0.70, abs(viewPos.z));
    float NdotV = clamp(dot(-V, N), 0.0, 1.0);
    float F0 = mix(0.04, 0.16, isGround);
    float fresnel = clamp(F0 + (1.0 - F0) * pow(1.0 - NdotV, 3.5), 0.03, 0.95);
    float mixAmount = fresnel * uStrength * wetness * nearFade * (1.0 - fogFactor);

    vec3 darkened = mix(original.rgb * (1.0 - uDarkening * wetness), original.rgb, fogFactor);

    // Отражение почти не видно — пропускаем трассировку и небо.
    if (mixAmount < 0.01) { fragColor = vec4(darkened, 1.0); return; }

    vec3 R = reflect(V, N);
    vec3 hitColor = vec3(0.0);
    float hitAlpha = 0.0;

    // Луч, летящий «на нас» (R.z >= .05), внутри экрана ничего не найдёт — трассировку пропускаем.
    if (R.z < 0.05) {
        float dither = getDither(gl_FragCoord.xy);
        vec3 origin = viewPos + N * 0.03;
        vec2 hitUV = vec2(-1.0);
        float hitT = 0.0;

        for (int i = 0; i < uMaxSteps; i++) {
            float t = (float(i) + dither) * uStepSize + uStepSize * 0.5;
            if (t > uMaxDist) break;

            vec3 P = origin + R * t;
            vec4 pc = uProjection * vec4(P, 1.0);
            if (pc.w <= 0.001) break;
            vec3 ndc = pc.xyz / pc.w;
            vec2 sUV = ndc.xy * 0.5 + 0.5;
            if (sUV.x < 0.001 || sUV.x > 0.999 || sUV.y < 0.001 || sUV.y > 0.999 || abs(ndc.z) > 1.0) break;

            float d = texture(uDepth, sUV).r;
            if (d >= 0.999999) continue;

            float delta = viewZFromDepth(d) - P.z;
            float thickness = max(uStepSize * 1.5, 0.06 + 0.015 * t);
            if (delta < 0.0 || delta >= thickness) continue;

            float tMin = max(0.0, t - uStepSize), tMax = t;
            vec2 refinedUV = sUV;
            float lastD = d;
            for (int b = 0; b < uBinarySteps; b++) {
                float tMid = (tMin + tMax) * 0.5;
                vec4 mc = uProjection * vec4(origin + R * tMid, 1.0);
                if (mc.w <= 0.001) break;
                vec2 mUV = (mc.xyz / mc.w).xy * 0.5 + 0.5;
                if (mUV.x < 0.001 || mUV.x > 0.999 || mUV.y < 0.001 || mUV.y > 0.999) break;
                float md = texture(uDepth, mUV).r;
                if (md >= 0.999999) { tMin = tMid; continue; }
                if (viewZFromDepth(md) - (origin + R * tMid).z >= 0.0) {
                    tMax = tMid; refinedUV = mUV; lastD = md;
                } else {
                    tMin = tMid;
                }
            }

            vec3 hitView = viewPosFromRawDepth(refinedUV, lastD);
            float finalDelta = hitView.z - (origin + R * tMax).z;
            if (finalDelta < -0.03 || finalDelta >= thickness) continue;

            // попали в самих себя
            if (length((refinedUV - uv) * uResolution) < 3.5 && tMax < uStepSize * 2.5) continue;
            // попали в заднюю грань
            if (dot(normalFromDepth(refinedUV, hitView), R) >= -0.05) continue;

            hitUV = refinedUV;
            hitT = tMax;
            break;
        }

        if (hitUV.x >= 0.0) {
            hitColor = texture(uScene, hitUV).rgb;
            float screenFade = smoothstep(0.0, 0.10, hitUV.x) * (1.0 - smoothstep(0.90, 1.0, hitUV.x))
                             * smoothstep(0.0, 0.10, hitUV.y) * (1.0 - smoothstep(0.90, 1.0, hitUV.y));
            float rayFade = 1.0 - smoothstep(uMaxDist * 0.65, uMaxDist, hitT);
            float facingFade = 1.0 - smoothstep(-0.25, 0.05, R.z);
            hitAlpha = screenFade * rayFade * facingFade;
        }
    }

    // Небо считаем, только если луч не попал в сцену целиком (fbm — самая дорогая часть).
    vec3 reflection = hitColor;
    if (hitAlpha < 0.99) {
        vec3 Rw = normalize(viewRot * R);
        float pitch = clamp(Rw.y * 0.5 + 0.5, 0.0, 1.0);
        vec3 sky = mix(uSkyHorizon, uSkyTop, pow(pitch, 0.75));
        float clouds = fbm(Rw.xz * 1.5 / max(abs(Rw.y) + 0.18, 0.08));
        sky = mix(sky, mix(uSkyHorizon, vec3(0.62), 0.55), clouds * 0.28 * smoothstep(-0.1, 0.5, Rw.y));
        sky += uSunColor * clamp(pow(max(dot(Rw, uSunDir), 0.0), 32.0), 0.0, 1.0) * 0.50 * isGround * uSunPower;
        sky *= uSkyLight;
        reflection = mix(sky, hitColor, hitAlpha);
    }

    fragColor = vec4(mix(darkened, reflection, mixAmount), 1.0);
}
