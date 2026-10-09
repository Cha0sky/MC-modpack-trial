package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.net.RpgNet;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.stat.AttributeApplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Rebuilds the runtime snapshot and re-applies every online player after a config
 * load or reload - otherwise a changed {@code cap} would only take effect on relogin.
 *
 * <p>Registered explicitly through {@link #init(net.neoforged.bus.api.IEventBus)}, so the
 * deprecated-for-removal {@code EventBusSubscriber.bus()} attribute is not needed.</p>
 */
public final class ConfigEvents {

    private ConfigEvents() {}

    /**
     * Explicit registration from the {@code @Mod} constructor. Kept inside this class so
     * the subscriber and its registration site are impossible to separate during review.
     */
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(ConfigEvents::onLoading);
        modBus.addListener(ConfigEvents::onReloading);
    }

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        if (!event.getConfig().getModId().equals("rpgleveling")) return;
        RpgConfig.refresh();
        ConfigEvents.applyToOnline();
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals("rpgleveling")) return;
        RpgConfig.refresh();
        CombatEvents.invalidateBypassCache();
        ConfigEvents.applyToOnline();
    }

    private static void applyToOnline() {
        // ModConfigEvent.Reloading also fires during early mod loading, when there is no
        // server yet. Caught by the dedicated-server smoke test (S-08b): without this guard
        // the whole mod fails to load with an NPE.
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.getPlayerList().getPlayers().forEach(ConfigEvents::applyOne);
    }

    private static void applyOne(ServerPlayer player) {
        AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
        RpgNet.syncTo(player);
    }
}