package com.rpgleveling.service;

import net.minecraft.server.level.ServerPlayer;

/**
 * Rolling 60 second experience budget - a <b>secondary</b> safety net.
 *
 * <p>After the first-time discovery rule landed, the primary anti-farm defence is the
 * ledger itself; this limiter only exists in case an administrator raises a reward to
 * an unreasonable value.</p>
 */
public final class XpRateLimiter {

    private static final int WINDOW_TICKS = 20 * 60;

    private XpRateLimiter() {}

    /**
     * @return how much of {@code wanted} may be booked right now (0 when the budget is spent).
     */
    public static long admit(ServerPlayer player, XpSource source, long wanted, int minuteCap) {
        if (minuteCap <= 0 || wanted <= 0L) return wanted;
        PlayerCache.Entry entry = PlayerCache.of(player.getUUID());
        int now = player.tickCount;
        ArrayDequePrune(entry, now);
        long used = 0L;
        for (long[] e : entry.xpWindow) used += e[1];
        long room = minuteCap - used;
        if (room <= 0L) return 0L;
        long granted = Math.min(wanted, room);
        entry.xpWindow.addLast(new long[] { now, granted });
        return granted;
    }

    private static void ArrayDequePrune(PlayerCache.Entry entry, int now) {
        while (!entry.xpWindow.isEmpty() && now - entry.xpWindow.peekFirst()[0] >= WINDOW_TICKS) {
            entry.xpWindow.pollFirst();
        }
    }

    public static void clear(ServerPlayer player) {
        PlayerCache.of(player.getUUID()).xpWindow.clear();
    }

    /** Experience booked inside the current window; used by {@code /rpg debug stats}. */
    public static long used(ServerPlayer player) {
        long used = 0L;
        for (long[] e : PlayerCache.of(player.getUUID()).xpWindow) used += e[1];
        return used;
    }
}
