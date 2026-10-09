package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待核实死亡候选队列的纯 JVM 测试。
 *
 * <p>注意：<b>长期去重不再由本队列承担</b>（已改为写在实体自身上的永久标记），
 * 所以这里只验证"候选的接受/覆盖/撤销/有界保留"。
 */
class PendingDeathQueueTest {

    @Test
    @DisplayName("接受候选并保留原始接受时间（重复通知只刷新载荷）")
    void putAndRefresh() {
        PendingDeathQueue<String> queue = new PendingDeathQueue<>();
        UUID victim = UUID.randomUUID();

        queue.put(victim, "first", 100L);
        assertTrue(queue.contains(victim));
        assertEquals("first", queue.get(victim).payload());
        assertEquals(100L, queue.get(victim).acceptedGameTime());

        // 同一实体再次收到死亡通知：载荷更新，但接受时间保持最初值（不能让保留期被无限刷新）
        queue.put(victim, "second", 1800L);
        assertEquals("second", queue.get(victim).payload());
        assertEquals(100L, queue.get(victim).acceptedGameTime());
        assertEquals(1, queue.size());
    }

    @Test
    @DisplayName("移除与清理")
    void removeAndClear() {
        PendingDeathQueue<String> queue = new PendingDeathQueue<>();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        queue.put(a, "a", 0L);
        queue.put(b, "b", 0L);

        queue.remove(a);
        assertFalse(queue.contains(a));
        assertNull(queue.get(a));
        assertEquals(1, queue.size());

        queue.clear();
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("保留上限：超过上限的候选被丢弃，未超限的保留")
    void expire() {
        PendingDeathQueue<String> queue = new PendingDeathQueue<>();
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        queue.put(old, "old", 0L);
        queue.put(recent, "recent", 900L);

        int expired = queue.expire(1001L, 1000L);
        assertEquals(1, expired);
        assertFalse(queue.contains(old), "超过保留上限的候选应被丢弃（内存有界）");
        assertTrue(queue.contains(recent));

        assertEquals(0, queue.expire(1900L, 1000L));
        assertEquals(1, queue.expire(1901L, 1000L));
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("snapshotIds 允许在遍历中安全移除")
    void snapshotAllowsRemovalDuringIteration() {
        PendingDeathQueue<String> queue = new PendingDeathQueue<>();
        for (int i = 0; i < 5; i++) {
            queue.put(UUID.randomUUID(), "v" + i, i);
        }
        int seen = 0;
        for (UUID id : queue.snapshotIds()) {
            seen++;
            queue.remove(id);
        }
        assertEquals(5, seen);
        assertEquals(0, queue.size());
    }

    @Test
    @DisplayName("null 输入安全")
    void nullSafety() {
        PendingDeathQueue<String> queue = new PendingDeathQueue<>();
        queue.put(null, "x", 0L);
        assertEquals(0, queue.size());
        assertNull(queue.get(null));
        assertFalse(queue.contains(null));
        queue.remove(null);
        assertNotNull(queue.snapshotIds());
    }

    @Test
    @DisplayName("默认保留上限足以覆盖原版末影龙的延迟死亡阶段")
    void defaultRetentionCoversVanillaDragon() {
        // 末影龙死亡动画为 200 tick；保留上限必须显著大于它。
        assertTrue(PendingDeathQueue.DEFAULT_RETENTION_TICKS >= 400L,
                "保留上限应远大于末影龙 200 tick 的死亡动画");
    }
}
