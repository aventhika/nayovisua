package nayomy.visuals.render;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Логотип Nayomy, нарисованный векторно (полигонами), поэтому он остаётся чётким
 * при любом GUI-масштабе. Цвет задаётся ARGB, как у остальных фигур.
 */
public final class LogoRenderer {

    /** Размер исходной сетки координат (логотип обрезан по габаритам). */
    private static final float SRC_W = 420f, SRC_H = 333f;
    public static final float ASPECT = SRC_W / SRC_H;

    private static final float[][] POLYS = {
            // левая «створка»
            {0, 78, 67, 122, 67, 264, 0, 214},
            // правая «створка»
            {419, 77, 419, 211, 354, 264, 354, 121},
            // основная фигура
            {116, 0, 301, 133, 301, 245, 342, 281, 289, 332, 288, 228, 116, 92},
            // три полоски
            {116, 137, 136, 151, 136, 254, 116, 240},
            {154, 150, 175, 165, 175, 279, 154, 264},
            {192, 179, 213, 195, 213, 302, 192, 287},
    };

    private LogoRenderer() {}

    /** Ширина логотипа при заданной высоте. */
    public static float widthFor(float height) {
        return height * ASPECT;
    }

    /** Рисует логотип высотой height по центру точки (cx, cy). */
    public static void draw(GuiGraphics g, float cx, float cy, float height, int color) {
        float w = widthFor(height);
        ShapeRenderer.polygons(g, cx - w / 2f, cy - height / 2f, w, height, POLYS, SRC_W, SRC_H, color);
    }
}
