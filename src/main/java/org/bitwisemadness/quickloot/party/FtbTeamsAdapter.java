package org.bitwisemadness.quickloot.party;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * FTB Teams membership via its public API. Only party-type teams count - a
 * player's personal single-member team is not a party (it would resolve to
 * solo behaviour anyway, but filtering keeps that explicit). Constructed
 * strictly behind an {@code ftbteams} {@code isLoaded} check.
 */
public final class FtbTeamsAdapter implements PartyBridge {

    public static final FtbTeamsAdapter INSTANCE = new FtbTeamsAdapter();

    private FtbTeamsAdapter() {
    }

    @Override
    public String modId() {
        return "ftbteams";
    }

    @Override
    public Optional<ResolvedParty> resolve(MinecraftServer server, UUID player) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return Optional.empty();
        }
        return api.getManager().getPlayerTeamForPlayerID(player)
                .filter(Team::isPartyTeam)
                // getMembers() has no ordering contract; uuid order keeps rotation stable.
                .map(team -> new ResolvedParty(team.getId(), team.getMembers().stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList()));
    }
}
