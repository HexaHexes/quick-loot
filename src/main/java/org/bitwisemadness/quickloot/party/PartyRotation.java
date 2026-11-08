package org.bitwisemadness.quickloot.party;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-party round-robin counters, persisted in the overworld's data storage so
 * the rotation survives restarts and stays independent of the dimension the
 * kills happen in.
 */
public final class PartyRotation extends SavedData {

    private static final String DATA_NAME = "quick_loot_rotation";

    private final Map<String, Long> counters = new HashMap<>();

    /**
     * The party's current rotation counter, advanced by one and persisted.
     *
     * @return the counter value to use for this kill (0 for a party's first kill)
     */
    public static long next(MinecraftServer server, UUID partyId) {
        PartyRotation data = server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(PartyRotation::new, PartyRotation::load), DATA_NAME);
        String key = partyId.toString();
        long current = data.counters.getOrDefault(key, 0L);
        data.counters.put(key, current + 1L);
        data.setDirty();
        return current;
    }

    private static PartyRotation load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyRotation data = new PartyRotation();
        for (String key : tag.getAllKeys()) {
            data.counters.put(key, tag.getLong(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        counters.forEach(tag::putLong);
        return tag;
    }
}
