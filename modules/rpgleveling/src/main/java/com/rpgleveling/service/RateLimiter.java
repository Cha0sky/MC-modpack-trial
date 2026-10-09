package com.rpgleveling.service;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Generic "at most {@code max} calls per {@code windowTicks}" limiter used to blunt
 * packet flooding ({@code /rpg spend} spam, replayed payloads).
 */
public final class RateLimiter {

    private RateLimiter() {}

    /**
     * @return {@code true} when the call is admitted and the counter has been consumed.
     */
    public static boolean admit(ServerPlayer player, String key, int max, int windowTicks) {
        if (max <= 0 || windowTicks <= 0) return true;
        UUID id = player.getUUID();
        int now = player.tickCount;
        int[] slot = PlayerCache.of(id).windows.computeIfAbsent(key, k -> new int[] { Integer.MIN_VALUE, 0 });
        int elapsed = now - slot[0];
        if (slot[0] == Integer.MIN_VALUE || elapsed < 0 || elapsed >= windowTicks) {
            slot[0] = now;
            slot[1] = 1;
            return true;
        }
        if (slot[1] >= max) return false;
        slot[1]++;
        return true;
    }

    public static void noteViolation(ServerPlayer player) {
        PlayerCache.of(player.getUUID()).violations++;
    }

    public static int violations(UUID id) {
        return PlayerCache.of(id).violations;
    }
}
