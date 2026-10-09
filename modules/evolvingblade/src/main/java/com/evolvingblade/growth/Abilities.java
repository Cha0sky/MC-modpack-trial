package com.evolvingblade.growth;

/**
 * 能力参数（全部来自服务器配置，默认值即 SPEC 中的默认值）。
 *
 * <p><b>本记录只负责"配置结构与校验"</b>；战斗与界面逻辑只读取这里的值，
 * 因此不存在"界面数值与配置不一致"的可能。
 *
 * @param killHeal                 阶段1+：用本剑完成一次有效击杀后回复的生命值
 * @param waveUnlockStage          解锁剑气的阶段（默认 2）
 * @param waveRange                剑气射程（方块）
 * @param waveDamageMultiplier     剑气伤害 = 该系数 × 阶段基础攻击
 * @param waveCooldownTicks        剑气冷却（tick）
 * @param waveFinalCooldownTicks   终阶剑气冷却（tick）
 * @param waveFinalPierce          终阶可穿透的父实体数上限
 * @param waveLifetimeTicks        射线/弹射物最长存活（tick），保证有限寿命
 * @param lifestealUnlockStage     解锁吸血的阶段（默认 3）
 * @param lifestealFraction        满蓄力主手近战吸血比例（占实际损失生命）
 * @param lifestealCap             吸血单次上限（生命）
 * @param lifestealHighUnlockStage 吸血强化的阶段（默认 6）
 * @param lifestealHighFraction    强化后吸血比例
 * @param lifestealHighCap         强化后单次上限
 * @param burstUnlockStage         解锁爆发的阶段（默认 4）
 * @param burstRadius              爆发半径（方块）
 * @param burstDamageMultiplier    爆发伤害 = 该系数 × 阶段基础攻击
 * @param burstCooldownTicks       爆发冷却（tick）
 * @param burstFinalRadius         终阶爆发半径（方块）
 * @param burstFinalCooldownTicks  终阶爆发冷却（tick）
 * @param burstMaxTargets          爆发最多命中的有效目标数
 * @param bossBonusUnlockStage     解锁"斩 Boss 额外伤害"的阶段（默认 5）
 * @param bossBonusFraction        额外伤害 = 该比例 × Boss 最大生命
 * @param bossBonusCap             额外伤害上限
 * @param bossBonusFinalFraction   终阶额外伤害比例
 * @param bossBonusFinalCap        终阶额外伤害上限
 * @param bossBonusCooldownTicks   同一玩家+同一目标的最小间隔（tick）
 * @param abilityDamageFromAttack  剑气/爆发是否按<b>持有者服务端有效 ATTACK_DAMAGE</b> 取值
 *                                 （默认 {@code true} = 1.1.0 的新兼容行为）。
 *                                 为 {@code true} 时技能伤害 =
 *                                 {@code min(系数 × 阶段基础攻击, 系数 × 有效攻击)}，
 *                                 于是第三方对 {@code minecraft:attack_damage} 的
 *                                 flat/百分比加伤都会体现在技能上；
 *                                 为 {@code false} 时退回 1.0.0 的"只看阶段配置"行为。
 */
public record Abilities(
        double killHeal,
        int waveUnlockStage,
        double waveRange,
        double waveDamageMultiplier,
        int waveCooldownTicks,
        int waveFinalCooldownTicks,
        int waveFinalPierce,
        int waveLifetimeTicks,
        int lifestealUnlockStage,
        double lifestealFraction,
        double lifestealCap,
        int lifestealHighUnlockStage,
        double lifestealHighFraction,
        double lifestealHighCap,
        int burstUnlockStage,
        double burstRadius,
        double burstDamageMultiplier,
        int burstCooldownTicks,
        double burstFinalRadius,
        int burstFinalCooldownTicks,
        int burstMaxTargets,
        int bossBonusUnlockStage,
        double bossBonusFraction,
        double bossBonusCap,
        double bossBonusFinalFraction,
        double bossBonusFinalCap,
        int bossBonusCooldownTicks,
        boolean abilityDamageFromAttack) {

    /** 最大允许的冷却 tick 数，避免配置写出让主线程长时间空转的数值。 */
    public static final int MAX_COOLDOWN_TICKS = 20 * 60 * 60; // 1 小时
    /** 最大允许的射程 / 半径，防止无界循环与离谱数值。 */
    public static final double MAX_RANGE = 256.0D;
    /** 暴击类比例上限（1000%），超出即视为配置错误回落到默认值。 */
    public static final double MAX_FRACTION = 10.0D;

    public static Abilities defaults() {
        return new Abilities(
                2.0D,
                2, 24.0D, 1.0D, 160, 80, 3, 200,
                3, 0.05D, 4.0D,
                6, 0.10D, 8.0D,
                4, 6.0D, 1.5D, 400, 8.0D, 200, 32,
                5, 0.01D, 200.0D, 0.03D, 1000.0D, 20,
                true);
    }

    /** 阶段 {@code stage} 是否已解锁某个能力。 */
    public static boolean unlocked(int stage, int unlockStage) {
        return stage >= unlockStage;
    }

    /** 剑气冷却：终阶使用更短冷却。 */
    public int waveCooldownFor(int stage) {
        return stage >= GrowthMode.STAGE_COUNT - 1 ? this.waveFinalCooldownTicks : this.waveCooldownTicks;
    }

    /** 爆发冷却：终阶使用更短冷却。 */
    public int burstCooldownFor(int stage) {
        return stage >= GrowthMode.STAGE_COUNT - 1 ? this.burstFinalCooldownTicks : this.burstCooldownTicks;
    }

    /** 爆发半径：终阶使用更大半径。 */
    public double burstRadiusFor(int stage) {
        return stage >= GrowthMode.STAGE_COUNT - 1 ? this.burstFinalRadius : this.burstRadius;
    }

    /** 吸血比例：按阶段取默认档或强化档。 */
    public double lifestealFractionFor(int stage) {
        return stage >= this.lifestealHighUnlockStage ? this.lifestealHighFraction : this.lifestealFraction;
    }

    /** 吸血上限：按阶段取默认档或强化档。 */
    public double lifestealCapFor(int stage) {
        return stage >= this.lifestealHighUnlockStage ? this.lifestealHighCap : this.lifestealCap;
    }

    /** Boss 额外伤害比例：终阶使用强化档。 */
    public double bossBonusFractionFor(int stage) {
        return stage >= GrowthMode.STAGE_COUNT - 1 ? this.bossBonusFinalFraction : this.bossBonusFraction;
    }

    /** Boss 额外伤害上限：终阶使用强化档。 */
    public double bossBonusCapFor(int stage) {
        return stage >= GrowthMode.STAGE_COUNT - 1 ? this.bossBonusFinalCap : this.bossBonusCap;
    }
}
