package org.bitwisemadness.quickloot.client;

import dev.betterparty.api.v1.client.BetterPartyClientApi;
import dev.betterparty.api.v1.settings.AddonSettingDefinition;
import dev.betterparty.api.v1.settings.BetterPartyAddonSettings;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.party.PartyAmount;
import org.bitwisemadness.quickloot.party.PartyRule;
import org.bitwisemadness.quickloot.party.PartySettingOverrides;

/**
 * Better Party's party-leader settings page for Quick: Loot! (issue #21):
 * three cycle buttons - the switch, the distribution rule and the amount
 * matrix - showing the *effective* value (party override, else the server
 * config) and writing the override back through Better Party's own
 * {@code requestPartySetting}. The server config is the master switch, so
 * the switch button is disabled while {@code party.enabled} is off for the
 * whole server: a party can opt out, never in.
 *
 * <p>Opened in-world only, i.e. long after config load; the
 * {@code isLoaded} gate still keeps a pathological early open on install
 * defaults instead of throwing (issue #17 rule).</p>
 */
public final class PartyLootSettingsScreen extends Screen {

    private static final PartyRule[] RULES = PartyRule.values();
    private static final PartyAmount[] AMOUNTS = PartyAmount.values();

    private final Screen parent;
    private boolean enabled;
    private PartyRule rule;
    private PartyAmount amount;
    private Button enabledButton;
    private Button ruleButton;
    private Button amountButton;

    public PartyLootSettingsScreen(Screen parent) {
        super(Component.translatable("quick_loot.party_settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Map<String, String> overrides = BetterPartyClientApi.partySettingOverrides();
        boolean configLoaded = QuickLootConfig.SERVER_SPEC.isLoaded();
        boolean master = configLoaded && QuickLootConfig.SERVER.partyEnabled.get();
        enabled = master && PartySettingOverrides.enabled(overrides, true);
        rule = configLoaded
                ? PartySettingOverrides.lootRule(overrides, QuickLootConfig.SERVER.partyLootRule.get())
                : PartyRule.KILLING_BLOW;
        amount = configLoaded
                ? PartySettingOverrides.lootAmount(overrides, QuickLootConfig.SERVER.partyLootAmount.get())
                : PartyAmount.OWNER_ONLY;

        int x = this.width / 2 - 100;
        int y = this.height / 4 + 24;
        enabledButton = this.addRenderableWidget(Button.builder(enabledLabel(), button -> cycleEnabled())
                .bounds(x, y, 200, 20)
                .build());
        ruleButton = this.addRenderableWidget(Button.builder(ruleLabel(), button -> cycleRule())
                .bounds(x, y + 24, 200, 20)
                .build());
        amountButton = this.addRenderableWidget(Button.builder(amountLabel(), button -> cycleAmount())
                .bounds(x, y + 48, 200, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(x, y + 80, 200, 20)
                .build());
        enabledButton.active = master;
    }

    private void cycleEnabled() {
        enabled = !enabled;
        request(PartySettingOverrides.KEY_ENABLED, enabled);
        enabledButton.setMessage(enabledLabel());
    }

    private void cycleRule() {
        rule = RULES[(rule.ordinal() + 1) % RULES.length];
        request(PartySettingOverrides.KEY_LOOT_RULE, rule.name());
        ruleButton.setMessage(ruleLabel());
    }

    private void cycleAmount() {
        amount = AMOUNTS[(amount.ordinal() + 1) % AMOUNTS.length];
        request(PartySettingOverrides.KEY_LOOT_AMOUNT, amount.name());
        amountButton.setMessage(amountLabel());
    }

    /**
     * Better Party validates writes against its definition registry, so the
     * registered definition is the handle for the request (issue #21). The
     * registry fills at mod construction on both physical sides, before any
     * screen can open.
     */
    @SuppressWarnings("unchecked")
    private static <T> void request(String key, T value) {
        AddonSettingDefinition<?> definition = BetterPartyAddonSettings.definition(key)
                .orElseThrow(() -> new IllegalStateException("Quick: Loot! settings are not registered"));
        BetterPartyClientApi.requestPartySetting((AddonSettingDefinition<T>) definition, value);
    }

    private Component enabledLabel() {
        return Component.translatable("quick_loot.party_settings.enabled").append(": ")
                .append(Component.translatable(enabled
                        ? "quick_loot.party_settings.on"
                        : "quick_loot.party_settings.off"));
    }

    private Component ruleLabel() {
        return Component.translatable("quick_loot.party_settings.rule").append(": ")
                .append(Component.translatable("quick_loot.rule." + rule.name()));
    }

    private Component amountLabel() {
        return Component.translatable("quick_loot.party_settings.amount").append(": ")
                .append(Component.translatable("quick_loot.amount." + amount.name()));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
