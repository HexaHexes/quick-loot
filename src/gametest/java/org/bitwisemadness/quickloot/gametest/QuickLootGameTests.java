package org.bitwisemadness.quickloot.gametest;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.container.DepositScope;
import org.bitwisemadness.quickloot.container.LootContainerLatches;
import org.bitwisemadness.quickloot.container.LootEvidence;
import org.bitwisemadness.quickloot.container.MenuWhitelist;
import org.bitwisemadness.quickloot.container.QuickDeposit;
import org.bitwisemadness.quickloot.loot.DropEvents;
import org.bitwisemadness.quickloot.net.InsertedPayload;
import org.bitwisemadness.quickloot.party.PartyAmount;
import org.bitwisemadness.quickloot.party.PartyBridge;
import org.bitwisemadness.quickloot.party.PartyBridgeRegistry;
import org.bitwisemadness.quickloot.party.PartyRule;
import org.bitwisemadness.quickloot.party.PartySource;
import org.bitwisemadness.quickloot.party.ResolvedParty;

/**
 * GameTests for the drop pipeline: real block breaks through the event bus,
 * constructed drop/XP events through the GameTest seams, party distribution
 * with a fabricated membership source, the quick-deposit validation chain, the
 * notification payload, and the staged party adapters.
 *
 * <p>Every test runs synchronously inside one method invocation on the server
 * thread, so config flips and gamerule changes are atomic here - each test
 * restores what it changed in a {@code finally} block. The wallet suite lives
 * in {@link LightmansCurrencyGameTests}.</p>
 */
@GameTestHolder(QuickLoot.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuickLootGameTests {

    private QuickLootGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * A real survival break of diamond ore: the drop lands in the killer's
     * inventory, the mining experience is awarded directly, and neither an
     * item entity nor an experience orb is left in the world.
     */
    @GameTest(template = "empty")
    public static void blockBreakAbsorbsDropsAndXp(GameTestHelper helper) {
        ServerPlayer miner = TestSupport.join(helper, "miner");
        miner.setGameMode(GameType.SURVIVAL);
        miner.getInventory().items.set(miner.getInventory().selected, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(BlockPos.ZERO, Blocks.DIAMOND_ORE);

        boolean broken = miner.gameMode.destroyBlock(helper.absolutePos(BlockPos.ZERO));
        TestSupport.check(broken, "the diamond ore must break");
        TestSupport.check(helper.getBlockState(BlockPos.ZERO).is(Blocks.AIR), "the ore block must be gone");
        TestSupport.check(TestSupport.countItem(miner, Items.DIAMOND) == 1,
                "the diamond must be inserted, not dropped in the world");
        TestSupport.check(miner.totalExperience > 0, "mining XP must be awarded directly to the miner");
        TestSupport.check(TestSupport.entitiesNear(helper, ItemEntity.class).isEmpty(),
                "no drop item entity may remain");
        TestSupport.check(TestSupport.entitiesNear(helper, ExperienceOrb.class).isEmpty(),
                "no experience orb may spawn");
        helper.succeed();
    }

    /**
     * With the master switch off the pipeline is inert: the vanilla item drop
     * and experience orb spawn exactly as if the mod were absent.
     */
    @GameTest(template = "empty")
    public static void blockBreakGatesOffSpawnsVanilla(GameTestHelper helper) {
        ServerPlayer miner = TestSupport.join(helper, "vanilla-miner");
        miner.setGameMode(GameType.SURVIVAL);
        miner.getInventory().items.set(miner.getInventory().selected, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(BlockPos.ZERO, Blocks.DIAMOND_ORE);

        var config = QuickLootConfig.SERVER;
        boolean wasEnabled = config.enabled.get();
        try {
            config.enabled.set(false);
            boolean broken = miner.gameMode.destroyBlock(helper.absolutePos(BlockPos.ZERO));
            TestSupport.check(broken, "the diamond ore must still break");
            TestSupport.check(TestSupport.countItem(miner, Items.DIAMOND) == 0,
                    "gates off: nothing may be inserted");
            TestSupport.check(!TestSupport.entitiesNear(helper, ItemEntity.class).isEmpty(),
                    "gates off: the diamond must drop in the world like vanilla");
            TestSupport.check(!TestSupport.entitiesNear(helper, ExperienceOrb.class).isEmpty(),
                    "gates off: vanilla must spawn the experience orb");
            TestSupport.check(miner.totalExperience == 0, "gates off: no direct award");
        } finally {
            config.enabled.set(wasEnabled);
        }
        helper.succeed();
    }

    /**
     * Block experience mirrors the gates {@code popExperience} applies
     * downstream of the event: with {@code doTileDrops} off or during a block
     * snapshot restore nothing may be awarded (and the event amount stays
     * untouched); with both gates on the amount is zeroed and the breaker is
     * awarded directly. The event is never canceled in any phase.
     */
    @GameTest(template = "empty")
    public static void blockXpMirrorsVanillaGates(GameTestHelper helper) {
        ServerPlayer breaker = TestSupport.join(helper, "xp-gates");
        var level = helper.getLevel();
        var server = level.getServer();
        BlockPos abs = helper.absolutePos(BlockPos.ZERO);
        var rules = level.getGameRules();
        boolean wasDrops = rules.getBoolean(GameRules.RULE_DOBLOCKDROPS);
        boolean wasSnapshot = level.restoringBlockSnapshots;
        try {
            rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false, server);
            var gatedByRule = blockEvent(level, abs, breaker, 11);
            DropEvents.handleBlockDrops(gatedByRule);
            TestSupport.check(!gatedByRule.isCanceled(), "never canceled");
            TestSupport.check(gatedByRule.getDroppedExperience() == 11,
                    "doTileDrops off: the amount must stay for vanilla to consume");
            TestSupport.check(breaker.totalExperience == 0, "doTileDrops off: no direct award");
            TestSupport.check(gatedByRule.getDrops().isEmpty(),
                    "the fabricated items still absorb - the vanilla list would be empty here");
            rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(true, server);

            level.restoringBlockSnapshots = true;
            var gatedBySnapshot = blockEvent(level, abs, breaker, 11);
            DropEvents.handleBlockDrops(gatedBySnapshot);
            TestSupport.check(!gatedBySnapshot.isCanceled(), "never canceled");
            TestSupport.check(gatedBySnapshot.getDroppedExperience() == 11,
                    "snapshot restore: the amount must stay untouched");
            TestSupport.check(breaker.totalExperience == 0, "snapshot restore: no direct award");
            level.restoringBlockSnapshots = false;

            var open = blockEvent(level, abs, breaker, 11);
            DropEvents.handleBlockDrops(open);
            TestSupport.check(!open.isCanceled(), "never canceled");
            TestSupport.check(open.getDroppedExperience() == 0,
                    "gates on: zeroed so popExperience becomes a no-op");
            TestSupport.check(breaker.totalExperience == 11, "gates on: awarded directly to the breaker");
            TestSupport.check(open.getDrops().isEmpty(), "the drop must be absorbed");
        } finally {
            rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(wasDrops, server);
            level.restoringBlockSnapshots = wasSnapshot;
        }
        helper.succeed();
    }

    /**
     * The living-drop and living-XP handlers are wired to the real event bus:
     * posting both events for one kill (experience first, as vanilla fires
     * them) awards the XP directly, absorbs the drop, zeroes the orb amount
     * and cancels nothing - with the real (staged) party registry resolving
     * the fresh killer as a solo player.
     */
    @GameTest(template = "empty")
    public static void livingEventsFlowThroughTheBus(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "bus-killer");
        var level = helper.getLevel();
        var victim = helper.makeMockPlayer(GameType.CREATIVE);

        var xpEvent = new LivingExperienceDropEvent(victim, killer, 30);
        NeoForge.EVENT_BUS.post(xpEvent);
        TestSupport.check(!xpEvent.isCanceled(), "the XP event must never be canceled");
        TestSupport.check(xpEvent.getDroppedExperience() == 0, "the orb amount must be zeroed");
        TestSupport.check(killer.totalExperience == 30, "the killer must be awarded the full share directly");

        var drops = new ArrayList<ItemEntity>();
        drops.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND)));
        var dropsEvent = new LivingDropsEvent(victim, level.damageSources().playerAttack(killer), drops, true);
        NeoForge.EVENT_BUS.post(dropsEvent);
        TestSupport.check(!dropsEvent.isCanceled(), "the drops event must never be canceled");
        TestSupport.check(dropsEvent.getDrops().isEmpty(), "the drop must be absorbed");
        TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 1,
                "the diamond must land with the killer");
        helper.succeed();
    }

    /**
     * FREE_FOR_ALL with SPLIT_EQUALLY through the fake-bridge seam: three
     * diamonds split two to the killer (primary, takes the remainder) and one
     * to the member, fully absorbed, never canceled.
     */
    @GameTest(template = "empty")
    public static void partyFreeForAllSplitShares(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "ffa-killer");
        ServerPlayer member = TestSupport.join(helper, "ffa-member");
        var config = QuickLootConfig.SERVER;
        var wasRule = config.partyLootRule.get();
        var wasAmount = config.partyLootAmount.get();
        try {
            config.partyLootRule.set(PartyRule.FREE_FOR_ALL);
            config.partyLootAmount.set(PartyAmount.SPLIT_EQUALLY);

            var level = helper.getLevel();
            var victim = helper.makeMockPlayer(GameType.CREATIVE);
            var drops = new ArrayList<ItemEntity>();
            drops.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND, 3)));
            var event = new LivingDropsEvent(
                    victim, level.damageSources().playerAttack(killer), drops, true);
            DropEvents.handleLivingDrops(event, fixedParty(killer, member));

            TestSupport.check(!event.isCanceled(), "never canceled");
            TestSupport.check(event.getDrops().isEmpty(), "the stack must be fully absorbed");
            TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 2,
                    "the primary (killer) takes the remainder share: 2 of 3");
            TestSupport.check(TestSupport.countItem(member, Items.DIAMOND) == 1,
                    "the member takes an equal share: 1 of 3");
        } finally {
            config.partyLootRule.set(wasRule);
            config.partyLootAmount.set(wasAmount);
        }
        helper.succeed();
    }

    /**
     * Install-with-defaults behaviour with a real two-member party behind the
     * seam: KILLING_BLOW + OWNER_ONLY pays only the killer, and a party whose
     * second member is offline collapses to the same solo outcome instead of
     * failing.
     */
    @GameTest(template = "empty")
    public static void partyDefaultsKeepKillerSolo(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "solo-killer");
        ServerPlayer member = TestSupport.join(helper, "solo-member");
        var config = QuickLootConfig.SERVER;
        TestSupport.check(config.partyLootRule.get() == PartyRule.KILLING_BLOW
                        && config.partyLootAmount.get() == PartyAmount.OWNER_ONLY,
                "the test assumes install-default party rules");

        var level = helper.getLevel();
        var source = level.damageSources();
        var victimOne = helper.makeMockPlayer(GameType.CREATIVE);
        var first = new ArrayList<ItemEntity>();
        first.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND, 5)));
        DropEvents.handleLivingDrops(
                new LivingDropsEvent(victimOne, source.playerAttack(killer), first, true),
                fixedParty(killer, member));
        TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 5,
                "KILLING_BLOW: the killer receives the whole stack");
        TestSupport.check(TestSupport.countItem(member, Items.DIAMOND) == 0,
                "KILLING_BLOW: the member receives nothing");

        var victimTwo = helper.makeMockPlayer(GameType.CREATIVE);
        var second = new ArrayList<ItemEntity>();
        second.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND, 4)));
        var offlineParty = fixedParty(List.of(killer.getUUID(), UUID.randomUUID()));
        var event = new LivingDropsEvent(victimTwo, source.playerAttack(killer), second, true);
        DropEvents.handleLivingDrops(event, offlineParty);
        TestSupport.check(!event.isCanceled(), "never canceled");
        TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 9,
                "an offline member collapses the plan to solo - the killer still receives everything");
        TestSupport.check(TestSupport.countItem(member, Items.DIAMOND) == 0,
                "the online member is unaffected");
        helper.succeed();
    }

    /**
     * ROUND_ROBIN consumes exactly one rotation per kill: the first kill's
     * experience and drops (two events, one victim - the memo must reuse the
     * plan) both go to rotation slot 0, and only the second kill moves the
     * loot to slot 1. OWNER_ONLY keeps each recipient's payout whole.
     */
    @GameTest(template = "empty")
    public static void partyRoundRobinAdvancesOncePerKill(GameTestHelper helper) {
        ServerPlayer killer = TestSupport.join(helper, "rr-killer");
        ServerPlayer member = TestSupport.join(helper, "rr-member");
        var config = QuickLootConfig.SERVER;
        var wasRule = config.partyLootRule.get();
        var wasAmount = config.partyLootAmount.get();
        try {
            config.partyLootRule.set(PartyRule.ROUND_ROBIN);
            config.partyLootAmount.set(PartyAmount.OWNER_ONLY);
            var party = fixedParty(killer, member);
            var level = helper.getLevel();
            var source = level.damageSources();

            var victim = helper.makeMockPlayer(GameType.CREATIVE);
            var xpEvent = new LivingExperienceDropEvent(victim, killer, 10);
            DropEvents.handleLivingXpDrop(xpEvent, party);
            TestSupport.check(!xpEvent.isCanceled(), "the XP event must never be canceled");
            TestSupport.check(xpEvent.getDroppedExperience() == 0, "the orb amount must be zeroed");
            TestSupport.check(killer.totalExperience == 10, "rotation slot 0 is the killer");

            var firstDrops = new ArrayList<ItemEntity>();
            firstDrops.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND, 4)));
            DropEvents.handleLivingDrops(
                    new LivingDropsEvent(victim, source.playerAttack(killer), firstDrops, true), party);
            TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 4,
                    "the same kill's drops reuse the plan resolved by the XP event");
            TestSupport.check(TestSupport.countItem(member, Items.DIAMOND) == 0,
                    "one rotation per kill: the member must not also receive the first kill");

            var victimTwo = helper.makeMockPlayer(GameType.CREATIVE);
            var secondDrops = new ArrayList<ItemEntity>();
            secondDrops.add(itemAt(level, helper.absolutePos(BlockPos.ZERO), new ItemStack(Items.DIAMOND, 7)));
            DropEvents.handleLivingDrops(
                    new LivingDropsEvent(victimTwo, source.playerAttack(killer), secondDrops, true), party);
            TestSupport.check(TestSupport.countItem(member, Items.DIAMOND) == 7,
                    "the second kill advances rotation exactly once - slot 1 is the member");
            TestSupport.check(TestSupport.countItem(killer, Items.DIAMOND) == 4,
                    "the second kill pays the killer nothing more");
        } finally {
            config.partyLootRule.set(wasRule);
            config.partyLootAmount.set(wasAmount);
        }
        helper.succeed();
    }

    /**
     * The quick-deposit validation chain, in gate order: the config toggle, the
     * LOOT_ONLY evidence scope, and the menu whitelist each refuse on their
     * own; once latched, eligible stacks move (merging into a full stack's
     * siblings, refusing novel items, and landing beside a full stack as a
     * fresh one) regardless of the backpack spill setting; ALL_CONTAINERS is
     * the documented escape hatch for an unlatched chest; a live loot table
     * counts as evidence on its own.
     */
    @GameTest(template = "empty")
    public static void depositValidationChain(GameTestHelper helper) {
        ServerPlayer player = TestSupport.join(helper, "depositor");
        var level = helper.getLevel();
        var server = level.getServer();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(BlockPos.ZERO, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(BlockPos.ZERO);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
        chest.setItem(1, new ItemStack(Items.EMERALD, 32));
        chest.setItem(2, new ItemStack(Items.IRON_INGOT, 10));
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 20));
        player.getInventory().items.set(1, new ItemStack(Items.IRON_INGOT, 64));
        var menu = ChestMenu.threeRows(100, player.getInventory(), chest);

        var config = QuickLootConfig.SERVER;
        boolean wasEnabled = config.quickDeposit.get();
        var wasScope = config.quickDepositScope.get();
        boolean wasBackpacks = config.useBackpacks.get();
        try {
            config.quickDeposit.set(false);
            player.containerMenu = menu;
            QuickDeposit.run(player);
            TestSupport.check(chest.getItem(0).getCount() == 10, "quickDeposit off: nothing moves");

            config.quickDeposit.set(true);
            QuickDeposit.run(player);
            TestSupport.check(chest.getItem(0).getCount() == 10,
                    "LOOT_ONLY without evidence: nothing moves");

            player.containerMenu = player.inventoryMenu;
            QuickDeposit.run(player);
            TestSupport.check(chest.getItem(0).getCount() == 10, "a non-whitelisted menu refuses");
            TestSupport.check(!MenuWhitelist.isWhitelisted(player.inventoryMenu),
                    "the inventory menu must be off the whitelist");
            TestSupport.check(MenuWhitelist.isWhitelisted(menu), "the chest menu must be on the whitelist");

            LootContainerLatches.latch(server, level.dimension(), origin);
            config.useBackpacks.set(false);
            player.containerMenu = menu;
            QuickDeposit.run(player);
            TestSupport.check(chest.getItem(0).isEmpty(), "merge: the chest's diamonds must move");
            TestSupport.check(TestSupport.countItem(player, Items.DIAMOND) == 30,
                    "merge: 20 + 10 diamonds in one stack");
            TestSupport.check(chest.getItem(1).getCount() == 32,
                    "novel: emeralds the player carries nowhere must stay");
            TestSupport.check(TestSupport.countItem(player, Items.EMERALD) == 0,
                    "novel: no emeralds may appear");
            TestSupport.check(chest.getItem(2).isEmpty(),
                    "full existing stack still qualifies - the iron must move");
            TestSupport.check(TestSupport.countItem(player, Items.IRON_INGOT) == 74,
                    "64 + 10 iron: a fresh stack beside the full one");
            config.useBackpacks.set(wasBackpacks);

            helper.setBlock(new BlockPos(1, 0, 0), Blocks.CHEST);
            ChestBlockEntity escapeHatch = helper.getBlockEntity(new BlockPos(1, 0, 0));
            escapeHatch.setItem(0, new ItemStack(Items.DIAMOND, 5));
            var escapeMenu = ChestMenu.threeRows(101, player.getInventory(), escapeHatch);
            config.quickDepositScope.set(DepositScope.ALL_CONTAINERS);
            player.containerMenu = escapeMenu;
            QuickDeposit.run(player);
            TestSupport.check(escapeHatch.getItem(0).isEmpty(),
                    "ALL_CONTAINERS: an unlatched chest must deposit");
            TestSupport.check(TestSupport.countItem(player, Items.DIAMOND) == 35, "30 + 5 diamonds");

            config.quickDepositScope.set(DepositScope.LOOT_ONLY);
            helper.setBlock(new BlockPos(2, 0, 0), Blocks.CHEST);
            ChestBlockEntity observed = helper.getBlockEntity(new BlockPos(2, 0, 0));
            TestSupport.check(!LootEvidence.hasEvidence(observed),
                    "an ordinary chest has no evidence under LOOT_ONLY");
            observed.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("quick_loot", "chest")));
            TestSupport.check(LootEvidence.hasEvidence(observed),
                    "a live loot table must count as evidence on its own");
        } finally {
            config.quickDeposit.set(wasEnabled);
            config.quickDepositScope.set(wasScope);
            config.useBackpacks.set(wasBackpacks);
        }
        helper.succeed();
    }

    /** The notification payload round-trips stacks and experience through its stream codec. */
    @GameTest(template = "empty")
    public static void insertedPayloadRoundTrips(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().getServer().registryAccess(),
                ConnectionType.NEOFORGE);
        try {
            var original = new InsertedPayload(
                    List.of(new ItemStack(Items.DIAMOND, 7), new ItemStack(Items.EMERALD)), 42);
            InsertedPayload.STREAM_CODEC.encode(buffer, original);
            var decoded = InsertedPayload.STREAM_CODEC.decode(buffer);
            TestSupport.check(decoded.xp() == 42, "the experience must round-trip");
            TestSupport.check(decoded.stacks().size() == 2, "both stacks must round-trip");
            TestSupport.check(ItemStack.matches(decoded.stacks().get(0), original.stacks().get(0)),
                    "the diamond stack must survive the round-trip");
            TestSupport.check(ItemStack.matches(decoded.stacks().get(1), original.stacks().get(1)),
                    "the emerald stack must survive the round-trip");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    /**
     * Adapter smoke over the staged party mods: AUTO prefers Better Party,
     * each explicit source constructs its own adapter, a player in no party
     * resolves empty through every one of them, and disabling party loot
     * tears the registry down to null.
     */
    @GameTest(template = "empty")
    public static void partyAdaptersConstructAndResolve(GameTestHelper helper) {
        var config = QuickLootConfig.SERVER;
        var server = helper.getLevel().getServer();
        var wasSource = config.partySource.get();
        boolean wasEnabled = config.partyEnabled.get();
        try {
            TestSupport.check(config.partySource.get() == PartySource.AUTO, "the test assumes AUTO default");
            PartyBridge auto = PartyBridgeRegistry.current();
            TestSupport.check(auto != null && "better_party".equals(auto.modId()),
                    "AUTO must prefer Better Party while it is staged");

            config.partySource.set(PartySource.OPAC);
            PartyBridge opac = PartyBridgeRegistry.current();
            TestSupport.check(opac != null && "openpartiesandclaims".equals(opac.modId()),
                    "OPaC must construct when selected");
            TestSupport.check(opac.resolve(server, UUID.randomUUID()).isEmpty(),
                    "a player in no party resolves empty");

            config.partySource.set(PartySource.FTB_TEAMS);
            PartyBridge ftb = PartyBridgeRegistry.current();
            TestSupport.check(ftb != null && "ftbteams".equals(ftb.modId()),
                    "FTB Teams must construct when selected");
            TestSupport.check(ftb.resolve(server, UUID.randomUUID()).isEmpty(),
                    "a player in no party resolves empty");

            config.partySource.set(PartySource.BETTER_PARTY);
            PartyBridge betterParty = PartyBridgeRegistry.current();
            TestSupport.check(betterParty != null && "better_party".equals(betterParty.modId()),
                    "Better Party must construct when selected");
            TestSupport.check(betterParty.resolve(server, UUID.randomUUID()).isEmpty(),
                    "a player in no party resolves empty");

            config.partyEnabled.set(false);
            TestSupport.check(PartyBridgeRegistry.current() == null,
                    "partyEnabled off must disable every source");
        } finally {
            config.partySource.set(wasSource);
            config.partyEnabled.set(wasEnabled);
        }
        helper.succeed();
    }

    /** A fabricated membership source: the given players are one party, everyone else is alone. */
    private static PartyBridge fixedParty(ServerPlayer... members) {
        List<UUID> ids = Arrays.stream(members).map(member -> member.getUUID()).toList();
        return fixedParty(ids);
    }

    /** A fabricated membership source over raw ids, for parties with offline members. */
    private static PartyBridge fixedParty(List<UUID> ids) {
        UUID partyId = UUID.randomUUID();
        return new PartyBridge() {
            @Override
            public String modId() {
                return "game_test_fixed_party";
            }

            @Override
            public Optional<ResolvedParty> resolve(MinecraftServer server, UUID player) {
                return ids.contains(player) ? Optional.of(new ResolvedParty(partyId, ids)) : Optional.empty();
            }
        };
    }

    private static ItemEntity itemAt(ServerLevel level, BlockPos absolutePos, ItemStack stack) {
        Vec3 pos = Vec3.atBottomCenterOf(absolutePos);
        return new ItemEntity(level, pos.x, pos.y, pos.z, stack);
    }

    private static BlockDropsEvent blockEvent(
            ServerLevel level, BlockPos absolutePos, ServerPlayer breaker, int experience) {
        var drops = new ArrayList<ItemEntity>();
        drops.add(itemAt(level, absolutePos, new ItemStack(Items.STONE)));
        var event = new BlockDropsEvent(level, absolutePos, Blocks.STONE.defaultBlockState(),
                null, drops, breaker, ItemStack.EMPTY);
        event.setDroppedExperience(experience);
        return event;
    }
}
