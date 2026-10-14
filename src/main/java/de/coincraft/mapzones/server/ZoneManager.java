package de.coincraft.mapzones.server;

import de.coincraft.mapzones.ZoneMessages;
import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import de.coincraft.mapzones.zone.ZonePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Die gesamte Zonen-Logik an einer Stelle, damit Kartenoberflaeche und Befehle
 * sich identisch verhalten.
 */
public final class ZoneManager {

    /** Normale Spieler duerfen Rechte nur im Abstand von 10 Sekunden aendern. */
    public static final long PERMISSION_COOLDOWN_MS = 10_000L;

    private static final Map<UUID, Long> LAST_PERMISSION_EDIT = new HashMap<>();

    private ZoneManager() {
    }

    /** Ergebnis einer Aenderung. Wurde nichts geaendert, steht der Grund in {@link #message()}. */
    public record Result(Kind kind, Component message, Zone zone) {

        /**
         * Wie die Nachricht beim Spieler ankommt.
         *
         * HINT ist keine Fehlermeldung, sondern eine Erklaerung der Spielregeln -
         * etwa dass ein Gebiet schon jemandem gehoert. Nur PROBLEM meldet etwas,
         * das tatsaechlich schiefgelaufen ist.
         */
        public enum Kind { SUCCESS, HINT, PROBLEM }

        public boolean success() {
            return kind == Kind.SUCCESS;
        }

        /** Etwas stimmt nicht - unbekannte Zone, leerer Name, fehlende Funktion. */
        public static Result fail(String key, Object... args) {
            return new Result(Kind.PROBLEM, ZoneMessages.of(key, args), null);
        }

        /** Die Aktion war nicht erlaubt oder nicht moeglich; der Spieler erfaehrt warum. */
        public static Result hint(String key, Object... args) {
            return new Result(Kind.HINT, ZoneMessages.of(key, args), null);
        }

        static Result ok(Component message, Zone zone) {
            return new Result(Kind.SUCCESS, message, zone);
        }
    }

    /**
     * Legt eine Zone an - oder erweitert eine beruehrende Zone mit gleichem Namen und
     * gleicher Farbe, sodass beide zu einer Flaeche werden.
     */
    public static Result createOrExtend(MinecraftServer server, ZoneActor actor, String rawName, int color,
                                        ResourceLocation dimension, ChunkRect rect) {
        String name = ZoneRules.sanitizeName(rawName);
        if (name.isEmpty()) {
            return Result.fail("message.mapzones.name_empty");
        }
        if (rect.chunkCount() > ZoneRules.MAX_CHUNKS_PER_ZONE) {
            return Result.hint("message.mapzones.limit_size", ZoneRules.MAX_CHUNKS_PER_ZONE, rect.chunkCount());
        }

        ZoneStore store = ZoneStore.get(server);

        // Auf einer fremden Zone darf niemand eine Zone anlegen - auch keine kleinere darin.
        Zone blocking = ZoneRules.findForeignOverlap(store, actor, dimension, rect, null);
        if (blocking != null) {
            return Result.fail("message.mapzones.overlaps_foreign", blocking.name(), ownerLabel(blocking));
        }

        // Nur eigene Zonen gleichen Stils werden erweitert; fremde bleiben unberuehrt.
        Zone target = store.findTouchingSameStyle(dimension, name, color, rect, null,
            zone -> ZoneRules.canEdit(actor, zone));
        if (target != null) {
            Result extended = addArea(server, actor, target.id(), rect);
            if (extended.success()) {
                return Result.ok(ZoneMessages.of("message.mapzones.extended",
                    Component.literal(target.name()).withStyle(style -> style.withColor(target.color()))), target);
            }
            return extended;
        }

        if (store.size() >= ZoneRules.MAX_ZONES_TOTAL) {
            return Result.hint("message.mapzones.limit_total", ZoneRules.MAX_ZONES_TOTAL);
        }
        if (!actor.admin() && actor.uuid() != null && store.countOwnedBy(actor.uuid()) >= ZoneRules.MAX_ZONES_PER_PLAYER) {
            return Result.hint("message.mapzones.limit_player", ZoneRules.MAX_ZONES_PER_PLAYER);
        }

        Zone zone = new Zone(UUID.randomUUID(), name, color, dimension, rect, actor.uuid(), actor.name());
        store.put(zone);
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(ZoneMessages.of("message.mapzones.created",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color()))), zone);
    }

    /** Fuegt einer bestehenden Zone einen weiteren Bereich hinzu. */
    public static Result addArea(MinecraftServer server, ZoneActor actor, UUID zoneId, ChunkRect rect) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(zone));
        }
        Zone blocking = ZoneRules.findForeignOverlap(store, actor, zone.dimension(), rect, zone.id());
        if (blocking != null) {
            return Result.fail("message.mapzones.overlaps_foreign", blocking.name(), ownerLabel(blocking));
        }
        if (!zone.touches(rect)) {
            return Result.hint("message.mapzones.not_adjacent");
        }
        if (zone.chunkCount() + rect.chunkCount() > ZoneRules.MAX_CHUNKS_PER_ZONE) {
            return Result.hint("message.mapzones.limit_size",
                ZoneRules.MAX_CHUNKS_PER_ZONE, zone.chunkCount() + rect.chunkCount());
        }
        if (zone.rectCount() >= Zone.MAX_RECTS) {
            return Result.hint("message.mapzones.limit_parts", Zone.MAX_RECTS);
        }

        zone.addRect(rect);
        absorbSameStyleNeighbours(server, store, actor, zone);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(ZoneMessages.of("message.mapzones.extended",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color()))), zone);
    }

    /**
     * Traegt die Zone {@code otherId} in die Zone {@code targetId} hinein.
     *
     * Die Zielzone bleibt mit Name, Farbe und Besitzer bestehen und uebernimmt die
     * Flaechen der anderen; die andere Zone verschwindet. Beide muessen dem
     * Handelnden gehoeren (oder er ist OP).
     */
    public static Result merge(MinecraftServer server, ZoneActor actor, UUID targetId, UUID otherId) {
        ZoneStore store = ZoneStore.get(server);
        Zone target = store.byId(targetId);
        Zone other = store.byId(otherId);
        if (target == null || other == null || target == other) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, target)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(target));
        }
        if (!ZoneRules.canEdit(actor, other)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(other));
        }
        if (!target.touches(other)) {
            return Result.hint("message.mapzones.not_adjacent");
        }
        if (target.chunkCount() + other.chunkCount() > ZoneRules.MAX_CHUNKS_PER_ZONE) {
            return Result.hint("message.mapzones.limit_size",
                ZoneRules.MAX_CHUNKS_PER_ZONE, target.chunkCount() + other.chunkCount());
        }
        if (target.rectCount() + other.rectCount() > Zone.MAX_RECTS) {
            return Result.hint("message.mapzones.limit_parts", Zone.MAX_RECTS);
        }

        String otherName = other.name();
        absorb(server, store, target, other);
        absorbSameStyleNeighbours(server, store, actor, target);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, target);
        return Result.ok(ZoneMessages.of("message.mapzones.merged",
            Component.literal(otherName),
            Component.literal(target.name()).withStyle(style -> style.withColor(target.color()))), target);
    }

    /** Aendert Name und Farbe und verschmilzt die Zone danach mit passenden Nachbarn. */
    public static Result edit(MinecraftServer server, ZoneActor actor, UUID zoneId, String rawName, int color) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(zone));
        }
        String name = ZoneRules.sanitizeName(rawName);
        if (name.isEmpty()) {
            return Result.fail("message.mapzones.name_empty");
        }

        zone.setName(name);
        zone.setColor(color);
        absorbSameStyleNeighbours(server, store, actor, zone);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(ZoneMessages.of("message.mapzones.updated",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color()))), zone);
    }

    /** Setzt die Bau-, Abbau- und Benutzungsrechte einer Zone. */
    public static Result setPermissions(MinecraftServer server, ZoneActor actor, UUID zoneId, ZonePermissions permissions) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(zone));
        }

        long remaining = cooldownRemaining(actor);
        if (remaining > 0) {
            return Result.hint("message.mapzones.permission_cooldown", (remaining + 999) / 1000);
        }
        if (actor.uuid() != null && !actor.admin()) {
            LAST_PERMISSION_EDIT.put(actor.uuid(), System.currentTimeMillis());
        }

        zone.setPermissions(permissions);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(ZoneMessages.of("message.mapzones.permissions_updated",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color()))), zone);
    }

    /** Verbleibende Wartezeit in Millisekunden; OPs und die Konsole warten nie. */
    public static long cooldownRemaining(ZoneActor actor) {
        if (actor.admin() || actor.uuid() == null) {
            return 0L;
        }
        Long last = LAST_PERMISSION_EDIT.get(actor.uuid());
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        return elapsed >= PERMISSION_COOLDOWN_MS ? 0L : PERMISSION_COOLDOWN_MS - elapsed;
    }

    /**
     * Uebergibt eine Zone an einen anderen Spieler.
     *
     * Der bisherige Besitzer gibt damit die volle Kontrolle ab: er darf die Zone
     * danach weder erweitern noch umbenennen, loeschen oder Rechte darin setzen.
     * Zurueckholen kann sie nur der neue Besitzer (oder ein OP).
     */
    public static Result transfer(MinecraftServer server, ZoneActor actor, UUID zoneId,
                                  UUID targetUuid, String targetName) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(zone));
        }
        if (targetUuid == null) {
            return Result.fail("message.mapzones.unknown_players",
                targetName == null || targetName.isEmpty() ? "?" : targetName);
        }
        if (targetUuid.equals(zone.owner())) {
            return Result.hint("message.mapzones.transfer_same", ownerLabel(zone));
        }
        if (store.countOwnedBy(targetUuid) >= ZoneRules.MAX_ZONES_PER_PLAYER) {
            return Result.hint("message.mapzones.transfer_limit", targetName, ZoneRules.MAX_ZONES_PER_PLAYER);
        }

        zone.setOwner(targetUuid, targetName);
        // Als Besitzer darf er ohnehin alles - ein Ausnahmeeintrag fuer ihn ist jetzt sinnlos.
        zone.permissions().remove(targetUuid);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);

        ServerPlayer receiver = server.getPlayerList().getPlayer(targetUuid);
        if (receiver != null) {
            receiver.sendSystemMessage(ZoneMessages.of("message.mapzones.transfer_received",
                actor.name() == null ? "-" : actor.name(),
                Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color())))
                .withStyle(ChatFormatting.GREEN));
        }
        return Result.ok(ZoneMessages.of("message.mapzones.transferred",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color())),
            targetName), zone);
    }

    public static Result delete(MinecraftServer server, ZoneActor actor, UUID zoneId) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.hint("message.mapzones.no_permission", ownerLabel(zone));
        }
        store.remove(zone.id());
        ZoneSync.broadcastRemoved(server, zone.id());
        return Result.ok(ZoneMessages.of("message.mapzones.deleted", zone.name()), zone);
    }

    /**
     * Zieht alle beruehrenden Zonen mit gleichem Namen und gleicher Farbe in diese Zone hinein.
     * Fremde Zonen bleiben dabei stehen, sonst koennte man sie ueber Name und Farbe uebernehmen.
     */
    private static void absorbSameStyleNeighbours(MinecraftServer server, ZoneStore store, ZoneActor actor, Zone zone) {
        boolean merged = true;
        while (merged) {
            merged = false;
            Zone neighbour = store.findTouchingSameStyle(zone.dimension(), zone.name(), zone.color(), zone, zone.id(),
                candidate -> ZoneRules.canEdit(actor, candidate));
            if (neighbour != null
                && zone.chunkCount() + neighbour.chunkCount() <= ZoneRules.MAX_CHUNKS_PER_ZONE
                && zone.rectCount() + neighbour.rectCount() <= Zone.MAX_RECTS) {
                absorb(server, store, zone, neighbour);
                merged = true;
            }
        }
    }

    private static void absorb(MinecraftServer server, ZoneStore store, Zone target, Zone other) {
        for (ChunkRect rect : other.rects()) {
            target.addRect(rect);
        }
        store.remove(other.id());
        ZoneSync.broadcastRemoved(server, other.id());
    }

    private static String ownerLabel(Zone zone) {
        return zone.ownerName() == null || zone.ownerName().isEmpty() ? "-" : zone.ownerName();
    }
}
