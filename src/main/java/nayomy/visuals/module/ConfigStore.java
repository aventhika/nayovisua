package nayomy.visuals.module;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import nayomy.visuals.NayomyVisuals;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Общий конфиг config/nayomy-visuals.json: модули читают и пишут только свои ключи. */
public final class ConfigStore {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("nayomy-visuals.json");

    private ConfigStore() {}

    public static JsonObject read() {
        if (!Files.exists(FILE)) return new JsonObject();
        try (Reader r = Files.newBufferedReader(FILE)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (Exception e) {
            NayomyVisuals.LOGGER.warn("Не удалось прочитать конфиг", e);
            return new JsonObject();
        }
    }

    public static void write(JsonObject o) {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(o, w);
        } catch (Exception e) {
            NayomyVisuals.LOGGER.warn("Не удалось сохранить конфиг", e);
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        return o.has(key) ? o.get(key).getAsBoolean() : def;
    }

    public static float num(JsonObject o, String key, float def) {
        return o.has(key) ? o.get(key).getAsFloat() : def;
    }

    public static int integer(JsonObject o, String key, int def) {
        return o.has(key) ? o.get(key).getAsInt() : def;
    }
}