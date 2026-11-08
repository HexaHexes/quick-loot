package org.bitwisemadness.quickloot.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

/**
 * The only class that touches Sophisticated Backpacks types. Callers must gate
 * on {@code ModList.isLoaded("sophisticatedbackpacks")} before entering, so
 * none of this can class-load when the mod is absent.
 *
 * <p>Inserts go through {@code getInventoryForInputOutput()} exclusively - the
 * filtered IO handler where filter, void, slot-blocker and overflow upgrades
 * apply. The raw handler is only used for read-only presence checks; never
 * insert through it (issue #1 rule).</p>
 *
 * <p> {@code runOnBackpacks} stops at the first consumer call returning true,
 * so consumers return false to keep scanning and true to finish.</p>
 */
public final class BackpackIntegration {

    private BackpackIntegration() {
    }

    /** Inserts into every carried backpack in provider order; returns the remainder. */
    public static ItemStack insert(ServerPlayer player, ItemStack stack) {
        ItemStack[] remaining = {stack};
        PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, inventory, slotName, slot) -> {
            if (remaining[0].isEmpty()) {
                return true;
            }
            IBackpackWrapper wrapper = BackpackWrapper.fromStack(backpack);
            remaining[0] = ItemHandlerHelper.insertItemStacked(
                    wrapper.getInventoryForInputOutput(), remaining[0], false);
            return remaining[0].isEmpty();
        });
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
