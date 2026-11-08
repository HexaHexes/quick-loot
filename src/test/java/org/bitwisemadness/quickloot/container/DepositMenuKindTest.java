package org.bitwisemadness.quickloot.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The pure config half of the quick-insert menu whitelist (issue #13): the
 * default list, the load-time predicate and the name parsing, all without a
 * Minecraft runtime.
 */
class DepositMenuKindTest {

    @Test
    void defaultsAreEveryKindInConfigOrder() {
        assertEquals(List.of("chest", "shulker", "hopper", "dispenser"),
                DepositMenuKind.defaultNames(),
                "the default quickDeposit.menus value must be every kind");
    }

    @Test
    void onlyKnownLowercaseNamesAreValid() {
        for (String name : DepositMenuKind.defaultNames()) {
            assertTrue(DepositMenuKind.isValidName(name), name + " must be valid");
        }
        assertFalse(DepositMenuKind.isValidName("Chest"), "names are case-sensitive");
        assertFalse(DepositMenuKind.isValidName("barrel"), "barrels ride the chest kind");
        assertFalse(DepositMenuKind.isValidName(""), "empty is not a kind");
        assertFalse(DepositMenuKind.isValidName(42), "only strings qualify");
        assertFalse(DepositMenuKind.isValidName(null), "null must not qualify");
    }

    @Test
    void parseMapsNamesAndDefaultsToEveryKind() {
        Set<DepositMenuKind> parsed =
                DepositMenuKind.parse(List.of("chest", "dispenser"));
        assertEquals(Set.of(DepositMenuKind.CHEST, DepositMenuKind.DISPENSER), parsed);
        assertEquals(Set.of(DepositMenuKind.values()),
                DepositMenuKind.parse(DepositMenuKind.defaultNames()),
                "the default list must parse to every kind");
    }
}
