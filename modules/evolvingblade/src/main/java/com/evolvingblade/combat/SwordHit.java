package com.evolvingblade.combat;

import java.util.UUID;

/**
 * "本剑的一次有效命中"的临时事实，记录在<b>受害者实体</b>上。
 *
 * <p>为什么记在受害者而不是玩家/物品上：
 * <ul>
 *   <li>击杀归属发生在<b>死亡</b>时刻，玩家可能早已把剑换下——记在物品上会丢失来源
 *       （SPEC 要求"换成别的武器命中后换回剑不回复，而真实用剑命中后换下剑不丢失来源"）；</li>
 *   <li>记在受害者身上可以精确回答"这具尸体最后一次被本剑命中是谁、什么时候"，
 *       与之后是否取消、是否被别的伤害补刀无关。</li>
 * </ul>
 *
 * <p>纯 Java（只用 {@link UUID}），可单测。
 */
public record SwordHit(UUID player, long gameTime) {

    /** 击杀回血的来源有效窗口（tick）：足够覆盖死亡事件到 tick 末的核实。 */
    public static final long VALID_WINDOW_TICKS = 20L;

    /** 空值哨兵：attachment 默认值必须非 null，用"不可能的时间戳"表示"没有来源"。 */
    public static final SwordHit NONE = new SwordHit(null, Long.MIN_VALUE);

    public static SwordHit of(UUID player, long gameTime) {
        return new SwordHit(player, gameTime);
    }

    /** 是否记录了一次真实来源。 */
    public boolean isPresent() {
        return this.player != null && this.gameTime != Long.MIN_VALUE;
    }

    /** 该命中是否仍可视为这次击杀的剑来源。 */
    public boolean isStillValidFor(long deathGameTime, long windowTicks) {
        if (!isPresent()) {
            return false;
        }
        long elapsed = deathGameTime - this.gameTime;
        return elapsed >= 0L && elapsed <= windowTicks;
    }
}
