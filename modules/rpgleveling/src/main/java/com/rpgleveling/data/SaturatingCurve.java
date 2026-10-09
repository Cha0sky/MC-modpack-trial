package com.rpgleveling.data;

/**
 * Inverse of the saturating curve, shared by the GUI tooltips and {@code /rpg list}.
 * Pure math - no Minecraft types.
 */
public final class SaturatingCurve {

    private SaturatingCurve() {}

    /** Fraction of the cap reached with {@code points} on a curve with the given half-point. */
    public static double ratio(int points, double halfPoints) {
        if (points <= 0) return 0.0;
        double h = (halfPoints > 0.0) ? halfPoints : 1.0E-9;
        return points / (points + h);
    }

    /** Points required to reach {@code ratio} of the cap (the algebraic inverse of the curve). */
    public static double pointsForRatio(double ratio, double halfPoints) {
        return RpgMath.inverseSaturating(ratio, halfPoints);
    }

    /** Points still missing to gain another {@code step} of the ratio, never negative. */
    public static double pointsToGain(int points, double halfPoints, double step) {
        double cur = ratio(points, halfPoints);
        double target = Math.min(0.99, cur + step);
        return Math.max(0.0, pointsForRatio(target, halfPoints) - points);
    }
}
