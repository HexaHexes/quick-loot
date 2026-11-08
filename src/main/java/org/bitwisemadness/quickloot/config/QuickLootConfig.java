package org.bitwisemadness.quickloot.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.bitwisemadness.quickloot.container.DepositMenuKind;
import org.bitwisemadness.quickloot.container.DepositScope;
import org.bitwisemadness.quickloot.party.PartyAmount;
import org.bitwisemadness.quickloot.party.PartyRule;
import org.bitwisemadness.quickloot.party.PartySource;

/**
 * Server and client config specs.
 *
 * <p>Every default keeps install-with-defaults behaviour equivalent to vanilla
 * plus straight insertion: party sharing inert (killing-blow + owner-only) and
 * the deposit gated to proven loot containers.</p>
 */
public final class QuickLootConfig {

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    private QuickLootConfig() {
    }

    /** SERVER: gameplay-authoritative values; clients cannot influence them. */
    public static final class Server {

        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.BooleanValue mobDrops;
        public final ModConfigSpec.BooleanValue blockDrops;
        public final ModConfigSpec.BooleanValue mobExperience;
        public final ModConfigSpec.BooleanValue blockExperience;
        public final ModConfigSpec.BooleanValue lightmansCoins;
        public final ModConfigSpec.BooleanValue useBackpacks;
        public final ModConfigSpec.BooleanValue coinsToWallet;
        public final ModConfigSpec.BooleanValue lootJournalNotify;
        public final ModConfigSpec.BooleanValue quickDeposit;
        public final ModConfigSpec.EnumValue<DepositScope> quickDepositScope;
        public final ModConfigSpec.BooleanValue requireCarried;
        public final ModConfigSpec.ConfigValue<List<? extends String>> quickDepositMenus;
        public final ModConfigSpec.BooleanValue partyEnabled;
        public final ModConfigSpec.EnumValue<PartySource> partySource;
        public final ModConfigSpec.EnumValue<PartyRule> partyLootRule;
        public final ModConfigSpec.EnumValue<PartyAmount> partyLootAmount;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("drops");
            enabled = builder.comment(
                    "Master switch for all Quick: Loot! behaviour.")
                    .define("enabled", true);
            mobDrops = builder.comment(
                    "Insert mob death drops into the killing player's inventory instead of spawning them.")
                    .define("mobDrops", true);
            blockDrops = builder.comment(
                    "Insert block break drops into the breaking player's inventory instead of spawning them.")
                    .define("blockDrops", true);
            mobExperience = builder.comment(
                    "Award mob kill experience directly to the recipients instead of spawning orbs. Independent of mobDrops: either key can be on without the other.")
                    .define("mobExperience", true);
            blockExperience = builder.comment(
                    "Award block break experience directly to the breaker instead of spawning orbs. Independent of blockDrops: either key can be on without the other.")
                    .define("blockExperience", true);
            lightmansCoins = builder.comment(
                    "Collect Lightman's Currency coins spawned at a mob's death into the insert pipeline (requires Lightman's Currency; also needs drops.enabled and drops.mobDrops). With this off the coins stay on the ground like any other drop.")
                    .define("lightmansCoins", true);
            useBackpacks = builder.comment(
                    "Run inserted loot through the pickup-response chain of carried Sophisticated Backpacks before the inventory: a Pickup, Advanced Pickup, Magnet or Advanced Magnet upgrade decides what its backpack claims (its filter and settings apply), a backpack without one of those upgrades receives nothing, and with this off backpacks are not consulted at all.")
                    .define("useBackpacks", true);
            coinsToWallet = builder.comment(
                    "Route Lightman's Currency coin stacks into the player's equipped wallet before inventory insertion (requires Lightman's Currency; inventory and backpacks remain the fallback when no wallet is equipped or the wallet cannot take the coins).")
                    .define("coinsToWallet", true);
            lootJournalNotify = builder.comment(
                    "Send inserted stacks and directly-awarded experience to the client so Loot Journal shows pickup notifications (requires Loot Journal on the client).")
                    .define("lootJournalNotify", true);
            builder.pop();

            builder.push("quickDeposit");
            quickDeposit = builder.comment(
                    "Allow the quick-insert button to deposit loot container contents (server-side validation of every click).")
                    .define("enabled", true);
            quickDepositScope = builder.comment(
                    "LOOT_ONLY: only containers the server has seen a loot table on (latched across reopens). ALL_CONTAINERS: any whitelisted block container, player storage included.")
                    .defineEnum("scope", DepositScope.LOOT_ONLY);
            requireCarried = builder.comment(
                    "Only move a container stack when the same item already exists in the player's 36 main inventory slots or a carried backpack (the gate against depositing items the player has never held). Off allows depositing any stack in the container.")
                    .define("requireCarried", true);
            quickDepositMenus = builder.comment(
                    "Which container screens accept the quick-insert button and its clicks: chest (chests, trapped chests, barrels), shulker, hopper, dispenser. Unknown names are rejected when the config loads; an empty list disables the button everywhere.")
                    .defineListAllowEmpty(
                            "menus", DepositMenuKind.defaultNames(), DepositMenuKind::isValidName);
            builder.pop();

            builder.push("party");
            partyEnabled = builder.comment(
                    "Distribute mob loot among party members. With lootRule = KILLING_BLOW the behaviour is identical to solo play.")
                    .define("enabled", true);
            partySource = builder.comment(
                    "Which party mod supplies membership. AUTO tries Better Party, then Open Parties and Claims, then FTB Teams.")
                    .defineEnum("source", PartySource.AUTO);
            partyLootRule = builder.comment(
                    "Who receives loot: KILLING_BLOW = the killer only; ROUND_ROBIN = one rotating member per kill; FREE_FOR_ALL = every eligible member.")
                    .defineEnum("lootRule", PartyRule.KILLING_BLOW);
            partyLootAmount = builder.comment(
                    "How much each recipient gets: OWNER_ONLY = the primary recipient only; FULL_TO_ALL = a full copy per recipient (duplicates by design); SPLIT_EQUALLY = divide stacks, remainders to the primary first.")
                    .defineEnum("lootAmount", PartyAmount.OWNER_ONLY);
            builder.pop();
        }
    }

    /** CLIENT: display-only values; the server never reads them. */
    public static final class Client {

        public final ModConfigSpec.BooleanValue showQuickDepositButton;

        private Client(ModConfigSpec.Builder builder) {
            showQuickDepositButton = builder.comment(
                    "Show the quick-insert button on loot container screens.")
                    .define("showQuickDepositButton", true);
        }
    }
}
