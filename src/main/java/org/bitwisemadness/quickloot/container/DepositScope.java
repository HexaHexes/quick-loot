package org.bitwisemadness.quickloot.container;

/** Which containers accept the quick-insert deposit. */
public enum DepositScope {
    /** Only containers with positive loot evidence (server-seen loot table). Default. */
    LOOT_ONLY,
    /** Any whitelisted block container, including player-placed storage. */
    ALL_CONTAINERS
}
