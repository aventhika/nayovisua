package nayomy.visuals.hud;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import nayomy.visuals.NayomyVisuals;
import nayomy.visuals.module.ConfigStore;

/**
 * Элемент HUD на экране (Watermark, Effects, GPS...) с настраиваемым положением.
 *
 * Положение хранится не в абсолютных пикселях, а как «якорь + отступ»: у левого / по центру / у правого края
 * (и так же по вертикали). Поэтому при смене размера окна или GUI-масштаба, а также когда у элемента
 * меняется ширина, он остаётся на своём месте: то, что стояло по центру, по центру и остаётся.
 */
public abstract class HudElement {

    /** Минимальный отступ от краёв экрана (GUI-пиксели). */
    public static final int MARGIN = 6;

    /** Нарисовать элемент так, чтобы его левый верхний угол оказался в (x, y). */
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphics g, int x, int y);
    }

    /** Подготовленный к рисованию элемент: размер + способ нарисовать. */
    public record Frame(int w, int h, Painter painter) {}

    public final String id;
    public final String title;

    // якорь: -1 = слева/сверху, 0 = по центру, 1 = справа/снизу
    private int ax = -1, ay = -1;
    private int ox = MARGIN, oy = MARGIN;
    /** false — элемент ни разу не двигали, стоит на стандартном месте (см. defaultX / defaultY). */
    private boolean custom = false;

    protected HudElement(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /** Включён ли элемент (и показывается ли он сейчас на экране). */
    public abstract boolean isActive();

    /**
     * Размер и рисовалка. null — рисовать нечего.
     * preview = true: вызов из редактора; элемент без данных (например, Effects без эффектов)
     * должен показать образец, чтобы его можно было двигать.
     */
    public abstract Frame frame(Minecraft mc, float partialTick, boolean preview);

    /** Стандартное положение (пока элемент не двигали). */
    protected int defaultX(int sw, int sh, int w, int h) {
        return MARGIN;
    }

    protected int defaultY(int sw, int sh, int w, int h) {
        return MARGIN;
    }

    // ----------------------------------------------------------- положение

    public final int x(int sw, int sh, int w, int h) {
        if (!custom) return defaultX(sw, sh, w, h);
        return switch (ax) {
            case -1 -> ox;
            case 1 -> sw - w - ox;
            default -> (sw - w) / 2 + ox;
        };
    }

    public final int y(int sw, int sh, int w, int h) {
        if (!custom) return defaultY(sw, sh, w, h);
        return switch (ay) {
            case -1 -> oy;
            case 1 -> sh - h - oy;
            default -> (sh - h) / 2 + oy;
        };
    }

    /** Запоминает абсолютное положение левого верхнего угла (якорь выбирается по трети экрана). */
    public final void setPosition(int x, int y, int w, int h, int sw, int sh) {
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        ax = cx < sw / 3.0 ? -1 : cx > sw * 2 / 3.0 ? 1 : 0;
        ay = cy < sh / 3.0 ? -1 : cy > sh * 2 / 3.0 ? 1 : 0;
        ox = switch (ax) {
            case -1 -> x;
            case 1 -> sw - w - x;
            default -> x - (sw - w) / 2;
        };
        oy = switch (ay) {
            case -1 -> y;
            case 1 -> sh - h - y;
            default -> y - (sh - h) / 2;
        };
        custom = true;
    }

    /** Вернуть на стандартное место. */
    public final void resetPosition() {
        custom = false;
    }

    // ----------------------------------------------------------- конфиг

    public final void load(JsonObject o) {
        String k = "hud." + id + ".";
        custom = ConfigStore.bool(o, k + "custom", false);
        ax = Math.max(-1, Math.min(1, ConfigStore.integer(o, k + "ax", -1)));
        ay = Math.max(-1, Math.min(1, ConfigStore.integer(o, k + "ay", -1)));
        ox = ConfigStore.integer(o, k + "ox", MARGIN);
        oy = ConfigStore.integer(o, k + "oy", MARGIN);
    }

    public final void save(JsonObject o) {
        String k = "hud." + id + ".";
        o.addProperty(k + "custom", custom);
        o.addProperty(k + "ax", ax);
        o.addProperty(k + "ay", ay);
        o.addProperty(k + "ox", ox);
        o.addProperty(k + "oy", oy);
    }

    // ----------------------------------------------------------- рисование в игре

    public final void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                NayomyVisuals.id(id),
                this::renderInGame
        );
    }

    private void renderInGame(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (!isActive()) return;
        // в редакторе сетки элементы рисует сам редактор
        if (HudManager.isEditing()) return;

        Frame f = frame(mc, delta.getGameTimeDeltaPartialTick(false), false);
        if (f == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        f.painter().paint(g, x(sw, sh, f.w(), f.h()), y(sw, sh, f.w(), f.h()));
    }
}
