package nayomy.visuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import nayomy.visuals.module.WatermarkModule;
import nayomy.visuals.render.Fonts;
import nayomy.visuals.render.GlassRenderer;
import nayomy.visuals.render.LogoRenderer;
import nayomy.visuals.render.ShapeRenderer;

import java.util.ArrayList;
import java.util.List;

public final class WatermarkHud extends HudElement {

    public static final WatermarkHud INSTANCE = new WatermarkHud();

    // позиция и размеры
    private static final int HEIGHT = 14;
    private static final int RADIUS = 4;
    private static final int PAD = 5;
    private static final int ICON_W = 7;
    private static final int ICON_GAP = 3;
    private static final int SEG_GAP = 7;
    private static final float LOGO_H = 9f;       // высота логотипа
    private static final int LOGO_W = (int) Math.ceil(LogoRenderer.widthFor(LOGO_H));
    private static final float TEXT_SIZE = 8f;    // размер текста (у остального GUI — 9)

    // стекло
    private static final int BLUR = 8;
    private static final int TINT = 170;

    // цвета
    private static final int WHITE = 0xFFFFFFFF;
    private static final int ICON = 0xFFB4B4BC;
    private static final int ACCENT = 0xFFF0701E;
    private static final int STATUS_RED = 0xFFE5484D;
    private static final int DIVIDER = 0x33FFFFFF;

    // заглушки (потом заменишь на реальные данные)
    private static final String VISUAL_NAME = "";
    private static final String ACCOUNT_NAME = "nayomy";
    private static final String STATUS = "Newgen";

    /** Иконки сегментов: коды глифов из msdf-шрифта icons (msdf/icons.json). */
    private enum Icon {
        USER(52),      // профиль
        CROWN(101),    // корона
        FPS(106),      // монитор
        PING(1053),    // молния
        SERVER(1091);  // сервер

        final int glyph;

        Icon(int glyph) { this.glyph = glyph; }
    }

    private static final float ICON_SIZE = 6.5f;

    /** counter = true: перед text рисуется анимированное число FPS (см. FpsCounter). */
    private record Seg(Icon icon, String text, int color, boolean counter) {
        Seg(Icon icon, String text, int color) { this(icon, text, color, false); }
    }

    private static final int FPS_GAP = 2;   // между числом и подписью «fps»

    /**
     * Анимированный счётчик FPS: когда число меняется, изменившиеся цифры «прокручиваются»
     * (старая уезжает и гаснет, новая выезжает), а ширина плавно подстраивается под количество цифр.
     * Цифры стоят в ячейках одинаковой ширины, поэтому плашка не дёргается.
     */
    private static final class FpsCounter {
        static final int SLOTS = 4;                    // до 9999 fps
        static final float DUR = 0.32f;                // секунд на прокрутку цифры
        static final float WIDTH_SPEED = 14f;          // скорость подстройки ширины

        static final char[] cur = new char[SLOTS];
        static final char[] prev = new char[SLOTS];
        static final long[] start = new long[SLOTS];
        static int target = -1;
        static int dir = 1;           // 1 — число выросло (цифры едут вверх), -1 — упало
        static float animW = -1f;     // текущая ширина числа
        static long lastNs = 0L;

        static float digitW = -1f;

        /** Ширина ячейки цифры = самая широкая цифра (в Onest цифры пропорциональные). */
        static float digitWidth() {
            if (digitW < 0f) {
                float m = 0f;
                for (char c = '0'; c <= '9'; c++) m = Math.max(m, Fonts.BODY.width(String.valueOf(c), TEXT_SIZE));
                if (m <= 0f) return 5f;   // шрифт ещё не загрузился — не запоминаем
                digitW = m;
            }
            return digitW;
        }

        static void update(int fps) {
            long now = System.nanoTime();
            String s = String.valueOf(Math.max(0, Math.min(9999, fps)));
            float dw = digitWidth();
            float tw = s.length() * dw;

            boolean stale = target < 0 || now - lastNs > 2_000_000_000L;   // первый показ / долго не рисовали
            if (stale) {
                for (int i = 0; i < SLOTS; i++) {
                    int idx = s.length() - 1 - i;
                    cur[i] = idx >= 0 ? s.charAt(idx) : ' ';
                    prev[i] = cur[i];
                    start[i] = 0L;
                }
                animW = tw;
            } else {
                if (fps != target) {
                    dir = fps > target ? 1 : -1;
                    for (int i = 0; i < SLOTS; i++) {
                        int idx = s.length() - 1 - i;
                        char nc = idx >= 0 ? s.charAt(idx) : ' ';
                        if (nc != cur[i]) {
                            prev[i] = cur[i];
                            cur[i] = nc;
                            start[i] = now;
                        }
                    }
                }
                float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
                animW += (tw - animW) * (1f - (float) Math.exp(-dt * WIDTH_SPEED));
                if (Math.abs(tw - animW) < 0.05f) animW = tw;
            }
            target = fps;
            lastNs = now;
        }

        static int width() {
            return Math.round(animW);
        }

        /** Рисует число; x — левый край области числа, textY — верх строки текста. */
        static void draw(GuiGraphics g, float x, float textY, int y, int color) {
            float dw = digitWidth();
            float sufX = x + animW;
            long now = System.nanoTime();
            float slide = TEXT_SIZE * 0.85f;

            // всё, что выезжает за область числа и за плашку, обрезается
            g.enableScissor((int) Math.floor(x), y + 1, (int) Math.ceil(sufX), y + HEIGHT - 1);
            for (int i = 0; i < SLOTS; i++) {
                float cellX = sufX - (i + 1) * dw;
                float p = start[i] == 0L ? 1f : Math.min(1f, (now - start[i]) / 1e9f / DUR);
                float e = 1f - (1f - p) * (1f - p) * (1f - p);   // easeOutCubic
                if (p < 1f) {
                    if (prev[i] != ' ') drawDigit(g, prev[i], cellX, dw, textY - dir * slide * e, color, 1f - e);
                    if (cur[i] != ' ') drawDigit(g, cur[i], cellX, dw, textY + dir * slide * (1f - e), color, e);
                } else if (cur[i] != ' ') {
                    drawDigit(g, cur[i], cellX, dw, textY, color, 1f);
                }
            }
            g.disableScissor();
        }

        private static void drawDigit(GuiGraphics g, char c, float cellX, float dw, float ty, int color, float alpha) {
            int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
            if (a <= 2) return;
            String ch = String.valueOf(c);
            float w = Fonts.BODY.width(ch, TEXT_SIZE);
            Fonts.BODY.draw(g, ch, cellX + (dw - w) / 2f, ty, TEXT_SIZE, (a << 24) | (color & 0xFFFFFF));
        }
    }

    private WatermarkHud() {
        super("watermark", "Watermark");
    }

    @Override
    public boolean isActive() {
        return WatermarkModule.INSTANCE.enabled;
    }

    /** Нижняя граница плашки (0, если Watermark выключен) — под ней ставятся другие элементы. */
    public static int bottomY() {
        return WatermarkModule.INSTANCE.enabled ? MARGIN + HEIGHT : 0;
    }

    @Override
    public Frame frame(Minecraft mc, float partialTick, boolean preview) {
        WatermarkModule m = WatermarkModule.INSTANCE;
        if (!m.enabled || mc.player == null || mc.level == null) return null;

        // сегменты: название и статус — всегда, остальное по настройкам
        List<Seg> segs = new ArrayList<>();
        segs.add(new Seg(Icon.USER, ACCOUNT_NAME, WHITE));
        segs.add(new Seg(Icon.CROWN, STATUS, STATUS_RED));
        if (m.showFps) {
            FpsCounter.update(mc.getFps());
            segs.add(new Seg(Icon.FPS, "fps", WHITE, true));
        }
        if (m.showPing) segs.add(new Seg(Icon.PING, ping(mc) + " ping", WHITE));
        if (m.showServer) segs.add(new Seg(Icon.SERVER, serverText(mc), WHITE));

        // ширина
        int total = PAD + LOGO_W + 4 + width(mc, VISUAL_NAME) + 6 + 1 + 6;
        for (int i = 0; i < segs.size(); i++) {
            if (i > 0) total += SEG_GAP;
            total += ICON_W + ICON_GAP + segWidth(mc, segs.get(i));
        }
        total += PAD;

        final int fullW = total;
        return new Frame(fullW, HEIGHT, (g, x, y) -> paint(g, mc, segs, x, y, fullW));
    }

    private static void paint(GuiGraphics g, Minecraft mc, List<Seg> segs, int x, int y, int total) {
        float textY = y + (HEIGHT - TEXT_SIZE) / 2f + TEXT_SIZE / 9f;
        float midY = y + HEIGHT / 2f;

        GlassRenderer.draw(g, x, y, total, HEIGHT, RADIUS, BLUR, TINT, 0);

        // логотип + название визуала
        int cx = x + PAD;
        LogoRenderer.draw(g, cx + LOGO_W / 2f, midY, LOGO_H, WHITE);
        cx += LOGO_W + 4;
        text(g, mc, VISUAL_NAME, cx, textY, WHITE);
        cx += width(mc, VISUAL_NAME) + 6;

        // divider
        ShapeRenderer.vLine(g, cx, y + 4, y + HEIGHT - 4, 0.6f, DIVIDER);
        cx += 1 + 6;

        // сегменты
        for (int i = 0; i < segs.size(); i++) {
            if (i > 0) cx += SEG_GAP;
            Seg s = segs.get(i);
            drawIcon(g, s.icon(), cx + ICON_W / 2f, midY, s.color() == STATUS_RED ? STATUS_RED : ICON);
            cx += ICON_W + ICON_GAP;
            if (s.counter()) {
                FpsCounter.draw(g, cx, textY, y, s.color());
                cx += FpsCounter.width() + FPS_GAP;
                text(g, mc, s.text(), cx, textY, s.color());
                cx += width(mc, s.text());
            } else {
                text(g, mc, s.text(), cx, textY, s.color());
                cx += width(mc, s.text());
            }
        }
    }

    private static int ping(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static String serverText(Minecraft mc) {
        ServerData sd = mc.getCurrentServer();
        if (sd == null || mc.hasSingleplayerServer()) return "Локальная игра";
        return sd.ip;
    }

    /** Ширина сегмента: у счётчика FPS — анимированная ширина числа + «fps». */
    private static int segWidth(Minecraft mc, Seg s) {
        if (s.counter()) return FpsCounter.width() + FPS_GAP + width(mc, s.text());
        return width(mc, s.text());
    }

    private static int width(Minecraft mc, String s) {
        return Fonts.BODY.widthInt(s, TEXT_SIZE);
    }

    private static void text(GuiGraphics g, Minecraft mc, String s, int x, float y, int color) {
        Fonts.BODY.draw(g, s, x, y, TEXT_SIZE, color);
    }

    private static void drawIcon(GuiGraphics g, Icon icon, float cx, float cy, int c) {
        Fonts.ICONS.drawIcon(g, icon.glyph, cx, cy, ICON_SIZE, c);
    }
}