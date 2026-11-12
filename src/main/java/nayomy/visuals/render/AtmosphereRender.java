package nayomy.visuals.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import nayomy.visuals.module.AtmosphereModule;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Подключает Atmosphere к рендеру мира: в конце основного прохода берёт матрицы камеры и рисует эффект. */
public final class AtmosphereRender {

    private AtmosphereRender() {}

    public static void register() {
        WorldRenderEvents.END_MAIN.register(context -> {
            AtmosphereModule m = AtmosphereModule.INSTANCE;
            if (!m.enabled) return;

            Minecraft mc = Minecraft.getInstance();
            GameRenderer gr = context.gameRenderer();
            Camera camera = gr.getMainCamera();
            if (mc.level == null || !camera.isInitialized()) return;

            // view = только поворот (камера в начале координат), позиция передаётся в шейдер отдельно
            Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
            Matrix4f projection = gr.getProjectionMatrix(gr.getFov(camera, camera.getPartialTickTime(), true));

            AtmospherePostPipeline.getInstance().render(camera, view, projection);
        });
    }

    /** Вызывается из GUI при переключении модуля: сбрасывает ошибку / освобождает GL-ресурсы. */
    public static void onToggle(boolean enabled) {
        if (enabled) AtmospherePostPipeline.getInstance().reset();
        else AtmospherePostPipeline.getInstance().close();
    }
}
