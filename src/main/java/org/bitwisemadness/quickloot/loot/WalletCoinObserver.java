package org.bitwisemadness.quickloot.loot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.compat.LightmansCurrencyIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.net.InsertNotifier;

/**
 * The single source of Loot Journal pickup toasts for currency that lands in a
 * Lightman's Currency wallet.
 *
 * <p>Every server-side wallet mutation - Quick Loot's own inserts, LC's
 * auto-pickup divert (which clears the item entity and suppresses the vanilla
 * take-packet, so Loot Journal would otherwise stay silent), LC's
 * {@code giveItemToPlayer} intercept, wallet enchantments, wallet-screen
 * deposits - moves the wallet's total value, and this observer sees all of
 * them through one lens. Value-based rather than slot-based because
 * {@code WalletItem.PickupCoin} auto-exchanges and re-sorts coin slots:
 * totals are exact where slot diffs would churn.</p>
 *
 * <p>Two invariants keep the toast stream honest: the baseline is fingerprinted
 * on the equipped wallet's <em>identity</em> ({@code equals} would false-match
 * because deposits mutate the wallet in place), so equipping a pre-loaded
 * wallet re-baselines silently instead of toasting a phantom gain; and
 * {@link org.bitwisemadness.quickloot.loot.DropInserter} never self-notifies
 * the routed portion, so this observer is the only wallet toaster - no
 * doubles by construction. Baselines clear while toasts are gated off, so
 * re-enabling the config never toasts an accumulated backlog.</p>
 *
 * <p>Inactive entirely without Lightman's Currency: every entry point checks
 * {@code isLoaded} before any LC type can resolve.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class WalletCoinObserver {

    /** Per-player baselines: the wallet identity and value last seen. */
    private static final Map<UUID, Baseline> BASELINES = new HashMap<>();

    private WalletCoinObserver() {
    }

    private record Baseline(ItemStack wallet, long total) {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!QuickLootConfig.SERVER.enabled.get() || !QuickLootConfig.SERVER.lootJournalNotify.get()) {
            // Gated: drop baselines so re-enabling starts fresh instead of
            // toasting everything inserted while the switch was off.
            if (!BASELINES.isEmpty()) {
                BASELINES.clear();
            }
            return;
        }
        if (!ModList.get().isLoaded("lightmanscurrency")) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            List<ItemStack> gained = capture(player);
            if (!gained.isEmpty()) {
                InsertNotifier.send(player, gained, 0);
            }
        }
    }

    /** Drops the leaver's baseline so a rejoin re-baselines instead of guessing. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BASELINES.remove(event.getEntity().getUUID());
    }

    /**
     * The coin stacks that entered the player's wallet since the last capture,
     * ready for a pickup toast; empty on first sight, after any wallet change,
     * or when nothing was gained. Production calls this from
     * {@link #onServerTick}; GameTests call it synchronously around an insert.
     */
    public static List<ItemStack> capture(ServerPlayer player) {
        ItemStack wallet = LightmansCurrencyIntegration.equippedWallet(player);
        long total = LightmansCurrencyIntegration.walletTotal(player);
        Baseline previous = BASELINES.put(player.getUUID(), new Baseline(wallet, total));
        if (previous != null && previous.wallet() == wallet && total > previous.total()) {
            return LightmansCurrencyIntegration.coinStacksFor(player, total - previous.total());
        }
        return List.of();
    }
}
