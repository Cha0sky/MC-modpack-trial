package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.boss.BossRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 配置快照的净化/校验测试：这是"配置写错也不会把 NaN 或越界值带进 tick 逻辑"的最后一道防线。
 */
class GrowthSettingsTest {

    @Test
    @DisplayName("默认快照完全合法，且数值与 SPEC 一致")
    void defaultsAreValidAndMatchSpec() {
        GrowthSettings settings = GrowthSettings.defaults();
        assertTrue(settings.isValid(), () -> settings.validate().toString());
        assertEquals(1L, settings.hostilePointWeight());
        assertEquals(100L, settings.bossPointWeight());
        assertEquals(List.of(7.0D, 12.0D, 24.0D, 48.0D, 120.0D, 300.0D, 750.0D, 1500.0D),
                settings.stageAttackDamage());
        assertEquals(List.of(1.6D, 1.7D, 1.8D, 1.9D, 2.0D, 2.1D, 2.2D, 2.4D),
                settings.stageAttackSpeed());
        for (GrowthMode mode : GrowthMode.values()) {
            assertEquals(mode.defaultThresholds(), settings.thresholds(mode));
        }
    }

    @Test
    @DisplayName("阶段总攻击已包含玩家基础 1.0：修饰值必须减去 1.0，不能加两次")
    void stageDamageModifierSubtractsPlayerBase() {
        GrowthSettings settings = GrowthSettings.defaults();
        assertEquals(6.0D, settings.stageDamageModifier(0), 1.0E-9D);
        assertEquals(1499.0D, settings.stageDamageModifier(7), 1.0E-9D);
        // 攻速是"总攻速"，物品修饰值 = 总攻速 - 属性基础 4.0
        assertEquals(1.6D - 4.0D, settings.stageAttackSpeedModifier(0), 1.0E-9D);
        assertEquals(2.4D - 4.0D, settings.stageAttackSpeedModifier(7), 1.0E-9D);
        // 越界下标钳制，不抛异常
        assertEquals(settings.stageAttackDamage(0), settings.stageAttackDamage(-3));
        assertEquals(settings.stageAttackDamage(7), settings.stageAttackDamage(99));
    }

    @Test
    @DisplayName("阶段数组净化：长度不对 / NaN / 非正数 都回落默认值并记录问题")
    void sanitizeStageValues() {
        List<Double> fallback = List.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D, 7.0D, 8.0D);

        List<String> problems = new ArrayList<>();
        assertEquals(fallback, GrowthSettings.sanitizeStageValues("x", List.of(1.0D, 2.0D), fallback, true, problems));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("exactly 8"));

        problems.clear();
        List<Double> withNaN = new ArrayList<>(fallback);
        withNaN.set(3, Double.NaN);
        assertEquals(fallback, GrowthSettings.sanitizeStageValues("x", withNaN, fallback, true, problems));
        assertFalse(problems.isEmpty());

        problems.clear();
        List<Double> withInfinity = new ArrayList<>(fallback);
        withInfinity.set(0, Double.POSITIVE_INFINITY);
        assertEquals(fallback, GrowthSettings.sanitizeStageValues("x", withInfinity, fallback, true, problems));

        problems.clear();
        List<Double> withZero = new ArrayList<>(fallback);
        withZero.set(0, 0.0D);
        assertEquals(fallback, GrowthSettings.sanitizeStageValues("x", withZero, fallback, true, problems));

        problems.clear();
        assertEquals(fallback, GrowthSettings.sanitizeStageValues("x", fallback, fallback, true, problems));
        assertTrue(problems.isEmpty());
    }

    @Test
    @DisplayName("数值净化：权重/整型/浮点越界与 NaN 一律回落默认值")
    void sanitizeScalars() {
        List<String> problems = new ArrayList<>();
        assertEquals(5L, GrowthSettings.sanitizeWeight("w", 5L, 1L, 100L, problems));
        assertTrue(problems.isEmpty());
        assertEquals(1L, GrowthSettings.sanitizeWeight("w", -1L, 1L, 100L, problems));
        assertEquals(1L, GrowthSettings.sanitizeWeight("w", 101L, 1L, 100L, problems));

        problems.clear();
        assertEquals(7, GrowthSettings.sanitizeInt("i", 7, 0, 10, 3, problems));
        assertTrue(problems.isEmpty());
        assertEquals(3, GrowthSettings.sanitizeInt("i", 11, 0, 10, 3, problems));
        assertEquals(3, GrowthSettings.sanitizeInt("i", -1, 0, 10, 3, problems));

        problems.clear();
        assertEquals(1.5D, GrowthSettings.sanitizeDouble("d", 1.5D, 0.0D, 10.0D, 0.5D, problems), 1.0E-9D);
        assertTrue(problems.isEmpty());
        assertEquals(0.5D, GrowthSettings.sanitizeDouble("d", Double.NaN, 0.0D, 10.0D, 0.5D, problems), 1.0E-9D);
        assertEquals(0.5D, GrowthSettings.sanitizeDouble("d", Double.POSITIVE_INFINITY, 0.0D, 10.0D, 0.5D, problems), 1.0E-9D);
        assertEquals(0.5D, GrowthSettings.sanitizeDouble("d", 11.0D, 0.0D, 10.0D, 0.5D, problems), 1.0E-9D);
    }

    @Test
    @DisplayName("能力旋钮：终阶冷却/半径/吸血与 Boss 加成按阶段切换")
    void abilityStageSwitches() {
        Abilities abilities = Abilities.defaults();
        assertEquals(160, abilities.waveCooldownFor(5));
        assertEquals(80, abilities.waveCooldownFor(7));
        assertEquals(400, abilities.burstCooldownFor(6));
        assertEquals(200, abilities.burstCooldownFor(7));
        assertEquals(6.0D, abilities.burstRadiusFor(6), 1.0E-9D);
        assertEquals(8.0D, abilities.burstRadiusFor(7), 1.0E-9D);
        assertEquals(0.05D, abilities.lifestealFractionFor(5), 1.0E-9D);
        assertEquals(0.10D, abilities.lifestealFractionFor(6), 1.0E-9D);
        assertEquals(4.0D, abilities.lifestealCapFor(5), 1.0E-9D);
        assertEquals(8.0D, abilities.lifestealCapFor(6), 1.0E-9D);
        assertEquals(0.01D, abilities.bossBonusFractionFor(5), 1.0E-9D);
        assertEquals(0.03D, abilities.bossBonusFractionFor(7), 1.0E-9D);
        assertEquals(200.0D, abilities.bossBonusCapFor(5), 1.0E-9D);
        assertEquals(1000.0D, abilities.bossBonusCapFor(7), 1.0E-9D);
        assertTrue(Abilities.unlocked(5, 5));
        assertFalse(Abilities.unlocked(4, 5));
    }

    @Test
    @DisplayName("Boss/敌对规则：默认包含末影龙与凋灵、排除典狱长")
    void defaultBossRules() {
        BossRules rules = BossRules.defaults();
        assertTrue(rules.autoDetect());
        assertEquals(200.0D, rules.autoDetectHealthThreshold(), 1.0E-9D);
        assertTrue(rules.isIncluded("minecraft:ender_dragon"));
        assertTrue(rules.isIncluded("minecraft:wither"));
        assertTrue(rules.isExcluded("minecraft:warden"));
        assertTrue(rules.validate().isEmpty());
    }

    @Test
    @DisplayName("非法阈值组合：validate 会报出问题")
    void invalidThresholdsAreReported() {
        GrowthThresholds broken = new GrowthThresholds(
                List.of(0L, 5L, 4L, 9L, 10L, 11L, 12L, 13L),
                List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));
        GrowthSettings settings = GrowthSettings.defaults().withThresholds(GrowthMode.QUICK, broken);
        assertFalse(settings.isValid());
        assertTrue(settings.validate().stream().anyMatch(problem -> problem.startsWith("quick:")));
    }
}
