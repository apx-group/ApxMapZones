package de.coincraft.mapzones.net;

import de.coincraft.mapzones.server.ZoneRequestHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): Clients ohne diese Mod koennen trotzdem auf dem Server spielen.
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(Payloads.SyncAll.TYPE, Payloads.SyncAll.CODEC,
            (payload, context) -> de.coincraft.mapzones.client.ClientZoneHandler.onSyncAll(payload, context));
        registrar.playToClient(Payloads.Upsert.TYPE, Payloads.Upsert.CODEC,
            (payload, context) -> de.coincraft.mapzones.client.ClientZoneHandler.onUpsert(payload, context));
        registrar.playToClient(Payloads.Removed.TYPE, Payloads.Removed.CODEC,
            (payload, context) -> de.coincraft.mapzones.client.ClientZoneHandler.onRemoved(payload, context));

        registrar.playToServer(Payloads.CreateRequest.TYPE, Payloads.CreateRequest.CODEC,
            ZoneRequestHandler::onCreate);
        registrar.playToServer(Payloads.AddAreaRequest.TYPE, Payloads.AddAreaRequest.CODEC,
            ZoneRequestHandler::onAddArea);
        registrar.playToServer(Payloads.MergeRequest.TYPE, Payloads.MergeRequest.CODEC,
            ZoneRequestHandler::onMerge);
        registrar.playToServer(Payloads.EditRequest.TYPE, Payloads.EditRequest.CODEC,
            ZoneRequestHandler::onEdit);
        registrar.playToServer(Payloads.PermissionsRequest.TYPE, Payloads.PermissionsRequest.CODEC,
            ZoneRequestHandler::onPermissions);
        registrar.playToServer(Payloads.DeleteRequest.TYPE, Payloads.DeleteRequest.CODEC,
            ZoneRequestHandler::onDelete);
    }
}
