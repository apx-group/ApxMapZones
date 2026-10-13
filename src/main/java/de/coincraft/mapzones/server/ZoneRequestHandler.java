package de.coincraft.mapzones.server;

import de.coincraft.mapzones.Features;
import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.ZoneAccess;
import de.coincraft.mapzones.zone.ZonePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
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

    public static void onPermissions(Payloads.PermissionsRequest payload, IPayloadContext context) {
        run(context, (server, actor) -> {
            if (!Features.PROTECTION) {
                return ZoneManager.Result.fail("message.mapzones.protection_disabled");
            }
            List<String> unknown = new ArrayList<>();
            List<ZoneAccess> players = new ArrayList<>();
            for (Payloads.AccessEntry entry : payload.entries()) {
                UUID uuid = resolve(server, entry.name());
                if (uuid == null) {
                    unknown.add(entry.name());
                    continue;
                }
                players.add(new ZoneAccess(uuid, entry.name(), entry.build(), entry.breakBlocks(), entry.interact()));
            }

            ZonePermissions permissions = new ZonePermissions(
                payload.publicBuild(), payload.publicBreak(), payload.publicInteract(), players);
            ZoneManager.Result result = ZoneManager.setPermissions(server, actor, payload.zoneId(), permissions);

            if (result.success() && !unknown.isEmpty() && context.player() instanceof ServerPlayer player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                    .translatable("message.mapzones.unknown_players", String.join(", ", unknown))
                    .withStyle(ChatFormatting.YELLOW));
            }
            return result;
        });
    }

    /** Spielername zu UUID; nutzt den Profilcache des Servers, sonst die Offline-UUID. */
    private static UUID resolve(MinecraftServer server, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 16) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(trimmed);
        if (online != null) {
            return online.getUUID();
        }
        Optional<GameProfile> profile = server.getProfileCache() == null
            ? Optional.empty()
            : server.getProfileCache().get(trimmed);
        if (profile.isPresent()) {
            return profile.get().getId();
        }
        return server.usesAuthentication() ? null : UUIDUtil.createOfflinePlayerUUID(trimmed);
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
