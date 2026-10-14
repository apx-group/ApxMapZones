package de.coincraft.mapzones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Nachrichten, die der Server an Spieler schickt.
 *
 * Die Mod ist bewusst optional (siehe NetworkHandler): wer sie nicht installiert
 * hat - oder noch eine aeltere Version benutzt - kann trotzdem mitspielen und
 * {@code /mapzone} benutzen. Ein reiner Uebersetzungsschluessel waere fuer diese
 * Spieler aber nur technischer Text wie "message.mapzones.transferred".
 *
 * Deshalb reist der fertige Satz als Rueckfalltext mit. Clients mit der Mod
 * zeigen ihre eigene Uebersetzung, alle anderen den mitgelieferten Text.
 */
public final class ZoneMessages {

    /**
     * Sprache des Rueckfalltexts fuer Clients ohne die Mod. Die Datei liegt in der
     * Jar, der Text bleibt dadurch automatisch gleich mit der Uebersetzung.
     * Fuer einen englischsprachigen Server auf "en_us" umstellen.
     */
    private static final String FALLBACK_LANGUAGE = "de_de";

    private static final Map<String, String> FALLBACKS = load();

    private ZoneMessages() {
    }

    /** Uebersetzbare Nachricht mit mitgeliefertem Text fuer Clients ohne die Mod. */
    public static MutableComponent of(String key, Object... args) {
        String fallback = FALLBACKS.get(key);
        if (fallback == null) {
            return Component.translatable(key, args);
        }
        return Component.translatableWithFallback(key, fallback, args);
    }

    private static Map<String, String> load() {
        String resource = "/assets/" + MapZones.MOD_ID + "/lang/" + FALLBACK_LANGUAGE + ".json";
        Map<String, String> entries = new HashMap<>();
        try (InputStream stream = ZoneMessages.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return Collections.emptyMap();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            for (String key : json.keySet()) {
                if (key.startsWith("message.")) {
                    entries.put(key, json.get(key).getAsString());
                }
            }
        } catch (Exception exception) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(entries);
    }
}
