package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 阶段计算的核心测试：覆盖全部 24 个（3 模式 × 8 阶段）边界、双门槛联合判定、
 * 溢出饱和与畸形输入边界。
 */
class StageCalculatorTest {

    @Test
    @DisplayName("SPEC 默认门槛：三种模式的点/Boss 数组逐项一致")
    void defaultThresholdsMatchSpec() {
        assertEquals(List.of(0L, 10L, 30L, 70L, 140L, 240L, 400L, 600L), GrowthMode.QUICK.defaultPoints());
        assertEquals(List.of(0L, 0L, 0L, 1L, 1L, 2L, 3L, 5L), GrowthMode.QUICK.defaultBosses());
        assertEquals(List.of(0L, 50L, 150L, 350L, 700L, 1200L, 2000L, 3000L), GrowthMode.STANDARD.defaultPoints());
        assertEquals(List.of(0L, 0L, 0L, 1L, 3L, 6L, 12L, 20L), GrowthMode.STANDARD.defaultBosses());
        assertEquals(List.of(0L, 300L, 1000L, 2400L, 4800L, 8000L, 13000L, 20000L), GrowthMode.LONG.defaultPoints());
        assertEquals(List.of(0L, 0L, 0L, 5L, 15L, 30L, 60L, 100L), GrowthMode.LONG.defaultBosses());
    }

    @Test
    @DisplayName("全部 24 个阶段边界：恰好达标即到达该阶段")
    void allTwentyFourStageBoundaries() {
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds thresholds = mode.defaultThresholds();
            for (int stage = 0; stage < GrowthMode.STAGE_COUNT; stage++) {
                final int expectedStage = stage;
                final long points = thresholds.pointAt(stage);
                final long bosses = thresholds.bossAt(stage);
                int actual = StageCalculator.stageFor(points, bosses, thresholds);
                assertEquals(expectedStage, actual,
                        () -> "mode=" + mode.id() + " stage=" + expectedStage + " points=" + points + " bosses=" + bosses);
            }
        }
    }

    @Test
    @DisplayName("双门槛联合判定：任一实际生效的门槛差 1，都必须掉到该阶段以下")
    void bothGatesMustBeSatisfied() {
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds thresholds = mode.defaultThresholds();
            for (int stage = 1; stage < GrowthMode.STAGE_COUNT; stage++) {
                final int expectedStage = stage;
                final String modeId = mode.id();
                long points = thresholds.pointAt(stage);
                long bosses = thresholds.bossAt(stage);

                // 门槛为 0 时该维度还不是约束（例如快速模式 stage1 的 Boss 门槛就是 0），跳过。
                if (points > 0L) {
                    int missingPoints = StageCalculator.stageFor(points - 1L, bosses, thresholds);
                    assertTrue(missingPoints < expectedStage,
                            () -> modeId + " stage=" + expectedStage + ": 缺点数不应到达该阶段，实际=" + missingPoints);
                }
                if (bosses > 0L) {
                    int missingBosses = StageCalculator.stageFor(points, bosses - 1L, thresholds);
                    assertTrue(missingBosses < expectedStage,
                            () -> modeId + " stage=" + expectedStage + ": 缺 Boss 数不应到达该阶段，实际=" + missingBosses);
                }
            }
        }
    }

    @Test
    @DisplayName("门槛为 0 的维度不构成约束（快速模式 stage1 只看成长点）")
    void zeroGateIsNotAConstraint() {
        GrowthThresholds quick = GrowthMode.QUICK.defaultThresholds();
        assertEquals(0L, quick.bossAt(1), "快速模式 stage1 的 Boss 门槛应为 0");
        assertEquals(1, StageCalculator.stageFor(10L, 0L, quick));
        assertEquals(1, StageCalculator.stageFor(10L, -1L, quick));
        assertEquals(1, StageCalculator.stageFor(10L, 4L, quick));
        // 但 stage3 起 Boss 门槛变成 1，此时必须同时满足
        assertEquals(2, StageCalculator.stageFor(140L, 0L, quick));
        assertEquals(4, StageCalculator.stageFor(140L, 1L, quick));
    }

    @Test
    @DisplayName("门槛恰好前一点：必须停留在上一阶段或更低")
    void justBelowThreshold() {
        GrowthThresholds thresholds = GrowthMode.STANDARD.defaultThresholds();
        // 标准模式 stage2 = (150, 0)，stage3 = (350, 1)
        assertEquals(1, StageCalculator.stageFor(149L, 0L, thresholds));
        assertEquals(2, StageCalculator.stageFor(150L, 0L, thresholds));
        assertEquals(2, StageCalculator.stageFor(349L, 1L, thresholds));
        assertEquals(3, StageCalculator.stageFor(350L, 1L, thresholds));
        // 点数很多但 Boss 数为 0：不能越过需要 Boss 的阶段
        assertEquals(2, StageCalculator.stageFor(100_000L, 0L, thresholds));
        // Boss 很多但点数不足：同样不能越级
        assertEquals(0, StageCalculator.stageFor(0L, 1_000L, thresholds));
    }

    @Test
    @DisplayName("溢出：饱和到 Long.MAX_VALUE，绝不回绕")
    void overflowSaturates() {
        GrowthThresholds thresholds = GrowthMode.LONG.defaultThresholds();
        assertEquals(7, StageCalculator.stageFor(Long.MAX_VALUE, Long.MAX_VALUE, thresholds));
        assertEquals(Long.MAX_VALUE, StageCalculator.saturatedAdd(Long.MAX_VALUE, 1L));
        assertEquals(Long.MAX_VALUE, StageCalculator.saturatedAdd(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, StageCalculator.saturatedAdd(Long.MIN_VALUE, -1L));
        assertEquals(Long.MAX_VALUE, StageCalculator.saturatedMultiply(Long.MAX_VALUE, 2L));
        assertEquals(0L, StageCalculator.saturatedMultiply(0L, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, StageCalculator.computePoints(Long.MAX_VALUE, 2L, 0L, 100L));
        assertEquals(Long.MAX_VALUE, StageCalculator.computePoints(Long.MAX_VALUE, 1L, 1L, 1L));
        assertEquals(1_000_000L, StageCalculator.computePoints(10_000L, 100L, 0L, 100L));
    }

    @Test
    @DisplayName("畸形输入：负计数按 0 处理，显式边界校验抛异常")
    void malformedInputIsRejected() {
        GrowthThresholds thresholds = GrowthMode.QUICK.defaultThresholds();
        assertEquals(0, StageCalculator.stageFor(-1L, -1L, thresholds));
        assertEquals(0, StageCalculator.stageFor(-100L, 5L, thresholds));
        assertEquals(0L, StageCalculator.computePoints(-5L, 10L, -5L, 10L));
        assertThrows(IllegalArgumentException.class, () -> StageCalculator.requireNonNegative("hostileKills", -1L));
        assertEquals(0L, StageCalculator.requireNonNegative("hostileKills", 0L));
    }

    @Test
    @DisplayName("下一阶段差值：只在未满级时给出正数")
    void nextStageRemainders() {
        GrowthThresholds thresholds = GrowthMode.QUICK.defaultThresholds();
        assertEquals(10L, StageCalculator.pointsForNextStage(0L, 0L, thresholds));
        assertEquals(0L, StageCalculator.bossesForNextStage(0L, 0L, thresholds));
        assertEquals(1L, StageCalculator.bossesForNextStage(70L, 0L, thresholds));
        assertEquals(0L, StageCalculator.pointsForNextStage(600L, 5L, thresholds));
        assertEquals(0L, StageCalculator.bossesForNextStage(600L, 5L, thresholds));
        assertEquals(0L, StageCalculator.pointsForNextStage(Long.MAX_VALUE, Long.MAX_VALUE, thresholds));
    }

    @Test
    @DisplayName("门槛对象：长度/负数/非单调都会被 validate 拒绝，并可回落默认值")
    void thresholdValidation() {
        GrowthThresholds valid = GrowthMode.STANDARD.defaultThresholds();
        assertTrue(valid.isValid());
        assertTrue(valid.validate().isEmpty());

        GrowthThresholds wrongLength = new GrowthThresholds(
                List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertFalse(wrongLength.isValid());
        assertTrue(wrongLength.validate().stream().anyMatch(problem -> problem.contains("length")));

        GrowthThresholds negative = new GrowthThresholds(
                List.of(0L, -1L, 2L, 3L, 4L, 5L, 6L, 7L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertFalse(negative.isValid());

        GrowthThresholds unsorted = new GrowthThresholds(
                List.of(0L, 50L, 40L, 60L, 70L, 80L, 90L, 100L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        assertFalse(unsorted.isValid());
        assertTrue(unsorted.validate().stream().anyMatch(problem -> problem.contains("sorted")));

        assertEquals(valid, GrowthThresholds.sanitized(wrongLength, valid));
        assertEquals(valid, GrowthThresholds.sanitized(null, valid));
    }
}
