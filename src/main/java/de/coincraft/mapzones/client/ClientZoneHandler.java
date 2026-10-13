package de.coincraft.mapzones.client;

import de.coincraft.mapzones.net.Payloads;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Nimmt die Zonen-Updates des Servers an (laeuft nur auf dem Client). */
public final class ClientZoneHandler {

    private ClientZoneHandler() {
    }

    public static void onSyncAll(Payloads.SyncAll payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientZones.replaceAll(payload.zones());
            ZoneMapBridge.refreshHighlights();
        });
    }

    public static void onUpsert(Payloads.Upsert payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientZones.upsert(payload.zone());
            ZoneMapBridge.refreshHighlights();
        });
    }

    public static void onRemoved(Payloads.Removed payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientZones.remove(payload.id());
            ZoneMapBridge.refreshHighlights();
        });
    }
}
