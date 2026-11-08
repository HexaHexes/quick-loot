package org.bitwisemadness.quickloot.container;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The menu kinds the quick-insert feature can serve, as a pure config type:
 * names, validation and parsing with no Minecraft classes on the type, so the
 * JUnit suite can pin them without a runtime (issue #13).
 *
 * <p>{@link MenuWhitelist} maps menu classes to these kinds; the server config
 * {@code quickDeposit.menus} is a list of the lowercase names below, and both
 * sides read the same synced value (NeoForge ships SERVER configs to clients
 * at login), which is what keeps the client button and the server validation
 * in lockstep.</p>
 */
public enum DepositMenuKind {

    /** Chests, trapped chests and barrels (all open a {@code ChestMenu}). */
    CHEST("chest"),
    /** Shulker boxes. */
    SHULKER("shulker"),
    /** Hoppers. */
    HOPPER("hopper"),
    /** Dispensers and droppers. */
    DISPENSER("dispenser");

    private final String configName;

    DepositMenuKind(String configName) {
        this.configName = configName;
    }

    /** The name this kind carries in the {@code quickDeposit.menus} list. */
    public String configName() {
        return configName;
    }

    /** The default {@code quickDeposit.menus} value: every kind. */
    public static List<String> defaultNames() {
        List<String> names = new ArrayList<>();
        for (DepositMenuKind kind : values()) {
            names.add(kind.configName);
        }
        return List.copyOf(names);
    }

    /**
     * The config predicate for {@code quickDeposit.menus}: every entry must be
     * a known lowercase kind name, so a typo fails the config loudly instead
     * of silently shrinking (or disabling) the feature.
     */
    public static boolean isValidName(Object value) {
        return value instanceof String name && byName(name).isPresent();
    }

    /** The kind for a config name, or empty for anything unknown. */
    public static Optional<DepositMenuKind> byName(String name) {
        for (DepositMenuKind kind : values()) {
            if (kind.configName.equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The kinds named by a config list; unknown names cannot appear (validated at load). */
    public static Set<DepositMenuKind> parse(List<? extends String> names) {
        Set<DepositMenuKind> kinds = EnumSet.noneOf(DepositMenuKind.class);
        for (String name : names) {
            byName(name).ifPresent(kinds::add);
        }
        return kinds;
    }
}
