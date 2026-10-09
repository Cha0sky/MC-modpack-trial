package com.evolvingblade.growth;

/**
 * 阶段计算与饱和算术。纯 Java（零 Minecraft 依赖），是本模组最关键的纯逻辑，
 * 由 {@code StageCalculatorTest} 覆盖全部 24 个阶段边界、联合门槛与溢出。
 *
 * <p>语义（来自 SPEC）：
 * <ul>
 *   <li>到达某阶段要求成长点与 Boss 击杀数<b>同时</b>达到该阶段门槛；</li>
 *   <li>取满足条件的最大阶段；</li>
 *   <li>未选择模式的玩家停留在阶段 0（但仍累积原始计数）；</li>
 *   <li>计数溢出时饱和到 {@link Long#MAX_VALUE}，绝不回绕成负数。</li>
 * </ul>
 */
public final class StageCalculator {

    private StageCalculator() {}

    /**
     * 计算满足"双门槛"的最大阶段下标（0..{@value GrowthMode#STAGE_COUNT}-1）。
     *
     * <p>负输入按 0 处理（防御性：任何畸形数据都不应让 tick 崩溃）。
     * 阶段 0 的门槛默认为 0，因此结果至少为 0。
     */
    public static int stageFor(long points, long bossKills, GrowthThresholds thresholds) {
        if (thresholds == null) {
            return 0;
        }
        long safePoints = Math.max(0L, points);
        long safeBosses = Math.max(0L, bossKills);
        int stage = 0;
        int limit = Math.min(GrowthMode.STAGE_COUNT, thresholds.size());
        for (int candidate = 0; candidate < limit; candidate++) {
            boolean pointGate = safePoints >= thresholds.pointAt(candidate);
            boolean bossGate = safeBosses >= thresholds.bossAt(candidate);
            if (pointGate && bossGate) {
                stage = candidate;
            }
        }
        return stage;
    }

    /**
     * 下一阶段还差多少成长点；已满级返回 {@code 0}。
     *
     * <p>这不是"只差成长点"的意思：同时也要看 {@link #bossesForNextStage}。
     * UI 需要同时展示两个门槛。
     */
    public static long pointsForNextStage(long points, long bossKills, GrowthThresholds thresholds) {
        int stage = stageFor(points, bossKills, thresholds);
        if (stage >= GrowthMode.STAGE_COUNT - 1 || thresholds == null) {
            return 0L;
        }
        return remaining(thresholds.pointAt(stage + 1), Math.max(0L, points));
    }

    /** 下一阶段还差多少 Boss 击杀；已满级返回 {@code 0}。 */
    public static long bossesForNextStage(long points, long bossKills, GrowthThresholds thresholds) {
        int stage = stageFor(points, bossKills, thresholds);
        if (stage >= GrowthMode.STAGE_COUNT - 1 || thresholds == null) {
            return 0L;
        }
        return remaining(thresholds.bossAt(stage + 1), Math.max(0L, bossKills));
    }

    private static long remaining(long required, long current) {
        if (required <= current) {
            return 0L;
        }
        long delta = required - current;
        return delta < 0L ? Long.MAX_VALUE : delta;
    }

    /**
     * 非负饱和加法。任何溢出都饱和到 {@link Long#MAX_VALUE}，绝不回绕。
     */
    public static long saturatedAdd(long left, long right) {
        long result = left + right;
        // 只有同号相加才可能溢出；溢出时符号位与任一操作数不同。
        if (((left ^ result) & (right ^ result)) < 0L) {
            return left < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * 非负饱和乘法。任何溢出都饱和到 {@link Long#MAX_VALUE}。
     */
    public static long saturatedMultiply(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        long result = left * right;
        if (result / left != right) {
            return Long.MAX_VALUE;
        }
        // 处理 Long.MIN_VALUE 相关的边界（非负输入不会走到，但保持函数自洽）。
        if (left == Long.MIN_VALUE || right == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    /**
     * 由原始计数按配置权重重算总成长点；负数输入视为 0，溢出饱和。
     */
    public static long computePoints(long hostileKills, long hostilePointWeight, long bossKills, long bossPointWeight) {
        long safeHostile = Math.max(0L, hostileKills);
        long safeBoss = Math.max(0L, bossKills);
        long safeHostileWeight = Math.max(0L, hostilePointWeight);
        long safeBossWeight = Math.max(0L, bossPointWeight);
        long fromHostile = saturatedMultiply(safeHostile, safeHostileWeight);
        long fromBoss = saturatedMultiply(safeBoss, safeBossWeight);
        return saturatedAdd(fromHostile, fromBoss);
    }

    /**
     * 拒绝畸形输入（负数）。用于命令与快照边界；调用方负责回显可读错误。
     */
    public static long requireNonNegative(String name, long value) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }
}
