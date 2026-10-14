package de.coincraft.mapzones.client.gui;

import de.coincraft.mapzones.net.Payloads;
import de.coincraft.mapzones.zone.Zone;
import de.coincraft.mapzones.zone.ZoneAccess;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Zone an einen anderen Spieler uebergeben.
 *
 * Zwei Schritte: erst den Spielernamen eintragen, dann die Warnung bestaetigen.
 * Erst mit der Bestaetigung geht die Anfrage an den Server, denn der bisherige
 * Besitzer gibt damit die volle Kontrolle ueber die Zone ab.
 */
public class ZoneTransferScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 196;

    private final Screen parent;
    private final Zone zone;

    private EditBox nameBox;
    private String target = "";
    private boolean confirming;
    private int panelLeft;
    private int panelTop;
    private List<FormattedCharSequence> warning = List.of();

    public ZoneTransferScreen(Screen parent, Zone zone) {
        super(Component.translatable("gui.mapzones.transfer_title"));
        this.parent = parent;
        this.zone = zone;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        int buttonsTop = panelTop + PANEL_HEIGHT - 28;
        int textWidth = PANEL_WIDTH - 28;

        if (confirming) {
            warning = font.split(Component.translatable("gui.mapzones.transfer_question", zone.name(), target),
                textWidth);
            addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.transfer_confirm", target),
                    button -> send())
                .bounds(panelLeft + 14, buttonsTop, 176, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(panelLeft + 196, buttonsTop, PANEL_WIDTH - 210, 20).build());
            return;
        }

        warning = font.split(Component.translatable("gui.mapzones.transfer_warning"), textWidth);

        nameBox = new EditBox(font, panelLeft + 14, panelTop + 48, textWidth, 20,
            Component.translatable("gui.mapzones.transfer_to"));
        nameBox.setMaxLength(ZoneAccess.MAX_NAME_LENGTH);
        nameBox.setValue(target);
        nameBox.setResponder(value -> target = value.trim());
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.mapzones.transfer_next"),
                button -> askForConfirmation())
            .bounds(panelLeft + 14, buttonsTop, 176, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(panelLeft + 196, buttonsTop, PANEL_WIDTH - 210, 20).build());
    }

    private void askForConfirmation() {
        if (target.isEmpty()) {
            nameBox.setFocused(true);
            return;
        }
        confirming = true;
        rebuildWidgets();
    }

    private void send() {
        if (!confirming || target.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new Payloads.TransferRequest(zone.id(), target));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && !confirming) { // Enter / Numpad-Enter
            askForConfirmation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Siehe ZonePermissionScreen: eigene Zeichnungen gehoeren vor die Widgets. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF0101010);
        graphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFF6A6A6A);

        graphics.drawCenteredString(font, title, panelLeft + PANEL_WIDTH / 2, panelTop + 10, 0xFFFFFF);
        graphics.drawString(font, Component.literal("▪ ").withStyle(style -> style.withColor(zone.color()))
            .append(Component.literal(zone.name()).withStyle(ChatFormatting.WHITE)), panelLeft + 14, panelTop + 26, 0xFFFFFF);

        int textTop;
        if (confirming) {
            textTop = panelTop + 50;
        } else {
            graphics.drawString(font, Component.translatable("gui.mapzones.transfer_to"),
                panelLeft + 14, panelTop + 37, 0xA0A0A0);
            textTop = panelTop + 80;
        }

        // Warnblock in Rot - das Abgeben der Kontrolle laesst sich nicht selbst zurueckholen.
        for (int i = 0; i < warning.size(); i++) {
            graphics.drawString(font, warning.get(i), panelLeft + 14, textTop + i * 11, 0xFFFF6B6B);
        }
    }

    @Override
    public void onClose() {
        if (confirming) {
            confirming = false;
            rebuildWidgets();
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
