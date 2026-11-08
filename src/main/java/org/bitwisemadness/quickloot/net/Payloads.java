package org.bitwisemadness.quickloot.net;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.bitwisemadness.quickloot.QuickLoot;

/** Registers the mod's three play-phase payloads on the mod bus (FML routes
 * {@link RegisterPayloadHandlersEvent} - an {@code IModBusEvent} - there
 * automatically; the annotation's {@code bus} attribute is deprecated). */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class Payloads {

    private Payloads() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(InsertedPayload.TYPE, InsertedPayload.STREAM_CODEC, InsertNotifier::handle);
        registrar.playToClient(ContainerFlagPayload.TYPE, ContainerFlagPayload.STREAM_CODEC, ContainerFlagPayload::handle);
        registrar.playToServer(QuickDepositPayload.TYPE, QuickDepositPayload.STREAM_CODEC, QuickDepositPayload::handle);
    }
}
