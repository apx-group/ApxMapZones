package de.coincraft.mapzones.server;

import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import de.coincraft.mapzones.zone.ZonePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

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

    /** Ergebnis einer Aenderung. Bei Misserfolg steht die Begruendung in {@link #message()}. */
    public record Result(boolean success, Component message, Zone zone) {

        static Result fail(String key, Object... args) {
            return new Result(false, Component.translatable(key, args), null);
        }

        static Result ok(Component message, Zone zone) {
            return new Result(true, message, zone);
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
            return Result.fail("message.mapzones.limit_size", ZoneRules.MAX_CHUNKS_PER_ZONE, rect.chunkCount());
        }

        ZoneStore store = ZoneStore.get(server);

        Zone target = store.findTouchingSameStyle(dimension, name, color, rect, null);
        if (target != null) {
            Result extended = addArea(server, actor, target.id(), rect);
            if (extended.success()) {
                return Result.ok(Component.translatable("message.mapzones.extended",
                    Component.literal(target.name()).withStyle(style -> style.withColor(target.color()))), target);
            }
            return extended;
        }

        if (store.size() >= ZoneRules.MAX_ZONES_TOTAL) {
            return Result.fail("message.mapzones.limit_total", ZoneRules.MAX_ZONES_TOTAL);
        }
        if (!actor.admin() && actor.uuid() != null && store.countOwnedBy(actor.uuid()) >= ZoneRules.MAX_ZONES_PER_PLAYER) {
            return Result.fail("message.mapzones.limit_player", ZoneRules.MAX_ZONES_PER_PLAYER);
        }

        Zone zone = new Zone(UUID.randomUUID(), name, color, dimension, rect, actor.uuid(), actor.name());
        store.put(zone);
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(Component.translatable("message.mapzones.created",
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
            return Result.fail("message.mapzones.no_permission", ownerLabel(zone));
        }
        if (!zone.touches(rect)) {
            return Result.fail("message.mapzones.not_adjacent");
        }
        if (zone.chunkCount() + rect.chunkCount() > ZoneRules.MAX_CHUNKS_PER_ZONE) {
            return Result.fail("message.mapzones.limit_size",
                ZoneRules.MAX_CHUNKS_PER_ZONE, zone.chunkCount() + rect.chunkCount());
        }
        if (zone.rectCount() >= Zone.MAX_RECTS) {
            return Result.fail("message.mapzones.limit_parts", Zone.MAX_RECTS);
        }

        zone.addRect(rect);
        absorbSameStyleNeighbours(server, store, zone);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(Component.translatable("message.mapzones.extended",
            Component.literal(zone.name()).withStyle(style -> style.withColor(zone.color()))), zone);
    }

    /** Verbindet zwei beruehrende Zonen; Name, Farbe und Besitzer der Zielzone bleiben. */
    public static Result merge(MinecraftServer server, ZoneActor actor, UUID targetId, UUID otherId) {
        ZoneStore store = ZoneStore.get(server);
        Zone target = store.byId(targetId);
        Zone other = store.byId(otherId);
        if (target == null || other == null || target == other) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, target)) {
            return Result.fail("message.mapzones.no_permission", ownerLabel(target));
        }
        if (!ZoneRules.canEdit(actor, other)) {
            return Result.fail("message.mapzones.no_permission", ownerLabel(other));
        }
        if (!target.touches(other)) {
            return Result.fail("message.mapzones.not_adjacent");
        }
        if (target.chunkCount() + other.chunkCount() > ZoneRules.MAX_CHUNKS_PER_ZONE) {
            return Result.fail("message.mapzones.limit_size",
                ZoneRules.MAX_CHUNKS_PER_ZONE, target.chunkCount() + other.chunkCount());
        }
        if (target.rectCount() + other.rectCount() > Zone.MAX_RECTS) {
            return Result.fail("message.mapzones.limit_parts", Zone.MAX_RECTS);
        }

        String otherName = other.name();
        absorb(server, store, target, other);
        absorbSameStyleNeighbours(server, store, target);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, target);
        return Result.ok(Component.translatable("message.mapzones.merged",
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
            return Result.fail("message.mapzones.no_permission", ownerLabel(zone));
        }
        String name = ZoneRules.sanitizeName(rawName);
        if (name.isEmpty()) {
            return Result.fail("message.mapzones.name_empty");
        }

        zone.setName(name);
        zone.setColor(color);
        absorbSameStyleNeighbours(server, store, zone);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(Component.translatable("message.mapzones.updated",
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
            return Result.fail("message.mapzones.no_permission", ownerLabel(zone));
        }

        long remaining = cooldownRemaining(actor);
        if (remaining > 0) {
            return Result.fail("message.mapzones.permission_cooldown", (remaining + 999) / 1000);
        }
        if (actor.uuid() != null && !actor.admin()) {
            LAST_PERMISSION_EDIT.put(actor.uuid(), System.currentTimeMillis());
        }

        zone.setPermissions(permissions);
        store.markChanged();
        ZoneSync.broadcastUpsert(server, zone);
        return Result.ok(Component.translatable("message.mapzones.permissions_updated",
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

    public static Result delete(MinecraftServer server, ZoneActor actor, UUID zoneId) {
        ZoneStore store = ZoneStore.get(server);
        Zone zone = store.byId(zoneId);
        if (zone == null) {
            return Result.fail("message.mapzones.unknown_zone");
        }
        if (!ZoneRules.canEdit(actor, zone)) {
            return Result.fail("message.mapzones.no_permission", ownerLabel(zone));
        }
        store.remove(zone.id());
        ZoneSync.broadcastRemoved(server, zone.id());
        return Result.ok(Component.translatable("message.mapzones.deleted", zone.name()), zone);
    }

    /** Zieht alle beruehrenden Zonen mit gleichem Namen und gleicher Farbe in diese Zone hinein. */
    private static void absorbSameStyleNeighbours(MinecraftServer server, ZoneStore store, Zone zone) {
        boolean merged = true;
        while (merged) {
            merged = false;
            Zone neighbour = store.findTouchingSameStyle(zone.dimension(), zone.name(), zone.color(), zone, zone.id());
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
