package org.bitwisemadness.quickloot.party;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The pure party-loot matrix: recipient selection (rule), primary selection,
 * round-robin rotation and the per-stack amount split.
 *
 * <p>No Minecraft types appear here, so the full rule x amount matrix is
 * covered by JUnit against fabricated {@link ResolvedParty}-style data - the
 * "fake bridge" tests.</p>
 */
public final class PartyLootRules {

    private PartyLootRules() {
    }

    /**
     * Who receives this kill's loot.
     *
     * @param rule the configured rule
     * @param eligible online, same-dimension members in stable order
     * @param killer the killing player (always eligible in practice)
     * @param rotationCounter this kill's per-party round-robin counter value
     * @return the ordered recipient list
     */
    public static List<UUID> recipients(PartyRule rule, List<UUID> eligible, UUID killer, long rotationCounter) {
        return switch (rule) {
            case KILLING_BLOW -> List.of(killer);
            case ROUND_ROBIN -> eligible.isEmpty()
                    ? List.of(killer)
                    : List.of(eligible.get(rotationIndex(rotationCounter, eligible.size())));
            case FREE_FOR_ALL -> eligible.isEmpty() ? List.of(killer) : List.copyOf(eligible);
        };
    }

    /**
     * The primary recipient - who {@link PartyAmount#OWNER_ONLY} pays and who
     * takes SPLIT_EQUALLY remainders: the killer when they are among the
     * recipients, otherwise the first recipient.
     */
    public static int primaryIndex(List<UUID> recipients, UUID killer) {
        return Math.max(recipients.indexOf(killer), 0);
    }

    /** The rotation slot for a kill: a monotonically advancing index over the eligible members. */
    public static int rotationIndex(long counter, int eligibleCount) {
        return eligibleCount <= 0 ? 0 : (int) Math.floorMod(counter, (long) eligibleCount);
    }

    /**
     * How much of one stack of {@code count} items each recipient gets.
     *
     * @return an array parallel to the recipient list; zeros mean "nothing this stack"
     */
    public static int[] distribute(int count, int recipientCount, int primaryIndex, PartyAmount amount) {
        if (recipientCount <= 0) {
            return new int[0];
        }
        int[] shares = new int[recipientCount];
        if (count <= 0) {
            return shares;
        }
        switch (amount) {
            case OWNER_ONLY -> {
                if (primaryIndex >= 0 && primaryIndex < recipientCount) {
                    shares[primaryIndex] = count;
                }
            }
            case FULL_TO_ALL -> Arrays.fill(shares, count);
            case SPLIT_EQUALLY -> {
                int base = count / recipientCount;
                int remainder = count % recipientCount;
                Arrays.fill(shares, base);
                // Remainders walk the stable order starting at the primary:
                // one each, wrapping around, never doubling up (the remainder
                // is always smaller than the recipient count).
                int start = primaryIndex >= 0 && primaryIndex < recipientCount ? primaryIndex : 0;
                for (int i = 0; i < remainder; i++) {
                    shares[(start + i) % recipientCount]++;
                }
            }
        }
        return shares;
    }
}
