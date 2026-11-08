package org.bitwisemadness.quickloot.party;

/**
 * How much loot each selected recipient receives.
 *
 * <p>Pure enum (no Minecraft types) so the full amount matrix is unit-testable
 * without a game runtime.</p>
 */
public enum PartyAmount {
    /** Only the primary recipient receives the stacks (killer / rotated member). */
    OWNER_ONLY,
    /** Every recipient receives a full copy - an explicit duplication opt-in. */
    FULL_TO_ALL,
    /** Stacks are divided evenly; indivisible remainders go to the primary first. */
    SPLIT_EQUALLY
}
