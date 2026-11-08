package org.bitwisemadness.quickloot.container;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.quickloot.net.ContainerFlagPayload;

/**
 * Client-side store of the server's per-position loot-evidence verdicts.
 *
 * <p>Deliberately a common (non-client-only) class: the flag payload handler
 * lives in common code but only ever executes on the client, so no
 * client-only types may appear here. State is cleared on logout so a new
 * session never inherits stale verdicts.</p>
 */
public final class ContainerFlagState {

    /** Positions in the current session the server flagged as loot-bearing. */
    private static final Set<Long> VISIBLE = new HashSet<>();

    private ContainerFlagState() {
    }

    /** Server: pushes a verdict to a single player (called when they interact). */
    public static void send(ServerPlayer player, BlockPos pos, boolean visible) {
        PacketDistributor.sendToPlayer(player, new ContainerFlagPayload(pos, visible));
    }

    /** Client: stores an incoming verdict. */
    public static void receive(ContainerFlagPayload payload, net.minecraft.world.entity.player.Player player) {
        if (payload.visible()) {
            VISIBLE.add(payload.pos().asLong());
        } else {
            VISIBLE.remove(payload.pos().asLong());
        }
    }

    /** Whether the server has flagged this position as loot-bearing this session. */
    public static boolean isVisible(BlockPos pos) {
        return VISIBLE.contains(pos.asLong());
    }

    /** Client: wipes state when the player logs out. */
    public static void clear() {
        VISIBLE.clear();
    }
}
