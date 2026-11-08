package org.bitwisemadness.quickloot.party;

import dev.betterparty.api.v1.model.PartyMemberView;
import dev.betterparty.api.v1.model.PartyView;
import dev.betterparty.api.v1.server.BetterPartyServerApi;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Better Party membership via its public server API (All Rights Reserved mod -
 * API calls only, no copied code). Constructed strictly behind a
 * {@code better_party} {@code isLoaded} check.
 */
public final class BetterPartyAdapter implements PartyBridge {

    public static final BetterPartyAdapter INSTANCE = new BetterPartyAdapter();

    private BetterPartyAdapter() {
    }

    @Override
    public String modId() {
        return "better_party";
    }

    @Override
    public Optional<ResolvedParty> resolve(MinecraftServer server, UUID player) {
        return BetterPartyServerApi.partyOf(server, player).map(BetterPartyAdapter::toParty);
    }

    private static ResolvedParty toParty(PartyView view) {
        // slotIndex is the party's own stable member order (issue #3 spec order).
        return new ResolvedParty(view.partyId(), view.members().stream()
                .sorted(Comparator.comparingInt(PartyMemberView::slotIndex))
                .map(PartyMemberView::playerId)
                .toList());
    }
}
