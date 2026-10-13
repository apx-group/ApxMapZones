package de.coincraft.mapzones.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Clienteinstellungen (config/mapzones-client.toml). */
public final class ClientConfig {

    public static final ClientConfig CONFIG;
    public static final ModConfigSpec SPEC;

    static {
        Pair<ClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.BooleanValue showZones;
    public final ModConfigSpec.IntValue fillOpacity;
    public final ModConfigSpec.IntValue borderOpacity;
    public final ModConfigSpec.BooleanValue showOwnerInTooltip;

    private ClientConfig(ModConfigSpec.Builder builder) {
        builder.push("worldmap");
        showZones = builder
            .comment("Zonen auf der Weltkarte anzeigen.")
            .define("showZones", true);
        fillOpacity = builder
            .comment("Deckkraft der Zonenflaeche in Prozent.")
            .defineInRange("fillOpacity", 45, 0, 100);
        borderOpacity = builder
            .comment("Deckkraft des Zonenrandes in Prozent.")
            .defineInRange("borderOpacity", 95, 0, 100);
        showOwnerInTooltip = builder
            .comment("Ersteller der Zone im Tooltip anzeigen.")
            .define("showOwnerInTooltip", true);
        builder.pop();
    }

    public static boolean zonesVisible() {
        return CONFIG.showZones.get();
    }

    public static void toggleZonesVisible() {
        CONFIG.showZones.set(!CONFIG.showZones.get());
        CONFIG.showZones.save();
        ZoneMapBridge.refreshHighlights();
    }
}
