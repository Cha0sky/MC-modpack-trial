package com.evolvingblade.combat;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 技能冷却与"每玩家+目标"速率限制的纯逻辑（零 Minecraft 依赖，可纯 JVM 单测）。
 *
 * <p>时间单位一律是<b>服务器主世界 tick</b>（由调用方传入），保证与存档、重连、死亡无关；
 * 冷却状态持久化在玩家 attachment 上，速率限制表则是有界且会过期的临时表。
 */
public final class AbilityState {

    /** 速率限制表的上限。表<b>严格</b>不超过该值：满了就拒绝新目标，绝不淘汰仍生效的条目。 */
    public static final int DEFAULT_MAX_RATE_ENTRIES = 256;

    private AbilityState() {}

    /** 冷却是否已就绪（{@code now >= readyAt}）。 */
    public static boolean isReady(long now, long readyAt) {
        return now >= readyAt;
    }

    /** 剩余冷却 tick（已就绪返回 0）。 */
    public static long remaining(long now, long readyAt) {
        return Math.max(0L, readyAt - now);
    }

    /** 由当前时间与冷却时长算出新的就绪时刻（饱和加法，避免溢出）。 */
    public static long readyAt(long now, int cooldownTicks) {
        long ready = now + Math.max(0, cooldownTicks);
        return ready < now ? Long.MAX_VALUE : ready;
    }

    /**
     * 检查并占用一次"每玩家+目标"速率限制。
     *
     * <p><b>严格有界</b>：调用返回后表大小恒为 {@code <= maxEntries}。
     * 当表已满且全部条目仍在生效时，<b>拒绝</b>新目标的本次请求，而不是淘汰一个仍然生效的条目——
     * 淘汰生效条目会让该目标在 20 tick 内被再次加成，等于绕过限速。
     *
     * @param now       当前服务器 tick（统一使用主世界 gameTime）
     * @param rateTicks 最小间隔（tick）
     * @return 允许本次操作返回 {@code true}，并写入新的截止时间
     */
    public static boolean tryConsumeRate(Map<UUID, Long> rateMap, UUID target, long now, int rateTicks, int maxEntries) {
        if (rateMap == null || target == null) {
            return false;
        }
        pruneExpired(rateMap, now, maxEntries);
        Long readyAt = rateMap.get(target);
        if (readyAt != null && !isReady(now, readyAt)) {
            return false;
        }
        if (readyAt == null && maxEntries >= 0 && rateMap.size() >= maxEntries) {
            // 表满且没有任何可回收的过期条目：保守拒绝，绝不淘汰仍生效的条目。
            return false;
        }
        rateMap.put(target, readyAt(now, rateTicks));
        return true;
    }

    /**
     * 清掉已过期的速率条目。
     *
     * <p>只清"已经可以再次触发"的条目；<b>绝不</b>为了让表变小而淘汰仍在冷却中的条目
     * （那会让对应目标提前恢复加成）。因此正常情况下表大小不会超过插入时的上限。
     *
     * @return 被清理的条目数
     */
    public static int pruneExpired(Map<UUID, Long> rateMap, long now, int maxEntries) {
        if (rateMap == null) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<UUID, Long>> iterator = rateMap.entrySet().iterator();
        while (iterator.hasNext()) {
            long deadline = iterator.next().getValue();
            if (isReady(now, deadline)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
}
