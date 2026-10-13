package de.coincraft.mapzones.client.gui;

import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.Zone;
import de.coincraft.mapzones.zone.ZoneAccess;
import de.coincraft.mapzones.zone.ZonePermissions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rechteverwaltung einer Zone: wer darf bauen, abbauen und Bloecke benutzen.
 * Standardmaessig ist alles fuer jeden erlaubt.
 */
public class ZonePermissionScreen extends Screen {

    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 240;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 5;
    private static final int BOX_SIZE = 12;

    private final Screen parent;
    private final Zone zone;
    private final List<Entry> entries = new ArrayList<>();

    private boolean publicBuild;
    private boolean publicBreak;
    private boolean publicInteract;
    private EditBox nameBox;
    private int panelLeft;
    private int panelTop;
    private int scrollOffset;
    private Component notice;

    public ZonePermissionScreen(Screen parent, Zone zone) {
        super(Component.translatable("gui.mapzones.permissions_title"));
        this.parent = parent;
        this.zone = zone;
        ZonePermissions permissions = zone.permissions();
        this.publicBuild = permissions.publicBuild();
        this.publicBreak = permissions.publicBreak();
        this.publicInteract = permissions.publicInteract();
        for (ZoneAccess access : permissions.players()) {
            entries.add(new Entry(access.name(), access.build(), access.breakBlocks(), access.interact()));
        }
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        nameBox = new EditBox(font, panelLeft + 14, panelTop + PANEL_HEIGHT - 58, 150, 18,
            Component.translatable("gui.mapzones.player_name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.mapzones.player_name"));
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.add_player"), button -> addPlayer())
            .bounds(panelLeft + 170, panelTop + PANEL_HEIGHT - 59, 70, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.save"), button -> save())
            .bounds(panelLeft + 14, panelTop + PANEL_HEIGHT - 30, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelLeft + 162, panelTop + PANEL_HEIGHT - 30, 140, 20).build());
    }

    private void addPlayer() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        for (Entry entry : entries) {
            if (entry.name.equalsIgnoreCase(name)) {
                nameBox.setValue("");
                return;
            }
        }
        if (entries.size() >= ZonePermissions.MAX_PLAYERS) {
            notice = Component.translatable("message.mapzones.limit_players", ZonePermissions.MAX_PLAYERS);
            return;
        }
        entries.add(new Entry(name, true, true, true));
        nameBox.setValue("");
        scrollOffset = Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private void save() {
        List<Payloads.AccessEntry> payloadEntries = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            payloadEntries.add(new Payloads.AccessEntry(entry.name, entry.build, entry.breakBlocks, entry.interact));
        }
        PacketDistributor.sendToServer(new Payloads.PermissionsRequest(
            zone.id(), publicBuild, publicBreak, publicInteract, payloadEntries));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Freigaben fuer alle
            int togglesTop = panelTop + 34;
            for (int i = 0; i < 3; i++) {
                if (inBox(mouseX, mouseY, panelLeft + 16, togglesTop + i * 16)) {
                    switch (i) {
                        case 0 -> publicBuild = !publicBuild;
                        case 1 -> publicBreak = !publicBreak;
                        default -> publicInteract = !publicInteract;
                    }
                    return true;
                }
            }

            // Rechte je Spieler
            int listTop = listTop();
            for (int row = 0; row < VISIBLE_ROWS; row++) {
                int index = scrollOffset + row;
                if (index >= entries.size()) {
                    break;
                }
                Entry entry = entries.get(index);
                int rowY = listTop + row * ROW_HEIGHT + 4;
                if (inBox(mouseX, mouseY, columnX(0), rowY)) {
                    entry.build = !entry.build;
                    return true;
                }
                if (inBox(mouseX, mouseY, columnX(1), rowY)) {
                    entry.breakBlocks = !entry.breakBlocks;
                    return true;
                }
                if (inBox(mouseX, mouseY, columnX(2), rowY)) {
                    entry.interact = !entry.interact;
                    return true;
                }
                int removeX = panelLeft + PANEL_WIDTH - 26;
                if (mouseX >= removeX && mouseX <= removeX + 12 && mouseY >= rowY - 1 && mouseY <= rowY + 13) {
                    entries.remove(index);
                    scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size() - VISIBLE_ROWS)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (entries.size() > VISIBLE_ROWS) {
            scrollOffset = Math.max(0, Math.min(entries.size() - VISIBLE_ROWS, scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF0101010);
        graphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6A6A6A);

        graphics.drawCenteredString(font, title, panelLeft + PANEL_WIDTH / 2, panelTop + 9, 0xFFFFFF);
        graphics.drawString(font, Component.literal("■ ").withStyle(style -> style.withColor(zone.color()))
            .append(Component.literal(zone.name())), panelLeft + 14, panelTop + 21, 0xC0C0C0);

        int togglesTop = panelTop + 34;
        drawCheckbox(graphics, panelLeft + 16, togglesTop, publicBuild,
            Component.translatable("gui.mapzones.public_build"), mouseX, mouseY);
        drawCheckbox(graphics, panelLeft + 16, togglesTop + 16, publicBreak,
            Component.translatable("gui.mapzones.public_break"), mouseX, mouseY);
        drawCheckbox(graphics, panelLeft + 16, togglesTop + 32, publicInteract,
            Component.translatable("gui.mapzones.public_interact"), mouseX, mouseY);

        int headerY = listTop() - 12;
        graphics.drawString(font, Component.translatable("gui.mapzones.allowed_players"), panelLeft + 14, headerY - 11, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.mapzones.col_build"), columnX(0) - 2, headerY, 0x808080);
        graphics.drawString(font, Component.translatable("gui.mapzones.col_break"), columnX(1) - 2, headerY, 0x808080);
        graphics.drawString(font, Component.translatable("gui.mapzones.col_use"), columnX(2) - 2, headerY, 0x808080);

        int listTop = listTop();
        graphics.fill(panelLeft + 12, listTop, panelLeft + PANEL_WIDTH - 12, listTop + VISIBLE_ROWS * ROW_HEIGHT, 0x40000000);

        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.mapzones.no_players"),
                panelLeft + 18, listTop + 8, 0x707070);
        }

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int rowY = listTop + row * ROW_HEIGHT + 4;
            graphics.drawString(font, entry.name, panelLeft + 18, rowY + 2, 0xE0E0E0);
            drawBox(graphics, columnX(0), rowY, entry.build, mouseX, mouseY);
            drawBox(graphics, columnX(1), rowY, entry.breakBlocks, mouseX, mouseY);
            drawBox(graphics, columnX(2), rowY, entry.interact, mouseX, mouseY);
            graphics.drawString(font, "✖", panelLeft + PANEL_WIDTH - 26, rowY + 2, 0xFF7070);
        }

        if (entries.size() > VISIBLE_ROWS) {
            graphics.drawString(font, (scrollOffset + 1) + "-" + Math.min(entries.size(), scrollOffset + VISIBLE_ROWS)
                + "/" + entries.size(), panelLeft + PANEL_WIDTH - 60, listTop - 11, 0x707070);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        Component hint = notice != null ? notice : Component.translatable("gui.mapzones.permissions_hint");
        graphics.drawString(font, hint, panelLeft + 14, panelTop + PANEL_HEIGHT - 74, notice != null ? 0xFFAA55 : 0x808080);
    }

    private int listTop() {
        return panelTop + 98;
    }

    private int columnX(int column) {
        return panelLeft + PANEL_WIDTH - 130 + column * 34;
    }

    private boolean inBox(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX <= x + BOX_SIZE && mouseY >= y && mouseY <= y + BOX_SIZE;
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, boolean checked, Component label, int mouseX, int mouseY) {
        drawBox(graphics, x, y, checked, mouseX, mouseY);
        graphics.drawString(font, label, x + BOX_SIZE + 6, y + 2, checked ? 0xE0E0E0 : 0x909090);
    }

    private void drawBox(GuiGraphics graphics, int x, int y, boolean checked, int mouseX, int mouseY) {
        boolean hovered = inBox(mouseX, mouseY, x, y);
        graphics.fill(x, y, x + BOX_SIZE, y + BOX_SIZE, checked ? 0xFF2E7D32 : 0xFF3A3A3A);
        graphics.renderOutline(x, y, BOX_SIZE, BOX_SIZE, hovered ? 0xFFFFFFFF : 0xFF808080);
        if (checked) {
            graphics.drawString(font, "✔", x + 2, y + 2, 0xFFFFFF, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Entry {
        private final String name;
        private boolean build;
        private boolean breakBlocks;
        private boolean interact;

        private Entry(String name, boolean build, boolean breakBlocks, boolean interact) {
            this.name = name == null ? "" : name.trim();
            this.build = build;
            this.breakBlocks = breakBlocks;
            this.interact = interact;
        }
    }
}
