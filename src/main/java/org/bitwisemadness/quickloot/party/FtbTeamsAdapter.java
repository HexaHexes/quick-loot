package org.bitwisemadness.quickloot.party;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.BooleanProperty;
import dev.ftb.mods.ftbteams.api.property.EnumProperty;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.bitwisemadness.quickloot.QuickLoot;

/**
 * FTB Teams membership and per-team settings via its public API. Only
 * party-type teams count - a player's personal single-member team is not a
 * party (it would resolve to solo behaviour anyway, but filtering keeps that
 * explicit). Constructed strictly behind an {@code ftbteams}
 * {@code isLoaded} check; {@link #registerTeamProperties()} runs from mod
 * construction behind the same check so the properties exist before any team
 * collects its property set.
 *
 * <p>Unlike Better Party's addon settings, FTB's team properties always hold
 * a value (an untouched team reads back the property default), so
 * "the party never chose" is modelled with a {@link #INHERIT} sentinel as the
 * property default: an untouched team reports it, this adapter drops it from
 * the override map, and the distribution code falls back to the server
 * config exactly as if no override existed (issue #21).</p>
 */
public final class FtbTeamsAdapter implements PartyBridge {

    public static final FtbTeamsAdapter INSTANCE = new FtbTeamsAdapter();

    /**
     * The override-map-absent sentinel stored by an untouched team: "use the
     * server config". A team picking any real value overrides the config.
     */
    public static final String INHERIT = "SERVER_DEFAULT";

    /**
     * Per-team share-loot switch. Defaults to {@code true} - the same value
     * the parser uses as its fallback, so an untouched team behaves exactly
     * like no override while a team can still opt out for itself.
     */
    public static final BooleanProperty ENABLED_PROPERTY = new BooleanProperty(
            ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "enabled"), true);

    /** Per-team distribution rule; default {@link #INHERIT} = follow the server config. */
    public static final EnumProperty LOOT_RULE_PROPERTY = new EnumProperty(
            ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "loot_rule"),
            () -> INHERIT,
            List.of(INHERIT, "KILLING_BLOW", "ROUND_ROBIN", "FREE_FOR_ALL"),
            Map.of(
                    INHERIT, Component.translatable("ftbteamsconfig.quick_loot.inherit"),
                    "KILLING_BLOW", Component.translatable("ftbteamsconfig.quick_loot.KILLING_BLOW"),
                    "ROUND_ROBIN", Component.translatable("ftbteamsconfig.quick_loot.ROUND_ROBIN"),
                    "FREE_FOR_ALL", Component.translatable("ftbteamsconfig.quick_loot.FREE_FOR_ALL")));

    /** Per-team amount matrix; default {@link #INHERIT} = follow the server config. */
    public static final EnumProperty LOOT_AMOUNT_PROPERTY = new EnumProperty(
            ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "loot_amount"),
            () -> INHERIT,
            List.of(INHERIT, "OWNER_ONLY", "FULL_TO_ALL", "SPLIT_EQUALLY"),
            Map.of(
                    INHERIT, Component.translatable("ftbteamsconfig.quick_loot.inherit"),
                    "OWNER_ONLY", Component.translatable("ftbteamsconfig.quick_loot.OWNER_ONLY"),
                    "FULL_TO_ALL", Component.translatable("ftbteamsconfig.quick_loot.FULL_TO_ALL"),
                    "SPLIT_EQUALLY", Component.translatable("ftbteamsconfig.quick_loot.SPLIT_EQUALLY")));

    private static boolean teamPropertiesRegistered;

    /** Guards registration; never the class monitor (SpotBugs USO rule). */
    private static final Object REGISTRATION_LOCK = new Object();

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

    /**
     * The team's raw addon-setting overrides (issue #21): the switch is
     * always present (its default equals the parser fallback), rule and
     * amount only when the team chose a real value - the inherit sentinel
     * is dropped so an untouched team falls back to the server config. A
     * missing team or an unloaded manager reads as "no overrides".
     */
    @Override
    public Map<String, String> settingOverrides(MinecraftServer server, UUID partyId) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return Map.of();
        }
        Optional<Team> team = api.getManager().getTeamByID(partyId);
        if (team.isEmpty()) {
            return Map.of();
        }
        Team found = team.get();
        Map<String, String> overrides = new HashMap<>();
        Boolean enabled = found.getProperty(ENABLED_PROPERTY);
        if (enabled != null) {
            overrides.put(PartySettingOverrides.KEY_ENABLED, String.valueOf(enabled));
        }
        putIfChosen(overrides, PartySettingOverrides.KEY_LOOT_RULE, found.getProperty(LOOT_RULE_PROPERTY));
        putIfChosen(overrides, PartySettingOverrides.KEY_LOOT_AMOUNT, found.getProperty(LOOT_AMOUNT_PROPERTY));
        return overrides;
    }

    /**
     * Subscribes the three team properties to FTB's property collection
     * (idempotent). Called from mod construction behind
     * {@code isLoaded("ftbteams")}: FTB fires the collection event whenever a
     * team's property set is (re)built - team creation and world load, both
     * after all mod constructors - and the FTB settings screen renders
     * whatever the collection holds, so this is the only registration point
     * needed.
     */
    public static void registerTeamProperties() {
        synchronized (REGISTRATION_LOCK) {
            if (teamPropertiesRegistered) {
                return;
            }
            teamPropertiesRegistered = true;
            TeamEvent.COLLECT_PROPERTIES.register(event -> {
                event.add(ENABLED_PROPERTY);
                event.add(LOOT_RULE_PROPERTY);
                event.add(LOOT_AMOUNT_PROPERTY);
            });
        }
    }

    private static void putIfChosen(Map<String, String> overrides, String key, String value) {
        if (value != null && !INHERIT.equals(value)) {
            overrides.put(key, value);
        }
    }
}
