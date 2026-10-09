package com.rpgleveling.api;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * The thirteen point-buy dimensions. The carrier decides how a dimension reaches
 * the game, {@code playerBase} is the real base on a player entity (proved by
 * {@code Player.createAttributes()}) and {@link #hardMax()} is the final clamp of
 * the mod - either the attribute's own live range ({@link LimitMode#LIVE}) or a
 * functional ceiling this mod chooses to keep ({@link LimitMode#FIXED}).
 */
public enum Stat {
    //                                                                        playerBase  limitMode          fixedMax  equipmentMax
    ATTACK_DAMAGE   ("attack_damage",        Carrier.VANILLA_ATTACK_DAMAGE,         1.0, LimitMode.LIVE,     2048.0,   9.0),
    MAX_HEALTH      ("max_health",           Carrier.VANILLA_MAX_HEALTH,           20.0, LimitMode.LIVE,     1024.0,   0.0),
    ARMOR           ("armor",                Carrier.VANILLA_ARMOR,                 0.0, LimitMode.LIVE,       30.0,  20.0),
    ARMOR_TOUGHNESS ("armor_toughness",      Carrier.VANILLA_ARMOR_TOUGHNESS,       0.0, LimitMode.LIVE,       20.0,  12.0),
    MOVEMENT_SPEED  ("movement_speed",       Carrier.VANILLA_MOVEMENT_SPEED,        0.1, LimitMode.LIVE,     1024.0,   0.0),
    KNOCKBACK_RESIST("knockback_resistance", Carrier.VANILLA_KNOCKBACK_RESISTANCE,  0.0, LimitMode.FIXED,       1.0,   0.4),
    HEALTH_REGEN    ("health_regen",         Carrier.CUSTOM_HEALTH_REGEN,           0.0, LimitMode.LIVE,       1.0E6,   0.0),
    DAMAGE_REDUCTION("damage_reduction",     Carrier.CUSTOM_DAMAGE_REDUCTION,       0.0, LimitMode.FIXED,      90.0,   0.0),
    CRIT_CHANCE     ("crit_chance",          Carrier.CUSTOM_CRIT_CHANCE,            0.0, LimitMode.FIXED,     100.0,   0.0),
    CRIT_DAMAGE     ("crit_damage",          Carrier.CUSTOM_CRIT_DAMAGE,            0.0, LimitMode.LIVE,       1.0E9,  0.0),
    LIFESTEAL       ("lifesteal",            Carrier.CUSTOM_LIFESTEAL,              0.0, LimitMode.LIVE,       1.0E6,  0.0),
    DROP_RATE       ("drop_rate",            Carrier.CUSTOM_DROP_RATE,              0.0, LimitMode.LIVE,       1.0E6,  0.0),
    XP_GAIN         ("xp_gain",              Carrier.CUSTOM_XP_GAIN,                0.0, LimitMode.LIVE,       1.0E6,  0.0);

    /**
     * How {@link #hardMax()} is resolved.
     *
     * <ul>
     *   <li>{@link #LIVE} - read {@code RangedAttribute.getMaxValue()} on every call, so a
     *       mod that raises the attribute's range (AttributeFix) automatically lifts this
     *       mod's clamp too. Nothing has to be reconfigured.</li>
     *   <li>{@link #FIXED} - the value is a functional ceiling of this mod and must never
     *       follow the attribute: going past 100% crit chance or 1.0 knockback resistance
     *       changes no mechanic, and damage reduction is asymptotically capped at 90%.</li>
     * </ul>
     */
    public enum LimitMode { LIVE, FIXED }

    public final String id;
    public final Carrier carrier;
    /** Real base on a player entity - never the raw {@code Attribute} default. */
    public final double playerBase;
    /** How {@link #hardMax()} resolves. */
    public final LimitMode limitMode;
    /** The value {@link #hardMax()} returns when {@link #limitMode} is {@link LimitMode#FIXED}. */
    public final double fixedMax;
    /**
     * Reference maximum an unspent player can get from vanilla gear on this attribute
     * (DESIGN 1.16.1): netherite armour 20 armour / 12 toughness / 0.4 knockback
     * resistance, netherite axe 9 attack damage, nothing for the rest. It is the
     * {@code equipment_max} term of the safety proof
     * {@code sup = equipment_max + flatCap + pctMaxContribution <= hardMax}.
     */
    public final double equipmentMax;

    public static final Stat[] VALUES = values();
    public static final int COUNT = VALUES.length;   // 13

    Stat(String id, Carrier carrier, double playerBase, LimitMode limitMode,
         double fixedMax, double equipmentMax) {
        this.id = id;
        this.carrier = carrier;
        this.playerBase = playerBase;
        this.limitMode = limitMode;
        this.fixedMax = fixedMax;
        this.equipmentMax = equipmentMax;
    }

    /**
     * The final clamp of this dimension.
     *
     * <p>Deliberately a <b>method</b>, never a constructor-time constant: the vanilla
     * attributes are registered before this enum's holder can be resolved, and a
     * {@link LimitMode#LIVE} dimension must observe the attribute range <i>as it is at
     * call time</i> - that is the whole point of the adaptive behaviour (AttributeFix
     * raises {@code RangedAttribute.maxValue} and this method follows it for free).</p>
     *
     * <p>Only {@link LimitMode#FIXED} dimensions may be resolved during class
     * initialization; {@link LimitMode#LIVE} ones touch the attribute registry.</p>
     */
    public double hardMax() {
        if (limitMode == LimitMode.LIVE) {
            Attribute attribute = carrier.holder().value();
            if (attribute instanceof RangedAttribute ranged) {
                return ranged.getMaxValue();
            }
        }
        return fixedMax;
    }

    static {
        // Guard rail for the "attribute default != player base" trap:
        // Attributes.ATTACK_DAMAGE defaults to 2.0 and MOVEMENT_SPEED to 0.7, while a
        // player entity is created with 1.0 and 0.1 (Player.createAttributes()).
        // Using the Attribute default here would make attack damage 2x and movement
        // speed 7x off, and it is very hard to notice from the inside.
        expect(ATTACK_DAMAGE, 1.0);
        expect(MAX_HEALTH, 20.0);
        expect(MOVEMENT_SPEED, 0.1);
        expect(ARMOR, 0.0);
        expect(ARMOR_TOUGHNESS, 0.0);
        expect(KNOCKBACK_RESIST, 0.0);
        // equipment_max is part of the safety proof, so a typo here silently breaks the
        // "no point is ever wasted" guarantee (DESIGN 1.16.1).
        expectEquipment(ARMOR, 20.0);
        expectEquipment(ARMOR_TOUGHNESS, 12.0);
        expectEquipment(KNOCKBACK_RESIST, 0.4);
        expectEquipment(ATTACK_DAMAGE, 9.0);
        expectEquipment(MAX_HEALTH, 0.0);
        expectEquipment(MOVEMENT_SPEED, 0.0);
        for (Stat stat : VALUES) {
            if (stat.carrier.isCustom() && stat.playerBase != 0.0) {
                throw new IllegalStateException("custom attribute " + stat.id + " must have player base 0.0");
            }
            if (stat.limitMode == LimitMode.FIXED && !(stat.fixedMax > 0.0)) {
                throw new IllegalStateException("FIXED dimension " + stat.id + " needs a positive fixedMax");
            }
        }
    }

    private static void expectEquipment(Stat stat, double expected) {
        if (stat.equipmentMax != expected) {
            throw new IllegalStateException("equipment_max of " + stat.id + " must be " + expected
                    + " but is " + stat.equipmentMax);
        }
    }

    private static void expect(Stat stat, double expected) {
        if (stat.playerBase != expected) {
            throw new IllegalStateException("player base of " + stat.id + " must be " + expected
                    + " (the entity base, not the Attribute default) but is " + stat.playerBase);
        }
    }

    public static Stat byIndex(int i) {
        return (i < 0 || i >= COUNT) ? ATTACK_DAMAGE : VALUES[i];
    }

    /** The line of the given mode belonging to this dimension. */
    public StatLine line(Mode mode) {
        return StatLine.lookup(this, mode);
    }

    /** {@code true} when something outside the mod can contribute ADD_VALUE (DESIGN 1.14.2). */
    public boolean hasExternalBasis() {
        return equipmentMax > 0.0 || playerBase > 0.0;
    }

    /**
     * Default basis of this dimension's percent line (DESIGN 1.16 / T6).
     *
     * <p>Only the three dimensions whose basis is dominated by the vanilla base use
     * {@link Basis#TOTAL}; the other ten - including armour, toughness and knockback
     * resistance, which used to be TOTAL - use {@link Basis#DESIGN_BASELINE} so the
     * equipment term can never push them past their hard ceiling.</p>
     */
    public Basis defaultBasis() {
        return switch (this) {
            case ATTACK_DAMAGE, MAX_HEALTH, MOVEMENT_SPEED -> Basis.TOTAL;
            default -> Basis.DESIGN_BASELINE;
        };
    }

    /** Language key of the dimension name. */
    public String langKey() {
        return "rpgleveling.stat." + id;
    }
}
