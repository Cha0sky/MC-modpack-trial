package com.evolvingblade.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.evolvingblade.growth.GrowthSettings;

/**
 * {@link EffectiveDamage} 的纯 JVM 单测：技能有效攻击取值与三维球判定的边界。
 *
 * <p>这些断言<b>不</b>依赖 Minecraft 运行时，因此可以在 `test` 任务里跑，
 * 也能在 GameTest 之外单独回归"球边界/有效攻击夹紧"这类纯数学事实。
 */
class EffectiveDamageTest {

    private static final double EPS = 1.0E-9D;

    // ------------------------------------------------------------------
    // 技能伤害取值
    // ------------------------------------------------------------------

    @Test
    @DisplayName("继承开启时技能伤害 = 系数 × 有效攻击（正加伤必须真的增强技能）")
    void inheritsEffectiveAttackWithoutStageCeiling() {
        // 有效攻击低于阶段基础 → 按有效攻击（不是阶段基础）。
        assertEquals(30.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 30.0D, true), EPS);
        // 有效攻击<b>高于</b>阶段基础 → 也必须按有效攻击，不再被阶段配置夹住。
        assertEquals(1000.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 1000.0D, true), EPS);
        assertEquals(1500.0D, EffectiveDamage.abilityDamage(120.0D, 1.5D, 1000.0D, true), EPS);
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 48.0D, true), EPS);
    }

    @Test
    @DisplayName("关闭继承时完全退回 1.0.0 的阶段配置口径（与有效攻击无关）")
    void inheritanceOffIgnoresEffectiveAttack() {
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 5.0D, false), EPS);
        assertEquals(72.0D, EffectiveDamage.abilityDamage(48.0D, 1.5D, 5.0D, false), EPS);
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 1000.0D, false), EPS);
    }

    @Test
    @DisplayName("系数缩放对两个口径都生效")
    void multiplierScalesBothBranches() {
        assertEquals(1500.0D, EffectiveDamage.abilityDamage(120.0D, 1.5D, 1000.0D, true), EPS);
        assertEquals(45.0D, EffectiveDamage.abilityDamage(120.0D, 1.5D, 30.0D, true), EPS);
        assertEquals(180.0D, EffectiveDamage.abilityDamage(120.0D, 1.5D, 30.0D, false), EPS);
    }

    @Test
    @DisplayName("非法有效攻击回落到阶段基础值，绝不产生 NaN/负数伤害")
    void illegalEffectiveAttackFallsBack() {
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, Double.NaN, true), EPS);
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, Double.POSITIVE_INFINITY, true), EPS);
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, -5.0D, true), EPS);
        assertEquals(48.0D, EffectiveDamage.abilityDamage(48.0D, 1.0D, 0.0D, true), EPS);
    }

    @Test
    @DisplayName("非法系数回落到 0 倍（不再无条件归零有效攻击）；非法阶段基础值退化为 0")
    void illegalInputsDegradeToZero() {
        // 系数为 NaN → 视为 0 倍，伤害 0（实现里 finiteOr(multiplier, 0)）。
        assertEquals(0.0D, EffectiveDamage.abilityDamage(48.0D, Double.NaN, 1000.0D, true), EPS);
        // 阶段基础值非法、但没有可用有效攻击时 → 0。
        assertEquals(0.0D, EffectiveDamage.abilityDamage(Double.NaN, 1.0D, Double.NaN, true), EPS);
        assertEquals(0.0D, EffectiveDamage.abilityDamage(-48.0D, 1.0D, Double.NaN, true), EPS);
        // 有效攻击可用时，非法阶段基础值不应影响结果（它只用于回落分支）。
        assertEquals(30.0D, EffectiveDamage.abilityDamage(Double.NaN, 1.0D, 30.0D, true), EPS);
    }

    @Test
    @DisplayName("技能伤害有绝对兜底上限，不会把溢出值送进 hurt()")
    void absoluteCapApplies() {
        assertEquals(EffectiveDamage.MAX_ABILITY_DAMAGE,
                EffectiveDamage.abilityDamage(1.0E9D, 1.0E6D, Double.NaN, false), EPS);
        // 有效攻击路径同样受绝对上限保护（原版属性上限 2048 之内的正常值不受影响）。
        assertEquals(EffectiveDamage.MAX_ABILITY_DAMAGE,
                EffectiveDamage.abilityDamage(1.0D, 1.0E6D, 2048.0D, true), EPS);
        assertEquals(2048.0D, EffectiveDamage.abilityDamage(1.0D, 1.0D, 2048.0D, true), EPS);
    }

    @Test
    @DisplayName("配置口径：默认继承开关为 true，且阶段基础值被正确读出")
    void defaultSettingsInherit() {
        GrowthSettings settings = GrowthSettings.defaults();
        assertTrue(settings.abilities().abilityDamageFromAttack(), "1.1.0 默认必须继承有效攻击");
        // 阶段 3 基础 48：系数 1.0 时直接跟随有效攻击（含高于阶段基础的情形）。
        assertEquals(1000.0D, EffectiveDamage.abilityDamage(settings, 3, 1.0D, 1000.0D, true), EPS);
        assertEquals(30.0D, EffectiveDamage.abilityDamage(settings, 3, 1.0D, 30.0D, true), EPS);
        // 系数 1.5：阶段 4 基础 120，有效 1000 → 1500（不再被 180 夹住）。
        assertEquals(1500.0D, EffectiveDamage.abilityDamage(settings, 4, 1.5D, 1000.0D, true), EPS);
        assertEquals(45.0D, EffectiveDamage.abilityDamage(settings, 3, 1.5D, 30.0D, true), EPS);
    }

    @Test
    @DisplayName("UI 只在「真的主手持剑」且继承开启时暴露有效攻击")
    void effectiveAttackExposureRule() {
        assertTrue(EffectiveDamage.shouldExposeEffectiveAttack(true, true));
        assertFalse(EffectiveDamage.shouldExposeEffectiveAttack(false, true),
                "预览别的武器时绝不能借用加成");
        assertFalse(EffectiveDamage.shouldExposeEffectiveAttack(true, false));
    }

    // ------------------------------------------------------------------
    // 三维球判定
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正上方/正下方的目标必须落在三维球内")
    void directlyAboveAndBelow() {
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, -0.3, 5.8, -0.3, 0.3, 6.2, 0.3));
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, -0.3, -6.2, -0.3, 0.3, -5.8, 0.3));
    }

    @Test
    @DisplayName("同一水平距离但在球外的高度必须 miss（证明不是 X/Z 圆柱）")
    void notACylinder() {
        // 两个轴都超出 → 球外
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, 5.5, 5.5, -0.2, 5.9, 5.9, 0.2));
        // 水平 5、垂直 5：距离 sqrt(50)=7.07 > 6 → miss（圆柱判定会误命中）
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, 4.7, 4.7, -0.3, 5.3, 5.3, 0.3));
    }

    @Test
    @DisplayName("最近点严格 <= r² 才命中：贴边算命中，多一点不算（整数坐标，无浮点歧义）")
    void boundaryIsInclusive() {
        // 盒 x∈[-0.5, 6.0] 把球心包在里面 → 最近点距离 0 → 命中；并且手算最近点 = 0。
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, -0.5, -0.5, -0.5, 6.0, 0.5, 0.5));
        assertEquals(0.0D,
                EffectiveDamage.distanceSqrToAabb(0, 0, 0, -0.5, -0.5, -0.5, 6.0, 0.5, 0.5), EPS);
        // 盒 x∈[4.5, 5.6]：最近点 4.5 < 6 → 20.25 <= 36 → 命中。
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, 4.5, -0.5, -0.5, 5.6, 0.5, 0.5));
        assertEquals(20.25D,
                EffectiveDamage.distanceSqrToAabb(0, 0, 0, 4.5, -0.5, -0.5, 5.6, 0.5, 0.5), EPS);
        // 盒 x∈[6.1, 7.3]：最近点 6.1 → 37.21 > 36 → miss。
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, 6.1, -0.5, -0.5, 7.3, 0.5, 0.5));
        // 两个轴都超出：最近点 (6,4,0) → 52 > 36 → miss。
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, 6.0, 4.0, -0.5, 6.5, 4.5, 0.5));
    }

    @Test
    @DisplayName("大型包围盒：中心在球外但最近点进球必须命中")
    void largeBoxCornerInside() {
        // 末影龙级：宽 16（半宽 8），盒 x∈[2,18]，球心 x=0.5 → 最近点 x=2 → dx=1.5 → 2.25 <= 64 → 命中。
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0.5, 0.9, 0.5, 8, 2.0, -4.0, -8.0, 18.0, 4.0, 8.0));
        // 同尺寸但盒 x∈[12,28] → 最近点 x=12 → dx=11.5 → 132.25 > 64 → miss。
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0.5, 0.9, 0.5, 8, 12.0, -4.0, -8.0, 28.0, 4.0, 8.0));
        // 半径 6、盒 x∈[4.2, 5.4]、中心 (4.8,0,0)：中心 4.8 在球内、最近点 4.2 → 17.64 <= 36 → 命中。
        assertTrue(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                4.2D, -0.5D, -0.5D, 5.4D, 0.5D, 0.5D));
        // 盒 x∈[6.1, 7.3]：最近点 6.1 → 37.21 > 36 → miss。
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0.0D, 0.0D, 0.0D, 6.0D,
                6.1D, -0.5D, -0.5D, 7.3D, 0.5D, 0.5D));
    }

    @Test
    @DisplayName("非法球心/半径/包围盒一律不命中")
    void illegalInputsNeverHit() {
        assertFalse(EffectiveDamage.sphereIntersectsAabb(Double.NaN, 0, 0, 6, -1, -1, -1, 1, 1, 1));
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, -1, -1, -1, -1, 1, 1, 1));
        assertFalse(EffectiveDamage.sphereIntersectsAabb(0, 0, 0, 6, Double.NaN, -1, -1, 1, 1, 1));
    }

    @Test
    @DisplayName("距离平方按三轴计算，不忽略 Y")
    void distanceIsThreeDimensional() {
        assertEquals(25.0D, EffectiveDamage.distanceSqrToPoint(0, 0, 0, 3, 4, 0), EPS);
        assertEquals(50.0D, EffectiveDamage.distanceSqrToPoint(0, 0, 0, 3, 4, 5), EPS);
    }
}
