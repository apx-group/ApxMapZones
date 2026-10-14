package de.coincraft.mapzones.client.gui;

import de.coincraft.mapzones.client.ClientConfig;
import de.coincraft.mapzones.client.ClientZones;
import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.zone.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/** Haengt die Zonen-Eintraege in das Rechtsklick-Menue der Weltkarte ein. */
public final class ZoneRightClickOptions {

    /** Mehr Nachbarn passen nicht sinnvoll ins Kontextmenue. */
    private static final int MAX_LISTED_NEIGHBOURS = 4;

    private ZoneRightClickOptions() {
    }

    public static void add(ArrayList<RightClickOption> options, IRightClickableElement target,
                           MapTileSelection selection, int chunkX, int chunkZ, ResourceKey<Level> dimension) {
        if (options == null || dimension == null) {
            return;
        }
        ResourceLocation dimensionId = dimension.location();

        if (selection != null) {
            ChunkRect rect = ChunkRect.of(selection.getLeft(), selection.getTop(),
                selection.getRight(), selection.getBottom());

            // Liegt in der Auswahl eine fremde Zone, lehnt der Server Anlegen und
            // Erweitern ohnehin ab - dann wird es gar nicht erst angeboten.
            Zone foreign = ClientZones.foreignOverlap(dimensionId, rect);
            if (foreign == null) {
                options.add(new ZoneOption("gui.mapzones.create_zone", options.size(), target,
                    screen -> Minecraft.getInstance().setScreen(ZoneEditScreen.forNewZone(screen, dimensionId, rect))));

                // Auswahl an eine eigene angrenzende Zone anhaengen - so entstehen beliebige Formen.
                List<Zone> touching = ClientZones.zonesTouching(dimensionId, rect);
                int listed = 0;
                for (int i = 0; i < touching.size() && listed < MAX_LISTED_NEIGHBOURS; i++) {
                    Zone zone = touching.get(i);
                    if (!ClientZones.canEditLocally(zone)) {
                        continue;
                    }
                    listed++;
                    options.add((ZoneOption) new ZoneOption("gui.mapzones.add_to_zone", options.size(), target,
                        screen -> {
                            PacketDistributor.sendToServer(new Payloads.AddAreaRequest(zone.id(), rect));
                            Minecraft.getInstance().setScreen(screen);
                        }).setNameFormatArgs(zone.name()));
                }
            } else {
                options.add((ZoneOption) new ZoneOption("gui.mapzones.blocked_by_zone", ChatFormatting.GRAY,
                    options.size(), target, screen -> Minecraft.getInstance().setScreen(screen))
                    .setNameFormatArgs(foreign.name(),
                        foreign.ownerName() == null || foreign.ownerName().isEmpty() ? "-" : foreign.ownerName()));
            }
        }

        Zone zone = ClientZones.zoneAt(dimensionId, chunkX, chunkZ);
        if (zone != null) {
            // Fremde Zonen zeigt das Fenster nur an; aendern darf sie dort niemand.
            options.add((ZoneOption) new ZoneOption(
                ClientZones.canEditLocally(zone) ? "gui.mapzones.edit_zone" : "gui.mapzones.view_zone",
                options.size(), target,
                screen -> {
                    Zone current = ClientZones.byId(zone.id());
                    if (current != null) {
                        Minecraft.getInstance().setScreen(ZoneEditScreen.forExistingZone(screen, current));
                    }
                }).setNameFormatArgs(zone.name()));

            // Verbinden setzt Rechte an beiden Zonen voraus.
            if (ClientZones.canEditLocally(zone)) {
                List<Zone> neighbours = ClientZones.neighboursOf(zone);
                int listed = 0;
                for (int i = 0; i < neighbours.size() && listed < MAX_LISTED_NEIGHBOURS; i++) {
                    Zone neighbour = neighbours.get(i);
                    if (!ClientZones.canEditLocally(neighbour)) {
                        continue;
                    }
                    listed++;
                    // Die angeklickte Zone wird in den Nachbarn hineingetragen: dessen
                    // Name, Farbe und Besitzer bleiben, die angeklickte Zone geht darin auf.
                    options.add((ZoneOption) new ZoneOption("gui.mapzones.merge_zone", options.size(), target,
                        screen -> {
                            PacketDistributor.sendToServer(new Payloads.MergeRequest(neighbour.id(), zone.id()));
                            Minecraft.getInstance().setScreen(screen);
                        }).setNameFormatArgs(zone.name(), neighbour.name()));
                }
            }
        }

        options.add(new ZoneOption(
            ClientConfig.zonesVisible() ? "gui.mapzones.toggle_hide" : "gui.mapzones.toggle_show",
            options.size(), target,
            screen -> ClientConfig.toggleZonesVisible()));
    }

    private static class ZoneOption extends RightClickOption {

        private final Consumer<Screen> action;

        ZoneOption(String translationKey, int index, IRightClickableElement target, Consumer<Screen> action) {
            this(translationKey, ChatFormatting.AQUA, index, target, action);
        }

        ZoneOption(String translationKey, ChatFormatting color, int index, IRightClickableElement target,
                   Consumer<Screen> action) {
            super(translationKey, Style.EMPTY.withColor(color), index, target);
            this.action = action;
        }

        @Override
        public void onAction(Screen screen) {
            action.accept(screen);
        }
    }
}
