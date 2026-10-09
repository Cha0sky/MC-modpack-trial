package com.rpgleveling.gametest;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Curve;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The <b>curve policy</b> suite (user decision, DESIGN 1.4).
 *
 * <p>The user removed the growth limit from every dimension except damage reduction:
 * 24 of the 26 lines are now {@link Curve#LINEAR} with
 * {@code perPoint = old cap / old half_points}, which keeps the early-game feel of the
 * old saturating curve while deleting the diminishing returns. Only damage reduction
 * still converges, asymptotically, to 90%.</p>
 *
 * <p>These tests pin the <b>numbers</b>, not the code that produces them: the expected
 * table below is transcribed from DESIGN 1.4 and recomputed by hand, so an accidental
 * edit to {@code StatLine} shows up as a red test instead of a silent balance change.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class CurvePolicySuite {

    private CurvePolicySuite() {}

    /**
     * Line -> {old saturating cap, old half_points}. The new {@code per_point} of a
     * LINEAR line must equal {@code cap / half}, because that is the derivative of
     * {@code SAT(x) = cap*x/(x+half)} at {@code x = 0} - i.e. the first point must pay
     * exactly what it used to pay.
     */
    private static Map<StatLine, double[]> oldSaturatingLines() {
        Map<StatLine, double[]> m = new LinkedHashMap<>();
        m.put(StatLine.ARMOR_FLAT, new double[] { 6.0, 40.0 });
        m.put(StatLine.ARMOR_PCT, new double[] { 100.0, 60.0 });
        m.put(StatLine.ARMOR_TOUGHNESS_FLAT, new double[] { 5.0, 40.0 });
        m.put(StatLine.ARMOR_TOUGHNESS_PCT, new double[] { 100.0, 60.0 });
        m.put(StatLine.MOVEMENT_SPEED_FLAT, new double[] { 0.4, 60.0 });
        m.put(StatLine.MOVEMENT_SPEED_PCT, new double[] { 200.0, 80.0 });
        m.put(StatLine.KNOCKBACK_RESIST_FLAT, new double[] { 0.35, 40.0 });
        m.put(StatLine.KNOCKBACK_RESIST_PCT, new double[] { 100.0, 60.0 });
        m.put(StatLine.HEALTH_REGEN_FLAT, new double[] { 5.0, 40.0 });
        m.put(StatLine.HEALTH_REGEN_PCT, new double[] { 200.0, 60.0 });
        m.put(StatLine.CRIT_CHANCE_FLAT, new double[] { 50.0, 60.0 });
        m.put(StatLine.CRIT_CHANCE_PCT, new double[] { 100.0, 80.0 });
        m.put(StatLine.LIFESTEAL_FLAT, new double[] { 20.0, 60.0 });
        m.put(StatLine.LIFESTEAL_PCT, new double[] { 100.0, 80.0 });
        m.put(StatLine.DROP_RATE_FLAT, new double[] { 150.0, 80.0 });
        m.put(StatLine.DROP_RATE_PCT, new double[] { 500.0, 100.0 });
        m.put(StatLine.XP_GAIN_FLAT, new double[] { 100.0, 60.0 });
        m.put(StatLine.XP_GAIN_PCT, new double[] { 500.0, 100.0 });
        return m;
    }

    /** Requirement: the growth limit is gone everywhere except damage reduction. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "curve-policy")
    public static void onlyDamageReductionKeepsACeiling(GameTestHelper helper) {
        GameTestSupport.Report report = new GameTestSupport.Report();
        LineCfg[] live = RpgConfig.snapshot();
        int saturating = 0;
        for (StatLine line : StatLine.VALUES) {
            // the live snapshot must agree with the shipped default; a config file written
            // by an older build is the one way this can silently differ
            report.check(live[line.index()].curve() == line.curve,
                    "live config curve for " + line.id() + " is " + live[line.index()].curve()
                            + " but the design says " + line.curve
                            + " - delete/regenerate rpgleveling-common.toml");
            boolean isSaturating = line.curve == Curve.SATURATING;
            if (isSaturating) saturating++;
            if (line.stat == Stat.DAMAGE_REDUCTION) {
                report.check(isSaturating, line.id() + " must keep the saturating curve but is " + line.curve);
            } else {
                // Not merely "not saturating": the removed-limit decision means LINEAR,
                // and SUBLINEAR_POW is unbounded too but is not what was agreed.
                report.check(line.curve == Curve.LINEAR,
                        line.id() + " must be LINEAR (the growth limit is gone) but is " + line.curve);
            }
        }
        report.check(saturating == 2, "exactly the two damage_reduction lines may saturate, found " + saturating);
        GameTestSupport.evidence("curve.saturatingLines", saturating);
        report.assertAllPassed(helper, "onlyDamageReductionKeepsACeiling");
        helper.succeed();
    }

    /**
     * Requirement: {@code per_point = old cap / old half} - the first point still pays
     * exactly what the old curve paid, so the early game feels unchanged.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "curve-policy")
    public static void everyLinearLineKeepsTheOldInitialSlope(GameTestHelper helper) {
        GameTestSupport.Report report = new GameTestSupport.Report();
        for (var entry : oldSaturatingLines().entrySet()) {
            StatLine line = entry.getKey();
            double cap = entry.getValue()[0];
            double half = entry.getValue()[1];
            double slope = cap / half;
            report.check(line.curve == Curve.LINEAR, line.id() + " must be LINEAR but is " + line.curve);
            report.near(line.id() + ".per_point", slope, line.defPerPoint, 1.0E-6);
            // and the curve really is per_point * x, with no cap in sight. This is the
            // SHIPPED DEFAULT under test - the live config is checked separately below and
            // in armorFlatIsLinearAndFollowsTheLiveLimit.
            double perPoint = line.defPerPoint;
            report.near(line.id() + " linear(1)", perPoint, RpgMath.linear(1, perPoint), 1.0E-12);
            report.near(line.id() + " linear(1000)", perPoint * 1000.0,
                    RpgMath.linear(1000, perPoint), 1.0E-9);
            report.near(line.id() + " linear(1e6)", perPoint * 1.0E6,
                    RpgMath.linear(1_000_000, perPoint), 1.0E-6);
        }
        report.check(report.checks() > 50, "expected the whole table to be checked, ran " + report.checks());
        report.assertAllPassed(helper, "everyLinearLineKeepsTheOldInitialSlope");
        helper.succeed();
    }

    /**
     * <b>New case 1:</b> ARMOR_FLAT is linear - 200 points are worth
     * {@code 200 * 0.15 = 30} armour (not the old {@code SAT(200, 6, 40) = 5}), and the
     * value the player actually gets is {@code min(raw, attribute limit)} - i.e. it
     * follows the live range, so AttributeFix lifts it automatically.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "curve-policy")
    public static void armorFlatIsLinearAndFollowsTheLiveLimit(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();
        LineCfg cfg = RpgConfig.snapshot()[StatLine.ARMOR_FLAT.index()];

        // The LIVE config must carry the design value - a stale config file left over from
        // an earlier build would silently keep the old curve, which is the defect this
        // whole change is about. Fail loudly rather than measuring the wrong thing.
        report.check(cfg.curve() == Curve.LINEAR,
                "live config for armor.flat is " + cfg.curve() + " - regenerate rpgleveling-common.toml");
        report.near("live config armor.flat.per_point", 0.15, cfg.perPoint(), 1.0E-9);

        RpgService.setPoints(player, StatLine.ARMOR_FLAT, 200);
        double contributed = RpgMath.lineValue(200, cfg);
        report.near("ARMOR_FLAT line value @200", 30.0, contributed, 1.0E-9);
        report.near("ARMOR_FLAT line value @400 (linear)", 60.0, RpgMath.lineValue(400, cfg), 1.0E-9);

        // The old saturating curve paid 5.0 at 200 points; proving the value moved away
        // from it is what would go red if the curve were flipped back.
        double oldValue = RpgMath.saturating(200, 6.0, 40.0);
        report.check(Math.abs(contributed - oldValue) > 1.0,
                "ARMOR_FLAT still behaves like SAT(200,6,40)=" + oldValue + " instead of 30");

        double limit = Stat.ARMOR.hardMax();
        report.near("armour @200 follows the live limit", Math.min(30.0, limit),
                DerivedStats.finalValue(player, Stat.ARMOR), 1.0E-6);
        GameTestSupport.evidence("armor.limit", limit);
        GameTestSupport.evidence("armor.at200", DerivedStats.finalValue(player, Stat.ARMOR));

        // Second investment, on a second player so the first measurement is untouched:
        // 400 points = 60 raw armour, which the vanilla range (30) truncates and a raised
        // range does not. The clamped flag must follow the live limit, exactly.
        ServerPlayer big = GameTestSupport.join(helper);
        RpgService.setPoints(big, StatLine.ARMOR_FLAT, 400);
        double raw = 60.0;
        double effective = DerivedStats.finalValue(big, Stat.ARMOR);
        report.near("armour @400 follows the live limit", Math.min(raw, limit), effective, 1.0E-6);
        report.check(DerivedStats.isClamped(big, Stat.ARMOR) == (raw > limit + 1.0E-9),
                "the clamped flag must track the attribute's real ceiling (raw=" + raw + " limit=" + limit
                        + " clamped=" + DerivedStats.isClamped(big, Stat.ARMOR) + ")");
        GameTestSupport.evidence("armor.at400", effective);
        GameTestSupport.evidence("armor.at400.clamped", DerivedStats.isClamped(big, Stat.ARMOR));

        report.assertAllPassed(helper, "armorFlatIsLinearAndFollowsTheLiveLimit");
        helper.succeed();
    }

    /**
     * <b>New case 2:</b> damage reduction is asymptotic - 10 000 points in each line
     * still leave the player <b>strictly</b> below 90%, so nobody can ever become immune.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "curve-policy")
    public static void damageReductionStaysStrictlyBelowNinety(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();

        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, 10_000);
        double flatOnly = DerivedStats.finalValue(player, Stat.DAMAGE_REDUCTION);
        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, 0);
        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_PCT, 10_000);
        double pctOnly = DerivedStats.finalValue(player, Stat.DAMAGE_REDUCTION);
        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, 10_000);
        double both = DerivedStats.finalValue(player, Stat.DAMAGE_REDUCTION);

        GameTestSupport.evidence("dr.flat@10000", flatOnly);
        GameTestSupport.evidence("dr.pct@10000", pctOnly);
        GameTestSupport.evidence("dr.both@10000", both);

        report.check(flatOnly < 90.0, "damage reduction flat line reached 90%: " + flatOnly);
        report.check(pctOnly < 90.0, "damage reduction percent line reached 90%: " + pctOnly);
        report.check(both < 90.0, "damage reduction reached 90% with both lines maxed: " + both);
        // ...and it really is asymptotic, not just broken: both lines together land at
        // 60*10000/10080 + 40*(75*10000/10080)/100 = 89.2857
        report.near("damage reduction @10000+10000", 89.2857143, both, 1.0E-5);
        report.near("damage reduction ceiling", 90.0, Stat.DAMAGE_REDUCTION.hardMax(), 1.0E-9);
        report.assertAllPassed(helper, "damageReductionStaysStrictlyBelowNinety");
        helper.succeed();
    }

    /**
     * The three functional ceilings are intentional and must stay {@code FIXED}: past
     * 100% crit chance and past 1.0 knockback resistance nothing changes, and damage
     * reduction can never reach 90.
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "curve-policy")
    public static void functionalCeilingsAreFixedAndBite(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();

        expectFixed(report, Stat.DAMAGE_REDUCTION, 90.0);
        expectFixed(report, Stat.CRIT_CHANCE, 100.0);
        expectFixed(report, Stat.KNOCKBACK_RESIST, 1.0);

        // Crit chance: points are still accepted, the effect is capped and the GUI marker
        // (isEffectivelyCapped) must light up even though the 1.0E6-wide custom attribute
        // itself was never truncated by vanilla.
        RpgService.setPoints(player, StatLine.CRIT_CHANCE_FLAT, 200);
        double critRaw = RpgMath.lineValue(200, RpgConfig.snapshot()[StatLine.CRIT_CHANCE_FLAT.index()]);
        report.near("crit raw @200", 166.6666, critRaw, 1.0E-3);
        report.near("crit effective @200", 100.0, DerivedStats.finalValue(player, Stat.CRIT_CHANCE), 1.0E-9);
        report.check(DerivedStats.isCappedByModCeiling(player, Stat.CRIT_CHANCE),
                "crit chance above 100 is not reported as capped");
        report.check(DerivedStats.isEffectivelyCapped(player, Stat.CRIT_CHANCE),
                "the GUI marker predicate missed a capped crit chance");

        // Knockback resistance: past 1.0 is immunity.
        RpgService.setPoints(player, StatLine.KNOCKBACK_RESIST_FLAT, 2_000);
        report.near("knockback effective @2000", 1.0,
                DerivedStats.finalValue(player, Stat.KNOCKBACK_RESIST), 1.0E-9);
        report.check(DerivedStats.isEffectivelyCapped(player, Stat.KNOCKBACK_RESIST),
                "the GUI marker predicate missed capped knockback resistance");

        // The LIVE dimensions must mirror the attribute's own range, live.
        for (Stat stat : Stat.VALUES) {
            if (stat.limitMode != Stat.LimitMode.LIVE) continue;
            Attribute attribute = stat.carrier.holder().value();
            report.check(attribute instanceof RangedAttribute,
                    stat.id + " is LIVE but its attribute is not a RangedAttribute");
            if (attribute instanceof RangedAttribute ranged) {
                report.near("live limit(" + stat.id + ")", ranged.getMaxValue(), stat.hardMax(), 1.0E-9);
            }
        }

        report.assertAllPassed(helper, "functionalCeilingsAreFixedAndBite");
        helper.succeed();
    }

    /**
     * T17: the mod-imposed movement speed ceiling (1.5x) is gone as well - MOVEMENT_SPEED
     * is a LIVE dimension now, so only the attribute's own range can truncate it. The old
     * runtime clamp {@code movement_speed.hard_cap_attr} defaults to 0 = disabled.
     *
     * <p>What a fast player will meet instead is the vanilla "moved too quickly!" pull-back,
     * a server mechanic that does not read the attribute - see DESIGN 1.7.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "curve-policy")
    public static void movementSpeedIsNoLongerCappedAtOnePointFive(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        GameTestSupport.Report report = new GameTestSupport.Report();

        report.check(!RpgConfig.movementSpeedHardCapEnabled(),
                "the mod-side movement speed clamp is still enabled: " + RpgConfig.movementSpeedHardCap());

        RpgService.setPoints(player, StatLine.MOVEMENT_SPEED_FLAT, 400);
        double raw = 0.1 + 400 * 0.006666667;   // player base + LINEAR contribution
        double limit = Stat.MOVEMENT_SPEED.hardMax();
        double effective = DerivedStats.finalValue(player, Stat.MOVEMENT_SPEED);
        GameTestSupport.evidence("speed.raw", DerivedStats.rawAttributeValue(player, Stat.MOVEMENT_SPEED));
        GameTestSupport.evidence("speed.effective", effective);
        GameTestSupport.evidence("speed.limit", limit);

        report.check(raw > 1.5, "test setup: 400 points should exceed the removed 1.5 ceiling, raw=" + raw);
        report.near("movement speed follows the attribute range", Math.min(raw, limit), effective, 1.0E-6);
        report.check(!DerivedStats.isClamped(player, Stat.MOVEMENT_SPEED),
                "movement speed is still truncated: " + DerivedStats.rawAttributeValue(player, Stat.MOVEMENT_SPEED)
                        + " -> " + effective);
        report.assertAllPassed(helper, "movementSpeedIsNoLongerCappedAtOnePointFive");
        helper.succeed();
    }

    private static void expectFixed(GameTestSupport.Report report, Stat stat, double expected) {
        report.check(stat.limitMode == Stat.LimitMode.FIXED,
                stat.id + " must keep a FIXED functional ceiling but is " + stat.limitMode);
        report.near("hardMax(" + stat.id + ")", expected, stat.hardMax(), 1.0E-9);
    }
}
