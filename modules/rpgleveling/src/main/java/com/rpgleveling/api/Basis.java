package com.rpgleveling.api;

/**
 * How a {@code percent} line's contribution is anchored (T5 ruling, DESIGN 1.14.3).
 */
public enum Basis {
    /**
     * Vanilla semantics: {@code final = (base + SUM(all ADD_VALUE, incl. equipment)) * (1 + V_pct/100)}.
     * Realised with {@code ADD_MULTIPLIED_BASE}, so it also scales equipment.
     */
    TOTAL,
    /**
     * Design reference basis: {@code final = base + V_flat + B_ref * V_pct / 100}.
     * Realised with a dedicated {@code ADD_VALUE} modifier so the contribution is
     * never zero as long as {@code B_ref > 0}.
     */
    DESIGN_BASELINE;

    public static final Basis[] VALUES = values();
}
