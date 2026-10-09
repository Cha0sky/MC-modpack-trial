package com.evolvingblade.growth;

/**
 * 一次"已捕获的死亡候选"在本 tick 末应当如何处理。
 *
 * <p>纯 Java 判定，不依赖 Minecraft；{@code EBKillCreditHandler} 只负责把世界里的
 * 事实翻译成布尔量，然后交给这里的决策表。
 *
 * <p>设计要点（来自审查）：
 * <ul>
 *   <li><b>最终取消契约优先</b>：保留原始 {@code LivingDeathEvent} 引用，在本 tick 末重新读取
 *       {@code isCanceled()}。同优先级更晚取消、且血量已经归零的情况必须被判否。</li>
 *   <li><b>不要求原版 DYING 姿态</b>：模组 Boss 可以完全不走原版死亡姿态；只要事件未被取消，
 *       并且出现"终局血量"或"被 KILLED 移出世界"这两个可接受事实之一，就应当入账。</li>
 *   <li><b>延迟死亡阶段</b>：原版末影龙在飞行/非坐姿阶段被致命一击后，会把血量恢复成 1
 *       并转入 DYING 阶段，稍后才归零。这种情况必须<b>保留候选继续等待</b>，既不能提前入账，
 *       也不能丢弃。</li>
 *   <li><b>活体合成事件</b>：对着活着且健康的实体手动 post 的死亡事件，永远不会满足终局条件，
 *       最终因超过保留上限被丢弃。</li>
 * </ul>
 */
public enum DeathDecision {
    /** 终局血量，或被以 {@code KILLED} 移出世界：可以入账。 */
    CREDIT,
    /** 事件在本 tick 末仍处于取消状态：撤销候选。 */
    DISCARD_CANCELLED,
    /** 这条命已经入账过（实体上的永久标记）：撤销候选。 */
    DISCARD_ALREADY_CREDITED,
    /** 被移出世界，但不是死亡（DISCARDED / UNLOADED_TO_CHUNK / CHANGED_DIMENSION）：撤销候选。 */
    DISCARD_REMOVED_NOT_KILLED,
    /** 长时间既没有终局也没有移除（例如对活体合成的死亡事件）：有界地撤销候选。 */
    DISCARD_RETENTION_EXPIRED,
    /** 处于延迟死亡阶段（血量被阶段逻辑临时恢复）：保留候选，下个 tick 继续核实。 */
    KEEP_WAITING;

    /**
     * 决策表。判定顺序：最终取消 &gt; 已入账 &gt; 已移除 &gt; 终局血量 &gt; 保留上限。
     *
     * @param eventCancelled   原始死亡事件在本 tick 末是否处于取消状态
     * @param alreadyCredited  该实体这条命是否已经入账
     * @param removed          实体是否已从世界移除
     * @param removedKilled    移除原因是否为 {@code KILLED}
     * @param terminal         血量是否已经归零（{@code isDeadOrDying()}）
     * @param retentionExpired 候选是否已超过保留上限
     */
    public static DeathDecision decide(boolean eventCancelled, boolean alreadyCredited, boolean removed,
            boolean removedKilled, boolean terminal, boolean retentionExpired) {
        if (eventCancelled) {
            return DISCARD_CANCELLED;
        }
        if (alreadyCredited) {
            return DISCARD_ALREADY_CREDITED;
        }
        if (removed) {
            return removedKilled ? CREDIT : DISCARD_REMOVED_NOT_KILLED;
        }
        if (terminal) {
            return CREDIT;
        }
        if (retentionExpired) {
            return DISCARD_RETENTION_EXPIRED;
        }
        return KEEP_WAITING;
    }

    /** 该决策是否意味着可以从待处理队列中移除候选。 */
    public boolean removesCandidate() {
        return this != KEEP_WAITING;
    }

    /** 该决策是否意味着应当写入成长。 */
    public boolean credits() {
        return this == CREDIT;
    }
}
