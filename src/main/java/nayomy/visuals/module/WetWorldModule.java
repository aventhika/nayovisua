package nayomy.visuals.module;

import com.google.gson.JsonObject;

/**
 * Wet World: мокрые блоки с отражениями в экранном пространстве (SSR) и рябью.
 * Здесь только настройки; рендер — см. render/WetWorldPipeline.
 */
public final class WetWorldModule {

    public static final WetWorldModule INSTANCE = new WetWorldModule();

    public static final String[] QUALITIES = {"Низкое", "Среднее", "Высокое", "Ультра"};

    /** Параметры трассировки по качеству: шаги, бинарное уточнение, дальность (блоки), длина шага. */
    private static final int[] STEPS = {24, 40, 64, 96};
    private static final int[] BINARY = {3, 4, 6, 8};
    private static final float[] MAX_DIST = {24f, 32f, 48f, 64f};
    private static final float[] STEP_SIZE = {.32f, .22f, .16f, .12f};

    /** По умолчанию выключен. */
    public boolean enabled = false;
    public int quality = 2;               // 0 .. 3
    public float reflection = .75f;       // .1 .. 1.5
    public float darkening = .30f;        // 0 .. .8
    public boolean ripples = true;
    public float rippleSpeed = 1f;        // .1 .. 3
    public boolean rainOnly = false;

    private WetWorldModule() {}

    public void cycleQuality() { quality = (quality + 1) % QUALITIES.length; save(); }

    public int steps() { return STEPS[quality]; }
    public int binarySteps() { return BINARY[quality]; }
    public float maxDist() { return MAX_DIST[quality]; }
    public float stepSize() { return STEP_SIZE[quality]; }

    public void load() {
        JsonObject o = ConfigStore.read();
        enabled = ConfigStore.bool(o, "wetworld.enabled", enabled);
        quality = Math.floorMod(ConfigStore.integer(o, "wetworld.quality", quality), QUALITIES.length);
        reflection = clamp(ConfigStore.num(o, "wetworld.reflection", reflection), .1f, 1.5f);
        darkening = clamp(ConfigStore.num(o, "wetworld.darkening", darkening), 0f, .8f);
        ripples = ConfigStore.bool(o, "wetworld.ripples", ripples);
        rippleSpeed = clamp(ConfigStore.num(o, "wetworld.rippleSpeed", rippleSpeed), .1f, 3f);
        rainOnly = ConfigStore.bool(o, "wetworld.rainOnly", rainOnly);
    }

    public void save() {
        JsonObject o = ConfigStore.read();
        o.addProperty("wetworld.enabled", enabled);
        o.addProperty("wetworld.quality", quality);
        o.addProperty("wetworld.reflection", reflection);
        o.addProperty("wetworld.darkening", darkening);
        o.addProperty("wetworld.ripples", ripples);
        o.addProperty("wetworld.rippleSpeed", rippleSpeed);
        o.addProperty("wetworld.rainOnly", rainOnly);
        ConfigStore.write(o);
    }

    private static float clamp(float v, float low, float high) {
        return Float.isNaN(v) ? low : Math.max(low, Math.min(high, v));
    }
}
