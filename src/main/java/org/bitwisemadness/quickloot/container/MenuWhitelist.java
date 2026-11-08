package org.bitwisemadness.quickloot.container;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;

/**
 * The exact menu set the quick-insert button may appear on, and the helper
 * that recovers the backing container from one.
 *
 * <p>Whitelist (issue #1 spec): {@link ChestMenu}, {@link ShulkerBoxMenu},
 * {@link HopperMenu}, {@link DispenserMenu}. The client button gate and the
 * server click validation must stay in lockstep on this list - both call
 * {@link #containerOf}.</p>
 */
public final class MenuWhitelist {

    private MenuWhitelist() {
    }

    /** The backing container of a whitelisted menu, or null for anything else. */
    public static Container containerOf(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu) {
            // ChestMenu exposes its container; the others keep it private, so
            // read Slot#container - container slots are always added first in
            // every whitelisted menu's constructor.
            return ((ChestMenu) menu).getContainer();
        }
        if (menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu) {
            if (menu.slots.isEmpty()) {
                return null;
            }
            return menu.slots.getFirst().container;
        }
        return null;
    }

    /** True when this menu class is on the quick-insert whitelist. */
    public static boolean isWhitelisted(AbstractContainerMenu menu) {
        return containerOf(menu) != null;
    }
}
