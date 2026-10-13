package de.coincraft.mapzones.client.gui;

import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.ChunkRect;
import de.coincraft.mapzones.server.ZoneColors;
import de.coincraft.mapzones.zone.Zone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fenster zum Anlegen und Bearbeiten einer Zone. */
public class ZoneEditScreen extends Screen {

    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 186;
    private static final int SWATCH_SIZE = 18;
    private static final int SWATCHES_PER_ROW = 8;

    private final Screen parent;
    private final Zone existing;
    private final ResourceLocation dimension;
    private final ChunkRect rect;
    private final List<Swatch> swatches = new ArrayList<>();

    private EditBox nameBox;
    private EditBox hexBox;
    private int color;
    private int panelLeft;
    private int panelTop;
    private boolean hexInvalid;

    private ZoneEditScreen(Screen parent, Zone existing, ResourceLocation dimension, ChunkRect rect, int color) {
        super(Component.translatable(existing == null ? "gui.mapzones.create_title" : "gui.mapzones.edit_title"));
        this.parent = parent;
        this.existing = existing;
        this.dimension = dimension;
        this.rect = rect;
        this.color = color;
    }

    public static ZoneEditScreen forNewZone(Screen parent, ResourceLocation dimension, ChunkRect rect) {
        return new ZoneEditScreen(parent, null, dimension, rect, ZoneColors.DEFAULT_COLOR);
    }

    public static ZoneEditScreen forExistingZone(Screen parent, Zone zone) {
        return new ZoneEditScreen(parent, zone, zone.dimension(),
            ChunkRect.of(zone.minChunkX(), zone.minChunkZ(), zone.maxChunkX(), zone.maxChunkZ()), zone.color());
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        nameBox = new EditBox(font, panelLeft + 14, panelTop + 36, PANEL_WIDTH - 28, 20,
            Component.translatable("gui.mapzones.name"));
        nameBox.setMaxLength(Zone.MAX_NAME_LENGTH);
        nameBox.setValue(existing != null ? existing.name() : "");
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        swatches.clear();
        int paletteTop = panelTop + 80;
        int paletteLeft = panelLeft + 14;
        int index = 0;
        for (Integer paletteColor : ZoneColors.PALETTE.values()) {
            int row = index / SWATCHES_PER_ROW;
            int column = index % SWATCHES_PER_ROW;
            swatches.add(new Swatch(
                paletteLeft + column * (SWATCH_SIZE + 2),
                paletteTop + row * (SWATCH_SIZE + 2),
                paletteColor));
            index++;
        }

        hexBox = new EditBox(font, panelLeft + 14, paletteTop + 2 * (SWATCH_SIZE + 2) + 8, 74, 18,
            Component.translatable("gui.mapzones.color"));
        hexBox.setMaxLength(7);
        hexBox.setValue(String.format("#%06X", color));
        hexBox.setResponder(this::onHexChanged);
        addRenderableWidget(hexBox);

        int buttonsTop = panelTop + PANEL_HEIGHT - 28;
        int buttonWidth = existing == null ? 104 : 72;
        int buttonLeft = panelLeft + 14;

        addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.save"), button -> save())
            .bounds(buttonLeft, buttonsTop, buttonWidth, 20).build());
        buttonLeft += buttonWidth + 6;

        if (existing != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.delete"), button -> delete())
                .bounds(buttonLeft, buttonsTop, buttonWidth, 20).build());
            buttonLeft += buttonWidth + 6;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(buttonLeft, buttonsTop, PANEL_WIDTH - 14 - (buttonLeft - panelLeft), 20).build());
    }

    private void onHexChanged(String value) {
        String text = value.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        try {
            if (text.length() != 6) {
                throw new NumberFormatException(text);
            }
            color = Integer.parseInt(text, 16) & 0xFFFFFF;
            hexInvalid = false;
        } catch (NumberFormatException exception) {
            hexInvalid = true;
        }
    }

    private void pickColor(int picked) {
        color = picked;
        hexInvalid = false;
        hexBox.setValue(String.format("#%06X", color));
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            nameBox.setFocused(true);
            return;
        }
        if (existing == null) {
            PacketDistributor.sendToServer(new Payloads.CreateRequest(name, color, dimension, rect));
        } else {
            PacketDistributor.sendToServer(new Payloads.EditRequest(existing.id(), name, color));
        }
        onClose();
    }

    private void delete() {
        if (existing != null) {
            PacketDistributor.sendToServer(new Payloads.DeleteRequest(existing.id()));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Swatch swatch : swatches) {
                if (swatch.contains(mouseX, mouseY)) {
                    pickColor(swatch.color());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad-Enter
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF0101010);
        graphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6A6A6A);

        graphics.drawCenteredString(font, title, panelLeft + PANEL_WIDTH / 2, panelTop + 10, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.mapzones.name"), panelLeft + 14, panelTop + 25, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("gui.mapzones.color"), panelLeft + 14, panelTop + 69, 0xA0A0A0);

        super.render(graphics, mouseX, mouseY, partialTick);

        for (Swatch swatch : swatches) {
            graphics.fill(swatch.x(), swatch.y(), swatch.x() + SWATCH_SIZE, swatch.y() + SWATCH_SIZE,
                0xFF000000 | swatch.color());
            boolean selected = swatch.color() == color;
            graphics.renderOutline(swatch.x(), swatch.y(), SWATCH_SIZE, SWATCH_SIZE,
                selected ? 0xFFFFFFFF : 0xFF202020);
            if (swatch.contains(mouseX, mouseY)) {
                graphics.renderOutline(swatch.x(), swatch.y(), SWATCH_SIZE, SWATCH_SIZE, 0xFFFFFF80);
            }
        }

        int previewLeft = hexBox.getX() + hexBox.getWidth() + 6;
        int previewTop = hexBox.getY();
        graphics.fill(previewLeft, previewTop, previewLeft + 18, previewTop + 18, 0xFF000000 | color);
        graphics.renderOutline(previewLeft, previewTop, 18, 18, hexInvalid ? 0xFFFF5555 : 0xFF9A9A9A);

        int chunksX = rect.maxChunkX() - rect.minChunkX() + 1;
        int chunksZ = rect.maxChunkZ() - rect.minChunkZ() + 1;
        Component area = existing == null
            ? Component.translatable("gui.mapzones.area_info",
                chunksX, chunksZ, chunksX * 16, chunksZ * 16,
                rect.minChunkX() << 4, rect.minChunkZ() << 4, (rect.maxChunkX() << 4) + 15, (rect.maxChunkZ() << 4) + 15)
            : Component.translatable("gui.mapzones.zone_info",
                existing.chunkCount(), existing.rectCount(),
                existing.ownerName() == null || existing.ownerName().isEmpty() ? "-" : existing.ownerName());
        graphics.drawString(font, area, panelLeft + 14, panelTop + PANEL_HEIGHT - 44, 0x909090);

        String dimensionText = dimension == null ? "?" : dimension.toString().toLowerCase(Locale.ROOT);
        graphics.drawString(font, Component.literal(dimensionText), previewLeft + 26, previewTop + 5, 0x707070);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Swatch(int x, int y, int color) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SWATCH_SIZE && mouseY >= y && mouseY < y + SWATCH_SIZE;
        }
    }
}
