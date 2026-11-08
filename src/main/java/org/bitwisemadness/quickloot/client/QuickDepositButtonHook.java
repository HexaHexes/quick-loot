package org.bitwisemadness.quickloot.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.container.ContainerFlagState;
import org.bitwisemadness.quickloot.container.MenuWhitelist;
import org.bitwisemadness.quickloot.net.QuickDepositPayload;

/**
 * Adds the quick-insert button to loot container screens (client only).
 *
 * <p>The gate has three cosmetic halves, all re-checked by the server anyway:
 * client config toggle, menu whitelist (same {@link MenuWhitelist} the server
 * validates against), and the server-sent loot-evidence flag matched against
 * the open screen's container block position. Default is hidden - a
 * modded client on an unmodded or {@code LOOT_ONLY} server never shows a dead
 * button.</p>
 *
 * <p>Placement: a small Lightman's-style column to the right of the container,
 * clamped inside the screen at any GUI scale.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID, value = Dist.CLIENT)
public final class QuickDepositButtonHook {

    private QuickDepositButtonHook() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!QuickLootConfig.CLIENT.showQuickDepositButton.get()) {
            return;
        }
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        Container container = MenuWhitelist.containerOf(containerScreen.getMenu());
        if (container == null) {
            return;
        }
        if (!(container instanceof BlockEntity blockEntity)
                || !ContainerFlagState.isVisible(blockEntity.getBlockPos())) {
            return;
        }

        int width = 60;
        int height = 20;
        int x = Math.min(
                containerScreen.getGuiLeft() + containerScreen.getXSize() + 4,
                containerScreen.width - width - 4);
        x = Math.max(x, 4);
        int y = containerScreen.getGuiTop() + 4;

        event.addListener(Button.builder(
                        Component.translatable("quick_loot.button.quick_insert"),
                        button -> PacketDistributor.sendToServer(new QuickDepositPayload()))
                .bounds(x, y, width, height)
                .tooltip(Tooltip.create(Component.translatable("quick_loot.tooltip.quick_insert")))
                .build());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Never carry evidence flags across sessions/worlds.
        ContainerFlagState.clear();
    }
}
