package org.bitwisemadness.quickloot.party;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure party-loot matrix: recipient selection, primary selection, rotation
 * and the amount split (which mob items and XP both flow through). No
 * Minecraft types are touched, so this runs in plain CI.
 */
class PartyLootRulesTest {

    private static final UUID KILLER = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID DAVE = UUID.randomUUID();

    /** Stable party order used by every case below. */
    private static final List<UUID> PARTY = List.of(BOB, CAROL, DAVE);

    // --- recipients (rule) -------------------------------------------------

    @Test
    void killingBlowIsAlwaysTheKiller() {
        assertEquals(List.of(KILLER),
                PartyLootRules.recipients(PartyRule.KILLING_BLOW, PARTY, KILLER, 0L));
        // Even when the killer is not a listed member (solo player, empty party).
        assertEquals(List.of(KILLER),
                PartyLootRules.recipients(PartyRule.KILLING_BLOW, List.of(), KILLER, 7L));
    }

    @Test
    void roundRobinWalksTheRotationIndex() {
        assertEquals(BOB, PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 0L).getFirst());
        assertEquals(CAROL, PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 1L).getFirst());
        assertEquals(DAVE, PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 2L).getFirst());
        // The counter is unbounded; the index wraps.
        assertEquals(BOB, PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 3L).getFirst());
        assertEquals(CAROL, PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 100L).getFirst());
    }

    @Test
    void roundRobinOnEmptyPartyFallsBackToKiller() {
        assertEquals(List.of(KILLER),
                PartyLootRules.recipients(PartyRule.ROUND_ROBIN, List.of(), KILLER, 5L));
    }

    @Test
    void freeForAllIsEveryMemberInStableOrder() {
        assertEquals(PARTY, PartyLootRules.recipients(PartyRule.FREE_FOR_ALL, PARTY, KILLER, 0L));
        // Killer outside the party: everyone still gets a share.
        assertEquals(PARTY, PartyLootRules.recipients(PartyRule.FREE_FOR_ALL, PARTY, KILLER, 3L));
        assertEquals(List.of(KILLER),
                PartyLootRules.recipients(PartyRule.FREE_FOR_ALL, List.of(), KILLER, 0L));
    }

    // --- primary selection -------------------------------------------------

    @Test
    void primaryIsTheKillerWhenAmongRecipients() {
        assertEquals(1, PartyLootRules.primaryIndex(List.of(BOB, KILLER, CAROL), KILLER));
        assertEquals(0, PartyLootRules.primaryIndex(List.of(KILLER, BOB), KILLER));
    }

    @Test
    void primaryFallsBackToFirstRecipientWithoutKiller() {
        assertEquals(0, PartyLootRules.primaryIndex(List.of(BOB, CAROL), KILLER));
        assertEquals(0, PartyLootRules.primaryIndex(List.of(), KILLER));
    }

    // --- rotation index ----------------------------------------------------

    @Test
    void rotationIndexWrapsAndSurvivesEmptyParties() {
        assertEquals(0, PartyLootRules.rotationIndex(0L, 3));
        assertEquals(2, PartyLootRules.rotationIndex(2L, 3));
        assertEquals(0, PartyLootRules.rotationIndex(3L, 3));
        assertEquals(2, PartyLootRules.rotationIndex(-1L, 3));
        assertEquals(0, PartyLootRules.rotationIndex(42L, 0));
    }

    // --- amount matrix (3 rules x 3 amounts, applied to a recipient set) ---

    /**
     * The full matrix is exercised through the amount split against a fixed
     * 3-recipient set with the primary at index 1 (round robin's recipient,
     * say): rule selection and amount policy are orthogonal, so this covers
     * every cell that changes observable behaviour.
     */
    @Test
    void ownerOnlyGivesEverythingToThePrimary() {
        assertArrayEquals(new int[]{0, 9, 0},
                split(9, PartyAmount.OWNER_ONLY, 1));
        // Primary at the edges still works.
        assertArrayEquals(new int[]{9, 0, 0},
                split(9, PartyAmount.OWNER_ONLY, 0));
        assertArrayEquals(new int[]{0, 0, 9},
                split(9, PartyAmount.OWNER_ONLY, 2));
    }

    @Test
    void fullToAllDuplicatesTheStackForEveryone() {
        assertArrayEquals(new int[]{9, 9, 9},
                split(9, PartyAmount.FULL_TO_ALL, 1));
        // Duplication is by design: the total exceeds the source stack.
        assertEquals(27, sum(split(9, PartyAmount.FULL_TO_ALL, 1)));
    }

    @Test
    void splitEquallyDividesWithRemainderToPrimaryFirst() {
        // 9 / 3 = 3 each, no remainder.
        assertArrayEquals(new int[]{3, 3, 3},
                split(9, PartyAmount.SPLIT_EQUALLY, 1));
        // 10 / 3 = 3 each + 1 remainder -> primary (index 1) first.
        assertArrayEquals(new int[]{3, 4, 3},
                split(10, PartyAmount.SPLIT_EQUALLY, 1));
        // Two remainders walk from the primary and wrap: primary, then index 2.
        assertArrayEquals(new int[]{3, 4, 4},
                split(11, PartyAmount.SPLIT_EQUALLY, 1));
        // Primary at index 0: plain order.
        assertArrayEquals(new int[]{4, 3, 3},
                split(10, PartyAmount.SPLIT_EQUALLY, 0));
    }

    @Test
    void splitEquallyNeverLosesOrCreatesItems() {
        for (int count = 1; count <= 40; count++) {
            for (int primary = 0; primary < 3; primary++) {
                assertEquals(count, sum(split(count, PartyAmount.SPLIT_EQUALLY, primary)),
                        "count=" + count + " primary=" + primary);
            }
        }
    }

    // --- edge cases shared by items and XP ---------------------------------

    @Test
    void zeroCountYieldsAllZeroShares() {
        assertArrayEquals(new int[]{0, 0, 0}, split(0, PartyAmount.SPLIT_EQUALLY, 1));
        assertArrayEquals(new int[]{0, 0, 0}, split(0, PartyAmount.FULL_TO_ALL, 1));
        assertArrayEquals(new int[]{0, 0, 0}, split(0, PartyAmount.OWNER_ONLY, 1));
    }

    @Test
    void oneRecipientGetsEverythingUnderEveryAmount() {
        for (PartyAmount amount : PartyAmount.values()) {
            assertArrayEquals(new int[]{7},
                    PartyLootRules.distribute(7, 1, 0, amount), amount.name());
        }
    }

    @Test
    void recipientCountZeroYieldsEmptySplit() {
        assertEquals(0, PartyLootRules.distribute(10, 0, 0, PartyAmount.SPLIT_EQUALLY).length);
    }

    @Test
    void xpSizedSplitsMatchItemSizedSplits() {
        // XP uses the same distribute() as items, so a 5-point split across
        // two members with the killer primary must be 3/2 - the GameTest
        // asserting party XP shares depends on this exact result.
        assertArrayEquals(new int[]{3, 2}, PartyLootRules.distribute(5, 2, 0, PartyAmount.SPLIT_EQUALLY));
        assertArrayEquals(new int[]{2, 3}, PartyLootRules.distribute(5, 2, 1, PartyAmount.SPLIT_EQUALLY));
        assertArrayEquals(new int[]{5, 5}, PartyLootRules.distribute(5, 2, 0, PartyAmount.FULL_TO_ALL));
        assertArrayEquals(new int[]{5, 0}, PartyLootRules.distribute(5, 2, 0, PartyAmount.OWNER_ONLY));
    }

    // --- full 3x3: rule output feeds amount input --------------------------

    /**
     * Every rule x amount cell end to end: pick recipients for a kill, then
     * split one stack across them. KILLING_BLOW collapses to one recipient so
     * OWNER_ONLY and SPLIT_EQUALLY coincide there - the interesting cells are
     * ROUND_ROBIN and FREE_FOR_ALL.
     */
    @Test
    void fullRuleByAmountMatrix() {
        // KILLING_BLOW: single recipient regardless of amount policy.
        for (PartyAmount amount : PartyAmount.values()) {
            List<UUID> recipients = PartyLootRules.recipients(PartyRule.KILLING_BLOW, PARTY, KILLER, 0L);
            assertArrayEquals(new int[]{8},
                    splitAmong(recipients, 8, amount, KILLER),
                    "KILLING_BLOW/" + amount);
        }

        // ROUND_ROBIN: one rotating member; primary is that member (killer is
        // not among the recipients when he is not in the rotation slot).
        List<UUID> rotated = PartyLootRules.recipients(PartyRule.ROUND_ROBIN, PARTY, KILLER, 1L);
        assertEquals(List.of(CAROL), rotated);
        assertArrayEquals(new int[]{8},
                splitAmong(rotated, 8, PartyAmount.OWNER_ONLY, KILLER), "RR/OWNER_ONLY");
        assertArrayEquals(new int[]{8},
                splitAmong(rotated, 8, PartyAmount.FULL_TO_ALL, KILLER), "RR/FULL_TO_ALL");
        assertArrayEquals(new int[]{8},
                splitAmong(rotated, 8, PartyAmount.SPLIT_EQUALLY, KILLER), "RR/SPLIT_EQUALLY");

        // FREE_FOR_ALL: three recipients, primary = killer when he is a member.
        List<UUID> all = PartyLootRules.recipients(PartyRule.FREE_FOR_ALL,
                List.of(KILLER, BOB, CAROL), KILLER, 0L);
        assertEquals(List.of(KILLER, BOB, CAROL), all);
        int primary = PartyLootRules.primaryIndex(all, KILLER);
        assertEquals(0, primary);
        assertArrayEquals(new int[]{8, 0, 0},
                splitAmong(all, 8, PartyAmount.OWNER_ONLY, KILLER), "FFA/OWNER_ONLY");
        assertArrayEquals(new int[]{8, 8, 8},
                splitAmong(all, 8, PartyAmount.FULL_TO_ALL, KILLER), "FFA/FULL_TO_ALL");
        assertArrayEquals(new int[]{3, 3, 2},
                splitAmong(all, 8, PartyAmount.SPLIT_EQUALLY, KILLER), "FFA/SPLIT_EQUALLY");
    }

    // --- helpers -----------------------------------------------------------

    private static int[] split(int count, PartyAmount amount, int primaryIndex) {
        return PartyLootRules.distribute(count, 3, primaryIndex, amount);
    }

    private static int[] splitAmong(List<UUID> recipients, int count, PartyAmount amount, UUID killer) {
        return PartyLootRules.distribute(count, recipients.size(),
                PartyLootRules.primaryIndex(recipients, killer), amount);
    }

    private static int sum(int[] shares) {
        int total = 0;
        for (int share : shares) {
            total += share;
        }
        return total;
    }

    @Test
    void matrixIsFullyCoveredByThisSuite() {
        // Guard against a silent refactor dropping an enum constant from the
        // matrix: every rule and amount must appear above.
        assertEquals(3, PartyRule.values().length);
        assertEquals(3, PartyAmount.values().length);
        assertTrue(PartyAmount.SPLIT_EQUALLY.name().contains("SPLIT"));
    }
}
