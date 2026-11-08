package org.bitwisemadness.quickloot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick Loot: insert mob and block drops straight into the player's inventory,
 * with optional Sophisticated Backpacks overflow, Loot Journal notifications,
 * a loot-container quick-insert button, and party loot distribution.
 *
 * <p>No mixins anywhere: everything hangs off NeoForge events and payloads.</p>
 */
@Mod(QuickLoot.MOD_ID)
public final class QuickLoot {

    public static final String MOD_ID = "quick_loot";
    public static final Logger LOGGER = LoggerFactory.getLogger("Quick Loot");

    public QuickLoot(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, QuickLootConfig.SERVER_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, QuickLootConfig.CLIENT_SPEC);
        LOGGER.info("Quick Loot initialized");
    }
}
