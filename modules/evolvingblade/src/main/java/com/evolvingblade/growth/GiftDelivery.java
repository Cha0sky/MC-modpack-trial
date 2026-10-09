package com.evolvingblade.growth;

import java.util.function.IntPredicate;

/**
 * 赠剑的<b>纯逻辑</b>部分：在"背包槽位"里找<b>真正可用</b>的空位。
 *
 * <p>为什么不能直接用 {@code Inventory.add(...)} 的返回值：1.21.1 的
 * {@code Inventory.add} 在 {@code player.hasInfiniteMaterials()}（创造模式）且背包已满时，
 * 会把传入的堆叠清空并返回 {@code true}——<b>实际什么都没放进去</b>。
 * 若以该返回值判定"已发放"，创造模式玩家的赠剑会被错误地标记为 {@link GiftState#GRANTED} 而永远拿不到剑。
 *
 * <p>因此这里只认"存在一个空槽位"这一硬事实；真正的写入与复核在
 * {@code GrowthService.tryDeliverGift} 里完成。
 */
public final class GiftDelivery {

    /** 玩家主背包槽位数（不含盔甲与副手），与 {@code Inventory.INVENTORY_SIZE} 一致。 */
    public static final int MAIN_INVENTORY_SLOTS = 36;

    private GiftDelivery() {}

    /**
     * 找到第一个空槽位。
     *
     * @param slotCount 要检查的槽位数量
     * @param isEmpty   判断第 i 个槽位是否为空
     * @return 空槽位下标；没有空位返回 {@code -1}
     */
    public static int findFreeSlot(int slotCount, IntPredicate isEmpty) {
        if (slotCount <= 0 || isEmpty == null) {
            return -1;
        }
        for (int slot = 0; slot < slotCount; slot++) {
            if (isEmpty.test(slot)) {
                return slot;
            }
        }
        return -1;
    }
}
