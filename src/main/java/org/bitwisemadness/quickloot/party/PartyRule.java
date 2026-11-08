package org.bitwisemadness.quickloot.party;

/**
 * Which party members receive a kill's loot.
 *
 * <p>Pure enum (no Minecraft types) so the full rule matrix is unit-testable
 * without a game runtime.</p>
 */
public enum PartyRule {
    /** Only the player who landed the killing blow - vanilla-equivalent default. */
    KILLING_BLOW,
    /** One rotating member per kill; loot splits across successive kills. */
    ROUND_ROBIN,
    /** Every eligible party member. */
    FREE_FOR_ALL
}
