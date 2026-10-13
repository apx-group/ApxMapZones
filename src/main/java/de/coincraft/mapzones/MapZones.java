package de.coincraft.mapzones;

import de.coincraft.mapzones.net.NetworkHandler;
import de.coincraft.mapzones.server.ZoneCommands;
import de.coincraft.mapzones.server.ZoneProtectionEvents;
import de.coincraft.mapzones.server.ZoneServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MapZones.MOD_ID)
public class MapZones {
    public static final String MOD_ID = "mapzones";
    public static final Logger LOGGER = LoggerFactory.getLogger("MapZones");

    public MapZones(IEventBus modBus, ModContainer container) {
        modBus.addListener(NetworkHandler::register);

        NeoForge.EVENT_BUS.addListener(ZoneCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ZoneServerEvents::onPlayerLoggedIn);

        if (Features.PROTECTION) {
            NeoForge.EVENT_BUS.addListener(ZoneProtectionEvents::onBlockBreak);
            NeoForge.EVENT_BUS.addListener(ZoneProtectionEvents::onBlockPlace);
            NeoForge.EVENT_BUS.addListener(ZoneProtectionEvents::onMultiPlace);
            NeoForge.EVENT_BUS.addListener(ZoneProtectionEvents::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(ZoneProtectionEvents::onLeftClickBlock);
            LOGGER.info("Map Zones laeuft mit Rechteverwaltung (Protect-Variante)");
        }

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, de.coincraft.mapzones.client.ClientConfig.SPEC);
            de.coincraft.mapzones.client.MapZonesClient.init(modBus);
        }
    }
}
