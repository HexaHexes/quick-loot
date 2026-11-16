package org.bitwisemadness.quickloot.party;

import java.util.Map;

/**
 * Parsing for the per-party Better Party addon-setting overrides (issue #21):
 * the raw {@code key -> encoded value} map a party has stored, resolved
 * against the server config as fallback.
 *
 * <p>Pure on purpose - no Minecraft or Better Party types - so the JUnit
 * suite can pin the fallback rules: absent means "the party never chose" and
 * wins nothing, garbage means "corrupt storage" and degrades to the server
 * config rather than throwing mid-distribution. Keys live here so the
 * definitions registered in {@link BetterPartyAdapter} and this parser can
 * never drift apart.</p>
 */
public final class PartySettingOverrides {

    /** Better Party addon-setting key: per-party share-loot switch. */
    public static final String KEY_ENABLED = "quick_loot:enabled";
    /** Better Party addon-setting key: per-party distribution rule. */
    public static final String KEY_LOOT_RULE = "quick_loot:loot_rule";
    /** Better Party addon-setting key: per-party distribution amount. */
    public static final String KEY_LOOT_AMOUNT = "quick_loot:loot_amount";

    private PartySettingOverrides() {
    }

    /** The party's switch, or {@code fallback} when unset or unparseable. */
    public static boolean enabled(Map<String, String> overrides, boolean fallback) {
        String raw = overrides.get(KEY_ENABLED);
        if (raw == null) {
            return fallback;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }

    /** The party's distribution rule, or {@code fallback} when unset or unparseable. */
    public static PartyRule lootRule(Map<String, String> overrides, PartyRule fallback) {
        String raw = overrides.get(KEY_LOOT_RULE);
        if (raw == null) {
            return fallback;
        }
        try {
            return PartyRule.valueOf(raw);
        } catch (IllegalArgumentException notAConstant) {
            return fallback;
        }
    }

    /** The party's distribution amount, or {@code fallback} when unset or unparseable. */
    public static PartyAmount lootAmount(Map<String, String> overrides, PartyAmount fallback) {
        String raw = overrides.get(KEY_LOOT_AMOUNT);
        if (raw == null) {
            return fallback;
        }
        try {
            return PartyAmount.valueOf(raw);
        } catch (IllegalArgumentException notAConstant) {
            return fallback;
        }
    }
}
