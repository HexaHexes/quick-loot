package org.bitwisemadness.quickloot.party;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Membership source for party loot. One implementation per party mod; the
 * registry constructs an adapter only when its mod is loaded, so a missing
 * dependency can never class-load the adapter.
 */
public interface PartyBridge {

    /** The mod id backing this adapter - used for logging and diagnostics. */
    String modId();

    /** The player's party, or empty when they are not in one. */
    Optional<ResolvedParty> resolve(MinecraftServer server, UUID player);

    /**
     * The party's raw addon-setting overrides (issue #21): key -> encoded
     * value, empty for sources that have no settings system. Only Better
     * Party implements this against its API, so the distribution code stays
     * free of foreign types.
     */
    default Map<String, String> settingOverrides(MinecraftServer server, UUID partyId) {
        return Map.of();
    }
}
