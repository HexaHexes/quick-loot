package org.bitwisemadness.quickloot.client;

import dev.obscuria.lootjournal.LootJournalHelper;
import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The only class that touches Loot Journal types. It is referenced from the
 * inserted-payload handler strictly behind
 * {@code ModList.isLoaded("loot_journal")}, so the reference never resolves
 * when the mod is absent; the handler itself only ever runs on the client.
 *
 * <p>Loot Journal's own pickup mixin only sees
 * {@code ClientboundTakeItemEntityPacket}, which never fires for direct
 * insertion (no item entity exists) - hence this payload path.</p>
 */
public final class LootJournalClientIntegration {

    private LootJournalClientIntegration() {
    }

    /**
     * Mirrors a vanilla pickup. {@code pickupItem} performs its own config,
     * allowed-item and tracking checks before firing the pickup event, giving
     * full behavioural parity with a real pickup; {@code pickupXp} does the
     * same for the experience toast ({@code SHOW_XP_PICKUPS}), matching what
     * collecting an {@code ExperienceOrb} would show. Since Quick Loot awards
     * experience directly (no orb entity exists), no vanilla packet can
     * produce this notification for us.
     */
    public static void notifyInserted(Player player, List<ItemStack> stacks, int xp) {
        if (!(player instanceof AbstractClientPlayer client)) {
            return;
        }
        for (ItemStack stack : stacks) {
            LootJournalHelper.pickupItem(client, stack);
        }
        if (xp > 0) {
            LootJournalHelper.pickupXp(client, xp);
        }
    }
}
