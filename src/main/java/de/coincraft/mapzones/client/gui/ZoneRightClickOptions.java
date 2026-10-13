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

            options.add(new ZoneOption("gui.mapzones.create_zone", options.size(), target,
                screen -> Minecraft.getInstance().setScreen(ZoneEditScreen.forNewZone(screen, dimensionId, rect))));

            // Auswahl direkt an eine angrenzende Zone anhaengen - so entstehen beliebige Formen.
            List<Zone> touching = ClientZones.zonesTouching(dimensionId, rect);
            for (int i = 0; i < touching.size() && i < MAX_LISTED_NEIGHBOURS; i++) {
                Zone zone = touching.get(i);
                options.add((ZoneOption) new ZoneOption("gui.mapzones.add_to_zone", options.size(), target,
                    screen -> {
                        PacketDistributor.sendToServer(new Payloads.AddAreaRequest(zone.id(), rect));
                        Minecraft.getInstance().setScreen(screen);
                    }).setNameFormatArgs(zone.name()));
            }
        }

        Zone zone = ClientZones.zoneAt(dimensionId, chunkX, chunkZ);
        if (zone != null) {
            options.add((ZoneOption) new ZoneOption("gui.mapzones.edit_zone", options.size(), target,
                screen -> {
                    Zone current = ClientZones.byId(zone.id());
                    if (current != null) {
                        Minecraft.getInstance().setScreen(ZoneEditScreen.forExistingZone(screen, current));
                    }
                }).setNameFormatArgs(zone.name()));

            List<Zone> neighbours = ClientZones.neighboursOf(zone);
            for (int i = 0; i < neighbours.size() && i < MAX_LISTED_NEIGHBOURS; i++) {
                Zone neighbour = neighbours.get(i);
                options.add((ZoneOption) new ZoneOption("gui.mapzones.merge_zone", options.size(), target,
                    screen -> {
                        PacketDistributor.sendToServer(new Payloads.MergeRequest(zone.id(), neighbour.id()));
                        Minecraft.getInstance().setScreen(screen);
                    }).setNameFormatArgs(neighbour.name(), zone.name()));
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
            super(translationKey, Style.EMPTY.withColor(ChatFormatting.AQUA), index, target);
            this.action = action;
        }

        @Override
        public void onAction(Screen screen) {
            action.accept(screen);
        }
    }
}
