package org.bitwisemadness.quickloot.client;

import dev.betterparty.api.v1.client.BetterPartyClientApi;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.bitwisemadness.quickloot.QuickLoot;

/**
 * Contributes Quick: Loot!'s page to Better Party's party-leader settings
 * menu (issue #21): page id {@code quick_loot:party_loot}, scope
 * {@code PARTY_LEADER} (Better Party gates opening and editing). No payloads
 * - Better Party carries every write, validation and snapshot sync itself.
 *
 * <p>Client-only subscriber; the handler checks {@code isLoaded} before any
 * Better Party type is touched, so on a client without Better Party nothing
 * resolves (the same discipline as {@link LootJournalClientIntegration}).
 * FML routes {@link FMLClientSetupEvent} to the mod bus automatically (the
 * annotation's {@code bus} attribute is deprecated), and registration is
 * just a map insert, so no {@code enqueueWork} is needed.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID, value = Dist.CLIENT)
public final class BetterPartySettingsPage {

    private BetterPartySettingsPage() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("better_party")) {
            return;
        }
        BetterPartyClientApi.registerPartyLeaderSettings(
                "quick_loot:party_loot",
                Component.translatable("quick_loot.party_settings.title"),
                PartyLootSettingsScreen::new);
    }
}
