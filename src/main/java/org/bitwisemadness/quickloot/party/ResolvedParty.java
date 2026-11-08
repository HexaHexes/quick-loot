package org.bitwisemadness.quickloot.party;

import java.util.List;
import java.util.UUID;

/**
 * An immutable party snapshot: the party id plus its members in stable order.
 *
 * <p>Pure data shared by the adapters and the distribution matrix, so tests can
 * fabricate parties without any party mod installed.</p>
 */
public record ResolvedParty(UUID id, List<UUID> members) {

    public ResolvedParty {
        members = List.copyOf(members);
    }
}
