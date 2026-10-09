package com.rpgleveling.service;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Basis;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.net.RpgNet;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.stat.AttributeApplier;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import com.rpgleveling.util.RpgLog;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * <b>The only writer of {@link PlayerRpgData}.</b>
 *
 * <p>Every mutation path - GUI payload, command, XP grant - funnels through here, so
 * all validation lives in one auditable place. The client can only ever ask; it never
 * reports a number the server would trust.</p>
 *
 * <p>No {@code net.minecraft.client.*} import (headless GameTest requirement).</p>
 */
public final class RpgService {

    /** Largest single delta accepted from a client packet. */
    public static final int MAX_STEP = 100;

    private RpgService() {}

    // ------------------------------------------------------------------
    // point spending
    // ------------------------------------------------------------------

    /**
     * Spends (or refunds, when {@code amount} is negative) points on one line.
     *
     * @return {@code true} when the mutation was committed
     */
    public static boolean spend(ServerPlayer player, int lineIndex, int amount) {
        if (lineIndex < 0 || lineIndex >= StatLine.COUNT) {
            violation(player, "lineIndex=" + lineIndex);
            RpgNet.feedback(player, RpgNet.FB_BAD_ARGS);
            return false;
        }
        if (amount == 0 || Math.abs(amount) > MAX_STEP) {
            violation(player, "amount=" + amount);
            RpgNet.feedback(player, RpgNet.FB_BAD_ARGS);
            return false;
        }
        if (!RateLimiter.admit(player, "spend", 20, 20)) {
            RateLimiter.noteViolation(player);
            RpgNet.feedback(player, RpgNet.FB_RATE_LIMIT);
            return false;
        }

        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        StatLine line = StatLine.byIndex(lineIndex);
        LineCfg cfg = RpgConfig.lineCfg(line);

        if (!cfg.enabled()) {
            RpgNet.feedback(player, RpgNet.FB_LINE_DISABLED);
            return false;
        }

        int current = data.points(line);
        long target = (long) current + (long) amount;
        if (target < 0L) {
            RpgNet.feedback(player, RpgNet.FB_NO_POINTS);
            return false;
        }
        if (target > Integer.MAX_VALUE) {
            violation(player, "target overflow");
            RpgNet.feedback(player, RpgNet.FB_BAD_ARGS);
            return false;
        }
        if (cfg.maxPoints() >= 0 && target > cfg.maxPoints()) {
            RpgNet.feedback(player, RpgNet.FB_LINE_CAP);
            return false;
        }
        long cost = target - current;
        if (cost > 0L && cost > data.unspentPoints()) {
            RpgNet.feedback(player, RpgNet.FB_NO_POINTS);
            return false;
        }
        if (amount > 0 && wouldWastePoints(line, cfg, data)) {
            RpgNet.feedback(player, RpgNet.FB_ZERO_BASIS);
            return false;
        }

        data.addPoints(line, amount);
        AttributeApplier.apply(player, data);
        RpgNet.syncTo(player);
        return true;
    }

    /**
     * Axiom A3 ("no point is ever wasted") enforced server side: a percent line whose
     * basis is the vanilla total degenerates to zero for a dimension that has no
     * external ADD_VALUE source and no flat investment.
     */
    private static boolean wouldWastePoints(StatLine line, LineCfg cfg, PlayerRpgData data) {
        if (line.mode != Mode.PERCENT) return false;
        if (cfg.basis() != Basis.TOTAL) return false;
        if (line.stat.hasExternalBasis()) return false;
        return data.points(line.stat.line(Mode.FLAT)) == 0;
    }

    // ------------------------------------------------------------------
    // respec
    // ------------------------------------------------------------------

    public static boolean respec(ServerPlayer player) {
        PlayerCache.Entry cache = PlayerCache.of(player.getUUID());
        int cooldown = RpgConfig.respecCooldownTicks();
        if (cooldown > 0 && cache.lastRespecTick != Integer.MIN_VALUE
                && player.tickCount - cache.lastRespecTick < cooldown) {
            RpgNet.feedback(player, RpgNet.FB_COOLDOWN);
            return false;
        }
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        data.respecAll();
        cache.lastRespecTick = player.tickCount;
        AttributeApplier.apply(player, data);
        RpgNet.syncTo(player);
        return true;
    }

    // ------------------------------------------------------------------
    // experience landing zone (XpService is the only caller of commitXp)
    // ------------------------------------------------------------------

    /**
     * Books experience that was already scaled and rate limited by {@code XpService}.
     * The only place {@code totalXp} is written outside the debug commands.
     */
    public static void commitXp(ServerPlayer player, long gained) {
        if (gained <= 0L) return;
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        int oldLevel = data.level();
        data.addTotalXp(gained);
        int newLevel = data.level();
        if (newLevel != oldLevel) onLevelUp(player, oldLevel, newLevel);
        RpgNet.syncTo(player);
    }

    /** Level transition hook: feedback only, never grants items. */
    public static void onLevelUp(ServerPlayer player, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel) return;
        player.displayClientMessage(Component.translatable("rpgleveling.levelup", newLevel), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    // ------------------------------------------------------------------
    // bulk / debug entry points
    // ------------------------------------------------------------------

    /** Applies modifiers and pushes data without touching points. */
    public static void syncOnly(ServerPlayer player) {
        AttributeApplier.apply(player, player.getData(RpgAttachments.PLAYER_DATA));
        RpgNet.syncTo(player);
    }

    /** Recomputes everything after a bulk change (debug commands, config reload). */
    public static void recomputeAfterBulkChange(ServerPlayer player) {
        syncOnly(player);
    }

    /** {@code /rpg debug setlevel}: level is derived, so we invert it into a total. */
    public static void setLevel(ServerPlayer player, int level) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        int clamped = Math.max(0, level);
        data.setTotalXpExact(RpgMath.cumulativeXpAtLevel(clamped, RpgMath.activeCurve()));
        data.invalidateDerived();
        recomputeAfterBulkChange(player);
    }

    public static void addXp(ServerPlayer player, long amount) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        if (amount >= 0L) {
            data.addTotalXp(amount);
        } else {
            data.setTotalXpExact(data.totalXp() + amount);
        }
        recomputeAfterBulkChange(player);
    }

    public static void setPoints(ServerPlayer player, StatLine line, int points) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        data.setPointsExact(line, points);
        recomputeAfterBulkChange(player);
    }

    /** {@code /rpg debug addpoints}: transient, never persisted, never touches totalXp. */
    public static long addPoints(ServerPlayer player, long amount) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        long total = data.addDebugPoints(amount);
        recomputeAfterBulkChange(player);
        return total;
    }

    /**
     * Grants allocatable points from a legitimate source (the skill scroll item).
     *
     * <p>Persisted, and still the only writer of {@link PlayerRpgData}: an item never
     * touches player data directly.</p>
     */
    public static long grantPoints(ServerPlayer player, long amount) {
        if (player == null || amount == 0L) return 0L;
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        long total = data.addBonusPoints(amount);
        recomputeAfterBulkChange(player);
        return total;
    }

    /**
     * Books experience for an item.
     *
     * <p>Delegates to {@link XpService#grant}, so the {@code xp_gain} multiplier is applied
     * exactly once and the single experience entry point (INV-2) is preserved. It is
     * deliberately <b>not</b> a second way to write {@code totalXp}.</p>
     *
     * @return the experience actually booked
     */
    public static int grantXp(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return 0;
        return XpService.grant(player, XpSource.OTHER, amount);
    }

    public static void resetPlayer(ServerPlayer player) {
        PlayerRpgData data = player.getData(RpgAttachments.PLAYER_DATA);
        data.resetAll();
        StatOverrides.clearAll(player.getUUID());
        AttributeApplier.clearAll(player);
        recomputeAfterBulkChange(player);
    }

    public static PlayerRpgData data(ServerPlayer player) {
        return player.getData(RpgAttachments.PLAYER_DATA);
    }

    /** Value reported by {@code attr.<name>} in the stats contract. */
    public static double attributeValue(ServerPlayer player, Stat stat) {
        return DerivedStats.finalValue(player, stat);
    }

    // ------------------------------------------------------------------
    // diagnostics
    // ------------------------------------------------------------------

    private static void violation(ServerPlayer player, String why) {
        if (player == null) return;
        int count = PlayerCache.of(player.getUUID()).violations + 1;
        PlayerCache.of(player.getUUID()).violations = count;
        if (count <= 5 || count % 100 == 0) {
            RpgLog.LOG.warn("rpgleveling: rejected suspicious request from {} ({}) - {} so far",
                    player.getGameProfile().getName(), why, count);
        }
    }

    public static int violations(UUID id) {
        return PlayerCache.of(id).violations;
    }
}