package de.coincraft.mapzones.zone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Ein rechteckiger Chunk-Bereich (Grenzen inklusive). */
public record ChunkRect(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {

    public static ChunkRect of(int chunkX1, int chunkZ1, int chunkX2, int chunkZ2) {
        return new ChunkRect(
            Math.min(chunkX1, chunkX2),
            Math.min(chunkZ1, chunkZ2),
            Math.max(chunkX1, chunkX2),
            Math.max(chunkZ1, chunkZ2)
        );
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }

    public int chunkCount() {
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }

    public boolean overlaps(ChunkRect other) {
        return minChunkX <= other.maxChunkX && maxChunkX >= other.minChunkX
            && minChunkZ <= other.maxChunkZ && maxChunkZ >= other.minChunkZ;
    }

    /** true, wenn die Rechtecke sich beruehren oder ueberlappen - also zusammenhaengen. */
    public boolean touches(ChunkRect other) {
        return grow(1).overlaps(other);
    }

    public ChunkRect grow(int amount) {
        return new ChunkRect(minChunkX - amount, minChunkZ - amount, maxChunkX + amount, maxChunkZ + amount);
    }

    /** true, wenn dieses Rechteck vollstaendig in dem anderen liegt. */
    public boolean isInside(ChunkRect other) {
        return minChunkX >= other.minChunkX && maxChunkX <= other.maxChunkX
            && minChunkZ >= other.minChunkZ && maxChunkZ <= other.maxChunkZ;
    }

    public long renderHash() {
        long hash = minChunkX;
        hash = hash * 37L + minChunkZ;
        hash = hash * 37L + maxChunkX;
        hash = hash * 37L + maxChunkZ;
        return hash;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinChunkX", minChunkX);
        tag.putInt("MinChunkZ", minChunkZ);
        tag.putInt("MaxChunkX", maxChunkX);
        tag.putInt("MaxChunkZ", maxChunkZ);
        return tag;
    }

    public static ChunkRect load(CompoundTag tag) {
        return ChunkRect.of(
            tag.getInt("MinChunkX"),
            tag.getInt("MinChunkZ"),
            tag.getInt("MaxChunkX"),
            tag.getInt("MaxChunkZ")
        );
    }

    public static final StreamCodec<FriendlyByteBuf, ChunkRect> STREAM_CODEC = StreamCodec.of(
        (buf, rect) -> {
            buf.writeVarInt(rect.minChunkX);
            buf.writeVarInt(rect.minChunkZ);
            buf.writeVarInt(rect.maxChunkX);
            buf.writeVarInt(rect.maxChunkZ);
        },
        buf -> ChunkRect.of(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    );
}
