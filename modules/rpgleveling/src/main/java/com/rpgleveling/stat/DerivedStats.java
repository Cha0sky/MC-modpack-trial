package com.rpgleveling.stat;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Basis;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.service.StatOverrides;
import com.rpgleveling.util.RpgLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * <b>The single place that reads the final value of a dimension.</b>
 *
 * <p>Everything in the game that needs "how much armour does this player have" or
 * "what is their crit chance" goes through {@link #finalValue}. Combat code never
 * evaluates a curve itself - if it did, a change to the basis policy would have to be
 * replicated in a dozen places.</p>
 *
 * <p>The basis policy itself lives in exactly one place, {@link #computedValue}, so it
 * can be swapped without touching any caller.</p>
 */
public final class DerivedStats {

    private DerivedStats() {}

    /**
     * Final value of a dimension for a player: debug override first, then the live
     * attribute (which already contains our modifiers, the player's base and any
     * equipment), clamped and NaN-guarded.
     */
    public static double finalValue(ServerPlayer player, Stat stat) {
        Double override = StatOverrides.get(player.getUUID(), stat);
        if (override != null && Double.isFinite(override)) return sanitize(override, stat);
        AttributeInstance inst = player.getAttribute(stat.carrier.holder());
        if (inst == null) return stat.playerBase;
        return sanitize(inst.getValue(), stat);
    }

    /**
     * Purely computed value of a dimension from an invested point table - the design
     * formula of DESIGN 1.3 / 1.14.3, independent of the live attribute.
     *
     * <p>Used by {@code /rpg stats} (the {@code line.*.value} channel) and as a fallback
     * when an attribute is missing. Because the basis is decided here and nowhere else,
     * changing the policy is a one-line change.</p>
     */
    public static double computedValue(Stat stat, int[] points, LineCfg[] cfgs) {
        StatLine flatLine = stat.line(Mode.FLAT);
        StatLine pctLine = stat.line(Mode.PERCENT);
        LineCfg flatCfg = cfgs[flatLine.index()];
        LineCfg pctCfg = cfgs[pctLine.index()];
        double flat = RpgMath.lineValue(points[flatLine.index()], flatCfg);
        double pct = RpgMath.lineValue(points[pctLine.index()], pctCfg);
        double hardMax = stat.hardMax();
        if (pctCfg.basis() == Basis.DESIGN_BASELINE) {
            return RpgMath.assembleBaseline(stat.playerBase, flat, pctCfg.basisValue(), pct, hardMax);
        }
        return RpgMath.assembleTotal(stat.playerBase, flat, pct, hardMax);
    }

    public static double computedValue(Stat stat, PlayerRpgData data) {
        return computedValue(stat, data.pointsView(), RpgConfig.snapshot());
    }

    /**
     * NaN / Infinity guard. The seven custom attributes are plain {@code RangedAttribute}s
     * whose {@code sanitizeValue} maps NaN to the minimum, but a NaN would still propagate
     * into the combat maths before that, and Infinity would produce an invulnerable player.
     * Every read path funnels through here.
     */
    // ------------------------------------------------------------------
    // pre-clamp observability (DESIGN 1.16.5)
    // ------------------------------------------------------------------

    /**
     * The value {@code AttributeInstance} would produce <b>before</b>
     * {@code Attribute#sanitizeValue} clamps it: reimplements the vanilla accumulation
     * {@code (base + SUM ADD_VALUE) * (1 + SUM ADD_MULTIPLIED_BASE)}.
     *
     * <p>This is what {@code /rpg stats} prints as {@code <dim>.computed}: it tracks the
     * equipment actually worn, so a naked and a fully equipped player legitimately report
     * different {@code computed} values while both stay {@code clamped=false}.</p>
     */
    public static double rawAttributeValue(LivingEntity entity, Stat stat) {
        AttributeInstance inst = entity.getAttribute(stat.carrier.holder());
        if (inst == null) return stat.playerBase;
        double sum = inst.getBaseValue();
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.operation() == AttributeModifier.Operation.ADD_VALUE) sum += m.amount();
        }
        double result = sum;
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) result += sum * m.amount();
        }
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) result *= 1.0 + m.amount();
        }
        return result;
    }

    /**
     * {@code true} when the vanilla {@code sanitizeValue} silently truncated the value -
     * the only evidence that a point was dropped on the floor.
     */
    public static boolean isClamped(LivingEntity entity, Stat stat) {
        AttributeInstance inst = entity.getAttribute(stat.carrier.holder());
        if (inst == null) return false;
        double raw = rawAttributeValue(entity, stat);
        double effective = inst.getValue();
        if (!Double.isFinite(raw)) return true;
        boolean clamped = Math.abs(effective - raw) > 1.0E-6;
        if (clamped && entity instanceof ServerPlayer player) {
            noteClamped(player, stat, raw, effective);
        }
        return clamped;
    }

    /**
     * {@code true} when one of this mod's <b>functional</b> ceilings is what a player is
     * actually feeling - the attribute holds a bigger number than the dimension is allowed
     * to hand out.
     *
     * <p>Needed because {@link #isClamped} only detects a truncation done by vanilla
     * {@code RangedAttribute.sanitizeValue}. The custom attributes are registered with a
     * 1.0E6 range, so crit chance can sit at 166% in the attribute while the dimension is
     * capped at 100 by {@link Stat.LimitMode#FIXED} - without this the GUI would show no
     * marker even though every further point is worthless.</p>
     */
    public static boolean isCappedByModCeiling(LivingEntity entity, Stat stat) {
        AttributeInstance inst = entity.getAttribute(stat.carrier.holder());
        if (inst == null) return false;
        double effective = inst.getValue();
        // >= with tolerance, not >: sitting exactly on the ceiling already means further
        // points do nothing, so the marker must show (T18-D5).
        return Double.isFinite(effective) && effective >= stat.hardMax() - 1.0E-9;
    }

    /**
     * The single predicate the GUI uses to draw the "!" marker: the number a player gets is
     * smaller than the number their points produce, for <b>either</b> reason - a vanilla
     * attribute truncation or one of this mod's functional ceilings.
     */
    public static boolean isEffectivelyCapped(LivingEntity entity, Stat stat) {
        return isClamped(entity, stat) || isCappedByModCeiling(entity, stat);
    }

    /** Logs the first truncation per player and dimension, then stays quiet. */
    private static void noteClamped(ServerPlayer player, Stat stat, double raw, double effective) {
        com.rpgleveling.service.PlayerCache.Entry entry =
                com.rpgleveling.service.PlayerCache.of(player.getUUID());
        if (!entry.clampWarned.add(stat)) return;
        RpgLog.LOG.info("rpgleveling: {} hit the vanilla ceiling on {} - computed={} effective={}",
                player.getGameProfile().getName(), stat.id, raw, effective);
    }

    /** Contribution of the player's equipment alone; diagnostics only. */
    public static double equipmentValue(ServerPlayer player, Stat stat) {
        LineCfg[] cfgs = RpgConfig.snapshot();
        PlayerRpgData data = player.getData(com.rpgleveling.registry.RpgAttachments.PLAYER_DATA);
        double ours = computedValue(stat, data.pointsView(), cfgs);
        double raw = rawAttributeValue(player, stat);
        double equipment = raw - ours;
        return Double.isFinite(equipment) ? Math.max(0.0, equipment) : 0.0;
    }

    private static double sanitize(double value, Stat stat) {
        if (!Double.isFinite(value)) return stat.playerBase;
        if (value < 0.0) return 0.0;
        double hardMax = stat.hardMax();
        if (value > hardMax) return hardMax;
        return value;
    }

    // ------------------------------------------------------------------
    // typed accessors - every consumer uses one of these
    // ------------------------------------------------------------------

    public static double attackDamage(ServerPlayer p)     { return finalValue(p, Stat.ATTACK_DAMAGE); }
    public static double maxHealth(ServerPlayer p)        { return finalValue(p, Stat.MAX_HEALTH); }
    public static double armor(ServerPlayer p)            { return finalValue(p, Stat.ARMOR); }
    public static double armorToughness(ServerPlayer p)   { return finalValue(p, Stat.ARMOR_TOUGHNESS); }
    public static double movementSpeed(ServerPlayer p)    { return finalValue(p, Stat.MOVEMENT_SPEED); }
    public static double knockbackResistance(ServerPlayer p) { return finalValue(p, Stat.KNOCKBACK_RESIST); }
    public static double healthRegenPerSecond(ServerPlayer p) { return finalValue(p, Stat.HEALTH_REGEN); }
    public static double damageReductionPercent(ServerPlayer p) { return finalValue(p, Stat.DAMAGE_REDUCTION); }
    public static double critChancePercent(ServerPlayer p) { return finalValue(p, Stat.CRIT_CHANCE); }
    public static double critDamagePercent(ServerPlayer p) { return finalValue(p, Stat.CRIT_DAMAGE); }
    public static double lifestealPercent(ServerPlayer p) { return finalValue(p, Stat.LIFESTEAL); }

    // NOTE: there is deliberately no experience-multiplier helper here. INV-2 requires the
    // experience dimension to be read and applied in XpService.java and nowhere else, so
    // even that convenience accessor lives there.

    /** Drop multiplier, {@code 1.0} = unchanged ({@code v} percentage points -> {@code 1 + v/100}). */
    public static double dropMultiplier(ServerPlayer p) {
        return 1.0 + finalValue(p, Stat.DROP_RATE) / 100.0;
    }

    /** Effective critical multiplier: {@code 1.5 + crit_damage/100}. */
    public static float critMultiplier(ServerPlayer p) {
        return (float) (1.5 + finalValue(p, Stat.CRIT_DAMAGE) / 100.0);
    }
}