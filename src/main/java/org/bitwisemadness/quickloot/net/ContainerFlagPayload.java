package org.bitwisemadness.quickloot.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.container.ContainerFlagState;

/**
 * Server to client: the server's loot-evidence verdict for a container
 * position (server-originated only - the client never sends this; it matches
 * the position against the open screen purely for UI).
 */
public record ContainerFlagPayload(BlockPos pos, boolean visible) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContainerFlagPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "container_flag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerFlagPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    ContainerFlagPayload::pos,
                    ByteBufCodecs.BOOL,
                    ContainerFlagPayload::visible,
                    ContainerFlagPayload::new);

    @Override
    public Type<ContainerFlagPayload> type() {
        return TYPE;
    }

    /** Clientbound handler - only ever invoked on the client. */
    public static void handle(ContainerFlagPayload payload, IPayloadContext context) {
        ContainerFlagState.receive(payload, context.player());
    }
}
