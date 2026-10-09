package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.net.RpgNet;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.service.PlayerCache;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.service.StatOverrides;
import com.rpgleveling.stat.AttributeApplier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps the modifiers in step with the player's life cycle.
 *
 * <p>The attachment already survives death and dimension changes via
 * {@code copyOnDeath()}; this class makes sure the (transient, in-memory) attribute
 * modifiers are rebuilt afterwards, and adds a belt-and-braces copy on
 * {@code PlayerEvent.Clone}.</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class PlayerLifecycleEvents {

    private PlayerLifecycleEvents() {}

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // One tick later the attachment is guaranteed to be deserialized (V5).
        player.server.execute(() -> {
            if (player.isRemoved()) return;
            AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
            RpgNet.syncTo(player);
        });
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getOriginal() instanceof ServerPlayer original)) return;
        PlayerRpgData from = original.getData(RpgAttachments.PLAYER_DATA);
        PlayerRpgData to = player.getData(RpgAttachments.PLAYER_DATA);
        if (to.totalXp() == 0L && from.totalXp() > 0L) {
            // copyOnDeath() should already have done this; the fallback costs nothing
            // and removes any dependence on the ordering of the two mechanisms (V3).
            to.setTotalXpExact(from.totalXp());
            for (var line : com.rpgleveling.api.StatLine.VALUES) {
                to.setPointsExact(line, from.points(line));
            }
        }
        player.server.execute(() -> {
            AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
            RpgNet.syncTo(player);
        });
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.server.execute(() -> {
            PlayerCache.of(player.getUUID()).lastAppliedMaxHealth = 0.0;   // "unknown": never gift health
            AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
            RpgNet.syncTo(player);
        });
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.server.execute(() -> {
            AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
            RpgNet.syncTo(player);
        });
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerCache.clear(player.getUUID());
        StatOverrides.clearAll(player.getUUID());   // debug state never survives a session
    }

    /** Safety net against a stale client after a server-side reload. */
    @SubscribeEvent
    public static void onTickForSync(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int interval = RpgConfig.reapplyIntervalTicks();
        if (interval > 0 && player.tickCount % interval == 0) {
            AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Anything an operator changed in the file while the game was down.
        RpgConfig.refresh();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            RpgService.syncOnly(player);
        }
    }
}
