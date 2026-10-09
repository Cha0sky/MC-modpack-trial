package com.rpgleveling.api;

/**
 * The three curve primitives of the mod. Nothing else is allowed to define a
 * numeric progression.
 *
 * <ul>
 *   <li>{@link #LINEAR} - {@code per_point * x}; truly unbounded. Only for
 *       dimensions with no functional ceiling (attack damage, max health,
 *       crit damage).</li>
 *   <li>{@link #SATURATING} - {@code cap * x / (x + half)}; strictly monotone,
 *       always strictly below {@code cap}. The default for every dimension that
 *       has a hard ceiling.</li>
 *   <li>{@link #SUBLINEAR_POW} - {@code per_point * x^gamma}; sub-linear but
 *       <b>unbounded</b>, therefore the safety proof of DESIGN 1.5 does not
 *       apply. Opt-in only.</li>
 * </ul>
 */
public enum Curve {
    LINEAR,
    SATURATING,
    SUBLINEAR_POW;

    public static final Curve[] VALUES = values();

    public static Curve byIndex(int i) {
        return (i < 0 || i >= VALUES.length) ? SATURATING : VALUES[i];
    }
}
