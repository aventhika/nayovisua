package nayomy.visuals.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import nayomy.visuals.gui.ClickGui;

public class KeyHandler {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            "nayomy-visuals",
                            "main"
                    )
            );

    private static final KeyMapping OPEN_GUI =
            KeyBindingHelper.registerKeyBinding(
                    new KeyMapping(
                            "key.nayomy-visuals.open_gui",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_RIGHT_SHIFT,
                            CATEGORY
                    )
            );

    public static void register() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            while (OPEN_GUI.consumeClick()) {

                if (client.screen instanceof ClickGui gui) {
                    gui.startClosing();
                } else if (client.screen == null) {
                    client.setScreen(new ClickGui());
                }

            }

        });
    }
}
