package org.bitwisemadness.quickloot.config;

import net.neoforged.neoforge.common.ModConfigSpec;
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
        public final ModConfigSpec.BooleanValue useBackpacks;
        public final ModConfigSpec.BooleanValue coinsToWallet;
        public final ModConfigSpec.BooleanValue lootJournalNotify;
        public final ModConfigSpec.BooleanValue quickDeposit;
        public final ModConfigSpec.EnumValue<DepositScope> quickDepositScope;
        public final ModConfigSpec.BooleanValue partyEnabled;
        public final ModConfigSpec.EnumValue<PartySource> partySource;
        public final ModConfigSpec.EnumValue<PartyRule> partyLootRule;
        public final ModConfigSpec.EnumValue<PartyAmount> partyLootAmount;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("drops");
            enabled = builder.comment(
                    "Master switch for all Quick Loot behaviour.")
                    .define("enabled", true);
            mobDrops = builder.comment(
                    "Insert mob death drops into the killing player's inventory instead of spawning them. Mob experience follows the same rules: it is awarded directly to the recipients instead of spawning orbs.")
                    .define("mobDrops", true);
            blockDrops = builder.comment(
                    "Insert block break drops into the breaking player's inventory. Block experience is awarded directly to the breaker instead of spawning orbs.")
                    .define("blockDrops", true);
            useBackpacks = builder.comment(
                    "Spill overflow into carried Sophisticated Backpacks through their filtered IO handler, so filter, void, slot-blocker and overflow upgrades all apply.")
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
