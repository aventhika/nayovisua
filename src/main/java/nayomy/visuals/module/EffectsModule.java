package nayomy.visuals.module;

import com.google.gson.JsonObject;

/** Настройки Effects. */
public final class EffectsModule {

    public static final EffectsModule INSTANCE = new EffectsModule();

    /** По умолчанию выключен. */
    public boolean enabled = false;

    private EffectsModule() {}

    public void load() {
        enabled = ConfigStore.bool(ConfigStore.read(), "effects.enabled", enabled);
    }

    public void save() {
        JsonObject o = ConfigStore.read();
        o.addProperty("effects.enabled", enabled);
        ConfigStore.write(o);
    }
}