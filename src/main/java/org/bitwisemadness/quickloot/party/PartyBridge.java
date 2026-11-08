package org.bitwisemadness.quickloot.party;

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
}
