package com.rpgleveling.data;

/**
 * Tunable parameters of the level curve (DESIGN 8.4). Immutable value object so
 * that an internal reload can swap the whole curve atomically.
 *
 * <pre>
 *   need(L) = linearBase + linearStep * (L - 1)                                , 1 &lt;= L &lt;= linearUntilLevel
 *   need(L) = need(linearUntilLevel) * (1 + logScale * ln(L - linearUntilLevel + 1)) , L &gt; linearUntilLevel
 *   earnedPoints(L) = L * pointsPerLevel
 * </pre>
 */
public record LevelCurve(int linearUntilLevel,
                         double linearBase,
                         double linearStep,
                         double logScale,
                         int pointsPerLevel,
                         int maxLevel) {

    /** The values locked by the lead (DESIGN 8.4 / 0.1). */
    public static final LevelCurve DEFAULT = new LevelCurve(200, 100.0, 20.0, 0.15, 1, -1);

    public LevelCurve {
        if (linearUntilLevel < 1) linearUntilLevel = 1;
        if (pointsPerLevel < 0) pointsPerLevel = 0;
        // maxLevel < 0 means "unbounded", which is the shipped default.
    }

    public boolean isCapped() {
        return maxLevel >= 0;
    }
}