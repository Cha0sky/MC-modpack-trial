package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 死亡核实决策表的纯 JVM 测试。
 *
 * <p>这张表是审查要求的核心契约：
 * <ul>
 *   <li>事件最终被取消 → 不成立，<b>即使血量已经归零</b>；</li>
 *   <li>不要求任何原版死亡姿态 / 阶段；</li>
 *   <li>延迟死亡阶段（原版末影龙）→ 保留等待，既不提前入账也不丢弃；</li>
 *   <li>被 KILLED 移出世界 → 入账；被其它原因移除 → 不成立；</li>
 *   <li>活体合成事件 → 一直等待，直到保留上限后丢弃。</li>
 * </ul>
 */
class DeathDecisionTest {

    @Test
    @DisplayName("真实终局：未取消 + 血量归零 → 入账")
    void terminalDeathCredits() {
        assertEquals(DeathDecision.CREDIT,
                DeathDecision.decide(false, false, false, false, true, false));
    }

    @Test
    @DisplayName("最终取消优先：即使血量已归零也不成立")
    void cancelledWinsEvenAtZeroHealth() {
        // 血量 0、未移除、未入账，但事件在本 tick 末被同优先级监听器取消
        assertEquals(DeathDecision.DISCARD_CANCELLED,
                DeathDecision.decide(true, false, false, false, true, false));
        // 即使同时满足 KILLED 移除也不成立（取消意味着死亡未提交）
        assertEquals(DeathDecision.DISCARD_CANCELLED,
                DeathDecision.decide(true, false, true, true, true, false));
    }

    @Test
    @DisplayName("已入账：任何后续通知都不得二次入账")
    void alreadyCreditedIsRejected() {
        assertEquals(DeathDecision.DISCARD_ALREADY_CREDITED,
                DeathDecision.decide(false, true, false, false, true, false));
        assertEquals(DeathDecision.DISCARD_ALREADY_CREDITED,
                DeathDecision.decide(false, true, true, true, true, false));
    }

    @Test
    @DisplayName("被 KILLED 立即移除的合法模组 Boss → 入账；其它移除原因 → 不成立")
    void removalReasons() {
        assertEquals(DeathDecision.CREDIT,
                DeathDecision.decide(false, false, true, true, false, false));
        // 血量尚未归零，但已被接受为死亡并 KILLED 移除
        assertEquals(DeathDecision.CREDIT,
                DeathDecision.decide(false, false, true, true, true, false));
        for (boolean terminal : new boolean[] {false, true}) {
            assertEquals(DeathDecision.DISCARD_REMOVED_NOT_KILLED,
                    DeathDecision.decide(false, false, true, false, terminal, false),
                    "非 KILLED 的移除（DISCARDED/UNLOADED/换维度）不得入账");
        }
    }

    @Test
    @DisplayName("延迟死亡阶段（原版末影龙 DYING）：血量被恢复为 1 → 保留等待")
    void deferredDeathPhaseKeepsWaiting() {
        // 血量 > 0（被阶段逻辑恢复成 1）、未移除、未取消 → 既不能入账也不能丢弃
        assertEquals(DeathDecision.KEEP_WAITING,
                DeathDecision.decide(false, false, false, false, false, false));
        assertTrue(DeathDecision.KEEP_WAITING.removesCandidate() == false);
        assertFalse(DeathDecision.KEEP_WAITING.credits());
    }

    @Test
    @DisplayName("活体合成事件：一直等待，超过保留上限才丢弃")
    void syntheticEventExpires() {
        assertEquals(DeathDecision.KEEP_WAITING,
                DeathDecision.decide(false, false, false, false, false, false));
        assertEquals(DeathDecision.DISCARD_RETENTION_EXPIRED,
                DeathDecision.decide(false, false, false, false, false, true));
    }

    @Test
    @DisplayName("removesCandidate / credits 语义")
    void helperSemantics() {
        assertTrue(DeathDecision.CREDIT.credits());
        assertTrue(DeathDecision.CREDIT.removesCandidate());
        for (DeathDecision decision : new DeathDecision[] {
                DeathDecision.DISCARD_CANCELLED,
                DeathDecision.DISCARD_ALREADY_CREDITED,
                DeathDecision.DISCARD_REMOVED_NOT_KILLED,
                DeathDecision.DISCARD_RETENTION_EXPIRED }) {
            assertFalse(decision.credits(), decision + " 不应入账");
            assertTrue(decision.removesCandidate(), decision + " 应移除候选");
        }
    }
}
