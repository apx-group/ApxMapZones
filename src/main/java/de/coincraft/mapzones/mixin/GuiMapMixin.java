package de.coincraft.mapzones.mixin;

import de.coincraft.mapzones.client.gui.ZoneRightClickOptions;
import java.util.ArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/** Ergaenzt das Rechtsklick-Menue der Weltkarte um die Zonen-Eintraege. */
@Mixin(value = GuiMap.class, remap = false)
public class GuiMapMixin {

    @Shadow
    private MapTileSelection mapTileSelection;

    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickZ;

    @Shadow
    private ResourceKey<Level> rightClickDim;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"))
    private void mapzones$addZoneOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> callback) {
        ZoneRightClickOptions.add(
            callback.getReturnValue(),
            (IRightClickableElement) (Object) this,
            mapTileSelection,
            rightClickX >> 4,
            rightClickZ >> 4,
            rightClickDim
        );
    }
}
