package com.infinite.core.gen;

/** Binary-baseline arithmetic. Tier ordinals run from dirt (0) to netherite (7). */
public final class GenerationMath {
    private GenerationMath() {}

    public static double multiplier(int machine, int template) {
        return Math.max(0.1, Math.min(2.0, 1.0 + (template - machine) * 0.2));
    }

    public static int interval(int baseTicks, int machine, int template) {
        return Math.max(1, (int) Math.round(baseTicks / multiplier(machine, template)));
    }
}
