package de.coincraft.mapzones.net;

import de.coincraft.mapzones.MapZones;
import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Alle Pakete der Mod. S2C = Server -> Client, C2S = Client -> Server. */
public final class Payloads {

    private Payloads() {
    }

    /** S2C: vollstaendige Zonenliste (beim Join oder nach groesseren Aenderungen). */
    public record SyncAll(List<Zone> zones) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncAll> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "sync_all"));

        public static final StreamCodec<FriendlyByteBuf, SyncAll> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.zones.size());
                for (Zone zone : payload.zones) {
                    Zone.STREAM_CODEC.encode(buf, zone);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Zone> zones = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    zones.add(Zone.STREAM_CODEC.decode(buf));
                }
                return new SyncAll(zones);
            }
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: eine Zone wurde erstellt oder geaendert. */
    public record Upsert(Zone zone) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Upsert> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "upsert"));

        public static final StreamCodec<FriendlyByteBuf, Upsert> CODEC = StreamCodec.of(
            (buf, payload) -> Zone.STREAM_CODEC.encode(buf, payload.zone),
            buf -> new Upsert(Zone.STREAM_CODEC.decode(buf))
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C: eine Zone wurde geloescht. */
    public record Removed(UUID id) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Removed> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "removed"));

        public static final StreamCodec<FriendlyByteBuf, Removed> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUUID(payload.id),
            buf -> new Removed(buf.readUUID())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: neue Zone anlegen (aus der Kartenauswahl). */
    public record CreateRequest(String name, int color, ResourceLocation dimension, ChunkRect rect) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CreateRequest> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "create_request"));

        public static final StreamCodec<FriendlyByteBuf, CreateRequest> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.name, Zone.MAX_NAME_LENGTH);
                buf.writeInt(payload.color);
                buf.writeResourceLocation(payload.dimension);
                ChunkRect.STREAM_CODEC.encode(buf, payload.rect);
            },
            buf -> new CreateRequest(
                buf.readUtf(Zone.MAX_NAME_LENGTH),
                buf.readInt(),
                buf.readResourceLocation(),
                ChunkRect.STREAM_CODEC.decode(buf)
            )
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: ausgewaehlten Bereich einer bestehenden Zone hinzufuegen. */
    public record AddAreaRequest(UUID zoneId, ChunkRect rect) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AddAreaRequest> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "add_area_request"));

        public static final StreamCodec<FriendlyByteBuf, AddAreaRequest> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.zoneId);
                ChunkRect.STREAM_CODEC.encode(buf, payload.rect);
            },
            buf -> new AddAreaRequest(buf.readUUID(), ChunkRect.STREAM_CODEC.decode(buf))
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: zwei beruehrende Zonen zu einer verbinden. */
    public record MergeRequest(UUID targetId, UUID otherId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MergeRequest> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "merge_request"));

        public static final StreamCodec<FriendlyByteBuf, MergeRequest> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.targetId);
                buf.writeUUID(payload.otherId);
            },
            buf -> new MergeRequest(buf.readUUID(), buf.readUUID())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: Name und/oder Farbe einer Zone aendern. */
    public record EditRequest(UUID id, String name, int color) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<EditRequest> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "edit_request"));

        public static final StreamCodec<FriendlyByteBuf, EditRequest> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.id);
                buf.writeUtf(payload.name, Zone.MAX_NAME_LENGTH);
                buf.writeInt(payload.color);
            },
            buf -> new EditRequest(buf.readUUID(), buf.readUtf(Zone.MAX_NAME_LENGTH), buf.readInt())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S: Zone loeschen. */
    public record DeleteRequest(UUID id) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DeleteRequest> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MapZones.MOD_ID, "delete_request"));

        public static final StreamCodec<FriendlyByteBuf, DeleteRequest> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUUID(payload.id),
            buf -> new DeleteRequest(buf.readUUID())
        );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
