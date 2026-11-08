package org.bitwisemadness.quickloot.gametest;

import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler;
import io.github.lightman314.lightmanscurrency.common.core.ModItems;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.compat.LightmansCurrencyIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.container.LootContainerLatches;
import org.bitwisemadness.quickloot.container.QuickDeposit;
import org.bitwisemadness.quickloot.loot.DropEvents;
import org.bitwisemadness.quickloot.loot.DropInserter;
import org.bitwisemadness.quickloot.loot.WalletCoinObserver;
import org.bitwisemadness.quickloot.net.InsertedPayload;

/**
 * GameTests for the Lightman's Currency integration (issue #4): the wallet
 * stage in the insertion funnel, the fallback paths, and the observer that
 * owns every wallet-insert toast.
 *
 * <p>Lightman's Currency is staged through {@code gametestMods}, so coin data
 * is loaded (server-started config reload) before any test runs and the LC
 * types are on this source set's compile classpath. Value assertions are
 * computed from LC's own chain data rather than hard-coded denominations, so
 * they hold whatever the coin chain is worth.</p>
 */
@GameTestHolder(QuickLoot.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightmansCurrencyGameTests {

    private LightmansCurrencyGameTests() {
    }

    /**
     * With a wallet equipped every coin stack routed through the insertion
     * funnel lands in the wallet at exactly its value, never self-notifies
     * (the observer owns wallet toasts), never leaks into the inventory -
     * and non-coins plus quick-deposit coins keep their own paths.
     */
    @GameTest(template = "empty")
    public static void walletRoutingTakesCoinsBeforeInventory(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "wallet-router");
        var config = QuickLootConfig.SERVER;
        TestSupport.check(config.coinsToWallet.get(), "the test assumes coinsToWallet default on");
        WalletHandler.get(player).setWallet(new ItemStack(ModItems.WALLET_LEATHER.get()));
        TestSupport.check(!LightmansCurrencyIntegration.equippedWallet(player).isEmpty(),
                "the wallet must be equipped");

        var coins = new ItemStack(ModItems.COIN_COPPER.get(), 5);
        long expected = valueOf(coins);
        TestSupport.check(expected > 0, "LC coin data must recognise the copper coin");
        long before = LightmansCurrencyIntegration.walletTotal(player);
        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, coins, inserted);
        TestSupport.check(remainder.isEmpty(), "the wallet must take the whole stack");
        TestSupport.check(inserted.isEmpty(),
                "the routed portion must not self-notify - the observer owns wallet toasts");
        TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) - before == expected,
                "the balance rises by exactly the routed value");
        TestSupport.check(TestSupport.countItem(player, ModItems.COIN_COPPER.get()) == 0,
                "no coins may leak into the inventory");

        var gems = new ItemStack(Items.DIAMOND, 7);
        var gemInserted = new ArrayList<ItemStack>();
        var gemRemainder = DropInserter.insert(player, gems, gemInserted);
        TestSupport.check(gemRemainder.isEmpty() && gemInserted.size() == 1,
                "non-coins keep taking the inventory path beside a wallet");
        TestSupport.check(TestSupport.countItem(player, Items.DIAMOND) == 7,
                "the diamonds land in the inventory");
        TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) - before == expected,
                "the wallet is untouched by non-coins");

        // Quick-deposit rides the same funnel: chest coins go to the wallet,
        // while the player's loose coin (the eligibility evidence) stays put.
        var level = helper.getLevel();
        helper.setBlock(BlockPos.ZERO, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(BlockPos.ZERO);
        var chestCoins = new ItemStack(ModItems.COIN_COPPER.get(), 3);
        chest.setItem(0, chestCoins);
        long chestValue = valueOf(chestCoins);
        player.getInventory().items.set(0, new ItemStack(ModItems.COIN_COPPER.get(), 1));
        LootContainerLatches.latch(level.getServer(), level.dimension(),
                helper.absolutePos(BlockPos.ZERO));
        player.containerMenu = ChestMenu.threeRows(300, player.getInventory(), chest);
        long beforeDeposit = LightmansCurrencyIntegration.walletTotal(player);
        QuickDeposit.run(player);
        TestSupport.check(chest.getItem(0).isEmpty(), "the chest's coins must move");
        TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) - beforeDeposit == chestValue,
                "quick-deposit coins land in the wallet at exactly their value");
        TestSupport.check(TestSupport.countItem(player, ModItems.COIN_COPPER.get()) == 1,
                "only the player's original loose coin stays in the inventory");
        helper.succeed();
    }

    /**
     * The fallback paths: without a wallet equipped coins continue into the
     * inventory like any other stack, and with {@code coinsToWallet} off they
     * do the same even wearing one - nothing is lost either way.
     */
    @GameTest(template = "empty")
    public static void walletFallbackWithoutWalletOrToggle(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "wallet-off");
        var config = QuickLootConfig.SERVER;
        boolean wasOn = config.coinsToWallet.get();
        try {
            TestSupport.check(LightmansCurrencyIntegration.equippedWallet(player).isEmpty(),
                    "a fresh player wears no wallet");
            var unowned = new ItemStack(ModItems.COIN_COPPER.get(), 4);
            var unownedInserted = new ArrayList<ItemStack>();
            var unownedRest = DropInserter.insert(player, unowned, unownedInserted);
            TestSupport.check(unownedRest.isEmpty() && unownedInserted.size() == 1,
                    "without a wallet the coins fall through to the inventory");
            TestSupport.check(TestSupport.countItem(player, ModItems.COIN_COPPER.get()) == 4,
                    "all four coins land in the inventory");
            TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) == 0,
                    "no wallet means no balance");

            WalletHandler.get(player).setWallet(new ItemStack(ModItems.WALLET_LEATHER.get()));
            config.coinsToWallet.set(false);
            var toggled = new ItemStack(ModItems.COIN_COPPER.get(), 6);
            var toggledInserted = new ArrayList<ItemStack>();
            var toggledRest = DropInserter.insert(player, toggled, toggledInserted);
            TestSupport.check(toggledRest.isEmpty() && toggledInserted.size() == 1,
                    "coinsToWallet=false keeps coins as items even with a wallet equipped");
            TestSupport.check(TestSupport.countItem(player, ModItems.COIN_COPPER.get()) == 10,
                    "4 + 6 coins, none lost");
            TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) == 0,
                    "the wallet stays untouched while the toggle is off");
        } finally {
            config.coinsToWallet.set(wasOn);
        }
        helper.succeed();
    }

    /**
     * The observer's toast lifecycle: first sight is silent, equipping a
     * pre-loaded wallet re-baselines silently (no phantom gain), an insert is
     * toasted exactly once at exactly its value (verified on the wire via the
     * tick path), inserts made while toasts are gated off are never
     * back-logged, and logout drops the baseline.
     */
    @GameTest(template = "empty")
    public static void walletObserverOwnsEveryToast(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "wallet-toast");
        var config = QuickLootConfig.SERVER;
        var server = helper.getLevel().getServer();
        boolean wasNotify = config.lootJournalNotify.get();
        try {
            TestSupport.check(config.enabled.get() && wasNotify,
                    "the test assumes toasts enabled by default");
            TestSupport.check(WalletCoinObserver.capture(player).isEmpty(),
                    "first sight only records a baseline");

            var loaded = new ItemStack(ModItems.WALLET_LEATHER.get());
            WalletItem.PickupCoin(loaded, new ItemStack(ModItems.COIN_COPPER.get(), 9));
            WalletHandler.get(player).setWallet(loaded);
            TestSupport.check(WalletCoinObserver.capture(player).isEmpty(),
                    "equipping a pre-loaded wallet must re-baseline silently, never toast a phantom gain");

            var firstInsert = new ItemStack(ModItems.COIN_COPPER.get(), 5);
            long firstDelta = valueOf(firstInsert);
            boolean allowed = LightmansCurrencyIntegration.isCoinAllowed(firstInsert);
            long beforeInsert = LightmansCurrencyIntegration.walletTotal(player);
            DropInserter.insert(player, firstInsert, new ArrayList<>());
            long afterInsert = LightmansCurrencyIntegration.walletTotal(player);
            int looseCoins = TestSupport.countItem(player, ModItems.COIN_COPPER.get());
            List<ItemStack> gained = WalletCoinObserver.capture(player);
            TestSupport.check(!gained.isEmpty(),
                    "the insert must produce toast stacks (allowed=" + allowed
                            + ", before=" + beforeInsert + ", after=" + afterInsert
                            + ", loose=" + looseCoins + ", expectedDelta=" + firstDelta + ")");
            TestSupport.check(valueOf(gained) == firstDelta,
                    "the toast must report exactly the inserted value");
            TestSupport.check(WalletCoinObserver.capture(player).isEmpty(),
                    "a repeated capture must be empty");

            // The tick path is the production sender: one payload, exact value.
            TestSupport.drainInserted(player);
            var tickInsert = new ItemStack(ModItems.COIN_COPPER.get(), 6);
            long tickDelta = valueOf(tickInsert);
            DropInserter.insert(player, tickInsert, new ArrayList<>());
            NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server));
            List<InsertedPayload> toasts = TestSupport.drainInserted(player);
            TestSupport.check(toasts.size() == 1, "exactly one toast packet per wallet insert");
            TestSupport.check(valueOf(toasts.get(0).stacks()) == tickDelta,
                    "the sent payload carries exactly the inserted value");

            // Gated inserts are never back-logged once toasts come back on.
            config.lootJournalNotify.set(false);
            DropInserter.insert(player, new ItemStack(ModItems.COIN_COPPER.get(), 7), new ArrayList<>());
            NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server));
            TestSupport.check(TestSupport.drainInserted(player).isEmpty(),
                    "no toast may be sent while toasts are gated off");
            config.lootJournalNotify.set(true);
            TestSupport.check(WalletCoinObserver.capture(player).isEmpty(),
                    "re-enabling must not toast coins inserted while it was off");

            // Logout drops the baseline: the next capture cannot diff against it.
            DropInserter.insert(player, new ItemStack(ModItems.COIN_COPPER.get(), 2), new ArrayList<>());
            WalletCoinObserver.onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            TestSupport.check(WalletCoinObserver.capture(player).isEmpty(),
                    "a cleared baseline must not diff against the pre-logout value");
        } finally {
            config.lootJournalNotify.set(wasNotify);
        }
        helper.succeed();
    }

    /**
     * Lightman's Currency death-spawned coins (issue #11) ride the same funnel
     * as event drops: a kill whose only loot is an LC-spawned coin entity ends
     * with that coin discarded from the world, its value in the wallet, and
     * one Loot Journal toast of exactly that value on the observer's tick
     * path. Assertions are scoped to the fixture entity - sibling tests share
     * the mock victim's world-origin scan box, so a box query would read their
     * leftovers as ours.
     */
    @GameTest(template = "empty")
    public static void deathSpawnedCoinsReachTheWallet(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "coin-killer");
        WalletHandler.get(killer).setWallet(new ItemStack(ModItems.WALLET_LEATHER.get()));
        TestSupport.drainInserted(killer);
        TestSupport.check(WalletCoinObserver.capture(killer).isEmpty(),
                "first sight only records a baseline");
        long before = LightmansCurrencyIntegration.walletTotal(killer);
        long coinValue = valueOf(new ItemStack(ModItems.COIN_COPPER.get(), 3));

        var victim = helper.makeMockPlayer(GameType.CREATIVE);
        var level = helper.getLevel();
        var coin = new ItemEntity(level, 0.5, 1.0, 0.5, new ItemStack(ModItems.COIN_COPPER.get(), 3));
        TestSupport.check(level.addFreshEntity(coin), "fixture: the coin entity must spawn");

        var event = new LivingDropsEvent(victim, level.damageSources().playerAttack(killer),
                new ArrayList<>(), true);
        DropEvents.handleLivingDrops(event, null);

        TestSupport.check(!event.isCanceled(), "never canceled");
        TestSupport.check(coin.isRemoved(),
                "the death-spawned coin must be discarded (age=" + coin.getAge()
                        + ", allowed=" + LightmansCurrencyIntegration.isCoinAllowed(coin.getItem()) + ")");
        TestSupport.check(LightmansCurrencyIntegration.walletTotal(killer) - before == coinValue,
                "the coin's value must land in the wallet");
        TestSupport.check(TestSupport.countItem(killer, ModItems.COIN_COPPER.get()) == 0,
                "no loose coins in the inventory");

        // The observer owns wallet toasts and sends them on the tick path.
        var server = helper.getLevel().getServer();
        NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server));
        List<InsertedPayload> toasts = TestSupport.drainInserted(killer);
        TestSupport.check(toasts.size() == 1, "exactly one toast for the insert");
        TestSupport.check(valueOf(toasts.get(0).stacks()) == coinValue,
                "the toast must report the inserted value");
        helper.succeed();
    }

    /** With the gates off, LC's death coins stay on the ground like any vanilla drop. */
    @GameTest(template = "empty")
    public static void gatesOffLeaveDeathCoins(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "coin-gates");
        var config = QuickLootConfig.SERVER;
        boolean wasEnabled = config.enabled.get();
        var victim = helper.makeMockPlayer(GameType.CREATIVE);
        var level = helper.getLevel();
        var coin = new ItemEntity(level, 0.5, 1.0, 0.5, new ItemStack(ModItems.COIN_COPPER.get(), 3));
        TestSupport.check(level.addFreshEntity(coin), "fixture: the coin entity must spawn");
        var drops = new ArrayList<ItemEntity>();
        drops.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND)));
        var event = new LivingDropsEvent(victim, level.damageSources().playerAttack(killer), drops, true);
        try {
            config.enabled.set(false);
            DropEvents.handleLivingDrops(event, null);

            TestSupport.check(!event.isCanceled(), "never canceled");
            TestSupport.check(!coin.isRemoved(),
                    "gates off: LC's coins stay on the ground");
            TestSupport.check(event.getDrops().size() == 1,
                    "gates off: the event's own drops stay untouched");
        } finally {
            config.enabled.set(wasEnabled);
            // Fixture teardown: the mock victim's scan box is shared with
            // sibling tests, so a leftover coin here would pollute theirs.
            coin.discard();
        }
        helper.succeed();
    }

    /** A coin from an earlier tick near the victim is left to vanilla pickup. */
    @GameTest(template = "empty")
    public static void olderCoinsAreLeftAlone(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "coin-stale");
        WalletHandler.get(killer).setWallet(new ItemStack(ModItems.WALLET_LEATHER.get()));
        long before = LightmansCurrencyIntegration.walletTotal(killer);

        var victim = helper.makeMockPlayer(GameType.CREATIVE);
        var level = helper.getLevel();
        var coin = new ItemEntity(level, 0.5, 1.0, 0.5, new ItemStack(ModItems.COIN_COPPER.get(), 3));
        TestSupport.check(level.addFreshEntity(coin), "fixture: the coin entity must spawn");
        coin.tick();
        TestSupport.check(coin.getAge() > 0, "fixture: the coin must be older than this kill");

        var event = new LivingDropsEvent(victim, level.damageSources().playerAttack(killer),
                new ArrayList<>(), true);
        try {
            DropEvents.handleLivingDrops(event, null);

            TestSupport.check(!coin.isRemoved(), "an older coin must stay on the ground");
            TestSupport.check(coin.getAge() > 0, "the survivor must still be older than this kill");
            TestSupport.check(LightmansCurrencyIntegration.walletTotal(killer) == before,
                    "an older coin must not credit the wallet");
        } finally {
            // Fixture teardown: keep the shared scan box clean for siblings.
            coin.discard();
        }
        helper.succeed();
    }

    private static ItemEntity itemAt(ServerLevel level, BlockPos absolutePos, ItemStack stack) {
        Vec3 pos = Vec3.atBottomCenterOf(absolutePos);
        return new ItemEntity(level, pos.x, pos.y, pos.z, stack);
    }

    /** One stack's value in LC core units, straight from its own chain data. */
    private static long valueOf(ItemStack stack) {
        ChainData chain = CoinAPI.getApi().ChainDataOfCoin(stack);
        TestSupport.check(chain != null, "LC coin data must recognise " + stack);
        return chain.getCoreValue(stack) * stack.getCount();
    }

    /** The combined value of several coin stacks. */
    private static long valueOf(List<ItemStack> stacks) {
        long total = 0L;
        for (ItemStack stack : stacks) {
            total += valueOf(stack);
        }
        return total;
    }
}
