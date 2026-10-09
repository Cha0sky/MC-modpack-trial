package com.evolvingblade.growth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.evolvingblade.boss.BossRules;

/**
 * 一份<b>已经过校验</b>的服务器配置快照。<b>纯 Java，零 Minecraft 依赖</b>，
 * 因此阈值组合、阶段数值与非法配置回落都可以在纯 JVM 单元测试里覆盖。
 *
 * <p>设计原则：配置文件可以写错，但 {@code GrowthSettings} 永远自洽——
 * 非法项回落到默认值并记录问题，绝不把 NaN / 无穷 / 越界值带进 tick 逻辑。
 */
public record GrowthSettings(
        long hostilePointWeight,
        long bossPointWeight,
        Map<GrowthMode, GrowthThresholds> thresholdsByMode,
        List<Double> stageAttackDamage,
        List<Double> stageAttackSpeed,
        Abilities abilities,
        BossRules bossRules,
        HostileRules hostileRules) {

    /** 玩家自身的基础攻击力（1.21.1 {@code Player.createAttributes()} 实测为 1.0）。 */
    public static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0D;
    /** 玩家自身的基础攻击速度（1.21.1 属性默认值 4.0）。 */
    public static final double PLAYER_BASE_ATTACK_SPEED = 4.0D;

    public GrowthSettings {
        thresholdsByMode = Map.copyOf(thresholdsByMode);
        stageAttackDamage = List.copyOf(stageAttackDamage);
        stageAttackSpeed = List.copyOf(stageAttackSpeed);
        hostilePointWeight = Math.max(0L, hostilePointWeight);
        bossPointWeight = Math.max(0L, bossPointWeight);
    }

    public static GrowthSettings defaults() {
        Map<GrowthMode, GrowthThresholds> thresholds = new EnumMap<>(GrowthMode.class);
        for (GrowthMode mode : GrowthMode.values()) {
            thresholds.put(mode, mode.defaultThresholds());
        }
        return new GrowthSettings(
                1L,
                100L,
                thresholds,
                List.of(7.0D, 12.0D, 24.0D, 48.0D, 120.0D, 300.0D, 750.0D, 1500.0D),
                List.of(1.6D, 1.7D, 1.8D, 1.9D, 2.0D, 2.1D, 2.2D, 2.4D),
                Abilities.defaults(),
                BossRules.defaults(),
                HostileRules.defaults());
    }

    /** 某模式的门槛；缺失时回落到该模式的出厂默认值。 */
    public GrowthThresholds thresholds(GrowthMode mode) {
        if (mode == null) {
            return GrowthMode.STANDARD.defaultThresholds();
        }
        GrowthThresholds thresholds = this.thresholdsByMode.get(mode);
        return thresholds != null ? thresholds : mode.defaultThresholds();
    }

    /** 第 {@code stage} 阶段的"总基础攻击"（含玩家自身的 1.0）。 */
    public double stageAttackDamage(int stage) {
        return valueAt(this.stageAttackDamage, stage, 7.0D);
    }

    /** 第 {@code stage} 阶段的"总攻击速度"（每秒攻击次数）。 */
    public double stageAttackSpeed(int stage) {
        return valueAt(this.stageAttackSpeed, stage, 1.6D);
    }

    /**
     * 物品需要提供的攻击力修饰值 = 阶段总攻击 − 玩家基础 1.0。
     * 阶段数值本身已经包含玩家基础，绝不能再多加一次 +1。
     */
    public double stageDamageModifier(int stage) {
        return this.stageAttackDamage(stage) - PLAYER_BASE_ATTACK_DAMAGE;
    }

    /** 物品需要提供的攻速修饰值 = 阶段总攻速 − 属性基础 4.0（通常为负）。 */
    public double stageAttackSpeedModifier(int stage) {
        return this.stageAttackSpeed(stage) - PLAYER_BASE_ATTACK_SPEED;
    }

    private static double valueAt(List<Double> values, int index, double fallback) {
        if (values.isEmpty()) {
            return fallback;
        }
        int clamped = Math.max(0, Math.min(values.size() - 1, index));
        Double value = values.get(clamped);
        return value != null && Double.isFinite(value) ? value : fallback;
    }

    public GrowthSettings withThresholds(GrowthMode mode, GrowthThresholds thresholds) {
        Map<GrowthMode, GrowthThresholds> copy = new EnumMap<>(this.thresholdsByMode);
        copy.put(mode, thresholds);
        return new GrowthSettings(this.hostilePointWeight, this.bossPointWeight, copy,
                this.stageAttackDamage, this.stageAttackSpeed, this.abilities, this.bossRules, this.hostileRules);
    }

    /** 自检；空列表表示完全合法。 */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (this.hostilePointWeight < 0L) {
            problems.add("hostile_point_weight must be >= 0");
        }
        if (this.bossPointWeight < 0L) {
            problems.add("boss_point_weight must be >= 0");
        }
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds thresholds = this.thresholdsByMode.get(mode);
            if (thresholds == null) {
                problems.add("missing thresholds for mode " + mode.id());
            } else {
                for (String problem : thresholds.validate()) {
                    problems.add(mode.id() + ": " + problem);
                }
            }
        }
        checkStageValues("stage_attack_damage", this.stageAttackDamage, false, problems);
        checkStageValues("stage_attack_speed", this.stageAttackSpeed, false, problems);
        problems.addAll(this.bossRules.validate());
        return List.copyOf(problems);
    }

    public boolean isValid() {
        return validate().isEmpty();
    }

    // ------------------------------------------------------------------
    // 校验工具：配置层用它把任意（可能被手改坏的）输入收敛成合法快照。
    // ------------------------------------------------------------------

    /**
     * 长度必须为 {@value GrowthMode#STAGE_COUNT}；全部为有限数；可选要求 &gt; 0。
     * 不满足时回落到 {@code fallback} 并把问题写入 {@code problems}。
     */
    public static List<Double> sanitizeStageValues(String name, List<Double> candidate, List<Double> fallback,
            boolean requirePositive, List<String> problems) {
        if (candidate == null || candidate.size() != GrowthMode.STAGE_COUNT) {
            problems.add(name + " must have exactly " + GrowthMode.STAGE_COUNT + " entries, using defaults");
            return List.copyOf(fallback);
        }
        List<Double> sanitized = new ArrayList<>(GrowthMode.STAGE_COUNT);
        boolean ok = true;
        for (int i = 0; i < candidate.size(); i++) {
            Double value = candidate.get(i);
            if (value == null || !Double.isFinite(value) || (requirePositive && value <= 0.0D)) {
                problems.add(name + "[" + i + "] is not a valid " + (requirePositive ? "positive" : "finite") + " number");
                ok = false;
                break;
            }
            sanitized.add(value);
        }
        if (!ok) {
            return List.copyOf(fallback);
        }
        return List.copyOf(sanitized);
    }

    /** 非负权重；负数或超界回落到默认值。 */
    public static long sanitizeWeight(String name, long candidate, long fallback, long max, List<String> problems) {
        if (candidate < 0L || candidate > max) {
            problems.add(name + " must be within [0, " + max + "] but was " + candidate);
            return fallback;
        }
        return candidate;
    }

    /** 冷却/上限类整数：越界回落到默认值。 */
    public static int sanitizeInt(String name, int candidate, int min, int max, int fallback, List<String> problems) {
        if (candidate < min || candidate > max) {
            problems.add(name + " must be within [" + min + ", " + max + "] but was " + candidate);
            return fallback;
        }
        return candidate;
    }

    /** 有限 double：NaN / 无穷 / 越界回落到默认值。 */
    public static double sanitizeDouble(String name, double candidate, double min, double max, double fallback,
            List<String> problems) {
        if (!Double.isFinite(candidate) || candidate < min || candidate > max) {
            problems.add(name + " must be finite and within [" + min + ", " + max + "] but was " + candidate);
            return fallback;
        }
        return candidate;
    }

    private static void checkStageValues(String name, List<Double> values, boolean requirePositive, List<String> problems) {
        if (values.size() != GrowthMode.STAGE_COUNT) {
            problems.add(name + " must have exactly " + GrowthMode.STAGE_COUNT + " entries");
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null || !Double.isFinite(value) || (requirePositive && value <= 0.0D)) {
                problems.add(name + "[" + i + "] must be finite" + (requirePositive ? " and positive" : ""));
            }
        }
    }
}
