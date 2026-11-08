package org.bitwisemadness.quickloot.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

/**
 * Open Parties and Claims membership via its public server API (LGPL-3.0).
 * Constructed strictly behind an {@code openpartiesandclaims}
 * {@code isLoaded} check.
 */
public final class OpacAdapter implements PartyBridge {

    public static final OpacAdapter INSTANCE = new OpacAdapter();

    private OpacAdapter() {
    }

    @Override
    public String modId() {
        return "openpartiesandclaims";
    }

    @Override
    public Optional<ResolvedParty> resolve(MinecraftServer server, UUID player) {
        OpenPACServerAPI api = OpenPACServerAPI.get(server);
        IServerPartyAPI party = api.getPartyManager().getPartyByMember(player);
        if (party == null) {
            return Optional.empty();
        }
        // Owner first, then uuid order - the API exposes no slot index.
        List<UUID> members = new ArrayList<>();
        UUID owner = party.getOwner().getUUID();
        members.add(owner);
        party.getMemberInfoStream()
                .map(IPartyMemberAPI::getUUID)
                .filter(id -> !id.equals(owner))
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(members::add);
        return Optional.of(new ResolvedParty(party.getId(), members));
    }
}
