package nayomy.visuals.gui;

import nayomy.visuals.hud.HudElement;
import nayomy.visuals.hud.HudManager;
import nayomy.visuals.render.Fonts;
import nayomy.visuals.render.ShapeRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Grid Redactor: мелкая сетка на весь экран, на которой можно перетаскивать все включённые
 * элементы HUD (Watermark, Effects, GPS...).
 *
 * <ul>
 *   <li>Обычное перетаскивание прилипает к сетке.</li>
 *   <li>Ctrl — двигать как угодно, без сетки.</li>
 *   <li>Если элемент по центру экрана (по горизонтали и/или вертикали) — он «примагничивается»
 *       к центру и появляется отдельная яркая линия.</li>
 *   <li>От краёв экрана всегда остаётся минимальный отступ ({@link HudElement#MARGIN}).</li>
 *   <li>R — вернуть элемент под курсором на стандартное место, Esc — назад в ClickGui.</li>
 * </ul>
 */
public class GridEditorScreen extends Screen {

    // сетка (GUI-пиксели)
    private static final int GRID = 3;
    private static final int MAJOR_EVERY = 4;           // каждая 4-я линия чуть ярче
    private static final int MARGIN = HudElement.MARGIN;

    // насколько близко к центру элемент «прилипает» к нему
    private static final double CENTER_SNAP_GRID = 3.0;
    private static final double CENTER_SNAP_FREE = 1.5;
    // сколько надо протянуть мышь, чтобы клик стал перетаскиванием
    private static final double DRAG_START = 3.0;

    private static final float FADE_MS = 180f;

    // цвета (в тон ClickGui)
    private static final int DIM = 0x73000000;
    private static final int GRID_MINOR = 0x0EFFFFFF;
    private static final int GRID_MAJOR = 0x1EFFFFFF;
    private static final int SAFE_AREA = 0x22FFFFFF;
    private static final int GUIDE = 0xE0F0701E;         // линии центровки: акцентный оранжевый
    private static final int ACCENT = 0xFFF0701E;
    private static final int OUTLINE_IDLE = 0x44FFFFFF;
    private static final int OUTLINE_HOVER = 0xAAFFFFFF;
    private static final int CHIP_BG = 0xE6101012;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_ITEM = 0xFF9C9CA4;
    private static final int TEXT_DIM = 0xFF64646C;

    private static final float CHIP_TEXT = 7.5f;
    private static final int CHIP_H = 11;

    private record Item(HudElement el, HudElement.Frame frame, int x, int y) {
        boolean contains(double mx, double my) {
            return mx >= x - 2 && mx < x + frame.w() + 2 && my >= y - 2 && my < y + frame.h() + 2;
        }
    }

    private final ClickGui parent;
    private final long openNs = System.nanoTime();

    /** Элементы, нарисованные в этом кадре (в порядке рисования). */
    private final List<Item> items = new ArrayList<>();

    // перетаскивание
    private HudElement dragEl = null;
    private boolean dragMoved = false;
    private double pressX, pressY;       // где нажали
    private double grabDx, grabDy;       // смещение курсора от левого верхнего угла элемента
    private double mouseX, mouseY;       // последняя позиция мыши при перетаскивании
    private int hoverX = -1, hoverY = -1;

    // линии центровки в этом кадре
    private boolean guideV = false, guideH = false;

    public GridEditorScreen(ClickGui parent) {
        super(Component.literal("Grid Redactor"));
        this.parent = parent;
    }

    // ---------------------------------------------------------------- экран

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ни размытия, ни ванильного затемнения: фон рисуем сами
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Esc: сохраняем и возвращаемся в ClickGui (с его анимацией открытия)
        HudManager.save();
        parent.reopen();
        this.minecraft.setScreen(parent);
    }

    // ---------------------------------------------------------------- рисование

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        float t = Math.min(1f, (System.nanoTime() - openNs) / 1_000_000f / FADE_MS);
        float p = 1f - (1f - t) * (1f - t) * (1f - t);

        int sw = this.width;
        int sh = this.height;
        hoverX = mx;
        hoverY = my;

        // затемнение + сетка + граница минимальных отступов
        g.fill(0, 0, sw, sh, withAlpha(DIM, p));
        drawGrid(g, sw, sh, p);
        int safe = withAlpha(SAFE_AREA, p);
        ShapeRenderer.hLine(g, MARGIN, sw - MARGIN, MARGIN, 0.5f, safe);
        ShapeRenderer.hLine(g, MARGIN, sw - MARGIN, sh - MARGIN, 0.5f, safe);
        ShapeRenderer.vLine(g, MARGIN, MARGIN, sh - MARGIN, 0.5f, safe);
        ShapeRenderer.vLine(g, sw - MARGIN, MARGIN, sh - MARGIN, 0.5f, safe);

        layout(sw, sh);

        // линии центровки: под элементами
        if (guideV) ShapeRenderer.vLine(g, sw / 2f, 0, sh, 0.8f, GUIDE);
        if (guideH) ShapeRenderer.hLine(g, 0, sw, sh / 2f, 0.8f, GUIDE);

        // сами элементы — теми же методами, что и в игре
        for (Item it : items) {
            it.frame().painter().paint(g, it.x(), it.y());
        }

        // рамки и подписи
        Item hover = dragEl != null ? find(dragEl) : hit(mx, my);
        for (Item it : items) {
            boolean dragging = it.el() == dragEl;
            boolean hovered = it == hover;
            int color = dragging ? ACCENT : hovered ? OUTLINE_HOVER : OUTLINE_IDLE;
            ShapeRenderer.roundRectOutline(g, it.x() - 2, it.y() - 2, it.frame().w() + 4, it.frame().h() + 4,
                    6f, 0.8f, color);
        }
        if (hover != null) drawChip(g, hover, sw, sh);

        if (items.isEmpty()) drawEmptyHint(g, sw, sh, p);
        drawHint(g, sw, sh, p);

        super.render(g, mx, my, partialTick);
    }

    /** Собирает кадр: размеры элементов, применяет перетаскивание, считает линии центровки. */
    private void layout(int sw, int sh) {
        items.clear();
        guideV = false;
        guideH = false;

        float pt = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean dragSeen = false;

        for (HudElement el : HudManager.ELEMENTS) {
            if (!el.isActive()) continue;
            HudElement.Frame f = el.frame(this.minecraft, pt, true);
            if (f == null) continue;

            if (el == dragEl) {
                dragSeen = true;
                if (dragMoved) applyDrag(el, f, sw, sh);
            }
            items.add(new Item(el, f, el.x(sw, sh, f.w(), f.h()), el.y(sw, sh, f.w(), f.h())));
        }

        // элемент, который тащили, пропал (например, GPS дошёл до цели)
        if (dragEl != null && !dragSeen) {
            dragEl = null;
            dragMoved = false;
        }
    }

    /**
     * Новое положение перетаскиваемого элемента.
     * Обычно: прилипание к сетке. С Ctrl: как угодно. В обоих случаях: отступ от краёв
     * не меньше MARGIN и магнит к центру экрана.
     */
    private void applyDrag(HudElement el, HudElement.Frame f, int sw, int sh) {
        boolean free = this.minecraft.hasControlDown();
        int w = f.w();
        int h = f.h();

        double x = mouseX - grabDx;
        double y = mouseY - grabDy;
        if (free) {
            x = Math.round(x);
            y = Math.round(y);
        } else {
            x = Math.round(x / GRID) * (double) GRID;
            y = Math.round(y / GRID) * (double) GRID;
        }

        double minX = MARGIN, maxX = Math.max(minX, sw - w - MARGIN);
        double minY = MARGIN, maxY = Math.max(minY, sh - h - MARGIN);
        x = clamp(x, minX, maxX);
        y = clamp(y, minY, maxY);

        // центровка
        double snap = free ? CENTER_SNAP_FREE : CENTER_SNAP_GRID;
        if (Math.abs(x + w / 2.0 - sw / 2.0) <= snap) x = Math.round((sw - w) / 2.0);
        if (Math.abs(y + h / 2.0 - sh / 2.0) <= snap) y = Math.round((sh - h) / 2.0);
        x = clamp(x, minX, maxX);
        y = clamp(y, minY, maxY);

        guideV = Math.abs(x + w / 2.0 - sw / 2.0) <= 0.5;
        guideH = Math.abs(y + h / 2.0 - sh / 2.0) <= 0.5;

        el.setPosition((int) x, (int) y, w, h, sw, sh);
    }

    private void drawGrid(GuiGraphics g, int sw, int sh, float p) {
        double s = this.minecraft.getWindow().getGuiScale();
        int pw = (int) Math.round(sw * s);
        int ph = (int) Math.round(sh * s);
        int minor = withAlpha(GRID_MINOR, p);
        int major = withAlpha(GRID_MAJOR, p);

        // все линии в физических пикселях одной пачкой (ровно 1 px толщиной при любом GUI-масштабе)
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / s), (float) (1.0 / s));
        for (int i = 0, gx = 0; gx <= sw; i++, gx += GRID) {
            int px = (int) Math.round(gx * s);
            g.fill(px, 0, px + 1, ph, i % MAJOR_EVERY == 0 ? major : minor);
        }
        for (int i = 0, gy = 0; gy <= sh; i++, gy += GRID) {
            int py = (int) Math.round(gy * s);
            g.fill(0, py, pw, py + 1, i % MAJOR_EVERY == 0 ? major : minor);
        }
        g.pose().popMatrix();
    }

    /** Подпись над элементом: название (+ координаты при перетаскивании). */
    private void drawChip(GuiGraphics g, Item it, int sw, int sh) {
        String text = it.el().title;
        if (it.el() == dragEl && dragMoved) text += "   " + it.x() + ", " + it.y();

        int tw = Fonts.BODY.widthInt(text, CHIP_TEXT);
        int cw = tw + 10;
        int cx = clampInt(it.x() - 2, 2, Math.max(2, sw - cw - 2));
        int cy = it.y() - 2 - 3 - CHIP_H;
        if (cy < 2) cy = it.y() + it.frame().h() + 2 + 3;   // сверху нет места — под элементом
        cy = clampInt(cy, 2, Math.max(2, sh - CHIP_H - 2));

        ShapeRenderer.roundRect(g, cx, cy, cw, CHIP_H, 3.5f, CHIP_BG);
        float midY = cy + CHIP_H / 2f;
        Fonts.BODY.draw(g, text, cx + 5, midY - CHIP_TEXT / 2f + CHIP_TEXT / 9f, CHIP_TEXT, TEXT_MAIN);
    }

    private void drawEmptyHint(GuiGraphics g, int sw, int sh, float p) {
        String a = "Нет активных функций";
        String b = "Включите Watermark или Effects в Settings / Misc";
        int lh = this.font.lineHeight;
        Fonts.BODY.draw(g, a, (sw - Fonts.BODY.widthInt(a, Fonts.SIZE)) / 2f, sh / 2f - lh - 1,
                Fonts.SIZE, withAlpha(TEXT_MAIN, p));
        Fonts.BODY.draw(g, b, (sw - Fonts.BODY.widthInt(b, Fonts.SIZE)) / 2f, sh / 2f + 3,
                Fonts.SIZE, withAlpha(TEXT_DIM, p));
    }

    private void drawHint(GuiGraphics g, int sw, int sh, float p) {
        String text = "ЛКМ — двигать   ·   Ctrl — без сетки   ·   R — сбросить   ·   Esc — назад";
        int tw = Fonts.BODY.widthInt(text, Fonts.SIZE);
        int bw = tw + 16;
        int bh = 15;
        int bx = (sw - bw) / 2;
        int by = sh - MARGIN - 4 - bh;
        ShapeRenderer.roundRect(g, bx, by, bw, bh, 5f, withAlpha(CHIP_BG, p));
        Fonts.BODY.draw(g, text, bx + 8, by + (bh - this.font.lineHeight) / 2f + 1, Fonts.SIZE,
                withAlpha(TEXT_ITEM, p));
    }

    // ---------------------------------------------------------------- мышь и клавиатура

    private Item hit(double mx, double my) {
        for (int i = items.size() - 1; i >= 0; i--) {   // сверху вниз
            if (items.get(i).contains(mx, my)) return items.get(i);
        }
        return null;
    }

    private Item find(HudElement el) {
        for (Item it : items) if (it.el() == el) return it;
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            Item it = hit(event.x(), event.y());
            if (it != null) {
                dragEl = it.el();
                dragMoved = false;
                pressX = mouseX = event.x();
                pressY = mouseY = event.y();
                grabDx = event.x() - it.x();
                grabDy = event.y() - it.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragEl != null && event.button() == 0) {
            mouseX = event.x();
            mouseY = event.y();
            if (!dragMoved && Math.hypot(mouseX - pressX, mouseY - pressY) >= DRAG_START) {
                dragMoved = true;   // дальше элемент идёт за курсором (в render)
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragEl != null && event.button() == 0) {
            if (dragMoved) HudManager.save();   // в конфиг один раз, когда отпустили
            dragEl = null;
            dragMoved = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R && dragEl == null) {
            Item it = hit(hoverX, hoverY);
            if (it != null) {
                it.el().resetPosition();
                HudManager.save();
            }
            return true;
        }
        return super.keyPressed(event);   // Esc -> onClose()
    }

    // ---------------------------------------------------------------- утилиты

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
