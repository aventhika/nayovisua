package nayomy.visuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import nayomy.visuals.module.GpsModule;
import nayomy.visuals.render.Fonts;
import nayomy.visuals.render.GlassRenderer;

/** Стрелка-GPS с размытым свечением и плашкой расстояния. */
public final class GpsHud extends HudElement {

    public static final GpsHud INSTANCE = new GpsHud();

    // положение на экране: по центру, на Y_OFFSET пикселей выше центра экрана
    private static final int Y_OFFSET = 52;

    // стрелка
    private static final int GLYPH_ARROW = 1065;      // навигатор из msdf/icons.json (смотрит на СВ)
    private static final float ARROW_BASE_DEG = 45f;   // куда глиф смотрит по умолчанию (по часовой от «вверх»)
    private static final float ARROW_SIZE = 16f;
    private static final int ARROW_COLOR = 0xFFB07CFF;    // фиолетовый, как на примере
    private static final int GLOW_COLOR = 0xB07CFF;       // rgb свечения (альфа добавляется ниже)

    // плашка с расстоянием (то же стекло, что у Watermark)
    private static final int PILL_H = 13;
    private static final int PILL_PAD = 6;
    private static final int PILL_RADIUS = 5;
    private static final int BLUR = 8;
    private static final int TINT = 170;
    private static final float TEXT_SIZE = 8f;
    private static final int WHITE = 0xFFFFFFFF;

    /** На каком расстоянии считаем, что игрок дошёл (GPS сам выключается). */
    private static final double ARRIVE_DIST = 2.5;

    // рамка элемента в редакторе: квадрат под стрелку + плашка под ним
    private static final int ARROW_BOX = 24;
    private static final int ARROW_TO_PILL = 8;   // от низа стрелки до плашки

    private GpsHud() {
        super("gps", "GPS");
    }

    @Override
    public boolean isActive() {
        return GpsModule.INSTANCE.active;
    }

    /** Стандартное место: по центру, стрелка на Y_OFFSET выше центра экрана. */
    @Override
    protected int defaultX(int sw, int sh, int w, int h) {
        return (sw - w) / 2;
    }

    @Override
    protected int defaultY(int sw, int sh, int w, int h) {
        return sh / 2 - Y_OFFSET - ARROW_BOX / 2;
    }

    @Override
    public Frame frame(Minecraft mc, float pt, boolean preview) {
        GpsModule gps = GpsModule.INSTANCE;
        if (!gps.active || mc.player == null || mc.level == null) return null;
        double px = Mth.lerp(pt, mc.player.xo, mc.player.getX());
        double py = Mth.lerp(pt, mc.player.yo, mc.player.getY());
        double pz = Mth.lerp(pt, mc.player.zo, mc.player.getZ());

        double dx = gps.x - px;
        double dz = gps.z - pz;
        double dist = gps.hasY
                ? Math.sqrt(dx * dx + (gps.y - py) * (gps.y - py) + dz * dz)
                : Math.sqrt(dx * dx + dz * dz);

        if (dist <= ARRIVE_DIST) {
            gps.clear();
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aGPS: вы прибыли"), false);
            return null;
        }

        // yaw игрока: 0 = юг (+Z), растёт по часовой сверху (юг -> запад).
        // yaw на цель считается так же: atan2(-dx, dz).
        float playerYaw = Mth.lerp(pt, mc.player.yRotO, mc.player.getYRot());
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees((float) (targetYaw - playerYaw));   // >0 — цель справа

        // на экране поворот по часовой на relative; глиф сам смотрит на ARROW_BASE_DEG
        float angle = (float) Math.toRadians(relative - ARROW_BASE_DEG);

        String text = distanceText(dist);
        int tw = Fonts.BODY.widthInt(text, TEXT_SIZE);
        int pw = tw + PILL_PAD * 2;

        int w = Math.max(ARROW_BOX, pw);
        int h = ARROW_BOX / 2 + ARROW_SIZE_INT / 2 + ARROW_TO_PILL + PILL_H;

        return new Frame(w, h, (g, x, y) -> paint(g, x, y, w, angle, text, pw));
    }

    private static final int ARROW_SIZE_INT = (int) ARROW_SIZE;

    private static void paint(GuiGraphics g, int x, int y, int w, float angle, String text, int pw) {
        // центр стрелки: по центру рамки, в её верхнем квадрате
        float cx = x + w / 2f;
        float cy = y + ARROW_BOX / 2f;

        // свечение: несколько увеличенных полупрозрачных копий = мягкий «блюр»
        for (int i = 4; i >= 1; i--) {
            int a = 22 + (5 - i) * 6;                 // дальше от стрелки — прозрачнее
            float s = ARROW_SIZE + i * 4.5f;
            Fonts.ICONS.drawIconRotated(g, GLYPH_ARROW, cx, cy, s, angle, (a << 24) | GLOW_COLOR);
        }
        Fonts.ICONS.drawIconRotated(g, GLYPH_ARROW, cx, cy, ARROW_SIZE, angle, ARROW_COLOR);

        // плашка с расстоянием под стрелкой
        int pillX = Math.round(cx - pw / 2f);
        int pillY = Math.round(cy + ARROW_SIZE / 2f + ARROW_TO_PILL);

        GlassRenderer.draw(g, pillX, pillY, pw, PILL_H, PILL_RADIUS, BLUR, TINT, 0);
        float textTop = pillY + PILL_H / 2f - TEXT_SIZE / 2f + TEXT_SIZE / 9f;
        Fonts.BODY.draw(g, text, pillX + PILL_PAD, textTop, TEXT_SIZE, WHITE);
    }

    private static String distanceText(double d) {
        if (d >= 1000) return String.format("%.1fkm", d / 1000.0);
        return Math.round(d) + "m";
    }
}