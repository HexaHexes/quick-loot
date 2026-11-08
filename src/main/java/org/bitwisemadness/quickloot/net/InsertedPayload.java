package org.bitwisemadness.quickloot.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.bitwisemadness.quickloot.QuickLoot;

/**
 * Server to client: the stacks Quick Loot inserted for this player (and/or the
 * experience points it awarded directly), so Loot Journal can raise the same
 * pickup notification a real pickup or orb collection would.
 *
 * @param stacks the stacks that landed in this player's inventories
 * @param xp experience points awarded directly to this player (0 when none)
 */
public record InsertedPayload(List<ItemStack> stacks, int xp) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<InsertedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(QuickLoot.MOD_ID, "inserted"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InsertedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ItemStack.OPTIONAL_STREAM_CODEC),
                    InsertedPayload::stacks,
                    ByteBufCodecs.INT,
                    InsertedPayload::xp,
                    InsertedPayload::new);

    @Override
    public Type<InsertedPayload> type() {
        return TYPE;
    }
}
