package org.bitwisemadness.quickloot.gametest;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.bitwisemadness.quickloot.QuickLoot;

/**
 * GameTestServer environment fixes. This class lives only in the gametest
 * source set (the jar packages main alone) but registers like any other
 * subscriber, because the dev runs join both source sets into the mod.
 *
 * <p>Vanilla's {@code GameTestServer} boots with {@code NO_SERVICES} - a
 * {@link Services} whose profile cache is {@code null} by design, since a
 * headless test run never resolves game profiles. Open Parties and Claims
 * dereferences {@code server.getProfileCache()} unconditionally while
 * building its claim data on {@code ServerAboutToStart}, which crashed the
 * server before a single test could run. This handler runs at
 * {@link EventPriority#HIGHEST} - ahead of OPaC's default-priority listener -
 * and swaps in a real, empty profile cache so staged mods see the server API
 * they expect. {@code GameProfileCache.get(UUID)} is a pure map lookup (miss
 * yields an empty {@code Optional}), so the still-null profile repository
 * underneath is never touched.</p>
 */
@EventBusSubscriber(modid = QuickLoot.MOD_ID)
public final class GameTestServerFixes {

    private GameTestServerFixes() {
    }

    /** Gives a profile-cache-less GameTestServer the cache staged mods assume. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server.getProfileCache() != null) {
            return;
        }
        try {
            Field services = MinecraftServer.class.getDeclaredField("services");
            services.setAccessible(true);
            Services current = (Services) services.get(server);
            services.set(server, new Services(
                    current.sessionService(),
                    current.servicesKeySet(),
                    current.profileRepository(),
                    new GameProfileCache(current.profileRepository(), new File("usercache.json"))));
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "GameTest profile-cache setup failed - staged mods assume a real server",
                    reflectionFailure);
        }
    }
}
