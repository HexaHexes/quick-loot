package org.bitwisemadness.quickloot.container;

import java.util.Set;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.bitwisemadness.quickloot.config.QuickLootConfig;

/**
 * The menu set the quick-insert button may appear on, and the helper that
 * recovers the backing container from one.
 *
 * <p>Whitelist (issue #1 spec): {@link ChestMenu}, {@link ShulkerBoxMenu},
 * {@link HopperMenu}, {@link DispenserMenu} - each a
 * {@link DepositMenuKind} - further filtered by the server's
 * {@code quickDeposit.menus} config. The config check lives *inside*
 * {@link #containerOf} because the client button gate and the server's
 * {@code QuickDeposit.run} validation must stay in lockstep: both call this
 * one method, and NeoForge syncs SERVER configs to the client during login
 * (its {@code ConfigSync}), so both sides read the same values with no
 * whitelist payload of our own. Evidence is a separate latch
 * ({@link LootContainerLatches}) on top of this gate.</p>
 */
public final class MenuWhitelist {

    private MenuWhitelist() {
    }

    /** The kinds the server's {@code quickDeposit.menus} currently allows. */
    private static Set<DepositMenuKind> allowedKinds() {
        return DepositMenuKind.parse(QuickLootConfig.SERVER.quickDepositMenus.get());
    }

    /**
     * The kind of a menu this mod can serve, or null for anything else;
     * config-independent, so the class mapping never changes with settings.
     */
    private static DepositMenuKind kindOf(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu) {
            return DepositMenuKind.CHEST;
        }
        if (menu instanceof ShulkerBoxMenu) {
            return DepositMenuKind.SHULKER;
        }
        if (menu instanceof HopperMenu) {
            return DepositMenuKind.HOPPER;
        }
        if (menu instanceof DispenserMenu) {
            return DepositMenuKind.DISPENSER;
        }
        return null;
    }

    /** The backing container of an allowed menu, or null for anything else. */
    public static Container containerOf(AbstractContainerMenu menu) {
        DepositMenuKind kind = kindOf(menu);
        if (kind == null || !allowedKinds().contains(kind)) {
            return null;
        }
        if (menu instanceof ChestMenu chestMenu) {
            // ChestMenu exposes its container; the others keep it private, so
            // read Slot#container - container slots are always added first in
            // every whitelisted menu's constructor.
            return chestMenu.getContainer();
        }
        if (menu.slots.isEmpty()) {
            return null;
        }
        return menu.slots.getFirst().container;
    }

    /** True when this menu class is on the quick-insert whitelist. */
    public static boolean isWhitelisted(AbstractContainerMenu menu) {
        return containerOf(menu) != null;
    }
}
