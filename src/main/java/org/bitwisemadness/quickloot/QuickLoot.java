package org.bitwisemadness.quickloot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.party.BetterPartyAdapter;
import org.bitwisemadness.quickloot.party.FtbTeamsAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick: Loot! inserts mob and block drops straight into the player's
 * inventory, with optional Sophisticated Backpacks overflow, Loot Journal
 * notifications, and party loot distribution.
 *
 * <p>No mixins anywhere: everything hangs off NeoForge events and payloads.</p>
 */
@Mod(QuickLoot.MOD_ID)
public final class QuickLoot {

    public static final String MOD_ID = "quick_loot";
    public static final Logger LOGGER = LoggerFactory.getLogger("Quick: Loot!");

    public QuickLoot(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, QuickLootConfig.SERVER_SPEC);
        if (net.neoforged.fml.ModList.get().isLoaded("better_party")) {
            // Both physical sides: Better Party validates settings writes
            // against its registry, and the client encodes requests with the
            // same definitions (issue #21).
            BetterPartyAdapter.registerAddonSettings();
        }
        if (net.neoforged.fml.ModList.get().isLoaded("ftbteams")) {
            // Before any team collects its property set (team creation and
            // world load both happen after all mod constructors) (issue #21).
            FtbTeamsAdapter.registerTeamProperties();
        }
        LOGGER.info("Quick: Loot! initialized");
    }
}
