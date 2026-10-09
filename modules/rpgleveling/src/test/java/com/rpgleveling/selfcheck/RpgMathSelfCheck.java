package com.rpgleveling.selfcheck;

import com.rpgleveling.api.Curve;
import com.rpgleveling.data.LevelCurve;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.stat.LineCfg;

/**
 * Pure-JVM self check of the numeric core.
 *
 * <p>Covers: the DESIGN 8.5 level sampling table and its inverse, the DESIGN 1.14.9
 * TOTAL / DESIGN_BASELINE assembly formulas (as primitive fixtures), the
 * DESIGN 1.4 <b>LINEAR slope rule</b> ({@code per_point = old cap / old half}), the
 * saturating primitive, and the single remaining safety proof
 * (damage reduction: {@code 0 + 60 + 40*75/100 = 90}, approached but never reached).</p>
 *
 * <p><b>Scope note:</b> after the DESIGN 1.4 rebalance every dimension except damage
 * reduction is LINEAR, so it has no ceiling and no safety proof - by request. The
 * shipped {@code StatLine} defaults themselves cannot be asserted here because
 * {@code StatLine} references a Minecraft type; {@code CurvePolicySuite} pins those
 * inside the GameTest server instead.</p>
 *
 * <p>Run with {@code gradlew verifyMath}. It intentionally never touches a Minecraft
 * type so it also proves that {@link RpgMath} really is client- and server-independent.</p>
 */
public final class RpgMathSelfCheck {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        LevelCurve c = LevelCurve.DEFAULT;

        // ---------- DESIGN 8.5 main sampling table ----------
        long[] levels =  {1, 50, 100, 200, 201, 300, 500, 1000, 2000};
        long[] need    =  {100, 1080, 2080, 4080, 4504, 6904, 7573, 8172, 8668};
        long[] cum     =  {100, 29500, 109000, 418000, 422504, 1051430, 2511411, 6471888, 14924943};
        for (int i = 0; i < levels.length; i++) {
            int L = (int) levels[i];
            eq("need(" + L + ")", need[i], RpgMath.xpNeededForLevel(L, c));
            eq("cumXp(" + L + ")", cum[i], RpgMath.cumulativeXpAtLevel(L, c));
            eq("points(" + L + ")", 1, RpgMath.pointsForLevel(L, c));
            eq("earned(" + L + ")", L, RpgMath.earnedPointsAtLevel(L, c));
        }

        // ---------- lead / architect / verifier three-way agreed reference values ----------
        eq("need(199)", 4060L, RpgMath.xpNeededForLevel(199, c));
        eq("cumXp(199)", 413920L, RpgMath.cumulativeXpAtLevel(199, c));
        eq("need(10000)", 9704L, RpgMath.xpNeededForLevel(10000, c));
        eq("cumXp(200) closed form", 418000L, 10L * 200 * 200 + 90L * 200);

        // ---------- closed form of the linear segment (the discriminant guard) ----------
        // The closed form must agree with the exact sum for EVERY level of the linear
        // segment; a wrong discriminant is silently masked by the defensive loops, so
        // assert the raw guess too.
        int worstDrift = 0;
        for (int L = 0; L <= 200; L++) {
            long atLevel = RpgMath.cumulativeXpAtLevel(L, c);
            int guess = RpgMath.linearLevelGuess(atLevel, c);
            if (guess != L) { fail("linearLevelGuess(cumXp(" + L + ")) = " + guess); }
            worstDrift = Math.max(worstDrift, Math.abs(guess - L));
        }
        checks++;
        // just below a boundary the guess may sit one level low, which is what the
        // defensive loop is for; assert it never needs more than one step.
        for (int L = 1; L <= 200; L++) {
            long justBelow = RpgMath.cumulativeXpAtLevel(L, c) - 1;
            int guess = RpgMath.linearLevelGuess(justBelow, c);
            if (L - 1 - guess > 1) fail("linearLevelGuess(" + justBelow + ") too far off: " + guess + " vs " + (L - 1));
            if (RpgMath.levelForXp(justBelow, c) != L - 1) fail("levelForXp(" + justBelow + ") != " + (L - 1));
        }
        checks++;
        System.out.println("  [info] linear closed form: exact at every boundary, drift " + worstDrift);

        // ---------- DESIGN 8.5 boundary reverse lookup ----------
        long[] xps    = {0, 99, 100, 1000, 29500, 109000, 417999, 418000, 418001, 1051430, 2511411, 6471888};
        // NOTE: DESIGN 8.5 lists level 4 for totalXp = 1000, but that entry contradicts the
        // very formula it is derived from: cumXp(6) = 10*36 + 90*6 = 900 <= 1000 and
        // cumXp(7) = 1120 > 1000, and the closed form floor((-9+sqrt(81+0.4*1000))/2) = 6.
        // The formula is the contract, so the expected value here is 6 (reported to the lead).
        int[]  expect = {0, 0,   1,   6,    50,    100,    199,    200,    200,     300,     500,     1000};
        for (int i = 0; i < xps.length; i++) {
            eq("levelForXp(" + xps[i] + ")", expect[i], RpgMath.levelForXp(xps[i], c));
        }

        // ---------- SATURATING curve primitive fixtures ----------
        // {cap, half, v10, v20, v50, v100, v200, v500}
        //
        // NOTE (DESIGN 1.4, user decision): after the rebalance only the two
        // damage_reduction lines still use Curve.SATURATING - row 11 is exactly that
        // line's flat cap/half and row 12 its percent cap/half (75/80). The remaining
        // rows are fixtures retained from earlier design revisions; they exercise the
        // primitive on a range of cap/half pairs and are deliberately NOT a statement
        // about the shipped StatLine table (CurvePolicySuite pins the shipped values,
        // which this Minecraft-free class cannot reach).
        double[][] sat = {
            {15,   60,  2.1429,  3.7500,  6.8182,  9.3750, 11.5385, 13.3929},   // armor.flat
            {100,  60, 14.2857, 25.0000, 45.4545, 62.5000, 76.9231, 89.2857},   // armor.percent
            {10,   60,  1.4286,  2.5000,  4.5455,  6.2500,  7.6923,  8.9286},   // armor_toughness.flat
            {100,  60, 14.2857, 25.0000, 45.4545, 62.5000, 76.9231, 89.2857},   // armor_toughness.percent
            {0.4,  60,  0.0571,  0.1000,  0.1818,  0.2500,  0.3077,  0.3571},   // movement_speed.flat
            {200,  80, 22.2222, 40.0000, 76.9231,111.1111,142.8571,172.4138},   // movement_speed.percent
            {0.45, 60,  0.0643,  0.1125,  0.2045,  0.2813,  0.3462,  0.4018},   // knockback_resistance.flat
            {100,  80, 11.1111, 20.0000, 38.4615, 55.5556, 71.4286, 86.2069},   // knockback_resistance.percent
            {5,    40,  1.0000,  1.6667,  2.7778,  3.5714,  4.1667,  4.6296},   // health_regen.flat
            {200,  60, 28.5714, 50.0000, 90.9091,125.0000,153.8462,178.5714},   // health_regen.percent
            {60,   80,  6.6667, 12.0000, 23.0769, 33.3333, 42.8571, 51.7241},   // damage_reduction.flat
            {75,   80,  8.3333, 15.0000, 28.8462, 41.6667, 53.5714, 64.6552},   // damage_reduction.percent (new cap 75)
            {50,   60,  7.1429, 12.5000, 22.7273, 31.2500, 38.4615, 44.6429},   // crit_chance.flat
            {100,  80, 11.1111, 20.0000, 38.4615, 55.5556, 71.4286, 86.2069},   // crit_chance.percent
            {20,   60,  2.8571,  5.0000,  9.0909, 12.5000, 15.3846, 17.8571},   // lifesteal.flat
            {100,  80, 11.1111, 20.0000, 38.4615, 55.5556, 71.4286, 86.2069},   // lifesteal.percent
            {150,  80, 16.6667, 30.0000, 57.6923, 83.3333,107.1429,129.3103},   // drop_rate.flat
            {500, 100, 45.4545, 83.3333,166.6667,250.0000,333.3333,416.6667},   // drop_rate.percent
            {100,  60, 14.2857, 25.0000, 45.4545, 62.5000, 76.9231, 89.2857},   // xp_gain.flat
            {500, 100, 45.4545, 83.3333,166.6667,250.0000,333.3333,416.6667},   // xp_gain.percent
        };
        int[] sample = {10, 20, 50, 100, 200, 500};
        String[] names = {"fixture.a.sat","fixture.b.sat","fixture.c.sat","fixture.d.sat",
                "fixture.e.sat","fixture.f.sat","fixture.g.sat","fixture.h.sat",
                "fixture.i.sat","fixture.j.sat","damage_reduction.flat","damage_reduction.percent",
                "fixture.m.sat","fixture.n.sat","fixture.o.sat","fixture.p.sat",
                "fixture.q.sat","fixture.r.sat","fixture.s.sat","fixture.t.sat"};
        for (int r = 0; r < sat.length; r++) {
            LineCfg cfg = new LineCfg(true, Curve.SATURATING, 0.0, sat[r][0], sat[r][1], 0.5, com.rpgleveling.api.Basis.TOTAL, 0.0, -1);
            for (int k = 0; k < sample.length; k++) {
                near(names[r] + "@" + sample[k], sat[r][2 + k], cfg.value(sample[k]), 5.0E-5);
            }
        }

        // ---------- DESIGN 1.4 combination examples, recomputed by hand ----------
        // Damage reduction is the only saturating dimension left: flat 60/80, percent 75/80
        // against B_ref 40. Every other dimension below is LINEAR, so the percent contribution
        // is B_ref * per_point * points / 100 instead of an asymptote.
        near("armor(20 flat,20 pct)", 4.3333336,
                RpgMath.assembleBaseline(0.0, RpgMath.linear(20, 0.15), 4.0, RpgMath.linear(20, 1.666667), 30.0), 1.0E-4);
        near("armor(100 flat,100 pct)", 21.666668,
                RpgMath.assembleBaseline(0.0, RpgMath.linear(100, 0.15), 4.0, RpgMath.linear(100, 1.666667), 30.0), 1.0E-4);
        near("dr(50 flat,50 pct)", 34.615385,
                RpgMath.assembleBaseline(0.0, RpgMath.saturating(50, 60.0, 80.0), 40.0,
                        RpgMath.saturating(50, 75.0, 80.0), 90.0), 1.0E-3);
        near("crit(50 flat,50 pct)", 54.16665,
                RpgMath.assembleBaseline(0.0, RpgMath.linear(50, 0.833333), 20.0, RpgMath.linear(50, 1.25), 100.0), 1.0E-3);
        near("xp(100 flat,100 pct)", 416.6667,
                RpgMath.assembleBaseline(0.0, RpgMath.linear(100, 1.666667), 50.0, RpgMath.linear(100, 5.0), 1000.0), 1.0E-3);
        // 100+100 points overshoot immunity (0.875 + 0.3333) and land on the FIXED 1.0 ceiling
        near("kb(100 flat,100 pct) clamped to immunity", 1.0,
                RpgMath.assembleBaseline(0.0, RpgMath.linear(100, 0.00875), 0.20, RpgMath.linear(100, 1.666667), 1.0), 1.0E-9);
        // saturating primitive spot checks (lead's originals, verbatim)
        near("SAT(10,cap=15,half=60)", 2.1429, RpgMath.saturating(10, 15.0, 60.0), 5.0E-5);
        near("SAT(100,cap=15,half=60)", 9.3750, RpgMath.saturating(100, 15.0, 60.0), 5.0E-5);
        near("SAT(10,cap=60,half=80)", 6.6667, RpgMath.saturating(10, 60.0, 80.0), 5.0E-5);
        near("SAT(100,cap=100,half=60)", 62.5000, RpgMath.saturating(100, 100.0, 60.0), 5.0E-5);

        // ---------- DESIGN 1.14.9 : TOTAL vs DESIGN_BASELINE cross-check ----------
        // Primitive fixtures: the inputs below are hand-picked (several come from earlier
        // design revisions) so the two assembly formulas keep being validated independently
        // of whatever the shipped line table currently says.
        // TOTAL: final = (base + V_flat) * (1 + V_pct/100)
        near("TOTAL attack(0,100)", 4.0000, RpgMath.assembleTotal(1.0, 0.0, 300.0, 2048.0), 1.0E-4);
        near("TOTAL max_health(0,100)", 60.0000, RpgMath.assembleTotal(20.0, 0.0, 200.0, 1024.0), 1.0E-4);
        near("TOTAL armor(20,20)", 4.6875, RpgMath.assembleTotal(0.0, 3.7500, 25.0000, 30.0), 1.0E-4);
        near("TOTAL armor(100,100)", 15.2344, RpgMath.assembleTotal(0.0, 9.3750, 62.5000, 30.0), 1.0E-4);
        near("TOTAL knockback(100,100)", 0.4375, RpgMath.assembleTotal(0.0, 0.2813, 55.5556, 1.0), 1.0E-3);
        near("TOTAL movement(100,100)", 0.7389, RpgMath.assembleTotal(0.1, 0.2500, 111.1111, 1.5), 1.0E-3);
        // DESIGN_BASELINE: final = base + V_flat + B_ref * V_pct / 100
        near("BASELINE crit(0,80) B=20", 10.0000, RpgMath.assembleBaseline(0.0, 0.0, 20.0, 50.0000, 100.0), 1.0E-4);
        near("BASELINE crit(0,200) B=20", 14.2857, RpgMath.assembleBaseline(0.0, 0.0, 20.0, 71.4286, 100.0), 1.0E-4);
        near("BASELINE crit(50,80) B=20", 32.7273, RpgMath.assembleBaseline(0.0, 22.7273, 20.0, 50.0000, 100.0), 1.0E-4);
        near("BASELINE crit_dmg(0,50) B=50", 50.0000, RpgMath.assembleBaseline(0.0, 0.0, 50.0, 100.0000, 1.0E9), 1.0E-4);
        near("BASELINE xp(0,100) B=50", 125.0000, RpgMath.assembleBaseline(0.0, 0.0, 50.0, 250.0000, 1000.0), 1.0E-4);
        near("BASELINE xp(100,100) B=50", 187.5000, RpgMath.assembleBaseline(0.0, 62.5000, 50.0, 250.0000, 1000.0), 1.0E-4);
        near("BASELINE dr(50,80) B=40", 33.0769, RpgMath.assembleBaseline(0.0, 23.0769, 40.0, 25.0000, 90.0), 1.0E-4);
        near("BASELINE lifesteal(0,80) B=10", 5.0000, RpgMath.assembleBaseline(0.0, 0.0, 10.0, 50.0000, 60.0), 1.0E-4);
        near("BASELINE drop(0,200) B=50", 166.6667, RpgMath.assembleBaseline(0.0, 0.0, 50.0, 333.3333, 1000.0), 1.0E-4);
        near("BASELINE regen(0,60) B=2", 2.0000, RpgMath.assembleBaseline(0.0, 0.0, 2.0, 100.0000, 20.0), 1.0E-4);
        // NaN / Infinity containment (lead's mine 2)
        if (RpgMath.assembleTotal(0.0, Double.NaN, 0.0, 100.0) != 0.0) fail("assembleTotal did not contain NaN");
        if (RpgMath.assembleBaseline(0.0, 0.0, Double.NaN, 0.0, 100.0) != 0.0) fail("assembleBaseline did not contain NaN");
        // 0 * Infinity is NaN, which must be contained rather than propagated
        if (RpgMath.assembleBaseline(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, 100.0) != 0.0) fail("assembleBaseline did not contain NaN from Inf");
        if (RpgMath.assembleBaseline(0.0, 0.0, 1.0E9, 1.0E9, 100.0) != 100.0) fail("assembleBaseline did not clamp to hardMax");
        if (RpgMath.assembleTotal(0.0, Double.POSITIVE_INFINITY, 0.0, 100.0) != 100.0) fail("assembleTotal did not clamp Infinity");
        checks += 4;

        // ---------- DESIGN 1.4 : per_point = old cap / old half (early game unchanged) ----------
        // The old saturating curve's tangent at x = 0 is cap/half; keeping exactly that slope is
        // what "remove the diminishing returns but keep the early-game feel" means numerically.
        // {oldCap, oldHalf, newPerPoint} for the 18 lines that were converted.
        double[][] slopeTable = {
            {   6.0,  40.0, 0.15        },   // armor.flat
            { 100.0,  60.0, 1.666667    },   // armor.percent
            {   5.0,  40.0, 0.125       },   // armor_toughness.flat
            { 100.0,  60.0, 1.666667    },   // armor_toughness.percent
            {   0.4,  60.0, 0.006666667 },   // movement_speed.flat
            { 200.0,  80.0, 2.5         },   // movement_speed.percent
            {  0.35,  40.0, 0.00875     },   // knockback_resistance.flat
            { 100.0,  60.0, 1.666667    },   // knockback_resistance.percent
            {   5.0,  40.0, 0.125       },   // health_regen.flat
            { 200.0,  60.0, 3.333333    },   // health_regen.percent
            {  50.0,  60.0, 0.833333    },   // crit_chance.flat
            { 100.0,  80.0, 1.25        },   // crit_chance.percent
            {  20.0,  60.0, 0.333333    },   // lifesteal.flat
            { 100.0,  80.0, 1.25        },   // lifesteal.percent
            { 150.0,  80.0, 1.875       },   // drop_rate.flat
            { 500.0, 100.0, 5.0         },   // drop_rate.percent
            { 100.0,  60.0, 1.666667    },   // xp_gain.flat
            { 500.0, 100.0, 5.0         },   // xp_gain.percent
        };
        for (int r = 0; r < slopeTable.length; r++) {
            double oldCap = slopeTable[r][0];
            double oldHalf = slopeTable[r][1];
            double perPoint = slopeTable[r][2];
            near("linear[" + r + "] slope = old cap/half", oldCap / oldHalf, perPoint, 1.0E-6);
            near("linear[" + r + "] @200", perPoint * 200.0, RpgMath.linear(200, perPoint), 1.0E-9);
            // The old curve's first point can only differ from its tangent by the second-order
            // term 1/(1+half) - a wrong per_point shows up here immediately.
            double firstOldPoint = RpgMath.saturating(1, oldCap, oldHalf);
            checks++;
            if (Math.abs(firstOldPoint - perPoint) > perPoint / (oldHalf + 1.0) * 1.001) {
                fail("linear[" + r + "] first point drifted off the old curve: perPoint=" + perPoint
                        + " but SAT(1," + oldCap + "," + oldHalf + ")=" + firstOldPoint);
            }
        }
        System.out.println("  [info] LINEAR slope rule verified for " + slopeTable.length + " converted lines");

        // ---------- DESIGN 1.4 / 1.16 : the ONLY remaining safety proof ----------
        // sup = equipment_max + flatCap + B_ref * pctCap / 100 <= hardMax used to be checked for
        // nine DESIGN_BASELINE dimensions. Every one of them except damage_reduction is LINEAR
        // now: a LINEAR line has no ceiling and therefore NO safety proof at all. That is the
        // user's explicit choice ("cancel the growth limits"), not an oversight, and DESIGN 1.16
        // states it plainly. Only damage reduction is still provable:
        //   0 (equipment) + 60 (flat cap) + 40 * 75/100 (percent cap) = 90 = hardMax
        // an equality, so the comparison needs an epsilon rather than a strict >.
        double drSup = 0.0 + 60.0 + 40.0 * 75.0 / 100.0;
        near("sup(damage_reduction)", 90.0, drSup, 1.0E-12);
        checks++;
        if (drSup > 90.0 + 1.0E-9) fail("sup(damage_reduction) = " + drSup + " exceeds hardMax 90");
        // ...and it is asymptotic, not merely clamped: 10 000 points in BOTH lines still land
        // strictly below 90, so a player can never become immune.
        double drBoth = RpgMath.saturating(10_000, 60.0, 80.0)
                + 40.0 * RpgMath.saturating(10_000, 75.0, 80.0) / 100.0;
        near("damage reduction @10000+10000", 89.2857143, drBoth, 1.0E-6);
        checks++;
        if (!(drBoth < 90.0)) fail("damage reduction reached its 90% asymptote at 10000 points: " + drBoth);
        System.out.println("  [info] safety proof: damage_reduction only - every other dimension is LINEAR by design");

        // ---------- properties: monotone, bounded, never NaN ----------
        for (int r = 0; r < sat.length; r++) {
            LineCfg cfg = new LineCfg(true, Curve.SATURATING, 0.0, sat[r][0], sat[r][1], 0.5, com.rpgleveling.api.Basis.TOTAL, 0.0, -1);
            double prev = -1.0;
            for (int k = 1; k <= 200000; k = (k < 1000) ? k * 2 : k + 50000) {
                double v = cfg.value(k);
                if (!(v > prev)) fail(names[r] + " not strictly monotone at k=" + k + " (" + prev + " -> " + v + ")");
                if (v >= sat[r][0]) fail(names[r] + " reached its cap at k=" + k);
                prev = v;
            }
            checks++;
        }
        // inverse of the saturating curve
        near("inverseSAT(0.9, 60)", 540.0, RpgMath.inverseSaturating(0.9, 60.0), 1.0E-9);
        near("inverseSAT(0.5, 60)", 60.0, RpgMath.inverseSaturating(0.5, 60.0), 1.0E-9);
        near("inverseSAT(0.25, 60)", 20.0, RpgMath.inverseSaturating(0.25, 60.0), 1.0E-9);

        // ---------- E-03 monotonicity of the level curve ----------
        long prevNeed = -1;
        for (int L = 1; L <= 10000; L++) {
            long n = RpgMath.xpNeededForLevel(L, c);
            if (n < prevNeed) fail("need(" + L + ") decreased: " + prevNeed + " -> " + n);
            prevNeed = n;
        }
        checks++;
        // continuity at the seam
        eq("seam need(200)", 4080L, RpgMath.xpNeededForLevel(200, c));
        eq("seam need(201)", 4504L, RpgMath.xpNeededForLevel(201, c));

        // ---------- E-05 : deep level must be cheap and consistent ----------
        long tA = System.nanoTime();
        long deepCum = RpgMath.cumulativeXpAtLevel(1_000_000, c);
        long tB = System.nanoTime();
        int deepLevel = RpgMath.levelForXp(deepCum, c);
        long tC = System.nanoTime();
        int deepLevel2 = RpgMath.levelForXp(deepCum - 1, c);
        long tD = System.nanoTime();
        System.out.printf("  [info] cum(1e6) %.1f ms | lvl(A) %.1f ms | lvl(A-1) %.1f ms%n",
                (tB - tA) / 1.0E6, (tC - tB) / 1.0E6, (tD - tC) / 1.0E6);
        long t0 = tA; long t1 = tD;
        eq("levelForXp(cumXp(1e6))", 1_000_000, deepLevel);
        eq("levelForXp(cumXp(1e6)-1)", 999_999, deepLevel2);
        double ms = (t1 - t0) / 1.0E6;
        System.out.printf("  [info] deep-level probe (build + 2 lookups) took %.1f ms%n", ms);
        if (ms > 500.0) fail("deep level computation too slow: " + ms + " ms");

        // ---------- LONG overflow guard ----------
        eq("levelForXp(0)", 0, RpgMath.levelForXp(0L, c));
        eq("need(0)", 0L, RpgMath.xpNeededForLevel(0, c));
        eq("cumXp(0)", 0L, RpgMath.cumulativeXpAtLevel(0, c));
        long th0 = System.nanoTime();
        int huge = RpgMath.levelForXp(Long.MAX_VALUE / 4, c);
        long th1 = System.nanoTime();
        if (huge <= 0) fail("huge totalXp produced a non-positive level");
        System.out.printf("  [info] levelForXp(Long.MAX_VALUE/4) = %d in %.2f ms%n", huge, (th1 - th0) / 1.0E6);
        if (RpgMath.levelForXp(1L << 31, c) <= 0) fail("2^31 xp produced a non-positive level");

        // ---- T11 layout invariants (RpgLayout is Minecraft-free on purpose) ----
        int[] layout = LayoutSelfCheck.run();
        checks += layout[0];
        failures += layout[1];

        System.out.println();
        System.out.println("RpgMath self check: " + (checks - failures) + "/" + checks + " passed, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static void eq(String what, long expected, long actual) {
        checks++;
        if (expected != actual) fail(what + ": expected " + expected + " but got " + actual);
    }

    private static void near(String what, double expected, double actual, double tol) {
        checks++;
        if (Double.isNaN(actual) || Math.abs(expected - actual) > tol) {
            fail(what + ": expected " + expected + " but got " + actual + " (tol " + tol + ")");
        }
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("  FAIL " + msg);
    }
}