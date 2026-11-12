package nayomy.visuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import nayomy.visuals.NayomyVisuals;
import org.joml.Matrix3x2f;

public final class GlassRenderer {

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(NayomyVisuals.id("pipeline/glass_rect"))
                    .withFragmentShader(NayomyVisuals.id("core/glass_rect"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private static TextureTarget capture;
    /** Кадр, в котором экран уже скопирован: за кадр копируем ОДИН раз, а не на каждую плашку. */
    private static long capturedFrame = Long.MIN_VALUE;

    private GlassRenderer() {}

    private static TextureTarget captureScreen() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();

        if (capture == null) {
            capture = new TextureTarget("nayomy_capture", main.width, main.height, false);
            capturedFrame = Long.MIN_VALUE;
        } else if (capture.width != main.width || capture.height != main.height) {
            capture.resize(main.width, main.height);
            capturedFrame = Long.MIN_VALUE;
        }

        long frame = mc.getFrameTimeNs();
        if (frame == capturedFrame) return capture; // уже скопировано в этом кадре

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                main.getColorTexture(), capture.getColorTexture(),
                0, 0, 0, 0, 0, main.width, main.height
        );
        capturedFrame = frame;
        return capture;
    }

    /**
     * x, y, w, h, cornerRadius, blurRadius — в GUI-координатах.
     * tintAlpha 0..255: чем больше, тем плотнее цвет.
     * borderAlpha 0..255: яркость белой обводки (0 = без обводки).
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h,
                            int cornerRadius, int blurRadius, int tintAlpha, int borderAlpha) {
        submit(g, x, y, w, h, cornerRadius, blurRadius, tintAlpha, borderAlpha, false);
    }

    /**
     * Рисует только «углы» вне скруглённого прямоугольника, тем же цветом, что и окно.
     * Кладётся поверх квадратной картинки, чтобы скруглить её углы.
     */
    public static void drawInverse(GuiGraphics g, int x, int y, int w, int h,
                                   int cornerRadius, int blurRadius, int tintAlpha) {
        submit(g, x, y, w, h, cornerRadius, blurRadius, tintAlpha, 0, true);
    }

    private static void submit(GuiGraphics g, int x, int y, int w, int h,
                               int cornerRadius, int blurRadius, int tintAlpha,
                               int borderAlpha, boolean inverse) {
        double s = Minecraft.getInstance().getWindow().getGuiScale();

        int rPx = clamp((int) Math.round(cornerRadius * s), 255);
        int bPx = clamp((int) Math.round(blurRadius * s), 127);
        int tint = clamp(tintAlpha, 255);
        int border = clamp(borderAlpha, 255);

        int blurChannel = inverse ? (bPx | 128) : bPx;
        int packed = (border << 24) | (blurChannel << 16) | (rPx << 8) | tint;

        TextureTarget cap = captureScreen();

        g.guiRenderState.submitGuiElement(new BlitRenderState(
                PIPELINE,
                TextureSetup.singleTexture(
                        cap.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                ),
                new Matrix3x2f(g.pose()),
                x, y, x + w, y + h,
                0f, 1f, 0f, 1f,
                packed,
                null
        ));
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
