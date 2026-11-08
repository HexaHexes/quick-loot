package org.bitwisemadness.quickloot.container;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.config.QuickLootConfig;

/**
 * Positive loot-evidence detection for the quick-insert button.
 *
 * <p>The check runs on {@link PlayerInteractEvent.RightClickBlock} because at
 * right-click a fresh structure container still carries its loot table -
 * {@code unpackLootTable} nulls it during the first menu sync, i.e. one tick
 * <em>after</em> this event. The verdict is latched (persisted) so reopens of
 * an already-unpacked container still qualify, and pushed to the clicking
 * client as a {@code quick_loot:container_flag} for UI gating. The server
 * re-validates independently when the payload arrives - the client flag is
 * cosmetic only.</p>
 *
 * <p>Under {@link DepositScope#ALL_CONTAINERS} any whitelisted block container
 * is flagged (no latch required), which is the documented escape hatch for
 * player storage.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class LootEvidence {

    private LootEvidence() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!QuickLootConfig.SERVER.quickDeposit.get()) {
            return;
        }
        var pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            return;
        }

        boolean visible;
        if (blockEntity instanceof RandomizableContainer randomizable
                && randomizable.getLootTable() != null) {
            // Fresh structure loot: the marker still exists (we are pre-unpack).
            LootContainerLatches.latch(level.getServer(), level.dimension(), pos);
            visible = true;
        } else if (QuickLootConfig.SERVER.quickDepositScope.get() == DepositScope.ALL_CONTAINERS) {
            visible = true;
        } else {
            // Already-unpacked container: only the persisted latch qualifies it.
            visible = LootContainerLatches.hasLatched(level.getServer(), level.dimension(), pos);
        }
        ContainerFlagState.send(player, pos, visible);
    }

    /**
     * Payload-time evidence revalidation - the server-authoritative half of
     * the gate. Works for any container the menu whitelist resolves, including
     * non-block containers (which only qualify under {@code ALL_CONTAINERS}).
     */
    public static boolean hasEvidence(Container container) {
        if (QuickLootConfig.SERVER.quickDepositScope.get() == DepositScope.ALL_CONTAINERS) {
            return true;
        }
        if (container instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null) {
            return true;
        }
        if (container instanceof BlockEntity blockEntity
                && blockEntity.getLevel() instanceof ServerLevel level) {
            return LootContainerLatches.hasLatched(
                    level.getServer(), level.dimension(), blockEntity.getBlockPos());
        }
        return false;
    }
}
