package org.bitwisemadness.quickloot.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.container.QuickDeposit;

/**
 * Client to server: "deposit the open container now". Deliberately empty -
 * every piece of state (menu, container, scope evidence) is already known and
 * reach-checked server-side, so there is nothing for a client to spoof.
 */
public record QuickDepositPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuickDepositPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "quick_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickDepositPayload> STREAM_CODEC =
            StreamCodec.unit(new QuickDepositPayload());

    @Override
    public Type<QuickDepositPayload> type() {
        return TYPE;
    }

    public static void handle(QuickDepositPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuickDeposit.run(player);
        }
    }
}
