package de.coincraft.mapzones.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MapZonesClient {

    private MapZonesClient() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(MapZonesClient::onLoggingOut);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientZones.clear();
    }
}
