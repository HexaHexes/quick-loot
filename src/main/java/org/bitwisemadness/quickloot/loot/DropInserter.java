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
 * The single insertion chain every Quick: Loot! path flows through: the wallet
 * for Lightman's Currency coins, then the pickup-response chain of carried
 * Sophisticated Backpacks (their pickup and magnet upgrades claim what their
 * rules allow), then the 36 main inventory slots (merge into existing stacks
 * first, then empty slots - pickup semantics). Whatever remains is returned
 * for the caller to leave in the world; nothing is ever destroyed.
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
     * exactly one sender.</p>
     *
     * <p>Backpack second (issue #7): before the inventory sees anything, the
     * stack runs through each carried backpack's pickup-response chain - the
     * same path Sophisticated Backpacks runs when the player walks over an
     * item - so a Pickup, Advanced Pickup, Magnet or Advanced Magnet upgrade
     * decides what its backpack claims, and a backpack without one of those
     * upgrades claims nothing. What leaves our hands, backpack claims and
     * inventory inserts together, is appended to {@code insertedOut} as a
     * single stack for pickup notification - one entry per call.</p>
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
        ItemStack inserted = stack.copy();

        stack = routeToBackpacks(player, stack);
        ItemStack remaining = stack.isEmpty()
                ? ItemStack.EMPTY
                : ItemHandlerHelper.insertItemStacked(
                        new PlayerMainInvWrapper(player.getInventory()), stack.copy(), false);
        if (remaining.getCount() < original) {
            inserted.setCount(original - remaining.getCount());
            insertedOut.add(inserted);
        }
        return remaining;
    }

    /**
     * The wallet stage: routes an accepted coin stack into the equipped wallet
     * and returns what is left for the chain. Everything else (and every
     * stack, when {@code coinsToWallet} is off or Lightman's Currency is
     * absent) passes through untouched.
     */
    private static ItemStack routeToWallet(ServerPlayer player, ItemStack stack) {
        if (!QuickLootConfig.SERVER.coinsToWallet.get()
                || !ModList.get().isLoaded("lightmanscurrency")) {
            return stack;
        }
        return LightmansCurrencyIntegration.insertCoins(player, stack);
    }

    /**
     * The backpack stage (issue #7): each carried backpack gets the stack
     * through Sophisticated Backpacks' own pickup-response chain before the
     * inventory. Only a Pickup, Advanced Pickup, Magnet or Advanced Magnet
     * upgrade claims anything, with its filter and settings deciding; a
     * backpack without one of those upgrades passes the stack through. With
     * {@code useBackpacks} off or Sophisticated Backpacks absent, every stack
     * passes through untouched.
     */
    private static ItemStack routeToBackpacks(ServerPlayer player, ItemStack stack) {
        if (!QuickLootConfig.SERVER.useBackpacks.get()
                || !ModList.get().isLoaded("sophisticatedbackpacks")) {
            return stack;
        }
        return BackpackIntegration.runPickupChain(player, stack);
    }
}
