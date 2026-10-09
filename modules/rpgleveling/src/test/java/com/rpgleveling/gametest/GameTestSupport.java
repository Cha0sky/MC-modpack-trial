package com.rpgleveling.gametest;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.stat.AttributeApplier;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Shared scaffolding for the GameTest suite.
 *
 * <p>Every check goes through the public service surface ({@code RpgService},
 * {@code AttributeApplier}, {@code DerivedStats}) and reads the result back from the
 * live {@code AttributeInstance} or the attachment, never from a private shortcut.</p>
 */
public final class GameTestSupport {

    /** Tolerance for double attribute comparisons (VERIFICATION 5.0.4 asks for 1e-3). */
    public static final double EPS = 1.0e-6;

    private GameTestSupport() {}

    /** A mock server player with a real embedded connection, reset to a clean slate. */
    public static ServerPlayer join(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RpgService.resetPlayer(player);
        // GameTestHelper#makeMockServerPlayerInLevel hardcodes isCreative() == true, and
        // Player#hurt refuses every hit while abilities.invulnerable is set. The effect
        // layer can only be measured on a damageable player.
        player.getAbilities().invulnerable = false;
        player.setHealth(player.getMaxHealth());
        return player;
    }

    public static PlayerRpgData data(ServerPlayer player) {
        return player.getData(RpgAttachments.PLAYER_DATA);
    }

    public static AttributeInstance attribute(ServerPlayer player, Stat stat) {
        return player.getAttribute(stat.carrier.holder());
    }

    /** Collects failures so a loop can report every offender instead of just the first. */
    public static final class Report {
        private final List<String> failures = new ArrayList<>();
        private int checks;

        public void check(boolean condition, String message) {
            checks++;
            if (!condition) failures.add(message);
        }

        public void near(String what, double expected, double actual, double tolerance) {
            checks++;
            if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
                failures.add(String.format(Locale.ROOT, "%s: expected %.6f but got %.6f", what, expected, actual));
            }
        }

        public int checks() {
            return checks;
        }

        public void assertAllPassed(GameTestHelper helper, String context) {
            helper.assertTrue(failures.isEmpty(),
                    context + ": " + failures.size() + "/" + checks + " checks failed -> " + failures);
        }

        public void assertAllPassed(GameTestHelper helper, String context, List<String> extraFailures) {
            failures.addAll(extraFailures);
            assertAllPassed(helper, context);
        }
    }

    /** Point table with a single line invested, for isolating one line at a time. */
    public static int[] onlyLine(StatLine line, int points) {
        int[] points2 = new int[StatLine.COUNT];
        points2[line.index()] = points;
        return points2;
    }

    /**
     * The modifier id that must carry this line under the currently configured basis.
     * {@code <line>} for flat and TOTAL, {@code <line>_pctbasis} for DESIGN_BASELINE.
     */
    public static net.minecraft.resources.ResourceLocation activeModifierId(StatLine line, LineCfg cfg) {
        if (line.mode == com.rpgleveling.api.Mode.PERCENT
                && cfg.basis() == com.rpgleveling.api.Basis.DESIGN_BASELINE) {
            return line.pctBasisModifierId();
        }
        return line.modifierId();
    }

    /** The modifier id that must be absent (the other basis' slot). */
    public static net.minecraft.resources.ResourceLocation inactiveModifierId(StatLine line, LineCfg cfg) {
        if (line.mode == com.rpgleveling.api.Mode.PERCENT
                && cfg.basis() == com.rpgleveling.api.Basis.DESIGN_BASELINE) {
            return line.modifierId();
        }
        return line.pctBasisModifierId();
    }

    public static LineCfg[] cfgs() {
        return RpgConfig.snapshot();
    }

    /** Recomputes and re-applies everything, as a real mutation would. */
    public static void refresh(ServerPlayer player) {
        AttributeApplier.apply(player, data(player));
    }

    /** Expected value of one dimension with only the given point table applied. */
    public static double expectedValue(Stat stat, int[] points) {
        return DerivedStats.computedValue(stat, points, cfgs());
    }

    /** Expected contribution of one line. */
    public static double lineValue(StatLine line, int points) {
        return RpgMath.lineValue(points, cfgs()[line.index()]);
    }

    public static String describeModifiers(AttributeInstance instance) {
        StringBuilder sb = new StringBuilder("[");
        for (AttributeModifier modifier : instance.getModifiers()) {
            sb.append(modifier.id()).append('=').append(modifier.amount())
                    .append('/').append(modifier.operation()).append(' ');
        }
        return sb.append(']').toString();
    }

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** Structured evidence line for the GameTest log, mirroring the /rpg stats contract. */
    public static void evidence(String key, Object value) {
        LOG.info("RPGVERIFY {}={}", key, value);
    }
}