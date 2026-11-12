package nayomy.visuals.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import nayomy.visuals.module.EffectsModule;
import nayomy.visuals.render.Fonts;
import nayomy.visuals.render.GlassRenderer;
import nayomy.visuals.render.ShapeRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EffectsHud extends HudElement {

    public static final EffectsHud INSTANCE = new EffectsHud();

    // стекло: ровно те же значения, что у плашки Watermark (навбара)
    private static final int RADIUS = 5;
    private static final int BLUR = 8;
    private static final int TINT = 170;

    // позиция и размеры
    private static final int PAD = 6;
    private static final int HEADER_H = 15;
    private static final int ROW_H = 12;
    private static final int BOTTOM_PAD = 3;
    private static final int MIN_W = 92;
    private static final int EFFECT_ICON = 8;
    private static final int ICON_GAP = 4;      // иконка эффекта -> название
    private static final int NAME_GAP = 8;      // название -> таймер

    // размер текста (в GUI-пикселях); у Fonts.SIZE = 9, тут компактнее
    private static final float TEXT_SIZE = 7.5f;
    private static final float TITLE_SIZE = 8f;

    // msdf-иконки (шрифт icons): коды глифов
    private static final int GLYPH_FLASK = 117;   // 'u' — колба
    private static final int GLYPH_CLOCK = 1098;  // часы
    private static final float HEADER_ICON = 8f;

    // таймер-плашка
    private static final int PILL_H = 9;
    private static final int PILL_PAD = 4;
    private static final int CLOCK_W = 6;
    private static final int CLOCK_GAP = 2;

    // цвета
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT_ITEM = 0xFFB4B4BC;
    private static final int PILL_BG = 0x40000000;
    private static final int PILL_BORDER = 0x33FFFFFF;
    private static final int BAD = 0xFFE5484D;
    private static final int BAD_TEXT = 0xFFFF5A5F;
    private static final int BAD_BG = 0x40E5484D;
    private static final int BAD_GLOW = 0x30E5484D;

    // кэш названий эффектов (строка собирается один раз)
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    /** Образец для редактора сетки, когда на игроке нет эффектов (создаётся один раз). */
    private static List<MobEffectInstance> sample;

    private EffectsHud() {
        super("effects", "Effects");
    }

    @Override
    public boolean isActive() {
        return EffectsModule.INSTANCE.enabled;
    }

    /** Пока элемент не двигали: под Watermark, если он включён. */
    @Override
    protected int defaultY(int sw, int sh, int w, int h) {
        return WatermarkHud.bottomY() > 0 ? WatermarkHud.bottomY() + 6 : MARGIN;
    }

    private static List<MobEffectInstance> sampleEffects() {
        if (sample == null) {
            sample = List.of(
                    new MobEffectInstance(MobEffects.SPEED, 4800, 1),
                    new MobEffectInstance(MobEffects.REGENERATION, 1200),
                    new MobEffectInstance(MobEffects.POISON, 300)
            );
        }
        return sample;
    }

    @Override
    public Frame frame(Minecraft mc, float partialTick, boolean preview) {
        if (!EffectsModule.INSTANCE.enabled || mc.player == null) return null;

        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        if (effects.isEmpty()) {
            if (!preview) return null;
            effects = new ArrayList<>(sampleEffects());
        }

        // дольше всего остающиеся — сверху, бесконечные — самые первые
        effects.sort(Comparator.comparingInt(EffectsHud::sortKey).reversed());

        // размеры
        int n = effects.size();
        List<String> names = new ArrayList<>(n);
        List<String> times = new ArrayList<>(n);
        int maxName = 0;
        int maxTime = 0;
        for (MobEffectInstance e : effects) {
            String name = nameOf(e);
            String time = timeOf(e);
            names.add(name);
            times.add(time);
            maxName = Math.max(maxName, width(mc, name));
            maxTime = Math.max(maxTime, timeWidth(time));
        }
        int pillW = PILL_PAD + CLOCK_W + CLOCK_GAP + maxTime + PILL_PAD;
        int w = Math.max(MIN_W, PAD + EFFECT_ICON + ICON_GAP + maxName + NAME_GAP + pillW + PAD);
        int h = HEADER_H + n * ROW_H + BOTTOM_PAD;

        final int fw = w, fh = h, fPillW = pillW;
        final List<MobEffectInstance> fEffects = effects;
        return new Frame(fw, fh, (g, x, y) -> paint(g, mc, fEffects, names, times, x, y, fw, fh, fPillW));
    }

    private static void paint(GuiGraphics g, Minecraft mc, List<MobEffectInstance> effects,
                              List<String> names, List<String> times,
                              int x, int y, int w, int h, int pillW) {
        int n = effects.size();

        GlassRenderer.draw(g, x, y, w, h, RADIUS, BLUR, TINT, 0);

        // заголовок + колба справа
        Fonts.TITLE.draw(g, "Effects", x + PAD, textTop(y + HEADER_H / 2f, TITLE_SIZE), TITLE_SIZE, WHITE);
        Fonts.ICONS.drawIcon(g, GLYPH_FLASK, x + w - PAD - HEADER_ICON / 2f, y + HEADER_H / 2f, HEADER_ICON, WHITE);

        // строки
        for (int i = 0; i < n; i++) {
            MobEffectInstance e = effects.get(i);
            int ry = y + HEADER_H + i * ROW_H;
            float midY = ry + ROW_H / 2f;
            boolean bad = e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;

            // иконка эффекта
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()),
                    x + PAD, Math.round(midY - EFFECT_ICON / 2f), EFFECT_ICON, EFFECT_ICON);

            // название + уровень
            Fonts.BODY.draw(g, names.get(i), x + PAD + EFFECT_ICON + ICON_GAP,
                    textTop(midY, TEXT_SIZE), TEXT_SIZE, WHITE);

            // таймер
            int px = x + w - PAD - pillW;
            float py = midY - PILL_H / 2f;
            int fg = bad ? BAD_TEXT : TEXT_ITEM;
            if (bad) {
                ShapeRenderer.roundRectOutline(g, px - 0.6f, py - 0.6f, pillW + 1.2f, PILL_H + 1.2f,
                        3.5f, 1.6f, BAD_GLOW);
            }
            ShapeRenderer.roundRect(g, px, py, pillW, PILL_H, 3f, bad ? BAD_BG : PILL_BG);
            ShapeRenderer.roundRectOutline(g, px, py, pillW, PILL_H, 3f, 0.7f, bad ? BAD : PILL_BORDER);

            Fonts.ICONS.drawIcon(g, GLYPH_CLOCK, px + PILL_PAD + CLOCK_W / 2f, midY, CLOCK_W, fg);
            drawTime(g, times.get(i), px + PILL_PAD + CLOCK_W + CLOCK_GAP, textTop(midY, TEXT_SIZE), fg);
        }
    }

    private static int sortKey(MobEffectInstance e) {
        return e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration();
    }

    /** "Скорость", "Сила II" — уровень пишем римскими цифрами со второго. */
    private static String nameOf(MobEffectInstance e) {
        String key = e.getEffect().getRegisteredName() + "#" + e.getAmplifier();
        String cached = NAME_CACHE.get(key);
        if (cached != null) return cached;

        String name = e.getEffect().value().getDisplayName().getString();
        if (e.getAmplifier() >= 1) name += " " + roman(e.getAmplifier() + 1);
        NAME_CACHE.put(key, name);
        return name;
    }

    private static String timeOf(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "**:**";
        int total = Math.max(0, e.getDuration() / 20);
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        StringBuilder sb = new StringBuilder(8);
        if (hours > 0) sb.append(hours).append(':');
        if (hours > 0 || minutes >= 10) sb.append(minutes);
        else sb.append('0').append(minutes);
        sb.append(':');
        if (seconds < 10) sb.append('0');
        return sb.append(seconds).toString();
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : String.valueOf(n);
    }

    // Цифры в Onest пропорциональные («1» уже «0»), поэтому ширина таймера прыгала каждую секунду
    // и плашка то росла, то сжималась. Цифры рисуем в ячейках одинаковой ширины.
    private static float digitW = -1f;

    private static float digitWidth() {
        if (digitW < 0f) {
            float m = 0f;
            for (char c = '0'; c <= '9'; c++) m = Math.max(m, Fonts.BODY.width(String.valueOf(c), TEXT_SIZE));
            if (m <= 0f) return 5f;   // шрифт ещё не загрузился — не запоминаем
            digitW = m;
        }
        return digitW;
    }

    private static int timeWidth(String s) {
        float w = 0f;
        float dw = digitWidth();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += Character.isDigit(c) ? dw : Fonts.BODY.width(String.valueOf(c), TEXT_SIZE);
        }
        return (int) Math.ceil(w);
    }

    private static void drawTime(GuiGraphics g, String s, float x, float top, int color) {
        float dw = digitWidth();
        float pen = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float cw = Fonts.BODY.width(ch, TEXT_SIZE);
            if (Character.isDigit(ch.charAt(0))) {
                Fonts.BODY.draw(g, ch, pen + (dw - cw) / 2f, top, TEXT_SIZE, color);
                pen += dw;
            } else {
                Fonts.BODY.draw(g, ch, pen, top, TEXT_SIZE, color);
                pen += cw;
            }
        }
    }

    private static int width(Minecraft mc, String s) {
        return Fonts.BODY.widthInt(s, TEXT_SIZE);
    }

    /** Верх строки текста так, чтобы он стоял по центру midY (с той же подстройкой +1 при размере 9). */
    private static float textTop(float midY, float size) {
        return midY - size / 2f + size / 9f;
    }
}