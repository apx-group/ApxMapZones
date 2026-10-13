package de.coincraft.mapzones.server;

import de.coincraft.mapzones.zone.Zone;
import net.minecraft.server.level.ServerPlayer;

/** Grenzen und Rechte. Jeder darf Zonen anlegen und eigene bearbeiten, OPs duerfen alles. */
public final class ZoneRules {
    public static final int MAX_ZONES_TOTAL = 500;
    public static final int MAX_ZONES_PER_PLAYER = 50;
    public static final int MAX_CHUNKS_PER_ZONE = 4096;
    public static final int ADMIN_PERMISSION_LEVEL = 2;

    private static final char SECTION_SIGN = '§';

    private ZoneRules() {
    }

    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(ADMIN_PERMISSION_LEVEL);
    }

    public static boolean canEdit(ServerPlayer player, Zone zone) {
        return canEdit(ZoneActor.of(player), zone);
    }

    public static boolean canEdit(ZoneActor actor, Zone zone) {
        if (actor.admin()) {
            return true;
        }
        return zone.owner() != null && actor.uuid() != null && zone.owner().equals(actor.uuid());
    }

    /** Entfernt Steuerzeichen und Formatierungscodes und kuerzt auf die erlaubte Laenge. */
    public static String sanitizeName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length() && builder.length() < Zone.MAX_NAME_LENGTH; i++) {
            char character = trimmed.charAt(i);
            if (character == SECTION_SIGN || Character.isISOControl(character)) {
                continue;
            }
            builder.append(character);
        }
        return builder.toString().trim();
    }
}
