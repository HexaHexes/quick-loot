package org.bitwisemadness.quickloot.party;

import net.neoforged.fml.ModList;
import org.bitwisemadness.quickloot.config.QuickLootConfig;

/**
 * Resolves the active membership source from server config.
 *
 * <p>Adapter classes are referenced only inside {@code ModList.isLoaded}
 * branches, so the JVM never resolves an adapter whose mod is absent. Under
 * {@link PartySource#AUTO} the priority is Better Party, then Open Parties and
 * Claims (which may delegate its parties to FTB Teams), then FTB Teams.</p>
 */
public final class PartyBridgeRegistry {

    private PartyBridgeRegistry() {
    }

    /** The configured bridge, or null when party loot is disabled or no source is loaded. */
    public static PartyBridge current() {
        if (!QuickLootConfig.SERVER.partyEnabled.get()) {
            return null;
        }
        return switch (QuickLootConfig.SERVER.partySource.get()) {
            case AUTO -> firstLoaded();
            case BETTER_PARTY -> betterParty();
            case OPAC -> opac();
            case FTB_TEAMS -> ftbTeams();
        };
    }

    private static PartyBridge firstLoaded() {
        PartyBridge bridge = betterParty();
        if (bridge != null) {
            return bridge;
        }
        bridge = opac();
        if (bridge != null) {
            return bridge;
        }
        return ftbTeams();
    }

    private static PartyBridge betterParty() {
        if (ModList.get().isLoaded("better_party")) {
            return BetterPartyAdapter.INSTANCE;
        }
        return null;
    }

    private static PartyBridge opac() {
        if (ModList.get().isLoaded("openpartiesandclaims")) {
            return OpacAdapter.INSTANCE;
        }
        return null;
    }

    private static PartyBridge ftbTeams() {
        if (ModList.get().isLoaded("ftbteams")) {
            return FtbTeamsAdapter.INSTANCE;
        }
        return null;
    }
}
