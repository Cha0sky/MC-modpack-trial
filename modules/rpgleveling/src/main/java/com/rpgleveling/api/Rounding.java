package com.rpgleveling.api;

/** How a fractional drop count is turned into whole items (DESIGN 1.14.8). */
public enum Rounding {
    /** Unbiased randomised rounding. Default: expectation equals {@code oldCount * multiplier}. */
    STOCHASTIC,
    /** Always round up (generous). */
    CEIL,
    /** Always round down (conservative). */
    FLOOR;

    public static final Rounding[] VALUES = values();

    /** Applies the strategy to {@code exact}. {@code rng} must be drawn on the server only. */
    public int apply(double exact, net.minecraft.util.RandomSource rng) {
        if (exact <= 0.0) return 0;
        int base = (int) Math.floor(exact);
        double frac = exact - base;
        return switch (this) {
            case FLOOR -> base;
            case CEIL -> base + (frac > 0.0 ? 1 : 0);
            case STOCHASTIC -> base + ((frac > 0.0 && rng.nextDouble() < frac) ? 1 : 0);
        };
    }
}