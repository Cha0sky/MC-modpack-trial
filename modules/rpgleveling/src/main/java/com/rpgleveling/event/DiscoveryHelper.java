package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.data.DiscoveryData;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.service.XpService;
import com.rpgleveling.service.XpSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * First-time discovery bookkeeping. Split out of {@code XpEvents} so that class holds
 * only one-parameter event handlers (S-07).
 */
public final class DiscoveryHelper {

    private DiscoveryHelper() {}

    /**
     * O(1) bit test. Only the very first unlock of a given registry entry pays out.
     *
     * @return {@code true} when this call unlocked something new
     */
    public static boolean discover(ServerPlayer player, XpSource source, int id, String stableKey, int size) {
        DiscoveryData data = player.getData(RpgAttachments.DISCOVERY);
        data.setStorage(RpgConfig.discoveryStorage() == RpgConfig.DiscoveryStorage.STRING_SET
                ? DiscoveryData.Storage.STRING_SET : DiscoveryData.Storage.BITSET);
        if (!data.unlock(source, id, stableKey, size, RpgConfig.maxBitsPerSource())) return false;
        int reward = RpgConfig.discoveryReward(source);
        if (reward > 0) XpService.grant(player, source, reward);
        return true;
    }

    /** Action bar codex note. Never called from a tick path. */
    public static void announce(ServerPlayer player, XpSource source, Component label, int size) {
        DiscoveryData data = player.getData(RpgAttachments.DISCOVERY);
        player.displayClientMessage(Component.translatable("rpgleveling.discovery.new",
                label, data.count(source), size), true);
    }
}