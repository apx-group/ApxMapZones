package de.coincraft.mapzones.mixin;

import de.coincraft.mapzones.MapZones;
import de.coincraft.mapzones.client.ZoneHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Xaero schliesst die Highlighter-Liste direkt nach dem Aufbau (end() macht sie
 * unveraenderlich), daher wird unser Highlighter genau davor eingehaengt.
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class HighlighterRegistryMixin {

    @Shadow
    public abstract void register(AbstractHighlighter highlighter);

    @Inject(method = "end", at = @At("HEAD"))
    private void mapzones$registerZoneHighlighter(CallbackInfo callback) {
        register(new ZoneHighlighter());
        MapZones.LOGGER.info("Zonen-Highlighter in Xaero's World Map registriert");
    }
}
