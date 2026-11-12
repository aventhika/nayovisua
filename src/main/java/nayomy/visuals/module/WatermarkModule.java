package nayomy.visuals.module;

import com.google.gson.JsonObject;

/** Настройки Watermark. */
public final class WatermarkModule {

    public static final WatermarkModule INSTANCE = new WatermarkModule();

    /** По умолчанию выключен. */
    public boolean enabled = false;
    public boolean showFps = false;
    public boolean showPing = false;
    public boolean showServer = false;

    private WatermarkModule() {}

    public void load() {
        JsonObject o = ConfigStore.read();
        enabled = ConfigStore.bool(o, "watermark.enabled", enabled);
        showFps = ConfigStore.bool(o, "watermark.fps", showFps);
        showPing = ConfigStore.bool(o, "watermark.ping", showPing);
        showServer = ConfigStore.bool(o, "watermark.server", showServer);
    }

    public void save() {
        JsonObject o = ConfigStore.read();
        o.addProperty("watermark.enabled", enabled);
        o.addProperty("watermark.fps", showFps);
        o.addProperty("watermark.ping", showPing);
        o.addProperty("watermark.server", showServer);
        ConfigStore.write(o);
    }
}