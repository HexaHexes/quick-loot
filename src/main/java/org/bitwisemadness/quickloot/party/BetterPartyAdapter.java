package org.bitwisemadness.quickloot.party;

import dev.betterparty.api.v1.model.PartyMemberView;
import dev.betterparty.api.v1.model.PartyView;
import dev.betterparty.api.v1.server.BetterPartyServerApi;
import dev.betterparty.api.v1.settings.AddonSettingDefinition;
import dev.betterparty.api.v1.settings.AddonSettingScope;
import dev.betterparty.api.v1.settings.BetterPartyAddonSettings;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Better Party membership and per-party settings via its public server API
 * (All Rights Reserved mod - API calls only, no copied code). Constructed
 * strictly behind a {@code better_party} {@code isLoaded} check; the addon
 * settings registration is invoked from mod construction behind the same
 * check, on both physical sides, because Better Party validates writes
 * against its registry (a late registration would reject a leader's save
 * until the first kill loaded this class) and the client needs the
 * definitions to encode requests and decode snapshots.
 */
public final class BetterPartyAdapter implements PartyBridge {

    public static final BetterPartyAdapter INSTANCE = new BetterPartyAdapter();

    /** Guards registration; never the class monitor (SpotBugs USO rule). */
    private static final Object REGISTRATION_LOCK = new Object();

    /** Assigned once by {@link #registerAddonSettings()} - the idempotency guard. */
    private static AddonSettingDefinition<Boolean> enabledSetting;
    private static AddonSettingDefinition<String> lootRuleSetting;
    private static AddonSettingDefinition<String> lootAmountSetting;

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

    /**
     * The party's raw addon-setting overrides (issue #21). Better Party has
     * no party here (or storage is mid-write) -> empty, which every consumer
     * reads as "fall back to the server config".
     */
    @Override
    public Map<String, String> settingOverrides(MinecraftServer server, UUID partyId) {
        try {
            return BetterPartyServerApi.partySettingOverrides(server, partyId);
        } catch (RuntimeException noSuchParty) {
            return Map.of();
        }
    }

    /**
     * Registers the three PARTY-scoped addon settings (idempotent). Called
     * from mod construction behind {@code isLoaded("better_party")} on both
     * physical sides. Defaults mirror the server config defaults so an unset
     * party behaves exactly like the config it falls back to. The definitions
     * stay private: consumers look them up through Better Party's own
     * {@code BetterPartyAddonSettings.definition} registry.
     */
    public static void registerAddonSettings() {
        synchronized (REGISTRATION_LOCK) {
            if (enabledSetting != null) {
                return;
            }
            enabledSetting = BetterPartyAddonSettings.register(AddonSettingDefinition.bool(
                    PartySettingOverrides.KEY_ENABLED, AddonSettingScope.PARTY, true));
            lootRuleSetting = BetterPartyAddonSettings.register(AddonSettingDefinition.string(
                    PartySettingOverrides.KEY_LOOT_RULE,
                    AddonSettingScope.PARTY,
                    PartyRule.KILLING_BLOW.name(),
                    32));
            lootAmountSetting = BetterPartyAddonSettings.register(AddonSettingDefinition.string(
                    PartySettingOverrides.KEY_LOOT_AMOUNT,
                    AddonSettingScope.PARTY,
                    PartyAmount.OWNER_ONLY.name(),
                    32));
        }
    }

    private static ResolvedParty toParty(PartyView view) {
        // slotIndex is the party's own stable member order (issue #3 spec order).
        return new ResolvedParty(view.partyId(), view.members().stream()
                .sorted(Comparator.comparingInt(PartyMemberView::slotIndex))
                .map(PartyMemberView::playerId)
                .toList());
    }
}
