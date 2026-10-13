package de.coincraft.mapzones.server;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

/** Farbpalette, die Befehle und Kartenoberflaeche gemeinsam nutzen. */
public final class ZoneColors {

    public static final int DEFAULT_COLOR = 0x55FF55;

    /** Reihenfolge bestimmt auch die Anordnung im Editor-Fenster. */
    public static final Map<String, Integer> PALETTE = new LinkedHashMap<>();

    static {
        PALETTE.put("rot", 0xFF5555);
        PALETTE.put("orange", 0xFFA64D);
        PALETTE.put("gelb", 0xFFFF55);
        PALETTE.put("limette", 0xAAFF55);
        PALETTE.put("gruen", 0x55FF55);
        PALETTE.put("tuerkis", 0x55FFC8);
        PALETTE.put("cyan", 0x55FFFF);
        PALETTE.put("hellblau", 0x55AAFF);
        PALETTE.put("blau", 0x5555FF);
        PALETTE.put("violett", 0xAA55FF);
        PALETTE.put("magenta", 0xFF55FF);
        PALETTE.put("pink", 0xFF80BF);
        PALETTE.put("braun", 0xA6673A);
        PALETTE.put("weiss", 0xFFFFFF);
        PALETTE.put("grau", 0xAAAAAA);
        PALETTE.put("schwarz", 0x303030);
    }

    public static final SuggestionProvider<CommandSourceStack> SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(PALETTE.keySet(), builder);

    private ZoneColors() {
    }

    public static Integer byName(String name) {
        return PALETTE.get(name.toLowerCase(java.util.Locale.ROOT));
    }
}
