package com.evolvingblade.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Boss 判定内核的决策表测试。全部是纯 JVM 输入，不需要真实实体。
 *
 * <p>重点覆盖 SPEC 的两条硬要求：
 * <ul>
 *   <li>排除列表优先级最高；</li>
 *   <li>自动探测<b>绝不</b>只看生命值——典狱长（500 血、类名无 Boss、无 ServerBossEvent 字段）必须是非 Boss。</li>
 * </ul>
 */
class BossClassifierCoreTest {

    private static final BossRules DEFAULT = BossRules.defaults();

    @Test
    @DisplayName("非生物实体：直接判否")
    void notLiving() {
        BossClassification result = BossClassifierCore.decide("minecraft:item_frame", false, false, 20.0D, false, false, DEFAULT);
        assertFalse(result.boss());
        assertEquals(BossReason.NOT_LIVING, result.reason());
    }

    @Test
    @DisplayName("排除列表最高优先级：即使同时在包含列表/标签里也判否")
    void excludeWins() {
        BossRules rules = new BossRules(true, 200.0D,
                Set.of("minecraft:warden"), Set.of("minecraft:warden"));
        BossClassification result = BossClassifierCore.decide("minecraft:warden", true, true, 500.0D, true, true, rules);
        assertFalse(result.boss());
        assertEquals(BossReason.EXCLUDED_BY_CONFIG, result.reason());

        BossClassification tagged = BossClassifierCore.decide("custom:boss", true, true, 300.0D, false, false,
                new BossRules(true, 200.0D, Set.of(), Set.of("custom:boss")));
        assertFalse(tagged.boss());
        assertEquals(BossReason.EXCLUDED_BY_CONFIG, tagged.reason());
    }

    @Test
    @DisplayName("包含列表：无需标签也直接判是")
    void includeWins() {
        BossClassification result = BossClassifierCore.decide("minecraft:ender_dragon", true, false, 200.0D, false, false, DEFAULT);
        assertTrue(result.boss());
        assertEquals(BossReason.INCLUDED_BY_CONFIG, result.reason());
    }

    @Test
    @DisplayName("Boss 标签：可靠的判定输入")
    void bossTag() {
        BossClassification result = BossClassifierCore.decide("some_mod:raid_leader", true, true, 20.0D, false, false, DEFAULT);
        assertTrue(result.boss());
        assertEquals(BossReason.BOSS_TAG, result.reason());
    }

    @Test
    @DisplayName("自动探测关闭：名单/标签之外一律非 Boss")
    void autoDetectDisabled() {
        BossRules rules = new BossRules(false, 200.0D, Set.of(), Set.of());
        BossClassification result = BossClassifierCore.decide("minecraft:wither", true, false, 300.0D, true, true, rules);
        assertFalse(result.boss());
        assertEquals(BossReason.AUTO_DETECT_DISABLED, result.reason());
    }

    @Test
    @DisplayName("生命低于阈值：即使持有 ServerBossEvent 或类名含 Boss 也判否")
    void healthGateIsMandatory() {
        BossClassification lowHealth = BossClassifierCore.decide("mod:tiny_boss", true, false, 199.9D, true, true, DEFAULT);
        assertFalse(lowHealth.boss());
        assertEquals(BossReason.HEALTH_BELOW_THRESHOLD, lowHealth.reason());

        BossClassification notFinite = BossClassifierCore.decide("mod:tiny_boss", true, false, Double.NaN, true, true, DEFAULT);
        assertFalse(notFinite.boss());
        assertEquals(BossReason.HEALTH_BELOW_THRESHOLD, notFinite.reason());

        BossClassification exactlyAtThreshold = BossClassifierCore.decide("mod:tiny_boss", true, false, 200.0D, true, false, DEFAULT);
        assertTrue(exactlyAtThreshold.boss(), "阈值是闭区间：恰好等于阈值应判是");
        assertEquals(BossReason.SERVER_BOSS_EVENT_FIELD, exactlyAtThreshold.reason());
    }

    @Test
    @DisplayName("自动探测：ServerBossEvent 字段优先于类名")
    void bossEventFieldBeatsClassName() {
        BossClassification result = BossClassifierCore.decide("mod:unknown", true, false, 400.0D, true, true, DEFAULT);
        assertTrue(result.boss());
        assertEquals(BossReason.SERVER_BOSS_EVENT_FIELD, result.reason());
    }

    @Test
    @DisplayName("自动探测：类名含 Boss（如凋灵 WitherBoss）")
    void classNameFallback() {
        BossClassification result = BossClassifierCore.decide("mod:wither_like", true, false, 400.0D, false, true, DEFAULT);
        assertTrue(result.boss());
        assertEquals(BossReason.CLASS_NAME_CONTAINS_BOSS, result.reason());
    }

    @Test
    @DisplayName("只看生命值不算 Boss：典狱长 500 血必须是普通敌对")
    void wardenIsNotBoss() {
        // 默认配置把典狱长显式列入排除名单（排除优先级最高）。
        BossClassification excluded = BossClassifierCore.decide("minecraft:warden", true, false, 500.0D, false, false, DEFAULT);
        assertFalse(excluded.boss());
        assertEquals(BossReason.EXCLUDED_BY_CONFIG, excluded.reason());

        // 即使没有任何名单/标签，500 血 + 无 ServerBossEvent 字段 + 类名不含 Boss 也绝不算 Boss：
        // 自动探测绝不只看生命值。
        BossRules noLists = new BossRules(true, 200.0D, Set.of(), Set.of());
        BossClassification byHealthOnly = BossClassifierCore.decide("mod:big_monster", true, false, 500.0D, false, false, noLists);
        assertFalse(byHealthOnly.boss());
        assertEquals(BossReason.NO_RULE_MATCHED, byHealthOnly.reason());
    }

    @Test
    @DisplayName("描述文本包含实体 ID 与获胜理由")
    void describeContainsFacts() {
        String text = BossClassifierCore.decide("minecraft:wither", true, false, 300.0D, true, true, DEFAULT).describe();
        assertTrue(text.contains("minecraft:wither"));
        assertTrue(text.contains("included by config"));
    }
}
