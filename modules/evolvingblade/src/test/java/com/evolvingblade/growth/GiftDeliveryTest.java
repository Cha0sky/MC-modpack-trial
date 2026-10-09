package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 赠剑槽位查找的纯 JVM 测试。
 *
 * <p>这一层之所以被单独抽出来测，是因为审查确认的真实缺陷在
 * {@code Inventory.add()} 的返回值语义上：创造模式且背包已满时它会返回 {@code true} 并清空入参，
 * 却什么都没放进背包。因此交付判定必须基于"是否真的存在空槽位"这一事实。
 */
class GiftDeliveryTest {

    private static List<Boolean> inventory(int size, int... occupiedSlots) {
        List<Boolean> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(Boolean.FALSE);
        }
        for (int slot : occupiedSlots) {
            slots.set(slot, Boolean.TRUE);
        }
        return slots;
    }

    @Test
    @DisplayName("空背包返回 0 号槽位")
    void emptyInventory() {
        List<Boolean> slots = inventory(GiftDelivery.MAIN_INVENTORY_SLOTS);
        assertEquals(0, GiftDelivery.findFreeSlot(slots.size(), index -> !slots.get(index)));
    }

    @Test
    @DisplayName("全满（36 格）返回 -1：这正是创造模式也必须保持 PENDING 的判据")
    void fullInventory() {
        List<Boolean> slots = new ArrayList<>();
        for (int i = 0; i < GiftDelivery.MAIN_INVENTORY_SLOTS; i++) {
            slots.add(Boolean.TRUE);
        }
        assertEquals(-1, GiftDelivery.findFreeSlot(slots.size(), index -> !slots.get(index)));
    }

    @Test
    @DisplayName("返回第一个空位，且不会越过主背包边界")
    void firstFreeSlotWithinBounds() {
        List<Boolean> slots = inventory(GiftDelivery.MAIN_INVENTORY_SLOTS, 0, 1, 2, 4);
        assertEquals(3, GiftDelivery.findFreeSlot(slots.size(), index -> !slots.get(index)));

        // 只有最后一格空着
        List<Boolean> onlyLast = inventory(GiftDelivery.MAIN_INVENTORY_SLOTS);
        for (int i = 0; i < GiftDelivery.MAIN_INVENTORY_SLOTS - 1; i++) {
            onlyLast.set(i, Boolean.TRUE);
        }
        assertEquals(35, GiftDelivery.findFreeSlot(onlyLast.size(), index -> !onlyLast.get(index)));
        // 明确只扫描 36 个主背包槽位（盔甲 36..39 与副手 40 不参与）
        assertEquals(36, GiftDelivery.MAIN_INVENTORY_SLOTS);
    }

    @Test
    @DisplayName("非法入参安全返回 -1")
    void invalidArguments() {
        assertEquals(-1, GiftDelivery.findFreeSlot(0, index -> true));
        assertEquals(-1, GiftDelivery.findFreeSlot(-5, index -> true));
        assertEquals(-1, GiftDelivery.findFreeSlot(36, null));
    }
}
