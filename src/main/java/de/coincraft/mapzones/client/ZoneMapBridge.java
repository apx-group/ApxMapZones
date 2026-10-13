package de.coincraft.mapzones.client;

import de.coincraft.mapzones.MapZones;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapWorld;

/** Beruehrungspunkte mit Xaero's World Map. */
public final class ZoneMapBridge {

    private ZoneMapBridge() {
    }

    /**
     * Zwingt Xaero, die Kartenregionen neu zu zeichnen. Noetig, weil Xaero die
     * Highlights pro Region cached und nur beim Hash-Wechsel aktualisiert.
     */
    public static void refreshHighlights() {
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null) {
                return;
            }
            MapProcessor processor = session.getMapProcessor();
            if (processor == null) {
                return;
            }
            MapWorld mapWorld = processor.getMapWorld();
            if (mapWorld != null) {
                mapWorld.clearAllCachedHighlightHashes();
            }
        } catch (Throwable throwable) {
            MapZones.LOGGER.warn("Konnte Xaero-Highlights nicht aktualisieren", throwable);
        }
    }
}
