package org.bitwisemadness.quickloot.container;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Positive loot-evidence latch: container positions the server has seen a
 * loot table on, persisted per-dimension so the flag survives relogs and
 * chunk reloads.
 *
 * <p>Why a latch at all: {@code RandomizableContainer#getLootTable()} returns
 * null the moment the container is first unpacked, so a button that
 * re-checked live loot tables would flick off after the first open. Recording
 * the first sighting keeps the button stable across reopens (issue #1
 * timing gotcha).</p>
 */
public final class LootContainerLatches extends SavedData {

    private static final String DATA_NAME = "quick_loot_latches";

    private final Set<Long> latched = new HashSet<>();

    /** Records (or re-confirms) loot evidence at a position and marks the data dirty. */
    public static void latch(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        LootContainerLatches data = forLevel(server, dimension);
        if (data.latched.add(pos.asLong())) {
            data.setDirty();
        }
    }

    /** Whether this position ever showed loot evidence in its dimension. */
    public static boolean hasLatched(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        return forLevel(server, dimension).latched.contains(pos.asLong());
    }

    private static LootContainerLatches forLevel(MinecraftServer server, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return server.getLevel(dimension).getDataStorage()
                .computeIfAbsent(new Factory<>(LootContainerLatches::new, LootContainerLatches::load), DATA_NAME);
    }

    private static LootContainerLatches load(CompoundTag tag, HolderLookup.Provider registries) {
        LootContainerLatches data = new LootContainerLatches();
        ListTag list = tag.getList("positions", Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            data.latched.add(((LongTag) list.get(i)).getAsLong());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        latched.forEach(value -> list.add(LongTag.valueOf(value)));
        tag.put("positions", list);
        return tag;
    }
}
