package com.rpgleveling.gametest;

import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.stat.DerivedStats;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 3: a respec returns every point and leaves <b>no</b> modifier behind -
 * neither the active slot nor the other basis' slot.
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class RespecSuite {

    private RespecSuite() {}

    private static final int PER_LINE = 20;

    @GameTest(template = "empty", timeoutTicks = 400, batch = "respec")
    public static void respecClearsEveryLineAndEveryModifier(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        GameTestSupport.Report report = new GameTestSupport.Report();

        for (StatLine line : StatLine.VALUES) {
            RpgService.setPoints(player, line, PER_LINE);
        }
        report.check(data.spentPoints() == (long) PER_LINE * StatLine.COUNT,
                "setup failed: spent=" + data.spentPoints());

        // Every line must actually be present before the respec, otherwise the negative
        // assertion below would pass vacuously.
        for (StatLine line : StatLine.VALUES) {
            AttributeInstance instance = GameTestSupport.attribute(player, line.stat);
            report.check(instance != null, "attribute missing for " + line.stat.id);
            if (instance == null) continue;
            report.check(instance.getModifier(line.modifierId()) != null
                            || instance.getModifier(line.pctBasisModifierId()) != null,
                    "setup: no modifier present for " + line.id());
        }

        report.check(RpgService.respec(player), "respec was rejected");

        for (StatLine line : StatLine.VALUES) {
            report.check(data.points(line) == 0, "points not cleared for " + line.id() + ": " + data.points(line));
            AttributeInstance instance = GameTestSupport.attribute(player, line.stat);
            if (instance == null) continue;
            report.check(instance.getModifier(line.modifierId()) == null,
                    "residual " + line.modifierId() + " on " + line.id());
            report.check(instance.getModifier(line.pctBasisModifierId()) == null,
                    "residual " + line.pctBasisModifierId() + " on " + line.id());
        }
        for (Stat stat : Stat.VALUES) {
            AttributeInstance instance = GameTestSupport.attribute(player, stat);
            if (instance == null) continue;
            report.check(instance.getModifier(net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath("rpgleveling", "override_" + stat.id)) == null,
                    "residual override modifier on " + stat.id);
            // Back to the untouched player base.
            report.near("base restored(" + stat.id + ")", stat.playerBase, instance.getValue(), GameTestSupport.EPS);
        }

        report.check(data.spentPoints() == 0L, "spent points after respec: " + data.spentPoints());
        report.check(data.unspentPoints() == data.earnedPoints(),
                "points not fully returned: unspent=" + data.unspentPoints() + " earned=" + data.earnedPoints());

        // Idempotence: five more respecs must not change anything or throw.
        for (int i = 0; i < 5; i++) {
            RpgService.respec(player);
        }
        for (StatLine line : StatLine.VALUES) {
            report.check(data.points(line) == 0, "repeat respec changed " + line.id());
            AttributeInstance instance = GameTestSupport.attribute(player, line.stat);
            if (instance != null) {
                report.check(instance.getModifier(line.modifierId()) == null, "repeat respec left " + line.id());
                report.check(instance.getModifier(line.pctBasisModifierId()) == null, "repeat respec left pctbasis " + line.id());
            }
        }
        report.check(data.level() >= 0, "respec corrupted the level");

        report.assertAllPassed(helper, "RespecSuite");
        helper.succeed();
    }

    /** A respec must not touch experience or level (design 8.6). */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "respec")
    public static void respecKeepsExperienceAndLevel(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        RpgService.setLevel(player, 42);
        RpgService.setPoints(player, StatLine.ARMOR_FLAT, 15);
        long xpBefore = data.totalXp();
        int levelBefore = data.level();

        RpgService.respec(player);

        helper.assertTrue(data.totalXp() == xpBefore, "respec changed totalXp");
        helper.assertTrue(data.level() == levelBefore, "respec changed the level");
        helper.assertTrue(data.spentPoints() == 0L, "respec left points spent");
        helper.assertTrue(DerivedStats.finalValue(player, Stat.ARMOR) < 1.0E-9,
                "armor still boosted after respec");
        helper.succeed();
    }
}