package nayomy.visuals.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import nayomy.visuals.NayomyVisuals;
import org.joml.Matrix3x2f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Текст из MSDF-атласов (msdf-atlas-gen, формат mtsdf).
 * Одна строка = ОДИН элемент GUI (все буквы в одном списке вершин).
 *
 * Файлы:  assets/nayomy-visuals/msdf/<name>.json
 *         assets/nayomy-visuals/textures/msdf/<name>.png
 */
public final class MsdfFont {

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(NayomyVisuals.id("pipeline/msdf_text"))
                    .withFragmentShader(NayomyVisuals.id("core/msdf_text"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private record Glyph(float advance, boolean visible,
                         float pl, float pb, float pr, float pt,   // plane bounds (в em)
                         float u0, float v0, float u1, float v1) {} // UV (v0 = верх)

    /** Один элемент GUI: все квады строки. */
    private record TextState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                             float[] quads, int count, int color,
                             ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int i = 0; i < count; i++) {
                int o = i * 8;
                float x0 = quads[o], y0 = quads[o + 1], x1 = quads[o + 2], y1 = quads[o + 3];
                float u0 = quads[o + 4], v0 = quads[o + 5], u1 = quads[o + 6], v1 = quads[o + 7];
                vc.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
                vc.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
                vc.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
                vc.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
            }
        }
    }

    /** Один элемент GUI: один повёрнутый квад (4 произвольные вершины). */
    private record RotState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                            float[] p, float u0, float v0, float u1, float v1, int color,
                            ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer vc) {
            // порядок как у TextState: верх-лево, низ-лево, низ-право, верх-право
            vc.addVertexWith2DPose(pose, p[0], p[1]).setUv(u0, v0).setColor(color);
            vc.addVertexWith2DPose(pose, p[2], p[3]).setUv(u0, v1).setColor(color);
            vc.addVertexWith2DPose(pose, p[4], p[5]).setUv(u1, v1).setColor(color);
            vc.addVertexWith2DPose(pose, p[6], p[7]).setUv(u1, v0).setColor(color);
        }
    }

    private final String name;
    private Identifier jsonId;
    private Identifier atlasId;
    /** Если файлов этого шрифта нет в ресурсах — берём этот (например, Onest -> Inter). */
    private final String fallback;

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private float spaceAdvance = 0.25f;
    private float ascender = 0.95f;
    private float lineHeight = 1.2f;
    private boolean loaded = false;
    private boolean failed = false;

    MsdfFont(String name) {
        this(name, null);
    }

    MsdfFont(String name, String fallback) {
        this.name = name;
        this.fallback = fallback;
        this.jsonId = NayomyVisuals.id("msdf/" + name + ".json");
        this.atlasId = NayomyVisuals.id("textures/msdf/" + name + ".png");
    }

    // ------------------------------------------------------------------ загрузка

    private boolean ensureLoaded() {
        if (loaded) return true;
        if (failed) return false;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            if (fallback != null && (rm.getResource(jsonId).isEmpty() || rm.getResource(atlasId).isEmpty())) {
                NayomyVisuals.LOGGER.warn("Нет атласа {} — временно используется {}", name, fallback);
                jsonId = NayomyVisuals.id("msdf/" + fallback + ".json");
                atlasId = NayomyVisuals.id("textures/msdf/" + fallback + ".png");
            }
            var res = rm.getResource(jsonId);
            if (res.isEmpty()) throw new IllegalStateException("нет файла " + jsonId);

            try (Reader r = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

                JsonObject atlas = root.getAsJsonObject("atlas");
                float aw = atlas.get("width").getAsFloat();
                float ah = atlas.get("height").getAsFloat();
                boolean bottom = !atlas.has("yOrigin") || atlas.get("yOrigin").getAsString().equals("bottom");

                JsonObject metrics = root.getAsJsonObject("metrics");
                ascender = metrics.get("ascender").getAsFloat();
                lineHeight = metrics.get("lineHeight").getAsFloat();

                JsonArray arr = root.getAsJsonArray("glyphs");
                for (JsonElement el : arr) {
                    JsonObject g = el.getAsJsonObject();
                    if (!g.has("unicode")) continue;
                    int cp = g.get("unicode").getAsInt();
                    float adv = g.get("advance").getAsFloat();

                    if (g.has("planeBounds") && g.has("atlasBounds")) {
                        JsonObject p = g.getAsJsonObject("planeBounds");
                        JsonObject a = g.getAsJsonObject("atlasBounds");
                        float l = a.get("left").getAsFloat(), b = a.get("bottom").getAsFloat();
                        float rr = a.get("right").getAsFloat(), t = a.get("top").getAsFloat();
                        float u0 = l / aw, u1 = rr / aw, v0, v1;
                        if (bottom) { v0 = 1f - t / ah; v1 = 1f - b / ah; }   // v0 — верх буквы
                        else        { v0 = t / ah;      v1 = b / ah; }
                        glyphs.put(cp, new Glyph(adv, true,
                                p.get("left").getAsFloat(), p.get("bottom").getAsFloat(),
                                p.get("right").getAsFloat(), p.get("top").getAsFloat(),
                                u0, v0, u1, v1));
                    } else {
                        glyphs.put(cp, new Glyph(adv, false, 0, 0, 0, 0, 0, 0, 0, 0));
                    }
                }
                Glyph sp = glyphs.get((int) ' ');
                if (sp != null) spaceAdvance = sp.advance();
            }
            loaded = true;
        } catch (Exception e) {
            failed = true;
            NayomyVisuals.LOGGER.error("MSDF-шрифт {} не загрузился", name, e);
        }
        return loaded;
    }

    private Glyph glyph(int cp) {
        Glyph g = glyphs.get(cp);
        if (g != null) return g;
        g = glyphs.get((int) '?');
        return g;
    }

    // ------------------------------------------------------------------ размеры

    /** Ширина строки в GUI-пикселях при размере size (em в GUI-пикселях). */
    public float width(String text, float size) {
        if (text.isEmpty() || !ensureLoaded()) return 0f;
        float w = 0f;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Glyph g = glyph(cp);
            w += (g != null ? g.advance() : spaceAdvance) * size;
        }
        return w;
    }

    public int widthInt(String text, float size) {
        return (int) Math.ceil(width(text, size));
    }

    /** Высота строки в GUI-пикселях. */
    public float lineHeight(float size) {
        ensureLoaded();
        return lineHeight * size;
    }

    /** Жадный перенос по словам: каждая строка не шире maxWidth. */
    public List<String> wrap(String text, float size, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (width(candidate, size) <= maxWidth || cur.isEmpty()) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    // ------------------------------------------------------------------ отрисовка

    /**
     * x — левый край, y — ВЕРХ строки высотой ~size (как у ванильного drawString:
     * для size = 9 буквы встают ровно туда же). color — ARGB.
     */
    public void draw(GuiGraphics g, String text, float x, float y, float size, int color) {
        if (text.isEmpty() || (color >>> 24) == 0 || !ensureLoaded()) return;

        // высота заглавных у Inter ~0.727 em: ставим базовую линию так, чтобы заглавные стояли
        // по центру строки высотой size, плюс общая подстройка Fonts.Y_OFFSET (для 9 px)
        float baseline = y + size * 0.8635f + Fonts.Y_OFFSET * (size / 9f);
        float[] quads = new float[Math.max(1, text.length()) * 8];
        int n = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        float pen = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Glyph gl = glyph(cp);
            if (gl == null) { pen += spaceAdvance * size; continue; }

            if (gl.visible()) {
                float x0 = pen + gl.pl() * size;
                float x1 = pen + gl.pr() * size;
                float y0 = baseline - gl.pt() * size;
                float y1 = baseline - gl.pb() * size;
                int o = n * 8;
                quads[o] = x0; quads[o + 1] = y0; quads[o + 2] = x1; quads[o + 3] = y1;
                quads[o + 4] = gl.u0(); quads[o + 5] = gl.v0(); quads[o + 6] = gl.u1(); quads[o + 7] = gl.v1();
                n++;
                minX = Math.min(minX, x0); maxX = Math.max(maxX, x1);
                minY = Math.min(minY, y0); maxY = Math.max(maxY, y1);
            }
            pen += gl.advance() * size;
        }
        if (n == 0) return;

        submit(g, quads, n, color, minX, minY, maxX, maxY);
    }

    /** Отправляет готовые квады в GUI как один элемент. */
    private void submit(GuiGraphics g, float[] quads, int n, int color,
                        float minX, float minY, float maxX, float maxY) {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(atlasId);
        TextureSetup setup = TextureSetup.singleTexture(
                tex.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle raw = new ScreenRectangle(
                (int) Math.floor(minX) - 1, (int) Math.floor(minY) - 1,
                (int) Math.ceil(maxX - minX) + 3, (int) Math.ceil(maxY - minY) + 3)
                .transformMaxBounds(pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(raw) : raw;

        g.guiRenderState.submitGuiElement(new TextState(
                PIPELINE, setup, pose, quads, n, color, scissor, bounds));
    }

    /**
     * Иконка из icon-шрифта (icons / cosmetics_icons): глиф с кодом codepoint рисуется
     * по центру точки (cx, cy), а его бОльшая сторона равна size GUI-пикселям.
     * Так иконки не зависят от базовой линии и встают ровно в середину строки.
     */
    public void drawIcon(GuiGraphics g, int codepoint, float cx, float cy, float size, int color) {
        if ((color >>> 24) == 0 || !ensureLoaded()) return;
        Glyph gl = glyphs.get(codepoint);
        if (gl == null || !gl.visible()) return;

        float gw = gl.pr() - gl.pl();
        float gh = gl.pt() - gl.pb();
        float k = size / Math.max(gw, gh);
        float mx = (gl.pl() + gl.pr()) / 2f;
        float my = (gl.pb() + gl.pt()) / 2f;

        float x0 = cx + (gl.pl() - mx) * k;
        float x1 = cx + (gl.pr() - mx) * k;
        float y0 = cy - (gl.pt() - my) * k;
        float y1 = cy - (gl.pb() - my) * k;

        float[] quads = {x0, y0, x1, y1, gl.u0(), gl.v0(), gl.u1(), gl.v1()};
        submit(g, quads, 1, color, x0, y0, x1, y1);
    }

    /**
     * Иконка, повёрнутая на angleRad ПО ЧАСОВОЙ стрелке вокруг центра (cx, cy).
     * Остальное как у drawIcon: бОльшая сторона глифа = size GUI-пикселей.
     */
    public void drawIconRotated(GuiGraphics g, int codepoint, float cx, float cy, float size,
                                float angleRad, int color) {
        if ((color >>> 24) == 0 || !ensureLoaded()) return;
        Glyph gl = glyphs.get(codepoint);
        if (gl == null || !gl.visible()) return;

        float gw = gl.pr() - gl.pl();
        float gh = gl.pt() - gl.pb();
        float k = size / Math.max(gw, gh);
        float mx = (gl.pl() + gl.pr()) / 2f;
        float my = (gl.pb() + gl.pt()) / 2f;

        // углы без поворота, относительно центра: ВЛ, НЛ, НП, ВП
        float l = (gl.pl() - mx) * k, r = (gl.pr() - mx) * k;
        float t = -(gl.pt() - my) * k, b = -(gl.pb() - my) * k;
        float[] lx = {l, l, r, r};
        float[] ly = {t, b, b, t};

        float cos = (float) Math.cos(angleRad), sin = (float) Math.sin(angleRad);
        float[] p = new float[8];
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float px = cx + lx[i] * cos - ly[i] * sin;   // экранные координаты (y вниз) -> по часовой
            float py = cy + lx[i] * sin + ly[i] * cos;
            p[i * 2] = px;
            p[i * 2 + 1] = py;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }

        var tex = Minecraft.getInstance().getTextureManager().getTexture(atlasId);
        TextureSetup setup = TextureSetup.singleTexture(
                tex.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle scissor = g.scissorStack.peek();
        ScreenRectangle raw = new ScreenRectangle(
                (int) Math.floor(minX) - 1, (int) Math.floor(minY) - 1,
                (int) Math.ceil(maxX - minX) + 3, (int) Math.ceil(maxY - minY) + 3)
                .transformMaxBounds(pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(raw) : raw;

        g.guiRenderState.submitGuiElement(new RotState(
                PIPELINE, setup, pose, p, gl.u0(), gl.v0(), gl.u1(), gl.v1(), color, scissor, bounds));
    }

    /** Текст по центру вертикально внутри строки высотой h (верх = y). */
    public void drawCenteredY(GuiGraphics g, String text, float x, float y, float h, float size, int color) {
        draw(g, text, x, y + (h - size) / 2f, size, color);
    }
}