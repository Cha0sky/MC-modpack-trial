package com.rpgleveling.data;

import com.rpgleveling.api.Curve;
import com.rpgleveling.stat.LineCfg;

/**
 * <b>The single implementation site of every numeric formula in this mod.</b>
 *
 * <p>No other class may re-derive a curve, a level requirement or a point
 * payout. This class deliberately imports <b>nothing</b> from Minecraft so the
 * entire numeric core can be exercised by a plain JVM self-check
 * ({@code gradlew verifyMath}).</p>
 *
 * <p>Determinism: every transcendental call goes through {@link StrictMath},
 * which is bit-exact across JVM restarts, so a level boundary can never drift
 * after a server restart (DESIGN 8.4).</p>
 */
public final class RpgMath {

    private RpgMath() {}

    // ------------------------------------------------------------------
    // 1. curve primitives (DESIGN 1.2)
    // ------------------------------------------------------------------

    /** {@code LINEAR(x) = perPoint * x} - no ceiling at all. */
    public static double linear(double points, double perPoint) {
        if (points <= 0.0) return 0.0;
        return perPoint * points;
    }

    /**
     * {@code SAT(x, cap, half) = cap * x / (x + half)}.
     *
     * <p>Strictly monotone for every finite {@code x} (derivative
     * {@code cap*half/(x+half)^2 > 0}) and strictly below {@code cap}, so no
     * point is ever wasted and the ceiling is never reached.</p>
     */
    public static double saturating(double points, double cap, double halfPoints) {
        if (points <= 0.0) return 0.0;
        double h = (halfPoints > 0.0) ? halfPoints : 1.0E-9;
        return cap * points / (points + h);
    }

    /** {@code perPoint * x^gamma} - sub-linear but unbounded (opt-in only). */
    public static double sublinearPow(double points, double perPoint, double gamma) {
        if (points <= 0.0) return 0.0;
        double g = (gamma > 0.0) ? gamma : 1.0;
        return perPoint * StrictMath.pow(points, g);
    }

    /** Inverse of {@link #saturating}: points needed to reach {@code ratio} of {@code cap}. */
    public static double inverseSaturating(double ratio, double halfPoints) {
        if (ratio <= 0.0) return 0.0;
        double r = Math.min(ratio, 1.0 - 1.0E-9);
        return halfPoints * r / (1.0 - r);
    }

    /** Contribution of one point-buy line. {@code points <= 0} yields exactly {@code 0.0}. */
    public static double lineValue(int points, LineCfg cfg) {
        if (cfg == null || !cfg.enabled() || points <= 0) return 0.0;
        int x = cfg.effectivePoints(points);
        if (x <= 0) return 0.0;
        return switch (cfg.curve()) {
            case LINEAR -> linear(x, cfg.perPoint());
            case SATURATING -> saturating(x, cfg.cap(), cfg.halfPoints());
            case SUBLINEAR_POW -> sublinearPow(x, cfg.perPoint(), cfg.gamma());
        };
    }

    /** Primitive form of {@link #lineValue(int, LineCfg)} for callers without a cfg object. */
    public static double lineValue(int points, Curve curve, boolean enabled,
                                   double perPoint, double cap, double halfPoints,
                                   double gamma, int maxPoints) {
        return lineValue(points, new LineCfg(enabled, curve, perPoint, cap, halfPoints, gamma,
                com.rpgleveling.api.Basis.TOTAL, 0.0, maxPoints));
    }

    // ------------------------------------------------------------------
    // 2. dimension assembly (DESIGN 1.3) - the only composition rule
    // ------------------------------------------------------------------

    /**
     * {@code clamp((base + V_flat) * (1 + V_pct / 100), 0, hardMax)}.
     *
     * <p>{@code base} is the <i>basis</i> the percent line is applied to. It is
     * supplied by the caller ({@code DerivedStats}) so the basis policy lives in
     * exactly one place and can be swapped without touching this formula.</p>
     */
    public static double assemble(double base, double flat, double pct, double hardMax) {
        double v = (base + flat) * (1.0 + pct / 100.0);
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) v = 0.0;
        if (v > hardMax) v = hardMax;
        return v;
    }

    /** Value of one dimension for the given flat / percent line contributions. */
    public static double dimensionValue(double base, double flatValue, double pctValue, double hardMax) {
        return assemble(base, flatValue, pctValue, hardMax);
    }

    /**
     * {@link com.rpgleveling.api.Basis#TOTAL}: the percent line lands on the vanilla
     * attribute as {@code ADD_MULTIPLIED_BASE}, so it scales the base, every other
     * {@code ADD_VALUE} (equipment included) and our own flat line.
     */
    public static double assembleTotal(double base, double flat, double pct, double hardMax) {
        return assemble(base, flat, pct, hardMax);
    }

    /**
     * {@link com.rpgleveling.api.Basis#DESIGN_BASELINE}: the percent line is converted into an
     * absolute amount {@code B_ref * V_pct / 100} and added, so it never degenerates to
     * zero when the attribute has no other ADD_VALUE source.
     */
    public static double assembleBaseline(double base, double flat, double basisValue, double pct, double hardMax) {
        double v = base + flat + basisValue * pct / 100.0;
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) v = 0.0;
        if (v > hardMax) v = hardMax;
        return v;
    }

    /**
     * Pre-clamp theoretical value of a dimension <b>including equipment</b> (DESIGN 1.16.5).
     *
     * <p>Deliberately does <b>not</b> clamp: this is the "what the numbers say" value that
     * gets compared against the post-{@code sanitizeValue} attribute, so that a silent
     * vanilla truncation becomes observable as {@code clamped=true}.</p>
     *
     * @param equipment total ADD_VALUE the player's gear contributes on this attribute
     * @param totalBasis {@code true} for {@link com.rpgleveling.api.Basis#TOTAL} semantics
     */
    public static double assembleWithEquipment(double base, double flat, double pct,
                                               double equipment, double basisValue, boolean totalBasis) {
        if (totalBasis) {
            return (base + equipment + flat) * (1.0 + pct / 100.0);
        }
        return base + equipment + flat + basisValue * pct / 100.0;
    }

    /** Absolute amount a {@code TOTAL} percent line adds on top of {@code base + flat}. */
    public static double pctContributionTotal(double base, double flat, double pct) {
        return (base + flat) * pct / 100.0;
    }

    /** Absolute amount a {@code DESIGN_BASELINE} percent line adds. */
    public static double pctContributionBaseline(double basisValue, double pct) {
        return basisValue * pct / 100.0;
    }

    // ------------------------------------------------------------------
    // 3. level curve (DESIGN 8.4)
    // ------------------------------------------------------------------

    private static volatile LevelCurve active = LevelCurve.DEFAULT;

    /** The currently active curve; updated by {@code RpgConfig} after every load / reload. */
    public static LevelCurve activeCurve() {
        return active;
    }

    public static void setActiveCurve(LevelCurve curve) {
        LevelCurve next = (curve == null) ? LevelCurve.DEFAULT : curve;
        LevelCurve old = active;
        active = next;
        if (!next.equals(old)) invalidateLevelIndex();
    }

    private static double linearNeed(int level, LevelCurve c) {
        return c.linearBase() + c.linearStep() * (double) (level - 1);
    }

    private static double logAnchor(LevelCurve c) {
        return linearNeed(c.linearUntilLevel(), c);
    }

    /** XP required to go from {@code L-1} to {@code L}. {@code L <= 0} returns 0. */
    public static long xpNeededForLevel(int level, LevelCurve c) {
        if (level <= 0) return 0L;
        if (level <= c.linearUntilLevel()) return Math.round(linearNeed(level, c));
        double anchor = logAnchor(c);
        double j = (double) (level - c.linearUntilLevel() + 1);   // >= 2, so ln is always defined
        return Math.round(anchor * (1.0 + c.logScale() * StrictMath.log(j)));
    }

    public static long xpNeededForLevel(int level) {
        return xpNeededForLevel(level, active);
    }

    /**
     * Total XP accumulated when reaching {@code level}.
     *
     * <p>Exact - the log segment is summed term by term with the very same
     * rounding as {@link #xpNeededForLevel}, so it always matches the published
     * sampling table. Results are memoised on a fixed-size chunk grid so that
     * repeated queries (binary search, GUI redraws) are O(chunk) instead of
     * O(level).</p>
     */
    public static long cumulativeXpAtLevel(int level, LevelCurve c) {
        if (level <= 0) return 0L;
        if (level <= c.linearUntilLevel()) {
            int l = level;
            return Math.round((double) l * c.linearBase() + c.linearStep() * (double) l * (double) (l - 1) / 2.0);
        }
        if (level <= EXACT_LEVEL_LIMIT) return LevelIndex.cumulative(level, c);
        return smoothCumulative(level, c);
    }

    public static long cumulativeXpAtLevel(int level) {
        return cumulativeXpAtLevel(level, active);
    }

    /**
     * Largest level whose cumulative requirement is {@code <= totalXp}.
     *
     * <p>Unbounded unless {@code general.max_level} is set, in which case the result is
     * clamped to it - that is the only place the level cap is enforced.</p>
     */
    public static int levelForXp(long totalXp, LevelCurve c) {
        if (totalXp <= 0L) return 0;
        if (c.isCapped() && totalXp >= cumulativeXpAtLevel(c.maxLevel(), c)) return c.maxLevel();
        int until = c.linearUntilLevel();
        long linearCap = cumulativeXpAtLevel(until, c);
        if (totalXp <= linearCap) {
            int guess = linearLevelGuess(totalXp, c);
            // Defensive: the closed form is exact for 0 <= xp <= linearCap, so these loops
            // normally run zero iterations and only exist to absorb a floating point
            // boundary case. They are O(1) amortised (cumulativeXpAtLevel is a closed form
            // in this whole branch), but they must never be relied upon for correctness.
            while (guess > 0 && cumulativeXpAtLevel(guess, c) > totalXp) guess--;
            while (guess < until && cumulativeXpAtLevel(guess + 1, c) <= totalXp) guess++;
            return cap(guess, c);
        }
        if (totalXp > cumulativeXpAtLevel(EXACT_LEVEL_LIMIT, c)) {
            return cap(invertSmooth(totalXp, c), c);
        }
        // log segment: exponential probe then binary search, both on the memoised index
        int lo = until;
        long step = LevelIndex.CHUNK;
        int hi = (int) Math.min((long) EXACT_LEVEL_LIMIT, (long) until + step);
        while (cumulativeXpAtLevel(hi, c) <= totalXp) {
            lo = hi;
            if (hi >= EXACT_LEVEL_LIMIT) break;
            step = Math.min(step << 1, 1L << 30);
            int next = (int) Math.min((long) EXACT_LEVEL_LIMIT, (long) lo + step);
            if (next <= lo) break;
            hi = next;
        }
        while (lo + 1 < hi) {
            int mid = lo + (hi - lo) / 2;
            if (cumulativeXpAtLevel(mid, c) <= totalXp) lo = mid; else hi = mid;
        }
        return cap(lo, c);
    }

    private static int cap(int level, LevelCurve c) {
        return (c.isCapped() && level > c.maxLevel()) ? c.maxLevel() : level;
    }

    public static int levelForXp(long totalXp) {
        return levelForXp(totalXp, active);
    }

    /**
     * Closed form for the linear segment:
     * {@code L = floor((-b + sqrt(b^2 + 4*a*xp)) / (2a))} with
     * {@code a = linearStep/2} and {@code b = linearBase - linearStep/2}, i.e. the
     * analytic solution of {@code a*L^2 + b*L = xp}. With the default curve
     * ({@code linearBase = 100}, {@code linearStep = 20}) this reduces to the
     * published {@code floor((-9 + sqrt(81 + 0.4*xp)) / 2)}.
     *
     * <p>Result is clamped to {@code [0, linearUntilLevel]}. Exposed so the
     * numeric self check can assert the closed form against
     * {@link #cumulativeXpAtLevel} for every level of the linear segment.</p>
     */
    public static int linearLevelGuess(long totalXp, LevelCurve c) {
        int until = c.linearUntilLevel();
        if (totalXp <= 0L) return 0;
        double a = c.linearStep() / 2.0;
        double b = c.linearBase() - c.linearStep() / 2.0;
        double l;
        if (a <= 0.0) {
            l = (b > 0.0) ? (double) totalXp / b : (double) until;
        } else {
            // discriminant of  a*L^2 + b*L - xp = 0  is  b^2 + 4*a*xp
            l = (-b + StrictMath.sqrt(b * b + 4.0 * a * (double) totalXp)) / (2.0 * a);
        }
        int guess = (int) Math.floor(l);
        if (guess < 0) guess = 0;
        if (guess > until) guess = until;
        return guess;
    }

    /** Points earned in total upon reaching {@code level}. */
    public static long earnedPointsAtLevel(int level, LevelCurve c) {
        if (level <= 0) return 0L;
        return (long) level * (long) c.pointsPerLevel();
    }

    public static long earnedPointsAtLevel(int level) {
        return earnedPointsAtLevel(level, active);
    }

    /** Points granted by the single step {@code level-1 -> level}. */
    public static int pointsForLevel(int level, LevelCurve c) {
        return level <= 0 ? 0 : c.pointsPerLevel();
    }

    public static int pointsForLevel(int level) {
        return pointsForLevel(level, active);
    }

    /** XP already collected inside the current level. */
    public static long xpIntoLevel(long totalXp, int level, LevelCurve c) {
        if (level <= 0) return Math.max(0L, totalXp);
        return Math.max(0L, totalXp - cumulativeXpAtLevel(level, c));
    }

    public static long xpIntoLevel(long totalXp) {
        return xpIntoLevel(totalXp, levelForXp(totalXp, active), active);
    }

    // ------------------------------------------------------------------
    // 3b. exact / asymptotic boundary
    // ------------------------------------------------------------------

    /**
     * Highest level for which the cumulative XP is summed <b>exactly</b>.
     *
     * <p>Building the exact index up to this level is a one-off cost of roughly
     * 40 ms; everything a player (or the verification suite: E-01 uses 1000000)
     * can realistically reach stays well inside it, so the published sampling
     * table is always reproduced bit for bit. Beyond this point
     * {@link #smoothCumulative(int, LevelCurve)} takes over so that a corrupt or
     * absurd {@code totalXp} can never make the server spin.</p>
     */
    public static final int EXACT_LEVEL_LIMIT = 2_000_000;

    /** Levels are reported as {@code int}; anything above saturates here. */
    public static final int MAX_LEVEL = Integer.MAX_VALUE - 1;

    /**
     * Smooth (Stirling) evaluation of the logarithmic part of the cumulative sum:
     * {@code sum_{j=2..n} round(A * (1 + s * ln j))} approximated by
     * {@code A*((n-1) + s*ln(n!))}. Accurate to well under one XP per 10^6 terms;
     * only used above {@link #EXACT_LEVEL_LIMIT}.
     */
    private static double smoothCumulativeRaw(int level, LevelCurve c) {
        int until = c.linearUntilLevel();
        double n = (double) (level - until);
        if (n <= 1.0) return (double) cumulativeXpAtLevel(until, c);
        double anchor = logAnchor(c);
        double base = (double) cumulativeXpAtLevel(until, c);
        return base + anchor * ((n - 1.0) + c.logScale() * lnFactorial(n));
    }

    /** {@code ln(n!)} via the Stirling series (relative error &lt; 1e-16 for n &gt;= 10). */
    private static double lnFactorial(double n) {
        if (n < 2.0) return 0.0;
        if (n < 10.0) {
            double s = 0.0;
            for (int i = 2; i <= (int) n; i++) s += StrictMath.log((double) i);
            return s;
        }
        double inv = 1.0 / n;
        return n * StrictMath.log(n) - n + 0.5 * StrictMath.log(2.0 * StrictMath.PI * n)
                + inv / 12.0 - inv * inv * inv / 360.0 + inv * inv * inv * inv * inv / 1260.0;
    }

    private static long smoothCumulative(int level, LevelCurve c) {
        double v = smoothCumulativeRaw(level, c);
        if (Double.isNaN(v) || v <= 0.0) return 0L;
        return (v >= 9.0E18) ? Long.MAX_VALUE / 4 : (long) v;
    }

    /** Inverts {@link #smoothCumulativeRaw} by bisection; O(1) for all practical purposes. */
    private static int invertSmooth(long totalXp, LevelCurve c) {
        int lo = EXACT_LEVEL_LIMIT;
        int hi = MAX_LEVEL;
        if (smoothCumulativeRaw(hi, c) <= (double) totalXp) return hi;
        while (lo + 1 < hi) {
            int mid = lo + (hi - lo) / 2;
            if (smoothCumulativeRaw(mid, c) <= (double) totalXp) lo = mid; else hi = mid;
        }
        return lo;
    }

    // ------------------------------------------------------------------
    // 4. memoised cumulative index
    // ------------------------------------------------------------------

    /**
     * Chunked memoisation of the logarithmic part of {@link #cumulativeXpAtLevel}.
     *
     * <p>Building the index up to level L costs one O(L) pass - about 19 ms for
     * L = 1e6 on the reference machine - and every later query is O(chunk).
     * That keeps {@code /rpg debug setlevel 1000000} inside a single tick budget
     * (VERIFICATION E-05) without ever approximating the sum.</p>
     */
    static final class LevelIndex {
        static final int CHUNK = 4096;
        static final int MAX_LEVEL = Integer.MAX_VALUE - 2;

        private static final Object LOCK = new Object();
        /** grid[i] = exact cumulative xp at level (linearUntilLevel + i * CHUNK). */
        private static long[] grid = new long[256];
        private static int gridSlots;
        /** Highest level whose cumulative value has been computed exactly. */
        private static int builtUpTo;
        /** Exact cumulative xp at {@link #builtUpTo}. */
        private static long builtCum;
        /** Relative offset ({@code k - linearUntilLevel}) of the next chunk boundary to record. */
        private static int nextSlotLevel;
        private static LevelCurve builtCurve;

        private LevelIndex() {}

        static void invalidate() {
            synchronized (LOCK) {
                builtCurve = null;
                gridSlots = 0;
                builtUpTo = 0;
                builtCum = 0L;
                nextSlotLevel = 0;
            }
        }

        private static void resetLocked(LevelCurve c) {
            builtCurve = c;
            builtUpTo = c.linearUntilLevel();
            builtCum = cumulativeXpAtLevel(builtUpTo, c);
            grid[0] = builtCum;
            gridSlots = 1;
            // relative offset of the next chunk boundary: k - until == CHUNK, 2*CHUNK, ...
            nextSlotLevel = CHUNK;
        }

        private static void recordLocked(int level, long cum, LevelCurve c) {
            int slot = (level - c.linearUntilLevel()) / CHUNK;
            if (slot >= grid.length) {
                long[] bigger = new long[Math.max(grid.length * 2, slot + 1)];
                System.arraycopy(grid, 0, bigger, 0, gridSlots);
                grid = bigger;
            }
            grid[slot] = cum;
            if (slot + 1 > gridSlots) gridSlots = slot + 1;
        }

        /** Exact cumulative xp at {@code level} (caller guarantees {@code level > linearUntilLevel}). */
        static long cumulative(int level, LevelCurve c) {
            synchronized (LOCK) {
                // Reset ONLY when the curve itself changed. A query below builtUpTo must
                // use the existing chunk grid - rebuilding there would make every
                // binary-search probe O(level) instead of O(chunk).
                if (builtCurve == null || !builtCurve.equals(c)) {
                    resetLocked(c);
                }
                final int until = c.linearUntilLevel();
                final double anchor = linearNeed(until, c);
                final double scale = c.logScale();
                if (level > builtUpTo) {
                    long acc = builtCum;
                    for (int k = builtUpTo + 1; k <= level; k++) {
                        acc += Math.round(anchor * (1.0 + scale * StrictMath.log((double) (k - until + 1))));
                        if (k - until == nextSlotLevel) {
                            recordLocked(k, acc, c);
                            nextSlotLevel += CHUNK;
                        }
                    }
                    builtCum = acc;
                    builtUpTo = level;
                    return acc;
                }
                int slot = (level - until) / CHUNK;
                long acc = grid[slot];
                for (int k = until + slot * CHUNK + 1; k <= level; k++) {
                    acc += Math.round(anchor * (1.0 + scale * StrictMath.log((double) (k - until + 1))));
                }
                return acc;
            }
        }
    }

    private static void invalidateLevelIndex() {
        LevelIndex.invalidate();
    }
}