package org.bitwisemadness.quickloot.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The per-party override parser (issue #21): a party's raw addon-setting
 * map resolved against the server config as fallback. Absent means "the
 * party never chose" and wins nothing; garbage means corrupt storage and
 * degrades to the config rather than throwing mid-distribution. Pure - no
 * Minecraft or Better Party types - so this runs in plain CI.
 */
class PartySettingOverridesTest {

    // --- enabled -----------------------------------------------------------

    @Test
    void absentEnabledFallsBackToTheConfig() {
        assertTrue(PartySettingOverrides.enabled(Map.of(), true), "unset reads as the fallback");
        assertFalse(PartySettingOverrides.enabled(Map.of(), false), "unset never invents a value");
    }

    @Test
    void presentEnabledParsesEitherCase() {
        assertTrue(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "true"), false));
        assertTrue(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "TRUE"), false));
        assertFalse(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "false"), true));
        assertFalse(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "FALSE"), true));
    }

    @Test
    void garbageEnabledFallsBackToTheConfig() {
        assertTrue(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "banana"), true),
                "garbage is neutral: it yields the fallback, never a forced value");
        assertFalse(PartySettingOverrides.enabled(
                Map.of(PartySettingOverrides.KEY_ENABLED, "banana"), false),
                "garbage is neutral: it yields the fallback, never a forced value");
    }

    // --- loot rule ---------------------------------------------------------

    @Test
    void absentRuleFallsBackToTheConfig() {
        assertEquals(PartyRule.ROUND_ROBIN, PartySettingOverrides.lootRule(Map.of(), PartyRule.ROUND_ROBIN));
        assertEquals(PartyRule.KILLING_BLOW, PartySettingOverrides.lootRule(Map.of(), PartyRule.KILLING_BLOW));
    }

    @Test
    void presentRuleParses() {
        assertEquals(PartyRule.FREE_FOR_ALL, PartySettingOverrides.lootRule(
                Map.of(PartySettingOverrides.KEY_LOOT_RULE, "FREE_FOR_ALL"), PartyRule.KILLING_BLOW));
        assertEquals(PartyRule.ROUND_ROBIN, PartySettingOverrides.lootRule(
                Map.of(PartySettingOverrides.KEY_LOOT_RULE, "ROUND_ROBIN"), PartyRule.KILLING_BLOW));
    }

    @Test
    void garbageRuleFallsBackToTheConfig() {
        assertEquals(PartyRule.KILLING_BLOW, PartySettingOverrides.lootRule(
                Map.of(PartySettingOverrides.KEY_LOOT_RULE, "free_for_all"), PartyRule.KILLING_BLOW),
                "rule names are exact enum constants, never normalized");
        assertEquals(PartyRule.ROUND_ROBIN, PartySettingOverrides.lootRule(
                Map.of(PartySettingOverrides.KEY_LOOT_RULE, "42"), PartyRule.ROUND_ROBIN));
    }

    // --- loot amount -------------------------------------------------------

    @Test
    void absentAmountFallsBackToTheConfig() {
        assertEquals(PartyAmount.SPLIT_EQUALLY, PartySettingOverrides.lootAmount(Map.of(), PartyAmount.SPLIT_EQUALLY));
        assertEquals(PartyAmount.OWNER_ONLY, PartySettingOverrides.lootAmount(Map.of(), PartyAmount.OWNER_ONLY));
    }

    @Test
    void presentAmountParses() {
        assertEquals(PartyAmount.FULL_TO_ALL, PartySettingOverrides.lootAmount(
                Map.of(PartySettingOverrides.KEY_LOOT_AMOUNT, "FULL_TO_ALL"), PartyAmount.OWNER_ONLY));
        assertEquals(PartyAmount.SPLIT_EQUALLY, PartySettingOverrides.lootAmount(
                Map.of(PartySettingOverrides.KEY_LOOT_AMOUNT, "SPLIT_EQUALLY"), PartyAmount.OWNER_ONLY));
    }

    @Test
    void garbageAmountFallsBackToTheConfig() {
        assertEquals(PartyAmount.OWNER_ONLY, PartySettingOverrides.lootAmount(
                Map.of(PartySettingOverrides.KEY_LOOT_AMOUNT, "half"), PartyAmount.OWNER_ONLY));
    }

    // --- interaction -------------------------------------------------------

    @Test
    void unrelatedKeysAreIgnoredAndKeysNeverCross() {
        var mixed = Map.of(
                "someone_else:rule", "FREE_FOR_ALL",
                PartySettingOverrides.KEY_LOOT_AMOUNT, "SPLIT_EQUALLY");
        assertEquals(PartyRule.ROUND_ROBIN,
                PartySettingOverrides.lootRule(mixed, PartyRule.ROUND_ROBIN),
                "another addon's key must not leak into our rule");
        assertEquals(PartyAmount.SPLIT_EQUALLY,
                PartySettingOverrides.lootAmount(mixed, PartyAmount.OWNER_ONLY),
                "our amount key still parses beside foreign keys");
    }
}
