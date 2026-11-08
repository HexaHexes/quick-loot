package org.bitwisemadness.quickloot.gametest;

import io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModItems;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IPickupResponseUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.magnet.MagnetUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.pickup.PickupUpgradeWrapper;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.compat.LightmansCurrencyIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.loot.DropInserter;

/**
 * GameTests for issue #7: loot runs through the pickup-response chain of
 * carried Sophisticated Backpacks before the inventory, so the Pickup,
 * Advanced Pickup, Magnet and Advanced Magnet upgrades decide what their
 * backpack claims, and a backpack without one of those upgrades claims
 * nothing.
 *
 * <p>Sophisticated Backpacks is staged through {@code gametestMods}, so the
 * fixtures install real upgrades onto real carried backpack stacks. Each
 * fixture re-wraps the carried stack and asserts the change persisted, so a
 * silent save failure fails loudly instead of making the behavioural
 * assertions meaningless.</p>
 */
@GameTestHolder(QuickLoot.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SophisticatedBackpackGameTests {

    private SophisticatedBackpackGameTests() {
    }

    /** A pickup upgrade claims loot before the inventory sees it, and the claim is toasted. */
    @GameTest(template = "empty")
    public static void pickupUpgradeClaimsBeforeInventory(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "pickup-basic");
        carryBackpack(player, ModItems.PICKUP_UPGRADE.get());

        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, new ItemStack(Items.DIAMOND, 5), inserted);

        TestSupport.check(remainder.isEmpty(), "the pickup upgrade must claim the whole stack");
        TestSupport.check(TestSupport.countItem(player, Items.DIAMOND) == 0,
                "claimed loot must not reach the inventory");
        TestSupport.check(countInBackpack(player, Items.DIAMOND) == 5,
                "the claimed diamonds must land in the backpack");
        TestSupport.check(inserted.size() == 1 && inserted.get(0).getCount() == 5,
                "the claimed stack must be notified as inserted");
        helper.succeed();
    }

    /** A configured pickup filter decides per stack: rejected loot falls to the inventory. */
    @GameTest(template = "empty")
    public static void pickupFilterRejectsFallsToInventory(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "pickup-filter");
        carryBackpack(player, ModItems.PICKUP_UPGRADE.get());

        IBackpackWrapper wrapper = BackpackWrapper.fromStack(player.getInventory().items.get(0));
        List<PickupUpgradeWrapper> upgrades = wrapper.getUpgradeHandler()
                .getWrappersThatImplement(PickupUpgradeWrapper.class);
        TestSupport.check(!upgrades.isEmpty(), "fixture: the pickup upgrade must be installed");
        upgrades.get(0).getFilterLogic().setAllowList(true);
        upgrades.get(0).getFilterLogic().getFilterHandler()
                .setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        wrapper.getUpgradeHandler().saveInventory();
        TestSupport.check(!pickupUpgrade(player).getFilterLogic()
                        .matchesFilter(new ItemStack(Items.DIAMOND)),
                "fixture: the saved filter must reject diamonds");

        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, new ItemStack(Items.DIAMOND, 5), inserted);
        TestSupport.check(remainder.isEmpty(), "the inventory must take the rejected stack");
        TestSupport.check(TestSupport.countItem(player, Items.DIAMOND) == 5,
                "the rejected diamonds must land in the inventory");
        TestSupport.check(countInBackpack(player, Items.DIAMOND) == 0,
                "the filtered backpack must stay empty");
        helper.succeed();
    }

    /** Presence gate: a backpack without a pickup/magnet upgrade claims nothing - the world gets the remainder. */
    @GameTest(template = "empty")
    public static void plainBackpackClaimsNothing(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "plain-bp");
        carryBackpack(player, null);
        for (int slot = 1; slot < 36; slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, 64));
        }

        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, new ItemStack(Items.DIAMOND, 5), inserted);
        TestSupport.check(remainder.getCount() == 5,
                "without a pickup upgrade nothing claims - the world remainder must come back");
        TestSupport.check(countInBackpack(player, Items.DIAMOND) == 0,
                "a backpack without the upgrade must stay untouched");
        TestSupport.check(inserted.isEmpty(), "nothing inserted means no notification");
        helper.succeed();
    }

    /** The magnet's own pickup-items toggle gates its claim like SB's native vacuum. */
    @GameTest(template = "empty")
    public static void magnetToggleOffDoesNotClaim(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "magnet-off");
        carryBackpack(player, ModItems.MAGNET_UPGRADE.get());

        IBackpackWrapper wrapper = BackpackWrapper.fromStack(player.getInventory().items.get(0));
        List<MagnetUpgradeWrapper> magnets = wrapper.getUpgradeHandler()
                .getWrappersThatImplement(MagnetUpgradeWrapper.class);
        TestSupport.check(!magnets.isEmpty(), "fixture: the magnet upgrade must be installed");
        magnets.get(0).setPickupItems(false);
        wrapper.getUpgradeHandler().saveInventory();
        TestSupport.check(!BackpackWrapper.fromStack(player.getInventory().items.get(0))
                        .getUpgradeHandler().getWrappersThatImplement(MagnetUpgradeWrapper.class)
                        .get(0).shouldPickupItems(),
                "fixture: the toggle must persist as off");

        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, new ItemStack(Items.DIAMOND, 5), inserted);
        TestSupport.check(remainder.isEmpty() && TestSupport.countItem(player, Items.DIAMOND) == 5,
                "with pickup disabled the inventory must take the stack");
        TestSupport.check(countInBackpack(player, Items.DIAMOND) == 0,
                "the magnet must not claim while its toggle is off");
        helper.succeed();
    }

    /** drops.useBackpacks=false skips the chain entirely, upgrade or not. */
    @GameTest(template = "empty")
    public static void useBackpacksSkipsTheChain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "backpacks-off");
        carryBackpack(player, ModItems.PICKUP_UPGRADE.get());

        var config = QuickLootConfig.SERVER;
        boolean wasOn = config.useBackpacks.get();
        try {
            config.useBackpacks.set(false);
            var inserted = new ArrayList<ItemStack>();
            var remainder = DropInserter.insert(player, new ItemStack(Items.DIAMOND, 5), inserted);
            TestSupport.check(remainder.isEmpty() && TestSupport.countItem(player, Items.DIAMOND) == 5,
                    "with useBackpacks off the inventory must take the stack");
            TestSupport.check(countInBackpack(player, Items.DIAMOND) == 0,
                    "the chain must not run while useBackpacks is off");
        } finally {
            config.useBackpacks.set(wasOn);
        }
        helper.succeed();
    }

    /** The wallet stage still runs first: coins reach the wallet, never the pickup chain. */
    @GameTest(template = "empty")
    public static void walletBeatsThePickupChain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "wallet-first");
        carryBackpack(player, ModItems.PICKUP_UPGRADE.get());
        WalletHandler.get(player).setWallet(new ItemStack(lcWallet()));
        long before = LightmansCurrencyIntegration.walletTotal(player);

        var inserted = new ArrayList<ItemStack>();
        var remainder = DropInserter.insert(player, new ItemStack(lcCopper(), 5), inserted);
        TestSupport.check(remainder.isEmpty(), "the wallet must take the coins");
        TestSupport.check(LightmansCurrencyIntegration.walletTotal(player) > before,
                "the wallet balance must rise by the routed coins");
        TestSupport.check(TestSupport.countItem(player, lcCopper()) == 0
                        && countInBackpack(player, lcCopper()) == 0,
                "no coins may reach the inventory or the backpack");
        TestSupport.check(inserted.isEmpty(), "the wallet portion must not self-notify");
        helper.succeed();
    }

    /**
     * Installs an upgrade into a fresh backpack and carries it in the player's
     * first slot; {@code null} carries a plain backpack for the presence gate.
     *
     * <p>The storage/contents UUID matters: without it SB's
     * {@code getUpgradeHandler()} hands out a no-op handler whose upgrades can
     * never claim anything (real backpacks always carry the component), so the
     * fixture assigns one exactly like an obtained backpack has.</p>
     */
    private static void carryBackpack(ServerPlayer player, Item upgrade) {
        ItemStack stack = new ItemStack(ModItems.BACKPACK.get());
        stack.set(ModCoreDataComponents.STORAGE_UUID, UUID.randomUUID());
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(stack);
        if (upgrade != null) {
            var handler = wrapper.getUpgradeHandler();
            if (handler.getSlots() == 0) {
                handler.increaseSize(1);
            }
            handler.setStackInSlot(0, new ItemStack(upgrade));
            handler.saveInventory();
        }
        player.getInventory().items.set(0, stack);
        if (upgrade != null) {
            TestSupport.check(!BackpackWrapper.fromStack(player.getInventory().items.get(0))
                            .getUpgradeHandler()
                            .getWrappersThatImplement(IPickupResponseUpgrade.class).isEmpty(),
                    "fixture: the upgrade must persist on the carried stack");
        }
    }

    private static PickupUpgradeWrapper pickupUpgrade(ServerPlayer player) {
        List<PickupUpgradeWrapper> upgrades = BackpackWrapper
                .fromStack(player.getInventory().items.get(0))
                .getUpgradeHandler()
                .getWrappersThatImplement(PickupUpgradeWrapper.class);
        TestSupport.check(!upgrades.isEmpty(), "fixture: the pickup upgrade must be installed");
        return upgrades.get(0);
    }

    private static int countInBackpack(ServerPlayer player, Item item) {
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(player.getInventory().items.get(0));
        return sumItem(wrapper.getInventoryHandler(), item);
    }

    private static int sumItem(
            net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack present = handler.getStackInSlot(slot);
            if (present.is(item)) {
                total += present.getCount();
            }
        }
        return total;
    }

    /** Lightman's Currency items, fully qualified to avoid the ModItems name clash with SB. */
    private static Item lcCopper() {
        return io.github.lightman314.lightmanscurrency.common.core.ModItems.COIN_COPPER.get();
    }

    private static Item lcWallet() {
        return io.github.lightman314.lightmanscurrency.common.core.ModItems.WALLET_LEATHER.get();
    }
}
