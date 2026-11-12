package nayomy.visuals.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import nayomy.visuals.NayomyVisuals;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Фигуры с антиалиасингом. Раньше каждая фигура каждый кадр считалась попиксельно на CPU
 * и рисовалась сотнями g.fill(1x1). Теперь каждая фигура ОДИН раз «запекается» в маленькую
 * белую текстуру (альфа = покрытие), а в кадре рисуется ОДНИМ blit-ом с нужным цветом.
 * Один элемент GUI на фигуру вместо десятков/сотен — это и убирает лаги.
 */
public final class ShapeRenderer {

    @FunctionalInterface
    private interface Coverage {
        double at(double px, double py);
    }

    /** Ключ кэша: тип фигуры, GUI-масштаб и параметры в физических пикселях. */
    private record Key(int kind, double s, double a, double b, double c, double d, double e, double f) {}

    /** Запечённая фигура: текстура + её положение в физических пикселях. */
    private record Baked(Identifier id, int x, int y, int w, int h) {}

    private static final int MAX_CACHE = 768;
    private static int nextId = 0;
    private static final Map<Key, Baked> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Baked> eldest) {
            if (size() <= MAX_CACHE) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
            return true;
        }
    };

    private ShapeRenderer() {}

    // Все фигуры считаются в ЛОКАЛЬНЫХ координатах (относительно целого пикселя ox, oy).
    // Поэтому фигура, сдвинутая на целое число пикселей (например, плашка Watermark, у которой
    // меняется ширина), берёт ту же текстуру из кэша, а не печёт новую.

    /** Скруглённый прямоугольник (GUI-координаты). */
    public static void roundRect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        double s = scale();
        double ax = x * s, ay = y * s;
        int ox = (int) Math.floor(ax), oy = (int) Math.floor(ay);
        double x0 = ax - ox, y0 = ay - oy;
        double hw = w * s / 2.0, hh = h * s / 2.0;
        double r = Math.min(radius * s, Math.min(hw, hh));
        double cx = x0 + hw, cy = y0 + hh;

        raster(g, new Key(1, s, x0, y0, hw, hh, r, 0), ox, oy,
                x0, y0, x0 + hw * 2, y0 + hh * 2, color, (px, py) -> {
                    double qx = Math.abs(px - cx) - (hw - r);
                    double qy = Math.abs(py - cy) - (hh - r);
                    double d = len(Math.max(qx, 0.0), Math.max(qy, 0.0))
                            + Math.min(Math.max(qx, qy), 0.0) - r;
                    return clamp01(0.5 - d);
                });
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        ring(g, cx, cy, radius, 0f, color);
    }

    /** Кольцо: outer — внешний радиус, inner — внутренний. */
    public static void ring(GuiGraphics g, float cx, float cy, float outer, float inner, int color) {
        double s = scale();
        double ro = outer * s, ri = inner * s;
        double acx = cx * s, acy = cy * s;
        int ox = (int) Math.floor(acx - ro), oy = (int) Math.floor(acy - ro);
        double pcx = acx - ox, pcy = acy - oy;

        raster(g, new Key(2, s, pcx, pcy, ro, ri, 0, 0), ox, oy,
                pcx - ro, pcy - ro, pcx + ro, pcy + ro, color, (px, py) -> {
                    double dist = len(px - pcx, py - pcy);
                    double c = clamp01(ro - dist + 0.5);
                    if (ri > 0.0) c -= clamp01(ri - dist + 0.5);
                    return c;
                });
    }

    /** Левая половина круга. */
    public static void halfDiscLeft(GuiGraphics g, float cx, float cy, float radius, int color) {
        double s = scale();
        double ro = radius * s;
        double acx = cx * s, acy = cy * s;
        int ox = (int) Math.floor(acx - ro), oy = (int) Math.floor(acy - ro);
        double pcx = acx - ox, pcy = acy - oy;

        raster(g, new Key(3, s, pcx, pcy, ro, 0, 0, 0), ox, oy,
                pcx - ro, pcy - ro, pcx + ro, pcy + ro, color, (px, py) -> {
                    double dist = len(px - pcx, py - pcy);
                    return clamp01(ro - dist + 0.5) * clamp01(pcx - px + 0.5);
                });
    }

    /** Линия с закруглёнными концами. */
    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
        double s = scale();
        double r = thickness * s / 2.0;
        double aax = x1 * s, aay = y1 * s, abx2 = x2 * s, aby2 = y2 * s;
        int ox = (int) Math.floor(Math.min(aax, abx2) - r);
        int oy = (int) Math.floor(Math.min(aay, aby2) - r);
        double ax = aax - ox, ay = aay - oy, bx = abx2 - ox, by = aby2 - oy;
        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;

        raster(g, new Key(4, s, ax, ay, bx, by, r, 0), ox, oy,
                Math.min(ax, bx) - r, Math.min(ay, by) - r,
                Math.max(ax, bx) + r, Math.max(ay, by) + r, color, (px, py) -> {
                    double t = len2 < 1e-9 ? 0.0 : ((px - ax) * abx + (py - ay) * aby) / len2;
                    t = Math.max(0.0, Math.min(1.0, t));
                    double dist = len(px - (ax + abx * t), py - (ay + aby * t));
                    return clamp01(r - dist + 0.5);
                });
    }

    /** Горизонтальная линия. thickness в GUI-пикселях, но минимум 1 физический пиксель. */
    public static void hLine(GuiGraphics g, float x1, float x2, float y, float thickness, int color) {
        double s = scale();
        int t = Math.max(1, (int) Math.round(thickness * s));
        int a = (int) Math.round(x1 * s);
        int b = (int) Math.round(x2 * s);
        int py = (int) Math.round(y * s);

        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / s), (float) (1.0 / s));
        g.fill(a, py, b, py + t, color);
        g.pose().popMatrix();
    }

    /** Вертикальная линия. */
    public static void vLine(GuiGraphics g, float x, float y1, float y2, float thickness, int color) {
        double s = scale();
        int t = Math.max(1, (int) Math.round(thickness * s));
        int px = (int) Math.round(x * s);
        int a = (int) Math.round(y1 * s);
        int b = (int) Math.round(y2 * s);

        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / s), (float) (1.0 / s));
        g.fill(px, a, px + t, b, color);
        g.pose().popMatrix();
    }

    /**
     * Заливка набора полигонов с антиалиасингом (логотип, треугольники).
     * Полигоны заданы в условных координатах 0..srcW x 0..srcH и масштабируются в прямоугольник
     * (x, y, w, h) в GUI-пикселях. Запекается один раз на размер, дальше берётся из кэша.
     */
    public static void polygons(GuiGraphics g, float x, float y, float w, float h,
                                float[][] polys, float srcW, float srcH, int color) {
        double s = scale();
        double ax = x * s, ay = y * s;
        int ox = (int) Math.floor(ax), oy = (int) Math.floor(ay);
        double x0 = ax - ox, y0 = ay - oy;
        double pw = w * s, ph = h * s;
        double kx = pw / srcW, ky = ph / srcH;

        raster(g, new Key(9, s, x0, y0, pw, ph, System.identityHashCode(polys), 0), ox, oy,
                x0, y0, x0 + pw, y0 + ph, color, (px, py) -> {
                    // 4x4 подвыборок на пиксель: гладкие края без шейдера
                    final int n = 4;
                    int hit = 0;
                    for (int j = 0; j < n; j++) {
                        for (int i = 0; i < n; i++) {
                            double sx = (px - 0.5 + (i + 0.5) / n - x0) / kx;
                            double sy = (py - 0.5 + (j + 0.5) / n - y0) / ky;
                            for (float[] poly : polys) {
                                if (inside(poly, sx, sy)) { hit++; break; }
                            }
                        }
                    }
                    return hit / (double) (n * n);
                });
    }

    /** Точка внутри полигона (чётно-нечётное правило). poly = {x0, y0, x1, y1, ...}. */
    private static boolean inside(float[] poly, double px, double py) {
        boolean in = false;
        int cnt = poly.length / 2;
        for (int i = 0, j = cnt - 1; i < cnt; j = i++) {
            double xi = poly[i * 2], yi = poly[i * 2 + 1];
            double xj = poly[j * 2], yj = poly[j * 2 + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    /** Контур скруглённого прямоугольника (рамка толщиной thickness). */
    public static void roundRectOutline(GuiGraphics g, float x, float y, float w, float h,
                                        float radius, float thickness, int color) {
        double s = scale();
        double ax = x * s, ay = y * s;
        int ox = (int) Math.floor(ax), oy = (int) Math.floor(ay);
        double x0 = ax - ox, y0 = ay - oy;
        double hw = w * s / 2.0, hh = h * s / 2.0;
        double r = Math.min(radius * s, Math.min(hw, hh));
        double t = thickness * s;
        double cx = x0 + hw, cy = y0 + hh;

        raster(g, new Key(5, s, x0, y0, hw, hh, r, t), ox, oy,
                x0, y0, x0 + hw * 2, y0 + hh * 2, color, (px, py) -> {
                    double qx = Math.abs(px - cx) - (hw - r);
                    double qy = Math.abs(py - cy) - (hh - r);
                    double d = len(Math.max(qx, 0.0), Math.max(qy, 0.0))
                            + Math.min(Math.max(qx, qy), 0.0) - r;
                    double outer = clamp01(0.5 - d);
                    double inner = clamp01(0.5 - (d + t));
                    return Math.max(0.0, outer - inner);
                });
    }

    // ------------------------------------------------------------ растеризация + кэш

    private static void raster(GuiGraphics g, Key key, int ox, int oy,
                               double minX, double minY, double maxX, double maxY,
                               int color, Coverage cov) {
        if ((color >>> 24) == 0) return;

        Baked b = CACHE.get(key);
        if (b == null) {
            b = bake(minX, minY, maxX, maxY, cov);
            if (b == null) return;
            CACHE.put(key, b);
        }

        double s = scale();
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / s), (float) (1.0 / s));
        g.blit(RenderPipelines.GUI_TEXTURED, b.id(), ox + b.x(), oy + b.y(), 0f, 0f, b.w(), b.h(), b.w(), b.h(), color);
        g.pose().popMatrix();
    }

    /** Один раз считает покрытие и кладёт его в белую текстуру (альфа = покрытие). */
    private static Baked bake(double minX, double minY, double maxX, double maxY, Coverage cov) {
        int x0 = (int) Math.floor(minX) - 1;
        int x1 = (int) Math.ceil(maxX) + 1;
        int y0 = (int) Math.floor(minY) - 1;
        int y1 = (int) Math.ceil(maxY) + 1;
        int w = x1 - x0;
        int h = y1 - y0;
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null;

        NativeImage img = new NativeImage(w, h, true); // заполнена нулями (прозрачная)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = quant(cov.at(x0 + x + 0.5, y0 + y + 0.5));
                if (a > 0) img.setPixel(x, y, (a << 24) | 0x00FFFFFF); // белый: ARGB == ABGR
            }
        }

        Identifier id = NayomyVisuals.id("shape/" + (nextId++));
        DynamicTexture tex = new DynamicTexture(() -> id.toString(), img);
        tex.upload();
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return new Baked(id, x0, y0, w, h);
    }

    private static int quant(double v) {
        return (int) Math.round(clamp01(v) * 255.0);
    }

    /** Быстрее Math.hypot в десятки раз. */
    private static double len(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    private static double scale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
