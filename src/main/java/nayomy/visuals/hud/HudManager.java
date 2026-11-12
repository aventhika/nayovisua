package nayomy.visuals.hud;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import nayomy.visuals.gui.GridEditorScreen;
import nayomy.visuals.module.ConfigStore;

import java.util.List;

/** Все элементы HUD, у которых можно менять положение. Новые элементы добавляй в ELEMENTS. */
public final class HudManager {

    /** Порядок = порядок рисования (последний — сверху). */
    public static final List<HudElement> ELEMENTS = List.of(
            WatermarkHud.INSTANCE,
            EffectsHud.INSTANCE,
            GpsHud.INSTANCE
    );

    private HudManager() {}

    public static void register() {
        for (HudElement e : ELEMENTS) e.register();
    }

    public static void load() {
        JsonObject o = ConfigStore.read();
        for (HudElement e : ELEMENTS) e.load(o);
    }

    public static void save() {
        JsonObject o = ConfigStore.read();
        for (HudElement e : ELEMENTS) e.save(o);
        ConfigStore.write(o);
    }

    /** Открыт ли сейчас редактор сетки. */
    public static boolean isEditing() {
        return Minecraft.getInstance().screen instanceof GridEditorScreen;
    }
}
