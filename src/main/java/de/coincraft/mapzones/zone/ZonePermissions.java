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

/**
 * Wer in einer Zone bauen, abbauen und Bloecke benutzen darf.
 *
 * Standard ist offen: alle drei Freigaben stehen auf true, es gilt also keine
 * Einschraenkung. Erst wenn der Besitzer eine Freigabe abschaltet, zaehlt die
 * Spielerliste. Der Besitzer der Zone darf immer alles.
 */
public final class ZonePermissions {

    public static final int MAX_PLAYERS = 64;

    private boolean publicBuild = true;
    private boolean publicBreak = true;
    private boolean publicInteract = true;
    private final List<ZoneAccess> players = new ArrayList<>();

    public ZonePermissions() {
    }

    public ZonePermissions(boolean publicBuild, boolean publicBreak, boolean publicInteract, List<ZoneAccess> players) {
        this.publicBuild = publicBuild;
        this.publicBreak = publicBreak;
        this.publicInteract = publicInteract;
        for (ZoneAccess access : players) {
            put(access);
        }
    }

    public boolean publicBuild() {
        return publicBuild;
    }

    public boolean publicBreak() {
        return publicBreak;
    }

    public boolean publicInteract() {
        return publicInteract;
    }

    public void setPublicBuild(boolean value) {
        this.publicBuild = value;
    }

    public void setPublicBreak(boolean value) {
        this.publicBreak = value;
    }

    public void setPublicInteract(boolean value) {
        this.publicInteract = value;
    }

    /** true, wenn nichts eingeschraenkt ist - dann greift der Schutz gar nicht. */
    public boolean isOpen() {
        return publicBuild && publicBreak && publicInteract;
    }

    public List<ZoneAccess> players() {
        return Collections.unmodifiableList(players);
    }

    public ZoneAccess forPlayer(UUID uuid) {
        for (int i = 0; i < players.size(); i++) {
            ZoneAccess access = players.get(i);
            if (access.uuid().equals(uuid)) {
                return access;
            }
        }
        return null;
    }

    /** Fuegt einen Eintrag hinzu oder ersetzt den vorhandenen desselben Spielers. */
    public void put(ZoneAccess access) {
        if (access == null || access.uuid() == null) {
            return;
        }
        players.removeIf(existing -> existing.uuid().equals(access.uuid()));
        if (!access.isEmpty() && players.size() < MAX_PLAYERS) {
            players.add(access);
        }
    }

    public void remove(UUID uuid) {
        players.removeIf(existing -> existing.uuid().equals(uuid));
    }

    public boolean mayBuild(UUID uuid) {
        if (publicBuild) {
            return true;
        }
        ZoneAccess access = forPlayer(uuid);
        return access != null && access.build();
    }

    public boolean mayBreak(UUID uuid) {
        if (publicBreak) {
            return true;
        }
        ZoneAccess access = forPlayer(uuid);
        return access != null && access.breakBlocks();
    }

    public boolean mayInteract(UUID uuid) {
        if (publicInteract) {
            return true;
        }
        ZoneAccess access = forPlayer(uuid);
        return access != null && access.interact();
    }

    public ZonePermissions copy() {
        return new ZonePermissions(publicBuild, publicBreak, publicInteract, players);
    }

    public long renderHash() {
        long hash = (publicBuild ? 1 : 0);
        hash = hash * 37L + (publicBreak ? 1 : 0);
        hash = hash * 37L + (publicInteract ? 1 : 0);
        for (ZoneAccess access : players) {
            hash = hash * 37L + access.renderHash();
        }
        return hash;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("PublicBuild", publicBuild);
        tag.putBoolean("PublicBreak", publicBreak);
        tag.putBoolean("PublicInteract", publicInteract);
        ListTag list = new ListTag();
        for (ZoneAccess access : players) {
            list.add(access.save());
        }
        tag.put("Players", list);
        return tag;
    }

    public static ZonePermissions load(CompoundTag tag) {
        ZonePermissions permissions = new ZonePermissions();
        permissions.publicBuild = !tag.contains("PublicBuild") || tag.getBoolean("PublicBuild");
        permissions.publicBreak = !tag.contains("PublicBreak") || tag.getBoolean("PublicBreak");
        permissions.publicInteract = !tag.contains("PublicInteract") || tag.getBoolean("PublicInteract");
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            permissions.put(ZoneAccess.load(list.getCompound(i)));
        }
        return permissions;
    }

    public static final StreamCodec<FriendlyByteBuf, ZonePermissions> STREAM_CODEC = StreamCodec.of(
        (buf, permissions) -> {
            buf.writeBoolean(permissions.publicBuild);
            buf.writeBoolean(permissions.publicBreak);
            buf.writeBoolean(permissions.publicInteract);
            buf.writeVarInt(permissions.players.size());
            for (ZoneAccess access : permissions.players) {
                ZoneAccess.STREAM_CODEC.encode(buf, access);
            }
        },
        buf -> {
            boolean publicBuild = buf.readBoolean();
            boolean publicBreak = buf.readBoolean();
            boolean publicInteract = buf.readBoolean();
            int count = buf.readVarInt();
            List<ZoneAccess> players = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                players.add(ZoneAccess.STREAM_CODEC.decode(buf));
            }
            return new ZonePermissions(publicBuild, publicBreak, publicInteract, players);
        }
    );
}
