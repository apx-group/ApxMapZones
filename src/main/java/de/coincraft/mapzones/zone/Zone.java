package de.coincraft.mapzones.zone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Ein benannter, gefaerbter Bereich in einer Dimension.
 *
 * Eine Zone besteht aus beliebig vielen Chunk-Rechtecken, damit sich auch
 * nicht-rechteckige Gebiete markieren lassen. Zwischen den Teilen einer Zone
 * zeichnet die Karte keine Umrandung - sie wirkt wie eine einzige Flaeche.
 */
public final class Zone {
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_RECTS = 256;

    private final UUID id;
    private final ResourceLocation dimension;
    private final List<ChunkRect> rects = new ArrayList<>();
    private UUID owner;
    private String ownerName;
    private String name;
    private int color;
    private ZonePermissions permissions = new ZonePermissions();

    public Zone(UUID id, String name, int color, ResourceLocation dimension,
                List<ChunkRect> rects, UUID owner, String ownerName) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.dimension = dimension;
        this.owner = owner;
        this.ownerName = ownerName;
        for (ChunkRect rect : rects) {
            addRect(rect);
        }
    }

    public Zone(UUID id, String name, int color, ResourceLocation dimension,
                ChunkRect rect, UUID owner, String ownerName) {
        this(id, name, color, dimension, List.of(rect), owner, ownerName);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Zonenfarbe als 0xRRGGBB. */
    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color & 0xFFFFFF;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    /**
     * Setzt einen neuen Besitzer. Der bisherige Besitzer verliert damit alle
     * Besitzerrechte an dieser Zone - siehe ZoneManager#transfer.
     */
    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /** Bau-, Abbau- und Benutzungsrechte dieser Zone. */
    public ZonePermissions permissions() {
        return permissions;
    }

    public void setPermissions(ZonePermissions permissions) {
        this.permissions = permissions == null ? new ZonePermissions() : permissions;
    }

    /** true, wenn der Spieler die Zone besitzt und daher nie eingeschraenkt wird. */
    public boolean isOwner(UUID uuid) {
        return owner != null && uuid != null && owner.equals(uuid);
    }

    public List<ChunkRect> rects() {
        return Collections.unmodifiableList(rects);
    }

    public int rectCount() {
        return rects.size();
    }

    /** Fuegt einen Bereich hinzu; vollstaendig enthaltene Bereiche werden ignoriert. */
    public boolean addRect(ChunkRect rect) {
        for (ChunkRect existing : rects) {
            if (rect.isInside(existing)) {
                return false;
            }
        }
        rects.removeIf(existing -> existing.isInside(rect));
        rects.add(rect);
        return true;
    }

    public boolean contains(int chunkX, int chunkZ) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    /** true, wenn der Bereich diese Zone beruehrt oder ueberlappt. */
    public boolean touches(ChunkRect rect) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).touches(rect)) {
                return true;
            }
        }
        return false;
    }

    /** true, wenn der Bereich eine Flaeche dieser Zone ueberdeckt; blosses Angrenzen zaehlt nicht. */
    public boolean overlaps(ChunkRect rect) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).overlaps(rect)) {
                return true;
            }
        }
        return false;
    }

    /** true, wenn beide Zonen zusammenhaengen und daher verbunden werden koennen. */
    public boolean touches(Zone other) {
        if (!dimension.equals(other.dimension)) {
            return false;
        }
        for (ChunkRect rect : other.rects) {
            if (touches(rect)) {
                return true;
            }
        }
        return false;
    }

    /** Gleiche Beschriftung und Farbe - solche Zonen verschmelzen beim Beruehren automatisch. */
    public boolean sameStyleAs(String otherName, int otherColor) {
        return color == (otherColor & 0xFFFFFF) && name.equalsIgnoreCase(otherName);
    }

    /** Ungefaehre Groesse; Ueberlappungen innerhalb einer Zone werden doppelt gezaehlt. */
    public int chunkCount() {
        int count = 0;
        for (int i = 0; i < rects.size(); i++) {
            count += rects.get(i).chunkCount();
        }
        return count;
    }

    public int minChunkX() {
        int value = Integer.MAX_VALUE;
        for (ChunkRect rect : rects) {
            value = Math.min(value, rect.minChunkX());
        }
        return value;
    }

    public int minChunkZ() {
        int value = Integer.MAX_VALUE;
        for (ChunkRect rect : rects) {
            value = Math.min(value, rect.minChunkZ());
        }
        return value;
    }

    public int maxChunkX() {
        int value = Integer.MIN_VALUE;
        for (ChunkRect rect : rects) {
            value = Math.max(value, rect.maxChunkX());
        }
        return value;
    }

    public int maxChunkZ() {
        int value = Integer.MIN_VALUE;
        for (ChunkRect rect : rects) {
            value = Math.max(value, rect.maxChunkZ());
        }
        return value;
    }

    /** Hash-Beitrag fuer Xaeros Region-Cache: aendert sich, sobald sich die Darstellung aendert. */
    public long renderHash() {
        long hash = id.getMostSignificantBits() * 37L + id.getLeastSignificantBits();
        hash = hash * 37L + color;
        hash = hash * 37L + name.hashCode();
        for (ChunkRect rect : rects) {
            hash = hash * 37L + rect.renderHash();
        }
        return hash;
    }

    public Zone copy() {
        Zone copy = new Zone(id, name, color, dimension, new ArrayList<>(rects), owner, ownerName);
        copy.permissions = permissions.copy();
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putInt("Color", color);
        tag.putString("Dimension", dimension.toString());
        ListTag rectList = new ListTag();
        for (ChunkRect rect : rects) {
            rectList.add(rect.save());
        }
        tag.put("Rects", rectList);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName == null ? "" : ownerName);
        tag.put("Permissions", permissions.save());
        return tag;
    }

    public static Zone load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dim == null) {
            return null;
        }
        List<ChunkRect> rects = new ArrayList<>();
        if (tag.contains("Rects", Tag.TAG_LIST)) {
            ListTag rectList = tag.getList("Rects", Tag.TAG_COMPOUND);
            for (int i = 0; i < rectList.size(); i++) {
                rects.add(ChunkRect.load(rectList.getCompound(i)));
            }
        } else {
            // Format der Version 1.0.0: genau ein Rechteck direkt in der Zone.
            rects.add(ChunkRect.load(tag));
        }
        if (rects.isEmpty()) {
            return null;
        }
        Zone zone = new Zone(
            tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
            tag.getString("Name"),
            tag.getInt("Color"),
            dim,
            rects,
            tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
            tag.getString("OwnerName")
        );
        if (tag.contains("Permissions", Tag.TAG_COMPOUND)) {
            zone.setPermissions(ZonePermissions.load(tag.getCompound("Permissions")));
        }
        return zone;
    }

    public static final StreamCodec<FriendlyByteBuf, Zone> STREAM_CODEC = StreamCodec.of(
        (buf, zone) -> {
            buf.writeUUID(zone.id);
            buf.writeUtf(zone.name, MAX_NAME_LENGTH);
            buf.writeInt(zone.color);
            buf.writeResourceLocation(zone.dimension);
            buf.writeVarInt(zone.rects.size());
            for (ChunkRect rect : zone.rects) {
                ChunkRect.STREAM_CODEC.encode(buf, rect);
            }
            buf.writeBoolean(zone.owner != null);
            if (zone.owner != null) {
                buf.writeUUID(zone.owner);
            }
            buf.writeUtf(zone.ownerName == null ? "" : zone.ownerName, 32);
            ZonePermissions.STREAM_CODEC.encode(buf, zone.permissions);
        },
        buf -> {
            UUID id = buf.readUUID();
            String name = buf.readUtf(MAX_NAME_LENGTH);
            int color = buf.readInt();
            ResourceLocation dimension = buf.readResourceLocation();
            int rectCount = buf.readVarInt();
            List<ChunkRect> rects = new ArrayList<>(rectCount);
            for (int i = 0; i < rectCount; i++) {
                rects.add(ChunkRect.STREAM_CODEC.decode(buf));
            }
            UUID owner = buf.readBoolean() ? buf.readUUID() : null;
            String ownerName = buf.readUtf(32);
            Zone zone = new Zone(id, name, color, dimension, rects, owner, ownerName);
            zone.setPermissions(ZonePermissions.STREAM_CODEC.decode(buf));
            return zone;
        }
    );
}
