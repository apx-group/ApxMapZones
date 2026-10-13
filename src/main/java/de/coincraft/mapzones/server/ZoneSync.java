package de.coincraft.mapzones.server;

import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.Zone;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Verteilt Zonen-Aenderungen an alle Spieler, die die Mod installiert haben. */
public final class ZoneSync {

    private ZoneSync() {
    }

    public static void sendAll(ServerPlayer player) {
        ZoneStore store = ZoneStore.get(player.getServer());
        send(player, new Payloads.SyncAll(store.snapshot()));
    }

    public static void broadcastUpsert(MinecraftServer server, Zone zone) {
        broadcast(server, new Payloads.Upsert(zone.copy()));
    }

    public static void broadcastRemoved(MinecraftServer server, UUID id) {
        broadcast(server, new Payloads.Removed(id));
    }

    private static void broadcast(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, payload);
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type())) {
            player.connection.send(payload);
        }
    }
}
