package nayomy.visuals.client;

import net.fabricmc.api.ClientModInitializer;
import nayomy.visuals.hud.HudManager;
import nayomy.visuals.input.GpsCommand;
import nayomy.visuals.input.KeyHandler;
import nayomy.visuals.module.AtmosphereModule;
import nayomy.visuals.module.EffectsModule;
import nayomy.visuals.module.WatermarkModule;
import nayomy.visuals.module.WetWorldModule;
import nayomy.visuals.render.AtmosphereRender;
import nayomy.visuals.render.WetWorldRender;

public class NayomyVisualsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WatermarkModule.INSTANCE.load();
        EffectsModule.INSTANCE.load();
        AtmosphereModule.INSTANCE.load();
        WetWorldModule.INSTANCE.load();
        HudManager.load();
        HudManager.register();
        GpsCommand.register();
        // WetWorld раньше Atmosphere: атмосфера ложится поверх мокрых отражений
        WetWorldRender.register();
        AtmosphereRender.register();
        KeyHandler.register();
    }
}