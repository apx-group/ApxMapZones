package de.coincraft.mapzones.server;

import de.coincraft.mapzones.ZoneMessages;
import de.coincraft.mapzones.zone.Zone;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Setzt die Zonenrechte im Spiel durch (nur in der Protect-Variante aktiv).
 *
 * Der Besitzer einer Zone darf immer alles. Alle anderen - auch Spieler mit OP -
 * unterliegen den Einstellungen der Zone. OPs koennen sich aber ueber die Karte
 * selbst eintragen, weil sie jede Zone bearbeiten duerfen.
 */
public final class ZoneProtectionEvents {

    /** Damit die Hinweismeldung beim Dauerklicken nicht spammt. */
    private static final long MESSAGE_INTERVAL_MS = 2000L;

    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private ZoneProtectionEvents() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        Zone zone = zoneAt(player, event.getPos());
        if (zone != null && !zone.isOwner(player.getUUID()) && !zone.permissions().mayBreak(player.getUUID())) {
            deny(event, player, zone, "message.mapzones.denied_break");
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Zone zone = zoneAt(player, event.getPos());
        if (zone != null && !zone.isOwner(player.getUUID()) && !zone.permissions().mayBuild(player.getUUID())) {
            deny(event, player, zone, "message.mapzones.denied_build");
        }
    }

    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        onBlockPlace(event);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Zone zone = zoneAt(player, event.getPos());
        if (zone == null || zone.isOwner(player.getUUID())) {
            return;
        }
        UUID uuid = player.getUUID();
        // Ein Rechtsklick kann benutzen (Truhe) oder bauen (Block setzen) - beides pruefen.
        boolean blockedUse = !zone.permissions().mayInteract(uuid);
        boolean blockedBuild = !zone.permissions().mayBuild(uuid);
        if (!blockedUse && !blockedBuild) {
            return;
        }
        if (blockedUse) {
            // Truhe, Tuer, Knopf: Bloecke lassen sich nicht mehr benutzen.
            event.setUseBlock(TriState.FALSE);
        }
        if (blockedBuild) {
            // Verhindert das Setzen von Bloecken und das Benutzen von Items auf dem Block.
            event.setUseItem(TriState.FALSE);
        }
        if (blockedUse && blockedBuild) {
            event.setCanceled(true);
        }
        notifyDenied(player, zone, blockedUse ? "message.mapzones.denied_interact" : "message.mapzones.denied_build");
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Zone zone = zoneAt(player, event.getPos());
        if (zone != null && !zone.isOwner(player.getUUID()) && !zone.permissions().mayBreak(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static Zone zoneAt(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null || pos == null) {
            return null;
        }
        Level level = player.level();
        Zone zone = ZoneStore.get(server).zoneAt(level.dimension().location(),
            SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return zone != null && !zone.permissions().isOpen() ? zone : null;
    }

    private static void deny(Event event, ServerPlayer player, Zone zone, String messageKey) {
        if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
        notifyDenied(player, zone, messageKey);
    }

    private static void notifyDenied(ServerPlayer player, Zone zone, String messageKey) {
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(player.getUUID());
        if (last != null && now - last < MESSAGE_INTERVAL_MS) {
            return;
        }
        LAST_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(ZoneMessages.of(messageKey,
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color())),
            zone.ownerName() == null || zone.ownerName().isEmpty() ? "-" : zone.ownerName())
            .withStyle(ChatFormatting.RED), true);
    }

    /** Aufraeumen, damit die Hinweis-Zeitstempel nicht ewig wachsen. */
    public static void forget(Player player) {
        LAST_MESSAGE.remove(player.getUUID());
    }
}
