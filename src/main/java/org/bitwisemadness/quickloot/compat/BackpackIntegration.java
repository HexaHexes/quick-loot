package org.bitwisemadness.quickloot.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper;

/**
 * The only class that touches Sophisticated Backpacks types. Callers must gate
 * on {@code ModList.isLoaded("sophisticatedbackpacks")} before entering, so
 * none of this can class-load when the mod is absent.
 *
 * <p>Insertion goes through Sophisticated Backpacks' own pickup-response chain
 * ({@code InventoryHelper.runPickupOnPickupResponseUpgrades}): the Pickup,
 * Advanced Pickup, Magnet and Advanced Magnet upgrades of each carried backpack
 * claim what their filters and settings allow, and a backpack without one of
 * those upgrades passes the stack through untouched - the presence gate and
 * the filter decisions are both SB's own code (issue #7, superseding issue
 * #1's {@code getInventoryForInputOutput()} overflow clause). That IO handler
 * is never an insert path here; the raw handler is read-only, for presence
 * checks.</p>
 *
 * <p> {@code runOnBackpacks} stops at the first consumer call returning true,
 * so consumers return false to keep scanning and true to finish.</p>
 */
public final class BackpackIntegration {

    private BackpackIntegration() {
    }

    /**
     * Runs the stack through every carried backpack's pickup-response chain in
     * provider order and returns what none of them claimed. Mirrors Sophisticated
     * Backpacks' own pickup consumer: the wrapper comes from
     * {@code BackpackLinkedStorageResolver.resolveForGlobalUpgradeProcessing}
     * (a bare {@code fromStack} wrap gives the upgrade wrappers a no-op storage,
     * so nothing would ever claim), the player flag is {@code false} (a silent real insert; SB's own pickup passes its flavour flag for sounds and stats), and
     * the worn-only nerf config is honoured the same way
     * {@code CommonEventHandler.onItemPickup} honours it. Silent by design:
     * the feedback is the Loot Journal toast, not a pickup sound (the no-player
     * overload never plays one).
     */
    public static ItemStack runPickupChain(ServerPlayer player, ItemStack stack) {
        boolean onlyWorn = Config.SERVER.nerfsConfig.onlyWornBackpackTriggersUpgrades.get();
        ItemStack[] remaining = {stack};
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventory, slotName, slot) -> {
            if (remaining[0].isEmpty()) {
                return true;
            }
            IBackpackWrapper wrapper = BackpackLinkedStorageResolver.resolveForGlobalUpgradeProcessing(
                    player.level(), backpack);
            remaining[0] = InventoryHelper.runPickupOnPickupResponseUpgrades(
                    player.level(), wrapper.getUpgradeHandler(), remaining[0], false);
            return remaining[0].isEmpty();
        }, onlyWorn);
        return remaining[0];
    }

    /** Whether the player already carries this item in any backpack (eligibility scan). */
    public static boolean contains(ServerPlayer player, ItemStack stack) {
        boolean[] found = {false};
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventory, slotName, slot) -> {
            IBackpackWrapper wrapper = BackpackWrapper.fromStack(backpack);
            var handler = wrapper.getInventoryHandler();
            for (int i = 0; i < handler.getSlots() && !found[0]; i++) {
                ItemStack present = handler.getStackInSlot(i);
                found[0] = !present.isEmpty() && ItemStack.isSameItemSameComponents(present, stack);
            }
            return found[0];
        });
        return found[0];
    }
}
