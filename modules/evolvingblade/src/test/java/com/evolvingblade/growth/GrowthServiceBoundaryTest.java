package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 服务边界契约：公开的"选择模式"入口必须<b>显式拒绝 null</b>。
 *
 * <p>审查指出的缺陷是 {@code GrowthService.selectMode(player, null)} 会成功，
 * 从而留下一个"看起来选过、实际未选"的玩家，破坏原子性。
 * 这里无需构造真实玩家：校验发生在触碰玩家之前（null 会先抛异常）。
 */
class GrowthServiceBoundaryTest {

    @Test
    @DisplayName("selectMode(_, null) 抛 NPE，而不是静默成功")
    void selectModeRejectsNull() {
        assertThrows(NullPointerException.class, () -> GrowthService.selectMode(null, null));
    }

    @Test
    @DisplayName("forceMode(_, null) 抛 NPE")
    void forceModeRejectsNull() {
        assertThrows(NullPointerException.class, () -> GrowthService.forceMode(null, null));
    }

    @Test
    @DisplayName("setCounters(_, 负数) 在触碰玩家之前就被拒绝")
    void setCountersRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> GrowthService.setCounters(null, -1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> GrowthService.setCounters(null, 0L, -1L));
    }

    @Test
    @DisplayName("GrowthData.withMode(null) 被拒绝：未选择只能作为初始状态存在")
    void withModeRejectsNull() {
        assertThrows(NullPointerException.class, () -> GrowthData.EMPTY.withMode(null));
    }

    @Test
    @DisplayName("初始状态确实未选择，且选择后无法通过公开入口改回 null")
    void initialStateIsUnselectedAndSticky() {
        assertFalse(GrowthData.EMPTY.hasMode());
        GrowthData selected = GrowthData.EMPTY.withMode(GrowthMode.STANDARD);
        assertTrue(selected.hasMode());
        assertEquals(GrowthMode.STANDARD, selected.mode());
        assertThrows(NullPointerException.class, () -> selected.withMode(null));
        assertEquals(GrowthMode.STANDARD, selected.mode(), "失败的调用不得改变原对象");
    }
}
