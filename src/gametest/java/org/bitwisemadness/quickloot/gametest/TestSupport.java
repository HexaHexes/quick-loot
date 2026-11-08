package org.bitwisemadness.quickloot.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.net.InsertedPayload;

/**
 * Shared GameTest helpers: the real-join mock player plus the small
 * assertion and inventory utilities every suite uses.
 *
 * <p>The join mirrors the ability-iss pattern: a raw {@link ServerPlayer} is
 * unusable here because party-recipient resolution reads the {@code PlayerList}
 * and notification payloads need a connection, so the player is placed through
 * the real {@code PlayerList.placeNewPlayer}. NeoForge never runs the channel
 * handshake for the embedded connection, so every custom payload a staged mod
 * may push during login - and our own notification payloads during the tests -
 * must be listed in the ad-hoc channel set or the send throws.</p>
 */
final class TestSupport {

    /**
     * Ad-hoc channels for the mock connection. Entries that are never sent are
     * inert; a missing one surfaces as a join failure whose message names the
     * channel, so the run log completes this list.
     */
    private static final String[] AD_HOC_CHANNELS = {
            // ours: notification payloads sent by the code under test
            "quick_loot:inserted",
            "quick_loot:container_flag",
            "quick_loot:quick_deposit",
            // neoforge login syncs
            "neoforge:advanced_open_screen",
            "neoforge:sync_attachments",
            // lightmanscurrency: coin data and wallet/config syncs
            "lightmanscurrency:send_coin_data",
            "lightmanscurrency:s_config_sync",
            "lightmanscurrency:s_config_reload",
            "lightmanscurrency:s_config_view",
            "lightmanscurrency:s_config_edit",
            "lightmanscurrency:s_config_edit_list",
            "lightmanscurrency:s_config_edit_map",
            "lightmanscurrency:s_config_reset",
            "lightmanscurrency:s_sync_master_coin_list",
            "lightmanscurrency:s_sync_time",
            "lightmanscurrency:s_sync_admin_list",
            "lightmanscurrency:s_sync_custom_data",
            "lightmanscurrency:s_sync_event_unlocks",
            "lightmanscurrency:s_wallet_sync",
            "lightmanscurrency:s_play_pickup_sound",
            "lightmanscurrency:s_notification_chat",
            "lightmanscurrency:s_player_trade_sync",
            "lightmanscurrency:s_playername_update",
            "lightmanscurrency:s_trader_sync_users",
            "lightmanscurrency:s_trader_tax_warning",
            "lightmanscurrency:s_auction_stand_sync",
            "lightmanscurrency:s_auction_start_bid",
            "lightmanscurrency:s_atm_select_account_player_reply",
            "lightmanscurrency:s_bank_transfer_reply",
            "lightmanscurrency:s_debug_trader_data",
            "lightmanscurrency:s_lazy_menu",
            // sophisticatedcore: datpack settings and container syncs
            "sophisticatedcore:sync_player_settings",
            "sophisticatedcore:sync_datapack_settings_template",
            "sophisticatedcore:sync_container_client_data",
            "sophisticatedcore:sync_container_stacks",
            "sophisticatedcore:sync_slot_stack",
            "sophisticatedcore:sync_recent_crafted_results",
            "sophisticatedcore:sync_additional_slot_info",
            "sophisticatedcore:sync_empty_slot_icons",
            "sophisticatedcore:sync_slot_change_error",
            "sophisticatedcore:sync_block_highlights",
            "sophisticatedcore:sync_template_settings",
            "sophisticatedcore:sync_linker_crafting_diagnostics",
            "sophisticatedcore:transfer_full_slot",
            "sophisticatedcore:set_ghost_slot",
            "sophisticatedcore:set_memory_slot",
            "sophisticatedcore:tank_click",
            "sophisticatedcore:play_disc",
            "sophisticatedcore:stop_disc_playback",
            "sophisticatedcore:linked_storage_settings",
            "sophisticatedcore:request_linked_storage_contents",
            "sophisticatedcore:mounted_storage_contents",
            "sophisticatedcore:mounted_storage_update",
            "sophisticatedcore:jei_transfer_recipe",
            // sophisticatedbackpacks
            "sophisticatedbackpacks:sync_client_info",
            "sophisticatedbackpacks:backpack_contents",
            "sophisticatedbackpacks:backpack_open",
            "sophisticatedbackpacks:backpack_close",
            "sophisticatedbackpacks:request_backpack_inventory_contents",
            "sophisticatedbackpacks:upgrade_toggle",
            "sophisticatedbackpacks:request_player_settings",
            "sophisticatedbackpacks:inventory_interaction",
            "sophisticatedbackpacks:block_pick",
            "sophisticatedbackpacks:entity_tool_swap",
            "sophisticatedbackpacks:block_tool_swap",
            "sophisticatedbackpacks:another_player_backpack_open",
            "sophisticatedbackpacks:mob_catcher_release",
            "sophisticatedbackpacks:mob_catcher_capture_effect",
            // better_party (versioned payload ids, extracted from its payload classes)
            "better_party:party_snapshot_v4",
            "better_party:party_action_v6",
            "better_party:member_data_v1",
            "better_party:settings_result_v1",
            // ftbteams
            "ftbteams:team",
            "ftbteams:sync_teams",
            "ftbteams:sync_msg_history",
            "ftbteams:player_gui_operation",
            "ftbteams:send_message",
            "ftbteams:send_message_response",
            "ftbteams:update_presence",
            "ftbteams:update_properties_response",
            "ftbteams:toggle_chat_redirection",
            "ftbteams:toggle_chat_response",
            "ftbteams:open_gui",
            "ftbteams:open_my_team_gui",
            // open parties and claims (one main channel)
            "openpartiesandclaims:main",
    };

    private static final AtomicInteger PLAYER_IDS = new AtomicInteger();

    private TestSupport() {
    }

    /**
     * A real, connected ServerPlayer: constructed raw, then joined through the
     * real {@code placeNewPlayer} so the connection (payload sends) and the
     * PlayerList entry (party lookups) both exist. A mod login payload that
     * rejects the ad-hoc channel aborts the join after placement, in which case
     * the already-placed player is recovered; a failure before placement
     * surfaces with the offending message so the channel list above can be
     * extended from the run log.
     */
    static ServerPlayer join(GameTestHelper helper, String name) {
        var server = helper.getLevel().getServer();
        // Mock profile names feed real payload codecs (FTB team names are
        // username-limited to 16), so the generated name must stay within it.
        String unique = name + PLAYER_IDS.incrementAndGet();
        if (unique.length() > 16) {
            unique = unique.substring(0, 16);
        }
        var profile = new GameProfile(UUID.randomUUID(), unique);
        var player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());

        var embedded = new EmbeddedChannel();
        embedded.pipeline().addFirst(new ChannelInboundHandlerAdapter());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        try {
            connection.channelActive(embedded.pipeline().firstContext());
        } catch (Exception ignored) {
            // Best effort - the placement fallback below covers a partial join.
        }
        var channels = ChannelAttributes.getOrCreateAdHocChannels(connection);
        for (var id : AD_HOC_CHANNELS) {
            int split = id.indexOf(':');
            channels.add(ResourceLocation.fromNamespaceAndPath(id.substring(0, split), id.substring(split + 1)));
        }

        try {
            server.getPlayerList().placeNewPlayer(connection, player,
                    CommonListenerCookie.createInitial(profile, false));
        } catch (RuntimeException joinNoise) {
            QuickLoot.LOGGER.warn("GameTest join aborted ({}), recovering placed player", joinNoise.getMessage());
            for (var placed : server.getPlayerList().getPlayers()) {
                if (placed.getUUID().equals(profile.getId())) {
                    return placed;
                }
            }
            throw new GameTestAssertException("placeNewPlayer failed before placement: " + joinNoise);
        }
        return player;
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Total count of {@code item} across the 36 main inventory slots. */
    static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Every {@code quick_loot:inserted} payload the mock connection has queued
     * since the last call, draining the outbound queue on the way. Lets a test
     * assert on the exact toast content a code path sent, not just that a send
     * did not throw.
     */
    static List<InsertedPayload> drainInserted(ServerPlayer player) {
        var channel = (EmbeddedChannel) player.connection.getConnection().channel();
        List<InsertedPayload> found = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof InsertedPayload inserted) {
                found.add(inserted);
            }
        }
        return found;
    }

    /** Entities of one type near the structure origin - for "no orb spawned" style assertions. */
    static <T extends Entity> List<T> entitiesNear(GameTestHelper helper, Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, around(helper, BlockPos.ZERO, 4.0));
    }

    /** A structure-relative position converted to world space, inflated. */
    static AABB around(GameTestHelper helper, BlockPos relativePos, double radius) {
        return new AABB(helper.absolutePos(relativePos)).inflate(radius);
    }
}
