package nayomy.visuals.render;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import nayomy.visuals.module.WetWorldModule;
import org.joml.Matrix4f;

/**
 * Подключает Wet World к рендеру мира. Регистрировать ДО AtmosphereRender:
 * тогда мокрые отражения считаются по чистой сцене, а атмосфера ложится поверх.
 */
public final class WetWorldRender {

    private WetWorldRender() {}

    public static void register() {
        WorldRenderEvents.END_MAIN.register(context -> {
            if (!WetWorldModule.INSTANCE.enabled) return;

            Minecraft mc = Minecraft.getInstance();
            GameRenderer gr = context.gameRenderer();
            Camera camera = gr.getMainCamera();
            if (mc.level == null || !camera.isInitialized()) return;

            Matrix4f projection = gr.getProjectionMatrix(gr.getFov(camera, camera.getPartialTickTime(), true));
            WetWorldPipeline.getInstance().render(camera, projection);
        });
    }

    /** Вызывается из GUI при переключении модуля: сбрасывает ошибку / освобождает GL-ресурсы. */
    public static void onToggle(boolean enabled) {
        if (enabled) WetWorldPipeline.getInstance().reset();
        else WetWorldPipeline.getInstance().close();
    }
}
