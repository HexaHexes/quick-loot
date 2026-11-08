package org.bitwisemadness.quickloot.party;

/** Which party mod supplies membership for party loot distribution. */
public enum PartySource {
    /** First loaded of Better Party, Open Parties and Claims, FTB Teams. */
    AUTO,
    /** Better Party only. */
    BETTER_PARTY,
    /** Open Parties and Claims only. */
    OPAC,
    /** FTB Teams only. */
    FTB_TEAMS
}
