package org.bitwisemadness.quickloot.loot;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.bitwisemadness.quickloot.compat.BackpackIntegration;
import org.bitwisemadness.quickloot.compat.LightmansCurrencyIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;

/**
 * The single insertion chain every Quick Loot path flows through: the wallet
 * for Lightman's Currency coins, then the 36 main inventory slots (merge into
 * existing stacks first, then empty slots - pickup semantics), then carried
 * backpacks when enabled. Whatever remains is returned for the caller to
 * leave in the world; nothing is ever destroyed.
 */
public final class DropInserter {

    private DropInserter() {
    }

    /**
     * Inserts {@code stack} for {@code player} and returns the uninserted
     * remainder (empty when everything landed).
     *
     * <p>Wallet first: Lightman's Currency coin stacks are credited to the
     * player's equipped wallet before any inventory work (issue #4,
     * {@code coinsToWallet}), so the baseline below counts only what reaches
     * inventories - the routed portion is toasted by
     * {@link WalletCoinObserver}, never here, which keeps wallet toasts to
     * exactly one sender. What remains of the stack is appended to
     * {@code insertedOut} as a single stack for pickup notification - one
     * entry per call, counting only what actually entered inventories.</p>
     */
    public static ItemStack insert(ServerPlayer player, ItemStack stack, List<ItemStack> insertedOut) {
        if (stack.isEmpty()) {
            return stack;
        }
        stack = routeToWallet(player, stack);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int original = stack.getCount();
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(
                new PlayerMainInvWrapper(player.getInventory()), stack.copy(), false);
        if (!remaining.isEmpty() && QuickLootConfig.SERVER.useBackpacks.get()
                && ModList.get().isLoaded("sophisticatedbackpacks")) {
            remaining = BackpackIntegration.insert(player, remaining);
        }
        if (remaining.getCount() < original) {
            ItemStack inserted = stack.copy();
            inserted.setCount(original - remaining.getCount());
            insertedOut.add(inserted);
        }
        return remaining;
    }

    /**
     * The wallet stage: routes an accepted coin stack into the equipped wallet
     * and returns what is left for the inventory chain. Everything else (and
     * every stack, when {@code coinsToWallet} is off or Lightman's Currency is
     * absent) passes through untouched.
     */
    private static ItemStack routeToWallet(ServerPlayer player, ItemStack stack) {
        if (!QuickLootConfig.SERVER.coinsToWallet.get()
                || !ModList.get().isLoaded("lightmanscurrency")) {
            return stack;
        }
        return LightmansCurrencyIntegration.insertCoins(player, stack);
    }
}
