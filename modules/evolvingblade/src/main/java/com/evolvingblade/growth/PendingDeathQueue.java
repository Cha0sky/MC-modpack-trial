package com.evolvingblade.growth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 待核实的死亡候选队列（纯 Java，可单测）。
 *
 * <p>只保存"已经接受但还没终局"的候选；<b>不再保存长期的已入账标记</b>——
 * 去重改为写在实体自身上的永久 attachment（见 {@code EBAttachments.KILL_CREDITED}），
 * 因此不存在"TTL/容量上限导致留存尸体可以二次入账"的缺陷。
 *
 * <p>队列本身仍然有界：超过 {@link #DEFAULT_RETENTION_TICKS} 仍未终局的候选会被丢弃，
 * 这让"对着活体手动 post 的合成死亡事件"不会无限期占用内存。
 */
public final class PendingDeathQueue<T> {

    /** 候选最长保留时间：足以覆盖原版末影龙从"进入 DYING 阶段"到"血量归零 / 被移除"的整个过程。 */
    public static final long DEFAULT_RETENTION_TICKS = 2400L;

    /** 一条候选：载荷 + 首次接受时的时间戳（统一使用服务器主世界 gameTime）。 */
    public record Entry<T>(T payload, long acceptedGameTime) {}

    private final Map<UUID, Entry<T>> entries = new HashMap<>();

    /**
     * 记录一条候选。同一实体再次发来死亡通知时<b>覆盖</b>为最新事实
     * （保留最初接受时间，避免刷新不断延长保留期）。
     */
    public void put(UUID victimId, T payload, long gameTime) {
        if (victimId == null) {
            return;
        }
        Entry<T> existing = this.entries.get(victimId);
        long acceptedAt = existing != null ? existing.acceptedGameTime() : gameTime;
        this.entries.put(victimId, new Entry<>(payload, acceptedAt));
    }

    public Entry<T> get(UUID victimId) {
        return victimId == null ? null : this.entries.get(victimId);
    }

    public boolean contains(UUID victimId) {
        return victimId != null && this.entries.containsKey(victimId);
    }

    public void remove(UUID victimId) {
        if (victimId != null) {
            this.entries.remove(victimId);
        }
    }

    /** 迭代用的 id 快照，允许在遍历过程中安全地移除条目。 */
    public List<UUID> snapshotIds() {
        return new ArrayList<>(this.entries.keySet());
    }

    /**
     * 丢弃超过保留上限的候选。
     *
     * @return 被丢弃的候选数量
     */
    public int expire(long gameTime, long retentionTicks) {
        int removed = 0;
        for (UUID id : snapshotIds()) {
            Entry<T> entry = this.entries.get(id);
            if (entry != null && gameTime - entry.acceptedGameTime() > retentionTicks) {
                this.entries.remove(id);
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return this.entries.size();
    }

    public void clear() {
        this.entries.clear();
    }
}
