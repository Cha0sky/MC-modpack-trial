package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GiftState;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 赠剑与模式选择契约的<b>服务器集成</b>测试（跑在 {@code runGameTestServer} 的无头服务器上）。
 *
 * <p>覆盖审查指出的两个真实缺陷：
 * <ul>
 *   <li>创造模式且背包已满时 {@code Inventory.add()} 会返回 {@code true} 却什么都不放——
 *       赠剑仍必须保持 {@link com.evolvingblade.growth.GiftState#PENDING}；</li>
 *   <li>{@code selectMode(player, null)} 必须被显式拒绝。</li>
 * </ul>
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class GiftModeGameTests {

    private static final String TEMPLATE = "empty";

    private GiftModeGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    @SuppressWarnings("removal")
    public static void fullSurvivalInventoryKeepsGiftPending(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false; // 生存模式
        fillMainInventory(player);
        GrowthService.forceMode(player, GrowthMode.STANDARD);

        boolean delivered = GrowthService.requestGift(player);
        helper.assertFalse(delivered, "生存模式背包满时不得交付");
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.PENDING,
                "背包满时应保持 PENDING，实际=" + GrowthService.data(player).giftState());
        helper.assertTrue(countStarEater(player) == 0, "背包满时不得放入任何噬星之刃");
        helper.assertTrue(countDroppedStarEater(helper) == 0, "背包满时不得掉落任何噬星之刃");

        // 腾出一格后重试：必须恰好一把
        player.getInventory().setItem(0, ItemStack.EMPTY);
        boolean retried = GrowthService.requestGift(player);
        helper.assertTrue(retried, "有空位后应交付成功");
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.GRANTED,
                "成功后应标记 GRANTED");
        helper.assertTrue(countStarEater(player) == 1, "必须恰好一把，实际=" + countStarEater(player));

        // 再次重试绝不能多发
        GrowthService.tryDeliverGift(player);
        GrowthService.requestGift(player);
        helper.assertTrue(countStarEater(player) == 1, "重复调用不得再发第二把");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    @SuppressWarnings("removal")
    public static void fullCreativeInventoryKeepsGiftPending(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = true; // 创造模式：Inventory.add() 会返回 true 却什么都不放
        fillMainInventory(player);
        GrowthService.forceMode(player, GrowthMode.STANDARD);

        boolean delivered = GrowthService.requestGift(player);
        helper.assertFalse(delivered,
                "创造模式背包满时不得交付（这正是 Inventory.add()==true 的陷阱）");
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.PENDING,
                "创造模式背包满时应保持 PENDING，实际=" + GrowthService.data(player).giftState());
        helper.assertTrue(countStarEater(player) == 0, "创造模式背包满时不得凭空获得噬星之刃");
        helper.assertTrue(countDroppedStarEater(helper) == 0, "不得掉落");

        // 腾出一格后重试：恰好一把
        player.getInventory().setItem(11, ItemStack.EMPTY);
        helper.assertTrue(GrowthService.requestGift(player), "创造模式有空位后应交付成功");
        helper.assertTrue(countStarEater(player) == 1, "必须恰好一把，实际=" + countStarEater(player));
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.GRANTED,
                "成功后应标记 GRANTED");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void nullModeIsRejected(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        boolean rejectedSelect = false;
        try {
            GrowthService.selectMode(player, null);
        } catch (NullPointerException expected) {
            rejectedSelect = true;
        }
        helper.assertTrue(rejectedSelect, "selectMode(player, null) 必须抛 NPE");
        helper.assertFalse(GrowthService.data(player).hasMode(), "失败的调用不得让玩家进入已选择状态");

        boolean rejectedForce = false;
        try {
            GrowthService.forceMode(player, null);
        } catch (NullPointerException expected) {
            rejectedForce = true;
        }
        helper.assertTrue(rejectedForce, "forceMode(player, null) 必须抛 NPE");
        helper.assertFalse(GrowthService.data(player).hasMode(), "失败的调用不得改变模式");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    @SuppressWarnings("removal")
    public static void growthAttachmentRoundTrip(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.LONG);
        GrowthService.creditHostileKill(player);
        GrowthService.creditBossKill(player);

        GrowthData data = GrowthService.data(player);
        helper.assertTrue(data.hostileKills() == 1L, "敌对击杀应为 1，实际=" + data.hostileKills());
        helper.assertTrue(data.bossKills() == 1L, "Boss 击杀应为 1，实际=" + data.bossKills());
        long expected = EBConfig.settings().hostilePointWeight() + EBConfig.settings().bossPointWeight();
        helper.assertTrue(data.growthPoints() == expected,
                "成长点应为 " + expected + "，实际=" + data.growthPoints());
        helper.assertTrue(GrowthService.stage(player) == 0,
                "长期模式 1 点击杀仍在阶段 0，实际=" + GrowthService.stage(player));
        helper.succeed();
    }

    private static void fillMainInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    /** 统计玩家所有容器槽位里的噬星之刃总数（含盔甲/副手，防止误放）。 */
    private static int countStarEater(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(EBItems.starEater())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 统计玩家周围掉落的噬星之刃物品实体数量。 */
    private static int countDroppedStarEater(GameTestHelper helper) {
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(64.0D),
                        entity -> entity.getItem().is(EBItems.starEater()))
                .size();
    }
}
