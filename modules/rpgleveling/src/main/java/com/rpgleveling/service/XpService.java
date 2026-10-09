package com.rpgleveling.service;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Stat;
import com.rpgleveling.stat.DerivedStats;
import net.minecraft.server.level.ServerPlayer;

/**
 * <b>The only experience entry point, and the only place the experience multiplier is
 * applied.</b>
 *
 * <p>INV-2: the {@code xp_gain} dimension is read exactly once, here. Registering a
 * second hook somewhere else (for example a {@code the vanilla player-experience event} listener) would
 * square the multiplier. {@code the vanilla player-experience event} is not referenced anywhere in this mod.</p>
 */
public final class XpService {

    private XpService() {}

    /**
     * Books experience.
     *
     * @param rawXp base experience before any multiplier, {@code >= 0}
     * @return the amount actually booked, after the multiplier and the rate limiter
     */
    public static int grant(ServerPlayer player, XpSource source, int rawXp) {
        if (player == null || rawXp <= 0) return 0;

        int perTick = RpgConfig.safetyMaxPerTick(source);
        if (perTick < rawXp) rawXp = perTick;

        // The single application of the xp_gain dimension.
        double multiplier = 1.0 + DerivedStats.finalValue(player, Stat.XP_GAIN) / 100.0;
        if (!Double.isFinite(multiplier) || multiplier < 0.0) multiplier = 0.0;
        long gained = Math.round(rawXp * multiplier);
        if (gained <= 0L) return 0;

        int minuteCap = RpgConfig.xpMinuteCap();
        if (minuteCap > 0) gained = XpRateLimiter.admit(player, source, gained, minuteCap);
        if (gained <= 0L) return 0;
        if (gained > Integer.MAX_VALUE) gained = Integer.MAX_VALUE;

        RpgService.commitXp(player, gained);
        return (int) gained;
    }

    /**
     * Multiplier currently in effect.
     *
     * <p><b>Diagnostics only - never call this to apply the multiplier.</b> Applying it
     * anywhere but in {@link #grant} would book experience twice (INV-2). Deliberately
     * package-private so no other package can reach it.</p>
     */
    static double diagnosticMultiplier(ServerPlayer player) {
        double m = 1.0 + DerivedStats.finalValue(player, Stat.XP_GAIN) / 100.0;
        return Double.isFinite(m) ? m : 0.0;
    }
}