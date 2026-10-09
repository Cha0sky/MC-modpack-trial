package com.rpgleveling.api;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * The twenty-six point-buy lines - the single source of truth shared by config,
 * modifiers, GUI, network and commands.
 *
 * <p><b>Curve policy (user decision, DESIGN 1.4):</b> every line is
 * {@link Curve#LINEAR} with {@code perPoint = original cap / original half_points},
 * so the early-game feel of the old saturating curve is preserved while the
 * diminishing returns are gone. The two damage-reduction lines stay
 * {@link Curve#SATURATING} so the 90% asymptotic ceiling can never be crossed.</p>
 */
public enum StatLine {
    //                        stat,               mode,          curve,          perPoint,     cap,   halfPoints
    ATTACK_DAMAGE_FLAT   (Stat.ATTACK_DAMAGE,    Mode.FLAT,    Curve.LINEAR,        0.5,        0.0,    0.0),
    ATTACK_DAMAGE_PCT    (Stat.ATTACK_DAMAGE,    Mode.PERCENT, Curve.LINEAR,        3.0,        0.0,    0.0),
    MAX_HEALTH_FLAT      (Stat.MAX_HEALTH,       Mode.FLAT,    Curve.LINEAR,        1.0,        0.0,    0.0),
    MAX_HEALTH_PCT       (Stat.MAX_HEALTH,       Mode.PERCENT, Curve.LINEAR,        2.0,        0.0,    0.0),
    ARMOR_FLAT           (Stat.ARMOR,            Mode.FLAT,    Curve.LINEAR,        0.15,       0.0,    0.0),
    ARMOR_PCT            (Stat.ARMOR,            Mode.PERCENT, Curve.LINEAR,        1.666667,   0.0,    0.0),
    ARMOR_TOUGHNESS_FLAT (Stat.ARMOR_TOUGHNESS,  Mode.FLAT,    Curve.LINEAR,        0.125,      0.0,    0.0),
    ARMOR_TOUGHNESS_PCT  (Stat.ARMOR_TOUGHNESS,  Mode.PERCENT, Curve.LINEAR,        1.666667,   0.0,    0.0),
    MOVEMENT_SPEED_FLAT  (Stat.MOVEMENT_SPEED,   Mode.FLAT,    Curve.LINEAR,        0.006666667, 0.0,   0.0),
    MOVEMENT_SPEED_PCT   (Stat.MOVEMENT_SPEED,   Mode.PERCENT, Curve.LINEAR,        2.5,        0.0,    0.0),
    KNOCKBACK_RESIST_FLAT(Stat.KNOCKBACK_RESIST, Mode.FLAT,    Curve.LINEAR,        0.00875,    0.0,    0.0),
    KNOCKBACK_RESIST_PCT (Stat.KNOCKBACK_RESIST, Mode.PERCENT, Curve.LINEAR,        1.666667,   0.0,    0.0),
    HEALTH_REGEN_FLAT    (Stat.HEALTH_REGEN,     Mode.FLAT,    Curve.LINEAR,        0.125,      0.0,    0.0),
    HEALTH_REGEN_PCT     (Stat.HEALTH_REGEN,     Mode.PERCENT, Curve.LINEAR,        3.333333,   0.0,    0.0),
    DAMAGE_REDUCTION_FLAT(Stat.DAMAGE_REDUCTION, Mode.FLAT,    Curve.SATURATING,    0.0,       60.0,   80.0),
    DAMAGE_REDUCTION_PCT (Stat.DAMAGE_REDUCTION, Mode.PERCENT, Curve.SATURATING,    0.0,       75.0,   80.0),
    CRIT_CHANCE_FLAT     (Stat.CRIT_CHANCE,      Mode.FLAT,    Curve.LINEAR,        0.833333,   0.0,    0.0),
    CRIT_CHANCE_PCT      (Stat.CRIT_CHANCE,      Mode.PERCENT, Curve.LINEAR,        1.25,       0.0,    0.0),
    CRIT_DAMAGE_FLAT     (Stat.CRIT_DAMAGE,      Mode.FLAT,    Curve.LINEAR,        1.0,        0.0,    0.0),
    CRIT_DAMAGE_PCT      (Stat.CRIT_DAMAGE,      Mode.PERCENT, Curve.LINEAR,        2.0,        0.0,    0.0),
    LIFESTEAL_FLAT       (Stat.LIFESTEAL,        Mode.FLAT,    Curve.LINEAR,        0.333333,   0.0,    0.0),
    LIFESTEAL_PCT        (Stat.LIFESTEAL,        Mode.PERCENT, Curve.LINEAR,        1.25,       0.0,    0.0),
    DROP_RATE_FLAT       (Stat.DROP_RATE,        Mode.FLAT,    Curve.LINEAR,        1.875,      0.0,    0.0),
    DROP_RATE_PCT        (Stat.DROP_RATE,        Mode.PERCENT, Curve.LINEAR,        5.0,        0.0,    0.0),
    XP_GAIN_FLAT         (Stat.XP_GAIN,          Mode.FLAT,    Curve.LINEAR,        1.666667,   0.0,    0.0),
    XP_GAIN_PCT          (Stat.XP_GAIN,          Mode.PERCENT, Curve.LINEAR,        5.0,        0.0,    0.0);

    public final Stat stat;
    public final Mode mode;
    public final Curve curve;
    public final double defPerPoint;
    public final double defCap;
    public final double defHalfPoints;

    public static final StatLine[] VALUES = values();
    public static final int COUNT = VALUES.length;   // 26
    public static final double DEFAULT_GAMMA = 0.5;

    private static final StatLine[][] LOOKUP = new StatLine[Stat.COUNT][Mode.VALUES.length];
    private static final Map<String, StatLine> BY_ID = new HashMap<>(64);

    static {
        for (StatLine line : VALUES) {
            LOOKUP[line.stat.ordinal()][line.mode.ordinal()] = line;
            BY_ID.put(line.id(), line);
        }
        verifySafetyProof();
    }

    /**
     * Startup guard for the numerical design of DESIGN 1.4 / 1.16.2.
     *
     * <ol>
     *   <li><b>Curve policy.</b> Exactly one dimension - damage reduction - may still use
     *       the saturating curve. Every other dimension is deliberately unbounded now
     *       (user decision), so a saturating curve sneaking back in is a regression
     *       against the agreed design and fails loudly at class load.</li>
     *   <li><b>Functional ceilings.</b> Damage reduction (90), crit chance (100) and
     *       knockback resistance (1.0) are the three intentional functional caps. They
     *       must stay {@link Stat.LimitMode#FIXED}: flipping one to
     *       {@link Stat.LimitMode#LIVE} would let crit chance pass 100% or knockback
     *       resistance pass immunity, which changes no mechanic but breaks every
     *       promise the guide book makes.</li>
     *   <li><b>Saturating safety proof.</b>
     *       {@code equipment_max + flatCap + B_ref * pctCap / 100 <= hardMax} for every
     *       line that uses {@link Basis#DESIGN_BASELINE} and a saturating curve.
     *       With the shipped defaults that is damage reduction alone:
     *       {@code 0 + 60 + 40 * 75/100 = 90 <= 90} (an equality, hence the epsilon).
     *       Every other dimension is LINEAR and has <b>no</b> safety proof on purpose -
     *       the growth limit was removed by request.</li>
     * </ol>
     *
     * @implNote rule 1 runs before rule 3 so that rule 3 can only ever resolve a
     *     {@code FIXED} {@link Stat#hardMax()} - a {@code LIVE} one would touch the
     *     attribute registry during class initialization.
     */
    private static void verifySafetyProof() {
        for (StatLine line : VALUES) {
            if (line.curve == Curve.SATURATING && line.stat != Stat.DAMAGE_REDUCTION) {
                throw new IllegalStateException("line " + line.id()
                        + " is saturating, but only damage_reduction may keep a ceiling");
            }
        }

        expectFixedMax(Stat.DAMAGE_REDUCTION, 90.0);
        expectFixedMax(Stat.CRIT_CHANCE, 100.0);
        expectFixedMax(Stat.KNOCKBACK_RESIST, 1.0);

        for (StatLine line : VALUES) {
            if (line.mode != Mode.PERCENT) continue;
            if (line.defBasis() != Basis.DESIGN_BASELINE) continue;
            if (line.curve == Curve.LINEAR) continue;   // no ceiling at all, by design
            StatLine flat = lookup(line.stat, Mode.FLAT);
            double sup = line.stat.equipmentMax + flat.defCap + line.defBasisValue() * line.defCap / 100.0;
            if (sup > line.stat.hardMax() + 1.0E-9) {
                throw new IllegalStateException("safety proof violated for " + line.stat.id
                        + ": sup=" + sup + " > hardMax=" + line.stat.hardMax());
            }
        }
    }

    private static void expectFixedMax(Stat stat, double expected) {
        if (stat.limitMode != Stat.LimitMode.FIXED) {
            throw new IllegalStateException("dimension " + stat.id
                    + " must keep a FIXED functional ceiling of " + expected
                    + " but is " + stat.limitMode);
        }
        if (Math.abs(stat.hardMax() - expected) > 1.0E-9) {
            throw new IllegalStateException("functional ceiling of " + stat.id
                    + " must be " + expected + " but is " + stat.hardMax());
        }
    }

    StatLine(Stat stat, Mode mode, Curve curve, double perPoint, double cap, double halfPoints) {
        this.stat = stat;
        this.mode = mode;
        this.curve = curve;
        this.defPerPoint = perPoint;
        this.defCap = cap;
        this.defHalfPoints = halfPoints;
    }

    public static StatLine lookup(Stat stat, Mode mode) {
        return LOOKUP[stat.ordinal()][mode.ordinal()];
    }

    /** {@code null} when the id is unknown - callers decide whether that is an error. */
    public static StatLine byId(String id) {
        return BY_ID.get(id);
    }

    public static java.util.Set<String> allIds() {
        return java.util.Collections.unmodifiableSet(BY_ID.keySet());
    }

    public int index() {
        return ordinal();
    }

    public String id() {
        return stat.id + "." + mode.suffix();
    }

    public String langKey() {
        return "rpgleveling.line." + id();
    }

    /** Modifier id used by flat lines and by percent lines in {@link Basis#TOTAL}. */
    public ResourceLocation modifierId() {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", id().replace('.', '_'));
    }

    /**
     * The <b>other</b> modifier id, used by percent lines in {@link Basis#DESIGN_BASELINE}.
     * Applier code must always clear the id it is not using, otherwise switching the
     * configured basis would leave both modifiers in place and double the value.
     */
    public ResourceLocation pctBasisModifierId() {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", id().replace('.', '_') + "_pctbasis");
    }

    /**
     * Default basis of this line (T6 ruling, DESIGN 1.16.3): only attack damage, max
     * health and movement speed stay on the vanilla {@link Basis#TOTAL} semantics; the
     * other ten use {@link Basis#DESIGN_BASELINE}, which keeps armour, armour toughness
     * and knockback resistance computable without an external {@code ADD_VALUE} source.
     */
    public Basis defBasis() {
        if (mode == Mode.FLAT) return Basis.TOTAL;
        return stat.defaultBasis();
    }

    /** Default design reference basis {@code B_ref}; {@code 0} for {@link Basis#TOTAL}. */
    public double defBasisValue() {
        if (mode == Mode.FLAT) return 0.0;
        return switch (stat) {
            case ARMOR -> 4.0;
            case ARMOR_TOUGHNESS -> 3.0;
            case KNOCKBACK_RESIST -> 0.20;
            case HEALTH_REGEN -> 2.0;
            case DAMAGE_REDUCTION -> 40.0;
            case CRIT_CHANCE -> 20.0;
            case CRIT_DAMAGE -> 50.0;
            case LIFESTEAL -> 10.0;
            case DROP_RATE -> 50.0;
            case XP_GAIN -> 50.0;
            default -> 0.0;
        };
    }

    public static StatLine byIndex(int i) {
        if (i < 0) return VALUES[0];
        if (i >= COUNT) return VALUES[COUNT - 1];
        return VALUES[i];
    }
}
