package org.bitwisemadness.quickloot.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.bitwisemadness.quickloot.QuickLoot;
import org.bitwisemadness.quickloot.config.QuickLootConfig;
import org.bitwisemadness.quickloot.net.InsertNotifier;
import org.bitwisemadness.quickloot.party.PartyAmount;
import org.bitwisemadness.quickloot.party.PartyBridge;
import org.bitwisemadness.quickloot.party.PartyBridgeRegistry;
import org.bitwisemadness.quickloot.party.PartyLootRules;
import org.bitwisemadness.quickloot.party.PartyRotation;
import org.bitwisemadness.quickloot.party.PartyRule;
import org.bitwisemadness.quickloot.party.ResolvedParty;

/**
 * Drop events: mutate the live drop collections and amounts, never cancel
 * them.
 *
 * <p>Items are taken by removing absorbed {@link ItemEntity}s (or shrinking a
 * partial one via {@code setItem}); experience is taken by awarding the
 * event's points directly to the recipients and mutating the amount to 0, so
 * {@code ExperienceOrb.award(..., 0)} / {@code popExperience} become no-ops
 * while {@code spawnAfterBreak} and every other vanilla side effect stay
 * intact. Cancellation is never used - for blocks it would suppress
 * {@code spawnAfterBreak} and experience entirely.</p>
 *
 * <p>Experience follows the same rules as the items it accompanies: the same
 * config gates ({@code enabled} + {@code mobDrops}/{@code blockDrops}), the
 * same party recipients and amount matrix, and the same Loot Journal pickup
 * notification (via {@code pickupXp}, since no orb entity exists to trigger
 * one). Block drops and block experience are never party-shared - the breaker
 * keeps them.</p>
 *
 * <p>Within one kill the party distribution is resolved exactly once: in
 * {@code dropAllDeathLoot} the experience event fires immediately before the
 * drops event, and the ender dragon additionally re-fires the experience
 * event on a timer throughout its 200-tick death animation - so whichever
 * handler runs first resolves the plan (advancing round robin at most once)
 * and the rest reuse it via {@link #distributionFor}.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class DropEvents {

    /**
     * The distributions already resolved for kills in flight, keyed weakly by
     * the killed entity. Whichever of the experience/drops handlers runs first
     * resolves the plan (advancing round robin at most once) and records it;
     * every later handler for the same kill reuses it instead of advancing
     * again. Keying by entity rather than a single slot keeps the ender
     * dragon's repeated experience drips on one plan even when another kill
     * interleaves with its long death animation. Entries vanish with the dead
     * entity, so nothing is retained. Server-thread only (events and GameTests
     * both run there); entity keys use identity semantics.
     */
    private static final Map<LivingEntity, ResolvedKill> RESOLVED_KILLS = new WeakHashMap<>();

    private record ResolvedKill(UUID killer, Distribution distribution) {
    }

    private DropEvents() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        handleLivingDrops(event, PartyBridgeRegistry.current());
    }

    /**
     * Kill-loot insertion with optional party distribution. The bridge overload
     * is the seam GameTests use to exercise distribution with a fake party
     * source; production calls it through {@link #onLivingDrops}.
     */
    public static void handleLivingDrops(LivingDropsEvent event, PartyBridge bridge) {
        QuickLootConfig.Server config = QuickLootConfig.SERVER;
        if (!config.enabled.get() || !config.mobDrops.get()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        // Resolved before the emptiness check so every player kill advances
        // round robin exactly once, whether or not this kill had items.
        Distribution distribution = distributionFor(event.getEntity(), killer, level, bridge);

        List<ItemEntity> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }

        PartyAmount amount = config.partyLootAmount.get();
        Map<UUID, List<ItemStack>> notifications = new LinkedHashMap<>();
        for (UUID id : distribution.recipients()) {
            notifications.put(id, new ArrayList<>());
        }

        List<ItemEntity> absorbed = new ArrayList<>();
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            int[] shares = PartyLootRules.distribute(
                    stack.getCount(),
                    distribution.recipients().size(),
                    distribution.primaryIndex(),
                    amount);
            List<ItemStack> leftovers = new ArrayList<>();
            for (int i = 0; i < shares.length; i++) {
                if (shares[i] <= 0) {
                    continue;
                }
                ItemStack share = stack.copy();
                share.setCount(shares[i]);
                ItemStack leftover = DropInserter.insert(
                        distribution.players().get(i),
                        share,
                        notifications.get(distribution.recipients().get(i)));
                if (!leftover.isEmpty()) {
                    leftovers.add(leftover);
                }
            }
            if (leftovers.isEmpty()) {
                absorbed.add(entity);
            } else {
                // First remainder rides the original entity; any others (party
                // shares that did not fit their recipient) spawn at the death spot.
                entity.setItem(leftovers.get(0));
                Vec3 pos = entity.position();
                for (int i = 1; i < leftovers.size(); i++) {
                    event.getDrops().add(new ItemEntity(level, pos.x, pos.y, pos.z, leftovers.get(i)));
                }
            }
        }
        event.getDrops().removeAll(absorbed);

        notifications.forEach((id, stacks) -> {
            ServerPlayer recipient = level.getServer().getPlayerList().getPlayer(id);
            if (recipient != null) {
                InsertNotifier.send(recipient, stacks);
            }
        });
    }

    @SubscribeEvent
    public static void onLivingXpDrop(LivingExperienceDropEvent event) {
        handleLivingXpDrop(event, PartyBridgeRegistry.current());
    }

    /**
     * Kill experience with the same rules as the kill's items: same config
     * gates, same party recipients and amount matrix, awarded directly instead
     * of spawning an orb (the event amount is mutated to 0, never canceled).
     * The bridge overload mirrors {@link #handleLivingDrops} as the GameTest
     * seam.
     */
    public static void handleLivingXpDrop(LivingExperienceDropEvent event, PartyBridge bridge) {
        QuickLootConfig.Server config = QuickLootConfig.SERVER;
        if (!config.enabled.get() || !config.mobDrops.get()) {
            return;
        }
        int xp = event.getDroppedExperience();
        if (xp <= 0) {
            return;
        }
        if (!(event.getAttackingPlayer() instanceof ServerPlayer killer)) {
            return;
        }
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        Distribution distribution = distributionFor(event.getEntity(), killer, level, bridge);

        // Zero first: if anything below were to fail, XP is lost at worst and
        // never duplicated by an orb spawn.
        event.setDroppedExperience(0);
        int[] shares = PartyLootRules.distribute(
                xp,
                distribution.recipients().size(),
                distribution.primaryIndex(),
                config.partyLootAmount.get());
        for (int i = 0; i < shares.length; i++) {
            if (shares[i] <= 0) {
                continue;
            }
            ServerPlayer recipient = distribution.players().get(i);
            XpAwards.award(recipient, shares[i]);
            InsertNotifier.send(recipient, List.of(), shares[i]);
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        handleBlockDrops(event);
    }

    /**
     * Break-loot insertion with a direct experience award to the breaker. No
     * party distribution (items or XP), never canceled - the amount mutation
     * alone turns the vanilla {@code popExperience} into a no-op.
     *
     * <p>The award mirrors the gates {@code popExperience} applies downstream
     * of the event: with {@code doTileDrops} off, or during a block-snapshot
     * restore, vanilla spawns no orb, so nothing may be awarded here either.
     * The item side needs no such check - the captured drop list is already
     * empty under those gates.</p>
     */
    public static void handleBlockDrops(BlockDropsEvent event) {
        QuickLootConfig.Server config = QuickLootConfig.SERVER;
        if (!config.enabled.get() || !config.blockDrops.get()) {
            return;
        }
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        List<ItemStack> notifications = new ArrayList<>();
        List<ItemEntity> drops = new ArrayList<>(event.getDrops());
        List<ItemEntity> absorbed = new ArrayList<>();
        for (ItemEntity entity : drops) {
            ItemStack leftover = DropInserter.insert(player, entity.getItem(), notifications);
            if (leftover.isEmpty()) {
                absorbed.add(entity);
            } else if (leftover.getCount() != entity.getItem().getCount()) {
                entity.setItem(leftover);
            }
        }
        event.getDrops().removeAll(absorbed);

        ServerLevel level = event.getLevel();
        int xp = event.getDroppedExperience();
        int awarded = 0;
        if (xp > 0
                && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
                && !level.restoringBlockSnapshots) {
            event.setDroppedExperience(0);
            XpAwards.award(player, xp);
            awarded = xp;
        }
        InsertNotifier.send(player, notifications, awarded);
    }

    /**
     * The distribution for the kill currently being processed, resolved at
     * most once per killed entity: the first handler to run resolves it
     * (advancing round robin) and records it; every later handler for the same
     * kill reuses the plan instead of advancing again. A miss (a different
     * kill, or the killer named by the two events differs) resolves fresh and
     * overwrites, so a stale entry can never serve the wrong kill.
     *
     * <p>The experience event names its killer as the last attacking player,
     * the drops event as the damage source's entity; those are the same player
     * for any player kill, and a mismatch deliberately falls back to a fresh
     * resolution rather than reusing a plan computed for someone else.</p>
     */
    private static Distribution distributionFor(
            LivingEntity killed, ServerPlayer killer, ServerLevel level, PartyBridge bridge) {
        UUID killerId = killer.getUUID();
        ResolvedKill existing = RESOLVED_KILLS.get(killed);
        if (existing != null && existing.killer().equals(killerId)) {
            return existing.distribution();
        }
        Distribution fresh = resolveDistribution(killer, level, bridge);
        RESOLVED_KILLS.put(killed, new ResolvedKill(killerId, fresh));
        return fresh;
    }

    private static Distribution resolveDistribution(
            ServerPlayer killer, ServerLevel level, PartyBridge bridge) {
        QuickLootConfig.Server config = QuickLootConfig.SERVER;
        if (bridge != null && config.partyEnabled.get()) {
            Optional<ResolvedParty> party = bridge.resolve(level.getServer(), killer.getUUID());
            if (party.isPresent() && party.get().members().contains(killer.getUUID())) {
                List<UUID> eligible = eligibleMembers(level, party.get().members());
                if (eligible.size() > 1) {
                    PartyRule rule = config.partyLootRule.get();
                    // Round robin is the only rule that consumes rotation state.
                    long counter = rule == PartyRule.ROUND_ROBIN
                            ? PartyRotation.next(level.getServer(), party.get().id())
                            : 0L;
                    List<UUID> recipients = PartyLootRules.recipients(rule, eligible, killer.getUUID(), counter);
                    List<ServerPlayer> players = new ArrayList<>(recipients.size());
                    boolean allReachable = true;
                    for (UUID id : recipients) {
                        ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
                        if (member == null) {
                            allReachable = false;
                            break;
                        }
                        players.add(member);
                    }
                    if (allReachable && !players.isEmpty()) {
                        return new Distribution(
                                recipients, players, PartyLootRules.primaryIndex(recipients, killer.getUUID()));
                    }
                }
            }
        }
        return new Distribution(List.of(killer.getUUID()), List.of(killer), 0);
    }

    /** Online members in the killed mob's dimension, in stable party order. */
    private static List<UUID> eligibleMembers(ServerLevel level, List<UUID> members) {
        List<UUID> eligible = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
            if (member != null && member.level().dimension() == level.dimension()) {
                eligible.add(id);
            }
        }
        return eligible;
    }

    /**
     * One kill's distribution plan: ordered recipients, their resolved players
     * (parallel list), and the index of the primary recipient for
     * OWNER_ONLY/remainder rules. Falls back to a solo plan for non-party kills.
     */
    public record Distribution(List<UUID> recipients, List<ServerPlayer> players, int primaryIndex) {
    }
}
