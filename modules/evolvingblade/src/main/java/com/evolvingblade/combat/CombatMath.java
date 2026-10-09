package com.evolvingblade.combat;

import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.growth.GrowthSettings;

/**
 * 战斗数值与射线的<b>纯计算</b>部分（零 Minecraft 依赖，可纯 JVM 单测）。
 *
 * <p>所有数值都来自服务器配置快照 {@link GrowthSettings}，绝不硬编码；
 * 阶段总攻击已包含玩家自身的 1.0，因此这里的"阶段基础攻击"直接用配置值。
 */
public final class CombatMath {

    /** 满蓄力阈值（SPEC：攻击强度 >= 0.9 视为满蓄力）。 */
    public static final float FULL_CHARGE_THRESHOLD = 0.9F;

    private CombatMath() {}

    /** 满蓄力判定：必须在原版重置攻击计时机之前采样。 */
    public static boolean isFullyCharged(float attackStrengthScale) {
        return attackStrengthScale >= FULL_CHARGE_THRESHOLD;
    }

    /**
     * 剑气伤害 = 系数 × 阶段基础攻击（<b>只用于"关闭继承"的 1.0.0 回退口径</b>）。
     *
     * <p>1.1.0 默认走
     * {@link EffectiveDamage#abilityDamage(com.evolvingblade.growth.GrowthSettings, int, double, double, boolean)}：
     * {@code 系数 × 服务端有效持剑 ATTACK_DAMAGE}。{@code 1.0} 是比例而非阶段天花板，
     * 因此正加伤会真的增强技能。
     */
    public static double waveDamage(GrowthSettings settings, int stage) {
        return settings.abilities().waveDamageMultiplier() * settings.stageAttackDamage(stage);
    }

    /** 爆发伤害 = 系数 × 阶段基础攻击（同样只是"关闭继承"的回退口径）。 */
    public static double burstDamage(GrowthSettings settings, int stage) {
        return settings.abilities().burstDamageMultiplier() * settings.stageAttackDamage(stage);
    }

    /**
     * 剑气伤害（按服务端有效攻击取值）。
     *
     * @param effectiveAttack 持有者服务端有效 {@code ATTACK_DAMAGE}（非正/非法时回落到阶段基础值）
     */
    public static double waveDamage(GrowthSettings settings, int stage, double effectiveAttack, boolean inherit) {
        return EffectiveDamage.abilityDamage(settings, stage, settings.abilities().waveDamageMultiplier(),
                effectiveAttack, inherit);
    }

    /** 爆发伤害（按服务端有效攻击取值）。 */
    public static double burstDamage(GrowthSettings settings, int stage, double effectiveAttack, boolean inherit) {
        return EffectiveDamage.abilityDamage(settings, stage, settings.abilities().burstDamageMultiplier(),
                effectiveAttack, inherit);
    }

    /**
     * 吸血量：按<b>实际损失的生命</b>计算，并按阶段上限截断。
     *
     * <p>{@code healthBefore} 必须是"本次主手伤害被接受之前"的生命值，
     * {@code healthAfter} 是原版结算之后的生命值；两者之差即真实损失
     * （护甲/吸收/免疫/取消都已体现在里面，过量击杀也被自然钳制）。
     *
     * @return 应当回复的生命值；无损失或非法输入返回 0
     */
    public static double lifestealAmount(GrowthSettings settings, int stage, double healthBefore, double healthAfter) {
        if (!Double.isFinite(healthBefore) || !Double.isFinite(healthAfter) || healthBefore <= 0.0D) {
            return 0.0D;
        }
        double lost = healthBefore - healthAfter;
        if (lost <= 0.0D) {
            return 0.0D;
        }
        double fraction = settings.abilities().lifestealFractionFor(stage);
        double cap = settings.abilities().lifestealCapFor(stage);
        return Math.min(lost * fraction, cap);
    }

    /** 斩 Boss 额外伤害 = 比例 × 目标最大生命，按阶段上限截断。 */
    public static double bossBonusDamage(GrowthSettings settings, int stage, double targetMaxHealth) {
        if (!Double.isFinite(targetMaxHealth) || targetMaxHealth <= 0.0D) {
            return 0.0D;
        }
        double fraction = settings.abilities().bossBonusFractionFor(stage);
        double cap = settings.abilities().bossBonusCapFor(stage);
        return Math.min(targetMaxHealth * fraction, cap);
    }

    /** 剑气冷却（终阶更短）。 */
    public static int waveCooldownTicks(GrowthSettings settings, int stage) {
        return settings.abilities().waveCooldownFor(stage);
    }

    /** 爆发冷却（终阶更短）。 */
    public static int burstCooldownTicks(GrowthSettings settings, int stage) {
        return settings.abilities().burstCooldownFor(stage);
    }

    /**
     * 剑气最多可命中的<b>父实体总数</b>（不是"额外穿透数"）：
     * 普通阶段 1 个（命中即停），终阶为配置的 {@code waveFinalPierce}（默认 3）。
     *
     * <p>调用方必须用 {@code hits >= maxTargets} 判定停止，否则会多命中一个目标。
     */
    public static int waveMaxTargets(GrowthSettings settings, int stage) {
        if (stage >= com.evolvingblade.growth.GrowthMode.STAGE_COUNT - 1) {
            return Math.max(1, settings.abilities().waveFinalPierce());
        }
        return 1;
    }

    /**
     * 沿射线采样：返回从近到远、间距为 {@code step} 的采样距离。
     *
     * <p>用于逐段做方块碰撞与目标检索，保证"墙壁阻挡、不穿墙"。
     */
    public static List<Double> raySampleDistances(double maxDistance, double step) {
        List<Double> distances = new ArrayList<>();
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0D || !Double.isFinite(step) || step <= 0.0D) {
            return distances;
        }
        for (double d = step; d <= maxDistance + 1.0E-6D; d += step) {
            distances.add(Math.min(d, maxDistance));
        }
        if (distances.isEmpty() || distances.get(distances.size() - 1) < maxDistance) {
            distances.add(maxDistance);
        }
        return distances;
    }

    /**
     * 按距离从近到远取前 {@code limit} 个目标（用于爆发的稳定上限）。
     *
     * @param distances 目标到中心的距离平方，{@code null} 视为不可用
     */
    public static int[] nearestWithinLimit(double[] distances, double radiusSqr, int limit) {
        if (distances == null || limit <= 0) {
            return new int[0];
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < distances.length; i++) {
            if (Double.isFinite(distances[i]) && distances[i] <= radiusSqr) {
                candidates.add(i);
            }
        }
        candidates.sort((a, b) -> Double.compare(distances[a], distances[b]));
        int size = Math.min(limit, candidates.size());
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = candidates.get(i);
        }
        return result;
    }
}
