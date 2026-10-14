package de.coincraft.mapzones.client;

import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientseitige Kopie aller Zonen des Servers.
 *
 * Xaero liest Highlights aus eigenen Threads, deshalb wird der Index bei jeder
 * Aenderung neu aufgebaut und als unveraenderliche Momentaufnahme atomar gesetzt.
 */
public final class ClientZones {

    /** Xaero-Kartenregion = 32x32 Chunks. */
    public static final int REGION_SHIFT = 5;

    private static volatile Index index = Index.EMPTY;

    private ClientZones() {
    }

    public static void replaceAll(List<Zone> zones) {
        Map<UUID, Zone> byId = new LinkedHashMap<>();
        for (Zone zone : zones) {
            byId.put(zone.id(), zone.copy());
        }
        index = Index.build(byId);
    }

    public static void upsert(Zone zone) {
        Map<UUID, Zone> byId = new LinkedHashMap<>(index.byId);
        byId.put(zone.id(), zone.copy());
        index = Index.build(byId);
    }

    public static void remove(UUID id) {
        if (!index.byId.containsKey(id)) {
            return;
        }
        Map<UUID, Zone> byId = new LinkedHashMap<>(index.byId);
        byId.remove(id);
        index = Index.build(byId);
    }

    public static void clear() {
        index = Index.EMPTY;
    }

    public static boolean isEmpty() {
        return index.byId.isEmpty();
    }

    public static List<Zone> all() {
        return new ArrayList<>(index.byId.values());
    }

    public static Zone byId(UUID id) {
        return index.byId.get(id);
    }

    public static List<Zone> inRegion(ResourceLocation dimension, int regionX, int regionZ) {
        Map<Long, List<Zone>> regions = index.byRegion.get(dimension);
        if (regions == null) {
            return Collections.emptyList();
        }
        List<Zone> zones = regions.get(regionKey(regionX, regionZ));
        return zones == null ? Collections.emptyList() : zones;
    }

    /** Zone an einem Chunk; bei Ueberlappung gewinnt die kleinere Zone. */
    public static Zone zoneAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        List<Zone> candidates = inRegion(dimension, chunkX >> REGION_SHIFT, chunkZ >> REGION_SHIFT);
        Zone best = null;
        for (int i = 0; i < candidates.size(); i++) {
            Zone zone = candidates.get(i);
            if (zone.contains(chunkX, chunkZ) && (best == null || zone.chunkCount() < best.chunkCount())) {
                best = zone;
            }
        }
        return best;
    }

    /** Zonen der Dimension, die den Bereich beruehren oder ueberlappen. */
    public static List<Zone> zonesTouching(ResourceLocation dimension, ChunkRect rect) {
        List<Zone> result = new ArrayList<>();
        for (Zone zone : index.byId.values()) {
            if (zone.dimension().equals(dimension) && zone.touches(rect)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * true, wenn der lokale Spieler diese Zone aendern darf - Besitzer oder OP.
     *
     * Nur fuer die Oberflaeche; entschieden wird immer auf dem Server.
     */
    public static boolean canEditLocally(Zone zone) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || zone == null) {
            return false;
        }
        return player.hasPermissions(2) || zone.isOwner(player.getUUID());
    }

    /** Erste Zone, die den Bereich ueberdeckt und der Spieler nicht aendern darf; sonst null. */
    public static Zone foreignOverlap(ResourceLocation dimension, ChunkRect rect) {
        for (Zone zone : index.byId.values()) {
            if (zone.dimension().equals(dimension) && zone.overlaps(rect) && !canEditLocally(zone)) {
                return zone;
            }
        }
        return null;
    }

    /** Andere Zonen, die an die angegebene Zone angrenzen - Kandidaten zum Verbinden. */
    public static List<Zone> neighboursOf(Zone zone) {
        List<Zone> result = new ArrayList<>();
        for (Zone other : index.byId.values()) {
            if (!other.id().equals(zone.id()) && other.dimension().equals(zone.dimension()) && other.touches(zone)) {
                result.add(other);
            }
        }
        return result;
    }

    public static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    private record Index(Map<UUID, Zone> byId, Map<ResourceLocation, Map<Long, List<Zone>>> byRegion) {

        static final Index EMPTY = new Index(Collections.emptyMap(), Collections.emptyMap());

        static Index build(Map<UUID, Zone> byId) {
            Map<ResourceLocation, Map<Long, List<Zone>>> byRegion = new HashMap<>();
            for (Zone zone : byId.values()) {
                Map<Long, List<Zone>> regions = byRegion.computeIfAbsent(zone.dimension(), key -> new HashMap<>());
                for (ChunkRect rect : zone.rects()) {
                    int minRegionX = rect.minChunkX() >> REGION_SHIFT;
                    int maxRegionX = rect.maxChunkX() >> REGION_SHIFT;
                    int minRegionZ = rect.minChunkZ() >> REGION_SHIFT;
                    int maxRegionZ = rect.maxChunkZ() >> REGION_SHIFT;
                    for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                            List<Zone> list = regions.computeIfAbsent(regionKey(regionX, regionZ), key -> new ArrayList<>());
                            if (!list.contains(zone)) {
                                list.add(zone);
                            }
                        }
                    }
                }
            }
            for (Map<Long, List<Zone>> regions : byRegion.values()) {
                regions.replaceAll((key, zones) -> Collections.unmodifiableList(zones));
            }
            return new Index(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byRegion));
        }
    }
}
