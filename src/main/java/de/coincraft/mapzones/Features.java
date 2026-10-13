package de.coincraft.mapzones;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Schalter, die beim Bauen festgelegt werden.
 *
 * Aus derselben Codebasis entstehen zwei Jars: die schlichte Variante ohne
 * Rechteverwaltung und die Protect-Variante. Unterschied ist allein die
 * Ressource mapzones-features.json.
 */
public final class Features {

    private static final String RESOURCE = "/mapzones-features.json";

    /** true in der Protect-Variante: Zonen koennen Bauen, Abbauen und Benutzen einschraenken. */
    public static final boolean PROTECTION = read("protection");

    private Features() {
    }

    private static boolean read(String key) {
        try (InputStream stream = Features.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return false;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.has(key) && json.get(key).getAsBoolean();
        } catch (Exception exception) {
            return false;
        }
    }
}
