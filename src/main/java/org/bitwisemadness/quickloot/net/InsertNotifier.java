package org.bitwisemadness.quickloot.net;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.quickloot.client.LootJournalClientIntegration;
import org.bitwisemadness.quickloot.config.QuickLootConfig;

/**
 * Queues inserted-stack pickup notifications to the player's client and owns
 * the inbound handler for them.
 */
public final class InsertNotifier {

    private InsertNotifier() {
    }

    /**
     * Server: one notification for everything that landed in this insert run -
     * inserted stacks and directly-awarded experience points together.
     */
    public static void send(ServerPlayer player, List<ItemStack> stacks, int xp) {
        if ((stacks.isEmpty() && xp <= 0) || !QuickLootConfig.SERVER.lootJournalNotify.get()) {
            return;
        }
        player.connection.send(new InsertedPayload(List.copyOf(stacks), xp));
    }

    /** Server: stacks-only notification (quick deposit, no experience involved). */
    public static void send(ServerPlayer player, List<ItemStack> stacks) {
        send(player, stacks, 0);
    }

    /**
     * Clientbound handler. The Loot Journal class reference lives in an
     * invokestatic that resolves only when this method first executes - on the
     * client - and the {@code isLoaded} gate short-circuits before it when the
     * mod is absent.
     */
    public static void handle(InsertedPayload payload, IPayloadContext context) {
        if (ModList.get().isLoaded("loot_journal")) {
            LootJournalClientIntegration.notifyInserted(context.player(), payload.stacks(), payload.xp());
        }
    }
}
