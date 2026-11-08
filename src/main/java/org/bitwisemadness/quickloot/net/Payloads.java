package org.bitwisemadness.quickloot.net;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.bitwisemadness.quickloot.QuickLoot;

/**
 * Registers the mod's three play-phase payloads on the mod bus (FML routes
 * {@link RegisterPayloadHandlersEvent} - an {@code IModBusEvent} - there
 * automatically; the annotation's {@code bus} attribute is deprecated).
 *
 * <p>The registrar is <em>optional</em>: a required channel makes NeoForge's
 * channel negotiation disconnect any client that does not offer it, so with
 * required channels no player could connect without this mod - not even a
 * NeoForge client that simply does not install it. Optional channels are
 * disabled for clients that lack them instead (NeoForge's own built-ins
 * register the same way), so every client can join: gameplay is entirely
 * server-side, and our clientbound payloads are only ever delivered to
 * clients that negotiated the channel. The cost is that clients without the
 * mod get no button and no Loot Journal toast - the insert itself is
 * unaffected.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class Payloads {

    private Payloads() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(InsertedPayload.TYPE, InsertedPayload.STREAM_CODEC, InsertNotifier::handle);
        registrar.playToClient(ContainerFlagPayload.TYPE, ContainerFlagPayload.STREAM_CODEC, ContainerFlagPayload::handle);
        registrar.playToServer(QuickDepositPayload.TYPE, QuickDepositPayload.STREAM_CODEC, QuickDepositPayload::handle);
    }
}
