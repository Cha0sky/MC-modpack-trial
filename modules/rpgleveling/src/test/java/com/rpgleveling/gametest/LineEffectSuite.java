package com.rpgleveling.gametest;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 1: every one of the 26 point-buy lines really moves its attribute, and the
 * value the attribute reports equals what {@code RpgMath} / {@code DerivedStats} predict.
 *
 * <p>This is the exact channel {@code /rpg stats} calls {@code line.*.value} - the pure
 * formula output - compared against the live {@code AttributeInstance.getValue()}, so a
 * wrong curve, a wrong modifier id or a wrong Operation all show up here.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class LineEffectSuite {

    private LineEffectSuite() {}

    private static final int FLAT_POINTS = 37;
    private static final int PCT_POINTS = 41;

    @GameTest(template = "empty", timeoutTicks = 400, batch = "lines")
    public static void allTwentySixLinesReachTheirAttribute(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        LineCfg[] cfgs = RpgConfig.snapshot();
        GameTestSupport.Report report = new GameTestSupport.Report();

        for (StatLine line : StatLine.VALUES) {
            int points = (line.mode == Mode.FLAT) ? FLAT_POINTS : PCT_POINTS;
            RpgService.respec(player);
            RpgService.setPoints(player, line, points);

            AttributeInstance instance = GameTestSupport.attribute(player, line.stat);
            if (instance == null) {
                report.check(false, "attribute missing for " + line.stat.id);
                continue;
            }

            // The entity base must be the PLAYER base, never the Attribute default
            // (attack damage 1.0 not 2.0, movement speed 0.1 not 0.7).
            report.near("base(" + line.stat.id + ")", line.stat.playerBase, instance.getBaseValue(), GameTestSupport.EPS);

            int[] only = GameTestSupport.onlyLine(line, points);
            double predicted = DerivedStats.computedValue(line.stat, only, cfgs);
            double actual = instance.getValue();
            report.near("attr(" + line.id() + ")", predicted, actual, GameTestSupport.EPS);

            // The raw formula channel must be strictly positive for a positive investment
            // (axiom A3: no point is ever wasted).
            double raw = GameTestSupport.lineValue(line, points);
            report.check(raw > 0.0, "line value not positive for " + line.id() + " -> " + raw);

            // The modifier must sit in the slot the configured basis demands, with the
            // operation the design fixes: ADD_VALUE for flat / DESIGN_BASELINE, and
            // ADD_MULTIPLIED_BASE (never ADD_MULTIPLIED_TOTAL) for TOTAL.
            LineCfg cfg = cfgs[line.index()];
            AttributeModifier active = instance.getModifier(GameTestSupport.activeModifierId(line, cfg));
            report.check(active != null, "missing modifier for " + line.id()
                    + " (wanted " + GameTestSupport.activeModifierId(line, cfg) + ") in "
                    + GameTestSupport.describeModifiers(instance));
            if (active != null) {
                AttributeModifier.Operation expected = (line.mode == Mode.FLAT
                        || cfg.basis() == com.rpgleveling.api.Basis.DESIGN_BASELINE)
                        ? AttributeModifier.Operation.ADD_VALUE
                        : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                report.check(active.operation() == expected,
                        "wrong operation for " + line.id() + ": " + active.operation() + " != " + expected);
                report.check(active.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        "ADD_MULTIPLIED_TOTAL is banned but present on " + line.id());
            }
            AttributeModifier stale = instance.getModifier(GameTestSupport.inactiveModifierId(line, cfg));
            report.check(stale == null, "stale modifier from the other basis left on " + line.id());

            GameTestSupport.evidence("line." + line.id() + ".points", points);
            GameTestSupport.evidence("line." + line.id() + ".predicted", String.format(java.util.Locale.ROOT, "%.6f", predicted));
            GameTestSupport.evidence("line." + line.id() + ".actual", String.format(java.util.Locale.ROOT, "%.6f", actual));
        }

        helper.assertTrue(report.checks() >= StatLine.COUNT * 5,
                "expected at least " + (StatLine.COUNT * 5) + " checks, ran " + report.checks());
        report.assertAllPassed(helper, "LineEffectSuite");
        helper.succeed();
    }

    /** Flat and percent of the SAME dimension must compose, not overwrite each other. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "lines")
    public static void flatAndPercentOfOneDimensionCompose(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        LineCfg[] cfgs = RpgConfig.snapshot();
        GameTestSupport.Report report = new GameTestSupport.Report();

        for (var stat : com.rpgleveling.api.Stat.VALUES) {
            RpgService.respec(player);
            StatLine flat = stat.line(Mode.FLAT);
            StatLine pct = stat.line(Mode.PERCENT);
            RpgService.setPoints(player, flat, 25);
            RpgService.setPoints(player, pct, 25);

            int[] both = new int[StatLine.COUNT];
            both[flat.index()] = 25;
            both[pct.index()] = 25;
            double predicted = DerivedStats.computedValue(stat, both, cfgs);
            double actual = GameTestSupport.attribute(player, stat).getValue();
            report.near("compose(" + stat.id + ")", predicted, actual, GameTestSupport.EPS);

            // Both slots occupied at once, never one replacing the other.
            AttributeInstance instance = GameTestSupport.attribute(player, stat);
            report.check(instance.getModifier(flat.modifierId()) != null, "flat slot empty for " + stat.id);
            report.check(instance.getModifier(GameTestSupport.activeModifierId(pct, cfgs[pct.index()])) != null,
                    "percent slot empty for " + stat.id);
        }
        report.assertAllPassed(helper, "flatAndPercentOfOneDimensionCompose");
        helper.succeed();
    }
}
