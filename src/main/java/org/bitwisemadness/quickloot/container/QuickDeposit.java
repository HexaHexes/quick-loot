package org.bitwisemadness.quickloot.container;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.bitwisemadness.quickloot.compat.BackpackIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.loot.DropInserter;
import org.bitwisemadness.quickloot.net.InsertNotifier;

/**
 * The server-authoritative quick-insert deposit: move a loot container's
 * contents into the player's inventory in one click.
 *
 * <p>Every call re-validates the full chain itself - config toggle, menu
 * whitelist (shared with the client via {@link MenuWhitelist}), and scope
 * evidence - because the trigger is an empty client payload with nothing to
 * trust. Only container slots are iterated ({@code 0..getContainerSize()-1});
 * player-owned menu slots are never touched, so the button can never smuggle
 * items <em>into</em> the container or reorder the player's own screens.</p>
 *
 * <p>The eligibility gate (issue #1): a container stack moves only if the same
 * item already exists in the player's 36 main slots or any carried backpack -
 * regardless of the {@code useBackpacks} insert setting, since the gate is
 * about what the player demonstrably carries, not where overflow may spill.
 * Full existing stacks simply result in a fresh stack in an empty slot;
 * ineligible stacks stay put.</p>
 */
public final class QuickDeposit {

    private QuickDeposit() {
    }

    /** Entry point for the {@code quick_loot:quick_deposit} payload handler. */
    public static void run(ServerPlayer player) {
        if (!QuickLootConfig.SERVER.quickDeposit.get()) {
            return;
        }
        Container container = MenuWhitelist.containerOf(player.containerMenu);
        if (container == null) {
            return;
        }
        if (!LootEvidence.hasEvidence(container)) {
            return;
        }
        deposit(player, container);
    }

    /** Moves every eligible stack to the player and syncs the open screen. */
    private static void deposit(ServerPlayer player, Container container) {
        List<ItemStack> notifications = new ArrayList<>();
        boolean anyMoved = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !isEligible(player, stack)) {
                continue;
            }
            ItemStack leftover = DropInserter.insert(player, stack, notifications);
            container.setItem(slot, leftover);
            anyMoved = true;
        }
        if (anyMoved) {
            // setChanged persists the container; broadcasting the menu pushes
            // the new contents to the open screen without reopening it.
            container.setChanged();
            player.containerMenu.broadcastChanges();
        }
        if (!notifications.isEmpty()) {
            InsertNotifier.send(player, notifications);
        }
    }

    /**
     * The eligibility gate: at least one matching stack in the 36 main slots,
     * or present in any carried backpack (read-only scan - see
     * {@link BackpackIntegration#contains}).
     */
    public static boolean isEligible(ServerPlayer player, ItemStack stack) {
        NonNullList<ItemStack> main = player.getInventory().items;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack present = main.get(slot);
            if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, stack)) {
                return true;
            }
        }
        return ModList.get().isLoaded("sophisticatedbackpacks")
                && BackpackIntegration.contains(player, stack);
    }
}
