package de.coincraft.mapzones.server;

import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Wer eine Aenderung ausloest - ein Spieler, die Konsole oder ein Befehlsblock. */
public record ZoneActor(UUID uuid, String name, boolean admin) {

    public static ZoneActor of(ServerPlayer player) {
        return new ZoneActor(player.getUUID(), player.getGameProfile().getName(), ZoneRules.isAdmin(player));
    }

    public static ZoneActor of(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            return new ZoneActor(player.getUUID(), player.getGameProfile().getName(),
                source.hasPermission(ZoneRules.ADMIN_PERMISSION_LEVEL));
        }
        return new ZoneActor(null, "Server", true);
    }
}
