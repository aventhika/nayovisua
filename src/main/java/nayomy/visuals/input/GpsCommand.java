package nayomy.visuals.input;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import nayomy.visuals.module.GpsModule;

/**
 * Перехват чата:
 *   .gps X Z       — цель по двум координатам (X и Z)
 *   .gps X Y Z     — цель по трём (Y — высота, посередине)
 *   .gps off       — выключить
 * Сообщение НЕ уходит на сервер.
 */
public final class GpsCommand {

    private static final String PREFIX = ".gps";

    private GpsCommand() {}

    public static void register() {
        // ALLOW_CHAT: вернуть false — сообщение не отправится на сервер
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !handle(message));
    }

    /** @return true, если это была наша команда (её нужно съесть). */
    private static boolean handle(String message) {
        String msg = message.trim();
        String lower = msg.toLowerCase();
        if (!lower.equals(PREFIX) && !lower.startsWith(PREFIX + " ")) return false;

        String[] args = msg.substring(PREFIX.length()).trim().replace(',', ' ').split("\\s+");
        if (args.length == 1 && args[0].isEmpty()) {
            reply("§eИспользование: §f.gps X Z §7| §f.gps X Y Z §7| §f.gps off для отключения");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            GpsModule.INSTANCE.clear();
            reply("§cGPS выключен");
            return true;
        }

        try {
            if (args.length == 2) {
                double x = Double.parseDouble(args[0]);
                double z = Double.parseDouble(args[1]);
                GpsModule.INSTANCE.setTarget(x, z);
                reply("§aGPS: §fX " + fmt(x) + "  Z " + fmt(z));
            } else if (args.length == 3) {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                GpsModule.INSTANCE.setTarget(x, y, z);
                reply("§aGPS: §fX " + fmt(x) + "  Y " + fmt(y) + "  Z " + fmt(z));
            } else {
                reply("§cНужно 2 или 3 координаты: §f.gps X Z §cили §f.gps X Y Z");
            }
        } catch (NumberFormatException e) {
            reply("§cКоординаты должны быть числами");
        }
        return true;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static void reply(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), false);
    }
}