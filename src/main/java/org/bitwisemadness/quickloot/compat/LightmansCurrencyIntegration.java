package org.bitwisemadness.quickloot.compat;

import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.money.value.IItemBasedValue;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler;
import io.github.lightman314.lightmanscurrency.common.attachments.wallet.WalletHelpers;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The only class that touches Lightman's Currency types. Callers must gate on
 * {@code ModList.isLoaded("lightmanscurrency")} before entering, so none of
 * this can class-load when the mod is absent.
 *
 * <p>The insert funnel is LC's own {@code WalletHandler.PickupCoins} - the
 * same method LC's auto-pickup divert and its {@code giveItemToPlayer}
 * intercept use - so Quick: Loot! deposits behave exactly like a wallet pickup,
 * curios wallets included. Acceptance is checked with LC's own
 * {@code IsAllowedInCoinContainer} gate first: the underlying
 * {@code TryPutItemStack} would happily stuff non-coins into the wallet's
 * slot container.</p>
 *
 * <p>Money is read as a value ({@link MoneyValue#getCoreValue}) rather than
 * by slot contents: {@code WalletItem.PickupCoin} auto-exchanges and re-sorts
 * the wallet's coin slots, so slot diffs would churn on denomination changes
 * while the value stays exact.</p>
 */
public final class LightmansCurrencyIntegration {

    private LightmansCurrencyIntegration() {
    }

    /** True when LC accepts this stack into a coin container (its own divert gate). */
    public static boolean isCoinAllowed(ItemStack stack) {
        return !stack.isEmpty() && CoinAPI.getApi().IsAllowedInCoinContainer(stack, false);
    }

    /**
     * The player's equipped wallet stack (curios-aware), or an empty stack.
     * Returns the handler's live field reference - identity-stable while the
     * wallet stays equipped, which is what the observer fingerprints on.
     */
    public static ItemStack equippedWallet(ServerPlayer player) {
        return WalletHandler.get(player).getWallet();
    }

    /**
     * Credits {@code stack} to the equipped wallet; returns what the wallet
     * could not take. Unchanged input means nothing was taken - no wallet, not
     * an accepted coin, or wallet full - so the caller falls back to its
     * normal insertion chain. Nothing is ever destroyed either way.
     */
    public static ItemStack insertCoins(ServerPlayer player, ItemStack stack) {
        if (!isCoinAllowed(stack)) {
            return stack;
        }
        WalletHandler handler = WalletHandler.get(player);
        ItemStack wallet = handler.getWallet();
        if (wallet.isEmpty() || !(wallet.getItem() instanceof WalletItem)) {
            return stack;
        }
        return handler.PickupCoins(stack);
    }

    /** The player's wallet balance as a single core value (0 without a wallet). */
    public static long walletTotal(ServerPlayer player) {
        if (equippedWallet(player).isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (MoneyValue value : WalletHelpers.getWalletMoney(player).allValues()) {
            total += value.getCoreValue();
        }
        return total;
    }

    /**
     * The money value expressed as coin stacks, for a pickup toast. The chain
     * comes from the coins currently in the wallet (delta &gt; 0 implies the
     * wallet holds coins); an unrecognised chain yields no stacks rather than
     * a wrong toast.
     *
     * <p>The coin slots are the wallet stack's own data contents - not
     * {@code WalletHelpers.getWalletContainer}, which is a one-slot container
     * holding the wallet <em>item</em> for the wallet screen.</p>
     */
    public static List<ItemStack> coinStacksFor(ServerPlayer player, long delta) {
        if (delta <= 0) {
            return List.of();
        }
        ItemStack wallet = equippedWallet(player);
        if (wallet.isEmpty() || !(wallet.getItem() instanceof WalletItem)) {
            return List.of();
        }
        Container contents = WalletItem.getDataWrapper(wallet).getContents();
        for (int slot = 0; slot < contents.getContainerSize(); slot++) {
            ItemStack present = contents.getItem(slot);
            if (present.isEmpty()) {
                continue;
            }
            ChainData chain = CoinAPI.getApi().ChainDataOfCoin(present);
            if (chain == null) {
                continue;
            }
            MoneyValue value = CoinValue.fromNumber(chain, delta);
            if (value instanceof IItemBasedValue itemBased) {
                List<ItemStack> stacks = new ArrayList<>(itemBased.getAsItemList());
                stacks.removeIf(ItemStack::isEmpty);
                return stacks;
            }
            return List.of();
        }
        return List.of();
    }
}
