package de.coincraft.mapzones.server;

import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Serverseitige Ablage aller Zonen; landet in world/data/mapzones.dat. */
public class ZoneStore extends SavedData {
    public static final String DATA_NAME = "mapzones";

    private static final SavedData.Factory<ZoneStore> FACTORY =
        new SavedData.Factory<>(ZoneStore::new, ZoneStore::load, null);

    private final Map<UUID, Zone> zones = new LinkedHashMap<>();

    public static ZoneStore get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = server.getAllLevels().iterator().next();
        }
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ZoneStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ZoneStore store = new ZoneStore();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Zone zone = Zone.load(list.getCompound(i));
            if (zone != null) {
                store.zones.put(zone.id(), zone);
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone zone : zones.values()) {
            list.add(zone.save());
        }
        tag.put("Zones", list);
        return tag;
    }

    public Collection<Zone> all() {
        return zones.values();
    }

    public List<Zone> snapshot() {
        return new ArrayList<>(zones.values());
    }

    public Zone byId(UUID id) {
        return zones.get(id);
    }

    public List<Zone> byName(String name, ResourceLocation dimension) {
        List<Zone> result = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.name().equalsIgnoreCase(name) && (dimension == null || zone.dimension().equals(dimension))) {
                result.add(zone);
            }
        }
        return result;
    }

    /** Beruehrende Zone mit gleichem Namen und gleicher Farbe - Kandidat zum Verschmelzen. */
    public Zone findTouchingSameStyle(ResourceLocation dimension, String name, int color, ChunkRect rect, UUID excludeId) {
        for (Zone zone : zones.values()) {
            if (zone.dimension().equals(dimension) && !zone.id().equals(excludeId)
                && zone.sameStyleAs(name, color) && zone.touches(rect)) {
                return zone;
            }
        }
        return null;
    }

    public Zone findTouchingSameStyle(ResourceLocation dimension, String name, int color, Zone other, UUID excludeId) {
        for (Zone zone : zones.values()) {
            if (zone.dimension().equals(dimension) && !zone.id().equals(excludeId)
                && zone.sameStyleAs(name, color) && zone.touches(other)) {
                return zone;
            }
        }
        return null;
    }

    /** Alle Zonen der Dimension, die den Bereich beruehren. */
    public List<Zone> findTouching(ResourceLocation dimension, ChunkRect rect, UUID excludeId) {
        List<Zone> result = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.dimension().equals(dimension) && !zone.id().equals(excludeId) && zone.touches(rect)) {
                result.add(zone);
            }
        }
        return result;
    }

    /** Zone an einem Chunk; bei Ueberlappung gewinnt die kleinere Zone. */
    public Zone zoneAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        Zone best = null;
        for (Zone zone : zones.values()) {
            if (zone.dimension().equals(dimension) && zone.contains(chunkX, chunkZ)
                && (best == null || zone.chunkCount() < best.chunkCount())) {
                best = zone;
            }
        }
        return best;
    }

    public int countOwnedBy(UUID owner) {
        int count = 0;
        for (Zone zone : zones.values()) {
            if (owner != null && owner.equals(zone.owner())) {
                count++;
            }
        }
        return count;
    }

    public void put(Zone zone) {
        zones.put(zone.id(), zone);
        setDirty();
    }

    public Zone remove(UUID id) {
        Zone removed = zones.remove(id);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public int size() {
        return zones.size();
    }

    public void markChanged() {
        setDirty();
    }
}
