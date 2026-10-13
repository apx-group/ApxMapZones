package de.coincraft.mapzones.zone;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Die Rechte eines einzelnen Spielers innerhalb einer Zone. */
public record ZoneAccess(UUID uuid, String name, boolean build, boolean breakBlocks, boolean interact) {

    public static final int MAX_NAME_LENGTH = 16;

    public ZoneAccess withFlags(boolean build, boolean breakBlocks, boolean interact) {
        return new ZoneAccess(uuid, name, build, breakBlocks, interact);
    }

    /** true, wenn der Eintrag nichts mehr erlaubt und damit entfallen kann. */
    public boolean isEmpty() {
        return !build && !breakBlocks && !interact;
    }

    public long renderHash() {
        long hash = uuid == null ? 0L : uuid.getMostSignificantBits() * 37L + uuid.getLeastSignificantBits();
        hash = hash * 37L + (build ? 1 : 0);
        hash = hash * 37L + (breakBlocks ? 1 : 0);
        hash = hash * 37L + (interact ? 1 : 0);
        return hash;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Uuid", uuid);
        tag.putString("Name", name == null ? "" : name);
        tag.putBoolean("Build", build);
        tag.putBoolean("Break", breakBlocks);
        tag.putBoolean("Interact", interact);
        return tag;
    }

    public static ZoneAccess load(CompoundTag tag) {
        if (!tag.hasUUID("Uuid")) {
            return null;
        }
        return new ZoneAccess(
            tag.getUUID("Uuid"),
            tag.getString("Name"),
            tag.getBoolean("Build"),
            tag.getBoolean("Break"),
            tag.getBoolean("Interact")
        );
    }

    public static final StreamCodec<FriendlyByteBuf, ZoneAccess> STREAM_CODEC = StreamCodec.of(
        (buf, access) -> {
            buf.writeUUID(access.uuid);
            buf.writeUtf(access.name == null ? "" : access.name, MAX_NAME_LENGTH);
            buf.writeBoolean(access.build);
            buf.writeBoolean(access.breakBlocks);
            buf.writeBoolean(access.interact);
        },
        buf -> new ZoneAccess(buf.readUUID(), buf.readUtf(MAX_NAME_LENGTH),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );
}
