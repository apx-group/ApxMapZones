package de.coincraft.mapzones.client;

import de.coincraft.mapzones.zone.Zone;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/** Zeichnet die Zonen als farbige Chunk-Kaesten in Xaero's World Map. */
public class ZoneHighlighter extends ChunkHighlighter {

    /** Mit -Dmapzones.debug=true protokolliert die Mod, welche Chunks sie faerbt. */
    private static final boolean DEBUG = Boolean.getBoolean("mapzones.debug");

    private int debugLogged;

    public ZoneHighlighter() {
        // true: Zonen sind auch ueber noch nicht erkundetem Gebiet sichtbar.
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!ClientConfig.zonesVisible()) {
            return false;
        }
        return !ClientZones.inRegion(dimension.location(), regionX, regionZ).isEmpty();
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ClientConfig.zonesVisible() && ClientZones.zoneAt(dimension.location(), chunkX, chunkZ) != null;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!ClientConfig.zonesVisible()) {
            return null;
        }
        ResourceLocation dimensionId = dimension.location();
        Zone zone = ClientZones.zoneAt(dimensionId, chunkX, chunkZ);
        if (zone == null) {
            return null;
        }

        if (DEBUG && debugLogged < 5) {
            debugLogged++;
            de.coincraft.mapzones.MapZones.LOGGER.info("Faerbe Chunk {}/{} fuer Zone \"{}\" (#{})",
                chunkX, chunkZ, zone.name(), String.format("%06X", zone.color()));
        }

        int fill = packColor(zone.color(), ClientConfig.CONFIG.fillOpacity.get());
        int border = packColor(zone.color(), ClientConfig.CONFIG.borderOpacity.get());

        this.resultStore[0] = fill;
        this.resultStore[1] = sameZone(dimensionId, chunkX, chunkZ - 1, zone) ? fill : border;
        this.resultStore[2] = sameZone(dimensionId, chunkX + 1, chunkZ, zone) ? fill : border;
        this.resultStore[3] = sameZone(dimensionId, chunkX, chunkZ + 1, zone) ? fill : border;
        this.resultStore[4] = sameZone(dimensionId, chunkX - 1, chunkZ, zone) ? fill : border;
        return this.resultStore;
    }

    private boolean sameZone(ResourceLocation dimension, int chunkX, int chunkZ, Zone zone) {
        Zone neighbour = ClientZones.zoneAt(dimension, chunkX, chunkZ);
        return neighbour != null && neighbour.id().equals(zone.id());
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!ClientConfig.zonesVisible()) {
            return 0;
        }
        ResourceLocation dimensionId = dimension.location();
        long accumulator = 17L;
        accumulator = accumulator * 37L + ClientConfig.CONFIG.fillOpacity.get();
        accumulator = accumulator * 37L + ClientConfig.CONFIG.borderOpacity.get();
        // Nachbarregionen mitzaehlen, damit die Randfarben an Regionsgrenzen stimmen.
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                for (Zone zone : ClientZones.inRegion(dimensionId, regionX + offsetX, regionZ + offsetZ)) {
                    accumulator = accumulator * 37L + zone.renderHash();
                }
            }
        }
        return (int) (accumulator >> 32) * 37 + (int) accumulator;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        Zone zone = ClientZones.zoneAt(dimension.location(), chunkX, chunkZ);
        if (zone == null) {
            return null;
        }
        Component tooltip = Component.literal("■ ").withStyle(style -> style.withColor(zone.color()))
            .append(Component.literal(zone.name()).withStyle(ChatFormatting.WHITE));
        if (ClientConfig.CONFIG.showOwnerInTooltip.get() && zone.ownerName() != null && !zone.ownerName().isEmpty()) {
            tooltip = tooltip.copy().append(Component.literal(" (" + zone.ownerName() + ")").withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension,
                                                 int blockX, int blockZ, int width) {
        // Absichtlich leer: Zonen sind eine Weltkarten-Funktion.
    }

    /**
     * Xaero erwartet die Pixelfarbe in der Byte-Reihenfolge des Texturpuffers
     * (Blau, Gruen, Rot, Alpha) - genauso wie die Claim-Anzeige der Mod selbst.
     */
    public static int packColor(int rgb, int opacityPercent) {
        int alpha = Math.max(0, Math.min(255, 255 * opacityPercent / 100));
        return ((rgb & 0xFF) << 24) | (((rgb >> 8) & 0xFF) << 16) | (((rgb >> 16) & 0xFF) << 8) | alpha;
    }
}
