package org.bitwisemadness.quickloot.loot;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Awards experience exactly the way picking up an {@code ExperienceOrb}
 * would: mending repair first, then whatever is left to the experience bar.
 *
 * <p>Mending matters because vanilla only ever repairs from orb pickup -
 * {@code ExperienceOrb.playerTouch} runs {@code repairPlayerItems} before
 * {@code giveExperiencePoints}, and no other XP grant path does it. Quick
 * Loot absorbs the orbs this mod gates, so without this step enabling the
 * mod would silently stop mending from ever repairing off kills and mining.
 * The repair loop mirrors {@code ExperienceOrb.repairPlayerItems} call for
 * call, including its "XP too small to repair anything is consumed anyway"
 * edge case, so behaviour is identical by construction.</p>
 *
 * <p>The Loot Journal toast reports the full share, not the post-repair
 * leftover: vanilla's toast reads the orb's value from the take packet,
 * which is sent before the repair runs.</p>
 */
final class XpAwards {

    private XpAwards() {
    }

    /**
     * Awards {@code xp} to {@code player} with vanilla orb-pickup semantics.
     *
     * @return the points actually granted to the experience bar (the share
     *         minus whatever mending consumed)
     */
    static int award(ServerPlayer player, int xp) {
        int leftover = repairMending(player, xp);
        if (leftover > 0) {
            player.giveExperiencePoints(leftover);
        }
        return leftover;
    }

    /**
     * Mirrors {@code ExperienceOrb.repairPlayerItems}, iteratively instead of
     * recursively. Each pass repairs one random equipped damaged mending item
     * and consumes its part of the XP; termination is guaranteed because every
     * pass either returns or reduces an item's damage by at least one.
     */
    private static int repairMending(ServerPlayer player, int xp) {
        int remaining = xp;
        while (remaining > 0) {
            Optional<EnchantedItemInUse> repairable = EnchantmentHelper.getRandomItemWith(
                    EnchantmentEffectComponents.REPAIR_WITH_XP, player, ItemStack::isDamaged);
            if (repairable.isEmpty()) {
                return remaining;
            }
            ItemStack stack = repairable.get().itemStack();
            int repairableFromXp = EnchantmentHelper.modifyDurabilityToRepairFromXp(
                    player.serverLevel(), stack, (int) (remaining * stack.getXpRepairRatio()));
            int repair = Math.min(repairableFromXp, stack.getDamageValue());
            stack.setDamageValue(stack.getDamageValue() - repair);
            if (repair <= 0) {
                // Vanilla returns 0 here: with no durability gained the whole
                // amount is consumed. Mirrored for exact parity.
                return 0;
            }
            remaining = remaining - repair * remaining / repairableFromXp;
        }
        return 0;
    }
}
