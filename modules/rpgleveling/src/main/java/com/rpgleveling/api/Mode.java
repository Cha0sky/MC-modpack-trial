package com.rpgleveling.api;

/**
 * The two point-buy modes every dimension supports.
 *
 * <p>{@link #FLAT} maps onto {@code AttributeModifier.Operation.ADD_VALUE} and
 * {@link #PERCENT} maps onto {@code ADD_MULTIPLIED_BASE} (never
 * {@code ADD_MULTIPLIED_TOTAL} - that one compounds and is banned mod-wide).</p>
 */
public enum Mode {
    /** Direct value increase, native unit of the dimension. */
    FLAT,
    /** Percentage increase; the modifier amount is a decimal ratio (value / 100). */
    PERCENT;

    public static final Mode[] VALUES = values();

    public static Mode byIndex(int i) {
        return (i < 0 || i >= VALUES.length) ? FLAT : VALUES[i];
    }

    public String suffix() {
        return this == FLAT ? "flat" : "percent";
    }
}
