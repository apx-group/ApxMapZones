package de.coincraft.mapzones.server;

import de.coincraft.mapzones.net.Payloads;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Verarbeitet die Anfragen aus der Weltkarten-Oberflaeche. */
public final class ZoneRequestHandler {

    private ZoneRequestHandler() {
    }

    public static void onCreate(Payloads.CreateRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> ZoneManager.createOrExtend(
            server, actor, payload.name(), payload.color(), payload.dimension(), payload.rect()));
    }

    public static void onAddArea(Payloads.AddAreaRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> ZoneManager.addArea(server, actor, payload.zoneId(), payload.rect()));
    }

    public static void onMerge(Payloads.MergeRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> ZoneManager.merge(server, actor, payload.targetId(), payload.otherId()));
    }

    public static void onEdit(Payloads.EditRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> ZoneManager.edit(server, actor, payload.id(), payload.name(), payload.color()));
    }

    public static void onDelete(Payloads.DeleteRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> ZoneManager.delete(server, actor, payload.id()));
    }

    private static void run(IPayloadContext context, BiFunction<MinecraftServer, ZoneActor, ZoneManager.Result> action) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            ZoneManager.Result result = action.apply(server, ZoneActor.of(player));
            if (result.success()) {
                player.sendSystemMessage(result.message());
            } else {
                player.sendSystemMessage(result.message().copy().withStyle(ChatFormatting.RED));
            }
        });
    }
}
