package nayomy.visuals.module;

import com.google.gson.JsonObject;

/**
 * Atmosphere: рассвет, пыль, снег и угли. Настройки + расчёт параметров для шейдера.
 * Рендер — см. render/AtmospherePostPipeline.
 */
public final class AtmosphereModule {

    public static final AtmosphereModule INSTANCE = new AtmosphereModule();

    public static final String[] MODES = {"Рассвет", "Пыль", "Снег", "Угли"};
    /** Пресеты «Цвета рассвета» (первый — как в оригинале). */
    public static final int[] DAWN_COLORS = {0xFFFFAD7A, 0xFFFF8FA3, 0xFFFFD27A, 0xFFB79AFF, 0xFF7AD7FF};
    public static final String[] DAWN_COLOR_NAMES = {"Персик", "Розовый", "Золото", "Фиолетовый", "Небесный"};

    /** По умолчанию выключен. */
    public boolean enabled = false;
    public int mode = 0;
    public int dawnColorIndex = 0;

    // режим «Рассвет»
    public float density = .35f;        // .05 .. .8
    public float scatterHeight = 76f;   // 60 .. 120
    public float godRays = 75f;         // 0 .. 100
    public float softness = 60f;        // 0 .. 100
    // остальные режимы
    public float amount = 50f;          // частицы, 0 .. 100
    public float speed = 100f;          // 10 .. 300
    public float haze = 50f;            // 0 .. 100
    public float grade = 70f;           // 0 .. 100
    public float vignette = 40f;        // 0 .. 100

    private AtmosphereModule() {}

    public boolean isMood() { return mode != 0; }
    public boolean isDawn() { return mode == 0; }
    public int dawnColor() { return DAWN_COLORS[Math.floorMod(dawnColorIndex, DAWN_COLORS.length)]; }

    public void cycleMode() { mode = (mode + 1) % MODES.length; save(); }
    public void cycleDawnColor() { dawnColorIndex = (dawnColorIndex + 1) % DAWN_COLORS.length; save(); }

    public void load() {
        JsonObject o = ConfigStore.read();
        enabled = ConfigStore.bool(o, "atmosphere.enabled", enabled);
        mode = Math.floorMod(ConfigStore.integer(o, "atmosphere.mode", mode), MODES.length);
        dawnColorIndex = Math.floorMod(ConfigStore.integer(o, "atmosphere.dawnColor", dawnColorIndex), DAWN_COLORS.length);
        density = clamp(ConfigStore.num(o, "atmosphere.density", density), .05f, .8f);
        scatterHeight = clamp(ConfigStore.num(o, "atmosphere.scatterHeight", scatterHeight), 60f, 120f);
        godRays = clamp(ConfigStore.num(o, "atmosphere.godRays", godRays), 0f, 100f);
        softness = clamp(ConfigStore.num(o, "atmosphere.softness", softness), 0f, 100f);
        amount = clamp(ConfigStore.num(o, "atmosphere.amount", amount), 0f, 100f);
        speed = clamp(ConfigStore.num(o, "atmosphere.speed", speed), 10f, 300f);
        haze = clamp(ConfigStore.num(o, "atmosphere.haze", haze), 0f, 100f);
        grade = clamp(ConfigStore.num(o, "atmosphere.grade", grade), 0f, 100f);
        vignette = clamp(ConfigStore.num(o, "atmosphere.vignette", vignette), 0f, 100f);
    }

    public void save() {
        JsonObject o = ConfigStore.read();
        o.addProperty("atmosphere.enabled", enabled);
        o.addProperty("atmosphere.mode", mode);
        o.addProperty("atmosphere.dawnColor", dawnColorIndex);
        o.addProperty("atmosphere.density", density);
        o.addProperty("atmosphere.scatterHeight", scatterHeight);
        o.addProperty("atmosphere.godRays", godRays);
        o.addProperty("atmosphere.softness", softness);
        o.addProperty("atmosphere.amount", amount);
        o.addProperty("atmosphere.speed", speed);
        o.addProperty("atmosphere.haze", haze);
        o.addProperty("atmosphere.grade", grade);
        o.addProperty("atmosphere.vignette", vignette);
        ConfigStore.write(o);
    }

    // ------------------------------------------------------------ параметры для шейдера

    public record VisualState(int mode, float[] skyTop, float[] skyHorizon,
                              float[] fogCool, float[] fogWarm, float[] shadow, float[] sunColor, float[] sunDirection,
                              float density, float scatterHeight, float godRays, float softness,
                              float amount, float speed, float haze, float grade, float vignette) {}

    public VisualState parameters(long worldTime) {
        if (mode == 0) {
            int argb = dawnColor();
            float[] warm = {(argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f};
            return new VisualState(0,
                    mixLms(new float[]{.16f, .19f, .38f}, warm, .14f),
                    scaled(warm, 1.12f, .88f, .62f), new float[]{.56f, .62f, .8f},
                    mixLms(warm, new float[]{.95f, .55f, .63f}, .42f),
                    new float[]{.6f, .67f, .82f}, scaled(warm, 1.08f, .94f, .72f),
                    sunDirection(worldTime, .11f),
                    clamp(density, .05f, .8f), scatterHeight,
                    percent(godRays), percent(softness), 0, 1, 0, 0, 0);
        }
        int p = mode - 1;
        return new VisualState(mode,
                AtmospherePalettes.rgb(p, 4), AtmospherePalettes.rgb(p, 0x10),
                AtmospherePalettes.rgb(p, 0x28), AtmospherePalettes.rgb(p, 0x34),
                AtmospherePalettes.rgb(p, 0x40), AtmospherePalettes.rgb(p, 0x4c),
                sunDirection(worldTime, AtmospherePalettes.value(p, 0x88)),
                0, 76, AtmospherePalettes.sunEnabled(p) ? AtmospherePalettes.value(p, 0x68) : 0, .35f,
                percent(amount), speed / 100f,
                percent(haze) * AtmospherePalettes.value(p, 0x8c) * 2, percent(grade), percent(vignette));
    }

    /** Солнце на фиксированной высоте, сторона (восток/запад) зависит от времени суток. */
    public static float[] sunDirection(long worldTime, float elevation) {
        double cycle = Math.floorMod(worldTime, 24000L) / 24000.0 - .25;
        cycle -= Math.floor(cycle);
        double angle = (2 * cycle + .5 - Math.cos(cycle * Math.PI) * .5) / 3 * 2 * Math.PI;
        float sign = -(float) Math.sin((float) angle) < 0 ? -1 : 1;
        return new float[]{sign * (float) Math.cos(elevation), (float) Math.sin(elevation), 0};
    }

    /** Смешивание в кубическом LMS (Oklab), результат — sRGB. */
    public static float[] mixLms(float[] a, float[] b, float weight) {
        float[] x = toLms(a), y = toLms(b);
        float t = clamp(weight, 0, 1);
        for (int i = 0; i < 3; i++) {
            float value = x[i] + (y[i] - x[i]) * t;
            x[i] = value * value * value;
        }
        return new float[]{
                srgb(4.0767417f * x[0] - 3.3077116f * x[1] + .23096994f * x[2]),
                srgb(-1.268438f * x[0] + 2.6097574f * x[1] - .34131938f * x[2]),
                srgb(-.0041960864f * x[0] - .70341861f * x[1] + 1.7076147f * x[2])};
    }

    private static float[] toLms(float[] c) {
        float r = linear(c[0]), g = linear(c[1]), b = linear(c[2]);
        return new float[]{
                (float) Math.cbrt(.41222147f * r + .53633255f * g + .051445995f * b),
                (float) Math.cbrt(.2119035f * r + .6806995f * g + .10739696f * b),
                (float) Math.cbrt(.08830246f * r + .28171885f * g + .6299787f * b)};
    }

    private static float linear(float v) {
        return v <= .04045f ? v / 12.92f : (float) Math.pow((v + .055f) / 1.055f, 2.4);
    }

    private static float srgb(float v) {
        v = clamp(v, 0, 1);
        return v <= .0031308f ? v * 12.92f : (float) (1.055 * Math.pow(v, 1 / 2.4) - .055);
    }

    private static float[] scaled(float[] a, float r, float g, float b) {
        return new float[]{clamp(a[0] * r, 0, 1), clamp(a[1] * g, 0, 1), clamp(a[2] * b, 0, 1)};
    }

    private static float percent(float v) { return clamp(v / 100f, 0, 1); }

    public static float clamp(float v, float low, float high) {
        return Float.isNaN(v) ? low : Math.max(low, Math.min(high, v));
    }
}
