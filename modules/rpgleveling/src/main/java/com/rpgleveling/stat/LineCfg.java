package com.rpgleveling.stat;

import com.rpgleveling.api.Basis;
import com.rpgleveling.api.Curve;
import com.rpgleveling.data.RpgMath;

/**
 * Immutable runtime snapshot of one point-buy line. The hot path never touches a
 * {@code ConfigValue}.
 *
 * <p>Free of any Minecraft type so the numeric core can be checked without a game.</p>
 */
public record LineCfg(boolean enabled,
                      Curve curve,
                      double perPoint,
                      double cap,
                      double halfPoints,
                      double gamma,
                      Basis basis,
                      double basisValue,
                      int maxPoints) {

    public static final int UNLIMITED = -1;

    public static LineCfg disabled() {
        return new LineCfg(false, Curve.SATURATING, 0.0, 0.0, 1.0, 0.5, Basis.TOTAL, 0.0, UNLIMITED);
    }

    public int effectivePoints(int points) {
        if (points <= 0) return 0;
        return (maxPoints >= 0) ? Math.min(points, maxPoints) : points;
    }

    /** Raw contribution {@code V} of this line for the given point investment. */
    public double value(int points) {
        return RpgMath.lineValue(points, this);
    }
}
