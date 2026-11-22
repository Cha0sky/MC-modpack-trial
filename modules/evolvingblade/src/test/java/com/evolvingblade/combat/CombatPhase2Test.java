package com.evolvingblade.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * PHASE 2 纯 JVM 回归：来源窗口哨兵、每目标速率限制。
 *
 * <p>这些规则曾经写错过（null 默认值、限速表过期），因此每条都固定一个断言。
 */
class CombatPhase2Test {

    // 来源窗口哨兵
    // ------------------------------------------------------------------

    @Test
    void noneSwordHitIsNeverValid() {
        assertFalse(SwordHit.NONE.isPresent());
        assertFalse(SwordHit.NONE.isStillValidFor(1_000L, SwordHit.VALID_WINDOW_TICKS));
    }

    @Test
    void swordHitIsValidOnlyInsideWindowAndNotInTheFuture() {
        UUID player = UUID.randomUUID();
        SwordHit hit = SwordHit.of(player, 500L);
        assertTrue(hit.isPresent());
        assertTrue(hit.isStillValidFor(500L, 20L));
        assertTrue(hit.isStillValidFor(520L, 20L));
        assertFalse(hit.isStillValidFor(521L, 20L), "超出窗口即失效");
        assertFalse(hit.isStillValidFor(499L, 20L), "未来时间戳不算有效来源");
    }

    // ------------------------------------------------------------------
    // 每目标速率限制：跨 tick 持续、按目标独立、超出上限被拒绝
    // ------------------------------------------------------------------

    @Test
    void rateLimitBlocksSameTargetWithinWindow() {
        Map<UUID, Long> table = new java.util.HashMap<>();
        UUID boss = UUID.randomUUID();
        assertTrue(AbilityState.tryConsumeRate(table, boss, 1_000L, 20, 8));
        assertFalse(AbilityState.tryConsumeRate(table, boss, 1_019L, 20, 8),
                "同一目标 20 tick 内只能吃一次加成");
        assertTrue(AbilityState.tryConsumeRate(table, boss, 1_020L, 20, 8));
    }

    @Test
    void rateLimitIsPerTargetAndBounded() {
        Map<UUID, Long> table = new java.util.HashMap<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(AbilityState.tryConsumeRate(table, first, 100L, 20, 2));
        assertTrue(AbilityState.tryConsumeRate(table, second, 100L, 20, 2),
                "不同目标互不影响");
        // 严格有界契约：表满且没有过期条目时**拒绝**新目标，绝不淘汰仍在生效的条目
        // （淘汰生效条目会让那个目标在 20 tick 内再次被加成，等于绕过限速）。
        assertFalse(AbilityState.tryConsumeRate(table, UUID.randomUUID(), 100L, 20, 2),
                "表满时必须拒绝新目标");
        assertEquals(2, table.size(), "表大小必须严格不超过上限");
        assertFalse(AbilityState.tryConsumeRate(table, first, 105L, 20, 2),
                "仍然生效的条目不能被淘汰，原目标必须继续被限速");
        assertFalse(AbilityState.tryConsumeRate(table, second, 105L, 20, 2),
                "第二个目标同样必须继续被限速");
        // 过了窗口后可以回收并被新目标使用。
        assertTrue(AbilityState.tryConsumeRate(table, UUID.randomUUID(), 130L, 20, 2),
                "过期条目被回收后，新目标可以被接受");
        assertEquals(1, table.size(), "回收+插入后仍然有界");
    }
}
