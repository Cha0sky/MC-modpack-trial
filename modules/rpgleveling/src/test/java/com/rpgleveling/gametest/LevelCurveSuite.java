package com.rpgleveling.gametest;

import com.rpgleveling.data.LevelCurve;
import com.rpgleveling.data.RpgMath;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 4: the level curve boundary, monotonicity and the inverse relation between
 * {@code levelForXp} and {@code cumulativeXpAtLevel}.
 *
 * <p>Pure arithmetic, but it runs inside the game test server so the exact numbers the
 * verifier reads from {@code /rpg stats} are pinned by the same artifact.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class LevelCurveSuite {

    private LevelCurveSuite() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "curve")
    public static void seamBetweenLinearAndLogarithmicSegment(GameTestHelper helper) {
        LevelCurve curve = LevelCurve.DEFAULT;
        GameTestSupport.Report report = new GameTestSupport.Report();

        report.check(RpgMath.xpNeededForLevel(199, curve) == 4060L, "need(199) != 4060");
        report.check(RpgMath.xpNeededForLevel(200, curve) == 4080L, "need(200) != 4080");
        report.check(RpgMath.xpNeededForLevel(201, curve) == 4504L, "need(201) != 4504");
        report.check(RpgMath.cumulativeXpAtLevel(199, curve) == 413920L, "cum(199) != 413920");
        report.check(RpgMath.cumulativeXpAtLevel(200, curve) == 418000L, "cum(200) != 418000");
        report.check(RpgMath.cumulativeXpAtLevel(201, curve) == 422504L, "cum(201) != 422504");

        report.check(RpgMath.levelForXp(417999L, curve) == 199, "levelForXp(417999) != 199");
        report.check(RpgMath.levelForXp(418000L, curve) == 200, "levelForXp(418000) != 200");
        report.check(RpgMath.levelForXp(418001L, curve) == 200, "levelForXp(418001) != 200");
        report.check(RpgMath.levelForXp(422503L, curve) == 200, "levelForXp(422503) != 200");
        report.check(RpgMath.levelForXp(422504L, curve) == 201, "levelForXp(422504) != 201");

        // The logarithm must never see an argument below 2, and the requirement must never
        // decrease across the seam (a negative jump would make levelling impossible).
        for (int level = 198; level <= 210; level++) {
            long need = RpgMath.xpNeededForLevel(level, curve);
            report.check(need > 0L, "non-positive requirement at L=" + level);
            report.check(need >= RpgMath.xpNeededForLevel(level - 1, curve),
                    "requirement decreased at L=" + level);
        }
        report.assertAllPassed(helper, "seamBetweenLinearAndLogarithmicSegment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "curve")
    public static void monotoneAndInvertibleOverALongRange(GameTestHelper helper) {
        LevelCurve curve = LevelCurve.DEFAULT;
        GameTestSupport.Report report = new GameTestSupport.Report();

        long previous = 0L;
        for (int level = 1; level <= 5000; level++) {
            long need = RpgMath.xpNeededForLevel(level, curve);
            if (need < previous) {
                report.check(false, "need decreased at L=" + level + ": " + previous + " -> " + need);
                break;
            }
            previous = need;
        }

        for (int level : new int[] { 1, 2, 50, 100, 199, 200, 201, 300, 500, 1000, 2000, 5000 }) {
            long cumulative = RpgMath.cumulativeXpAtLevel(level, curve);
            report.check(RpgMath.levelForXp(cumulative, curve) == level,
                    "round trip failed at L=" + level + " (cum=" + cumulative + ")");
            if (level > 1) {
                report.check(RpgMath.levelForXp(cumulative - 1L, curve) == level - 1,
                        "one xp below the boundary should be L=" + (level - 1));
            }
            report.check(RpgMath.earnedPointsAtLevel(level, curve) == level,
                    "earnedPoints(" + level + ") != " + level);
            report.check(RpgMath.pointsForLevel(level, curve) == 1, "pointsForLevel(" + level + ") != 1");
        }

        // Integer overflow guard: a very large total must not wrap negative.
        report.check(RpgMath.levelForXp(Integer.MAX_VALUE, curve) > 0, "levelForXp(int max) not positive");
        report.check(RpgMath.levelForXp(6_471_888L, curve) == 1000, "levelForXp(6471888) != 1000");
        report.check(RpgMath.levelForXp(14_924_943L, curve) == 2000, "levelForXp(14924943) != 2000");
        report.assertAllPassed(helper, "monotoneAndInvertibleOverALongRange");
        helper.succeed();
    }

    /** E-05: a deep level must resolve within a tick budget. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "curve")
    public static void deepLevelsAreCheapAndConsistent(GameTestHelper helper) {
        LevelCurve curve = LevelCurve.DEFAULT;
        long start = System.nanoTime();
        long cumulative = RpgMath.cumulativeXpAtLevel(1_000_000, curve);
        int level = RpgMath.levelForXp(cumulative, curve);
        int below = RpgMath.levelForXp(cumulative - 1L, curve);
        double millis = (System.nanoTime() - start) / 1.0E6;

        helper.assertTrue(level == 1_000_000, "levelForXp(cumXp(1e6)) = " + level);
        helper.assertTrue(below == 999_999, "levelForXp(cumXp(1e6)-1) = " + below);
        helper.assertTrue(millis < 500.0, "deep level resolution took " + millis + " ms");
        GameTestSupport.evidence("curve.deepMillis", String.format(java.util.Locale.ROOT, "%.2f", millis));
        helper.succeed();
    }
}
