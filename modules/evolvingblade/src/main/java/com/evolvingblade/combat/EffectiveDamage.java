package com.evolvingblade.combat;

import com.evolvingblade.growth.GrowthSettings;

/**
 * 技能伤害的<b>有效攻击取值</b>与爆发的<b>三维球判定</b>——纯计算，零 Minecraft 依赖。
 *
 * <h2>为什么技能要读持有者的有效攻击</h2>
 *
 * <p>1.0.0 里剑气/爆发伤害只取"阶段配置基础攻击"（{@code stageAttackDamage(stage)}），
 * 于是第三方模组对 {@code minecraft:attack_damage} 的 flat / 百分比加伤
 * <b>完全不体现在技能上</b>——近战直击照样吃加成，技能却吃不到。
 * 这是明确的功能缺口，1.1.0 起默认由本类把技能伤害与持有者的服务端有效攻击对齐。
 *
 * <h2>取值口径</h2>
 * <pre>
 *   继承开启（默认）：技能伤害 = 系数 × 服务端有效持剑 ATTACK_DAMAGE
 *   继承关闭        ：技能伤害 = 系数 × 阶段配置基础攻击（1.0.0 行为）
 * </pre>
 * {@code 1.0}（剑气）/ {@code 1.5}（爆发）是<b>比例</b>，<b>不是</b>阶段天花板：
 * 正的加伤必须真的增强技能。因此<b>不再</b>对阶段配置值取 {@code min}。
 *
 * <h2>边界与安全</h2>
 * <ul>
 *   <li>只做<b>一次</b>缩放，且只缩放技能自己要施加的那一份伤害；
 *       第三方对近战伤害的倍率由原版伤害管线在技能 {@code hurt()} 里执行一次，
 *       本类不会再乘第二遍，也不会把已经乘过的结果反减成 ADD。</li>
 *   <li>返回值永远有限、非负，并被 {@link #MAX_ABILITY_DAMAGE} 兜底；
 *       配置里出现 NaN / 无穷 / 负数时回落到阶段基础值。</li>
 *   <li>原版 {@code RangedAttribute} 把 ATTACK_DAMAGE 硬钳在 2048
 *       （{@code Attributes.ATTACK_DAMAGE} = {@code RangedAttribute(..., 0.0, 2048.0)}），
 *       所以"有效攻击"本身不可能超过 2048；因此技能伤害上限是
 *       {@code 2048 × 系数}（默认系数 1.0 / 1.5）。这是原版的全局上限，
 *       本模组<b>不修改</b>它，也不承诺任何绕过该上限的第三方模组。</li>
 * </ul>
 */
public final class EffectiveDamage {

    /**
     * 技能伤害的绝对兜底上限。原版攻击力上限 2048，取 4 倍余量（8192）作为防御性上限：
     * 即使配置系数被手改成极大值，也不会把 Float 溢出值送进 {@code hurt()}。
     */
    public static final double MAX_ABILITY_DAMAGE = 8192.0D;

    private EffectiveDamage() {}

    /**
     * 计算技能伤害。
     *
     * <pre>
     *   继承开启：技能伤害 = 系数 × 服务端有效持剑 ATTACK_DAMAGE
     *   继承关闭：技能伤害 = 系数 × 阶段配置基础攻击
     * </pre>
     *
     * @param settings        服务器配置快照
     * @param stage           持有者当前阶段
     * @param multiplier      技能比例（剑气 1.0 / 爆发 1.5）
     * @param effectiveAttack 持有者服务端有效 {@code ATTACK_DAMAGE}；
     *                        非有限或非正数时视为"无法取值"，回落到阶段基础值
     * @param inheritAttack   配置开关：false 时完全退回 1.0.0 的"只看阶段配置"
     * @return 有限、非负、且不超过 {@link #MAX_ABILITY_DAMAGE} 的伤害值
     */
    public static double abilityDamage(GrowthSettings settings, int stage, double multiplier,
            double effectiveAttack, boolean inheritAttack) {
        double stageBase = settings == null ? 0.0D : settings.stageAttackDamage(stage);
        return abilityDamage(stageBase, multiplier, effectiveAttack, inheritAttack);
    }

    /** 同 {@link #abilityDamage(GrowthSettings, int, double, double, boolean)}，但直接给定阶段基础值。 */
    public static double abilityDamage(double stageBase, double multiplier, double effectiveAttack,
            boolean inheritAttack) {
        double safeMultiplier = finiteOr(multiplier, 0.0D);
        double resolved;
        if (inheritAttack && Double.isFinite(effectiveAttack) && effectiveAttack > 0.0D) {
            resolved = safeMultiplier * effectiveAttack;
        } else {
            resolved = safeMultiplier * finiteOr(stageBase, 0.0D);
        }
        return clamp(resolved);
    }

    /**
     * 持有者当前是否<b>真的</b>主手握着本剑（决定 UI 是否显示"当前持剑有效攻击"）。
     *
     * <p>纯函数版本，避免 UI 层引入物品判断的时序问题；真实调用点传
     * {@code StarEaterItem.isStarEater(player.getMainHandItem())} 的结果。
     */
    public static boolean shouldExposeEffectiveAttack(boolean holdingStarEater, boolean inheritAttack) {
        return holdingStarEater && inheritAttack;
    }

    // ------------------------------------------------------------------
    // 三维球判定（爆发）
    // ------------------------------------------------------------------

    /**
     * 点到轴对齐包围盒的最近点距离平方（三轴各自取最近的投影）。
     *
     * <p>这是"包围盒与球相交"的核心：三轴都取最近点，而不是只看水平距离。
     * 因此正上方/正下方、斜上方、贴边的大型实体都能被正确判定。
     */
    public static double distanceSqrToAabb(double cx, double cy, double cz,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = axisGap(cx, minX, maxX);
        double dy = axisGap(cy, minY, maxY);
        double dz = axisGap(cz, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 目标包围盒是否与"以 {@code (cx,cy,cz)} 为球心、{@code radius} 为半径"的球相交。
     *
     * <p>判据是<b>严格</b> {@code nearestDistanceSqr <= radius²}（贴边算命中），
     * 且三轴都参与——不是 X/Z 圆柱，也不是"只算脚底到球心的距离"。
     *
     * @return 球心/半径/包围盒任一非法时返回 false（绝不误命中）
     */
    public static boolean sphereIntersectsAabb(double cx, double cy, double cz, double radius,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)
                || !Double.isFinite(radius) || radius < 0.0D) {
            return false;
        }
        if (!allFinite(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        double nearest = distanceSqrToAabb(cx, cy, cz, minX, minY, minZ, maxX, maxY, maxZ);
        return nearest <= radius * radius;
    }

    /** 球心到单个实体中心点的距离平方（诊断/测试用，三轴都算）。 */
    public static double distanceSqrToPoint(double cx, double cy, double cz, double px, double py, double pz) {
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 点到区间 [min, max] 的单轴距离：在区间内为 0，否则为到最近端点的距离。 */
    private static double axisGap(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0.0D;
        }
        return Math.min(value, MAX_ABILITY_DAMAGE);
    }
}
