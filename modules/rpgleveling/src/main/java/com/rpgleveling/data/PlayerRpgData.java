package com.rpgleveling.data;

import com.rpgleveling.api.StatLine;
import java.util.Arrays;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * The player's RPG state.
 *
 * <p>Only two things are persisted: {@code totalXp} and {@code points[]}. Level,
 * earned points and unspent points are all <b>derived</b>, which makes an
 * inconsistent state (level disagreeing with XP, points out of thin air)
 * structurally impossible and turns a respec into {@code Arrays.fill(points, 0)}.</p>
 *
 * <p><b>Only {@code RpgService} is allowed to mutate an instance of this class.</b>
 * The single exception is {@link #addTotalXp(long)} / {@link #setTotalXpExact(long)},
 * called from {@code XpService} / the debug command through {@code RpgService}.</p>
 */
public final class PlayerRpgData implements INBTSerializable<CompoundTag> {

    /** Cumulative RPG experience. Monotonic, {@code long}, the single source of truth. */
    private long totalXp = 0L;
    /** Points invested per line, indexed by {@link StatLine#ordinal()}. */
    private final int[] points = new int[StatLine.COUNT];
    /** Statistics only - never read for gameplay decisions. */
    private int respecCount = 0;
    /**
     * Points granted outside the level curve by the skill scroll item. <b>Persisted</b>:
     * an item must not lose what it granted on relog.
     */
    private long bonusPoints = 0L;

    /**
     * Extra points granted by {@code /rpg debug addpoints}. Deliberately <b>transient</b>:
     * it never reaches the save file, so test scaffolding cannot leak into a real world.
     */
    private transient long debugPoints = 0L;

    // ---- derived, never persisted ----
    private transient int cachedLevel = -1;
    private transient long cachedEarnedPoints = -1L;

    public PlayerRpgData() {}

    // ------------------------------------------------------------------
    // derived accessors
    // ------------------------------------------------------------------

    public int level() {
        ensureDerived();
        return cachedLevel;
    }

    public long earnedPoints() {
        ensureDerived();
        return cachedEarnedPoints + bonusPoints + debugPoints;
    }

    public long totalXp() {
        return totalXp;
    }

    public long xpIntoLevel() {
        return RpgMath.xpIntoLevel(totalXp, level(), RpgMath.activeCurve());
    }

    public long xpForNextLevel() {
        return RpgMath.xpNeededForLevel(level() + 1, RpgMath.activeCurve());
    }

    public long spentPoints() {
        long s = 0L;
        for (int p : points) s += p;
        return s;
    }

    public long unspentPoints() {
        return earnedPoints() - spentPoints();
    }

    public int respecCount() {
        return respecCount;
    }

    public int points(StatLine line) {
        return points[line.index()];
    }

    /** Read-only view for hot paths. Callers must not mutate the array. */
    public int[] pointsView() {
        return points;
    }

    public int pointsAt(int index) {
        return (index < 0 || index >= points.length) ? 0 : points[index];
    }

    // ------------------------------------------------------------------
    // mutation (RpgService only)
    // ------------------------------------------------------------------

    /** Adds XP, saturating well below {@code Long.MAX_VALUE} to avoid overflow. */
    public void addTotalXp(long delta) {
        if (delta <= 0L) return;
        long next = totalXp + delta;
        totalXp = (next < 0L || next > Long.MAX_VALUE / 2) ? Long.MAX_VALUE / 2 : next;
        invalidateDerived();
    }

    /** Sets the XP total exactly. Used by the level debug command (recomputing level). */
    public void setTotalXpExact(long value) {
        totalXp = Math.max(0L, Math.min(Long.MAX_VALUE / 2, value));
        invalidateDerived();
    }

    public void addPoints(StatLine line, int delta) {
        int i = line.index();
        long next = (long) points[i] + (long) delta;
        if (next < 0L) next = 0L;
        if (next > Integer.MAX_VALUE) next = Integer.MAX_VALUE;
        points[i] = (int) next;
    }

    /**
     * Grants (or removes) unspent points <b>without</b> touching {@code totalXp}.
     * <b>Persisted</b> - this is the item path (skill scroll) only, so a granted point
     * must survive a relog. Debug scaffolding uses {@link #addDebugPoints}.
     */
    public long addBonusPoints(long amount) {
        long next = bonusPoints + amount;
        if (next < 0L) next = 0L;
        if (next > Long.MAX_VALUE / 4) next = Long.MAX_VALUE / 4;
        bonusPoints = next;
        return bonusPoints;
    }

    /** Persisted points granted by items. */
    public long bonusPoints() {
        return bonusPoints;
    }

    /** Debug-only points; see {@link #debugPoints}. */
    public long addDebugPoints(long amount) {
        long next = debugPoints + amount;
        if (next < 0L) next = 0L;
        if (next > Long.MAX_VALUE / 4) next = Long.MAX_VALUE / 4;
        debugPoints = next;
        return debugPoints;
    }

    public long debugPoints() {
        return debugPoints;
    }

    public void setPointsExact(StatLine line, int value) {
        points[line.index()] = Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    public void respecAll() {
        Arrays.fill(points, 0);
        respecCount++;
    }

    public void resetAll() {
        totalXp = 0L;
        Arrays.fill(points, 0);
        bonusPoints = 0L;
        debugPoints = 0L;
        invalidateDerived();
    }

    public void invalidateDerived() {
        cachedLevel = -1;
        cachedEarnedPoints = -1L;
    }

    private void ensureDerived() {
        if (cachedLevel < 0) {
            cachedLevel = RpgMath.levelForXp(totalXp, RpgMath.activeCurve());
            cachedEarnedPoints = RpgMath.earnedPointsAtLevel(cachedLevel, RpgMath.activeCurve());
        }
    }

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("total_xp", totalXp);
        tag.putIntArray("points", points);
        tag.putInt("respec_count", respecCount);
        tag.putLong("bonus_points", bonusPoints);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        totalXp = Math.max(0L, Math.min(Long.MAX_VALUE / 2, tag.getLong("total_xp")));
        int[] stored = tag.getIntArray("points");
        if (stored.length == points.length) {
            System.arraycopy(stored, 0, points, 0, points.length);
        } else {
            Arrays.fill(points, 0);
            if (stored.length != 0) {
                com.rpgleveling.util.RpgLog.LOG.warn(
                        "rpgleveling: player point table has {} entries but {} were expected - reset to zero",
                        stored.length, points.length);
            }
        }
        respecCount = tag.getInt("respec_count");
        bonusPoints = Math.max(0L, tag.getLong("bonus_points"));
        invalidateDerived();
    }
}