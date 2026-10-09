package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StageAttributes;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 属性数学回归：阶段基线用<b>代数差值</b>，第三方的 ADD 与乘法都叠加在阶段基线之上；
 * base 值改动不被重置；重复协调/装备不累加；阶段 0 只移除本模组修饰符。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class AttributeMathGameTests {

    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;
    private static final ResourceLocation THIRD_ADD =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_add");
    private static final ResourceLocation THIRD_MULT =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_mult");

    private AttributeMathGameTests() {}

    private static ServerPlayer stagePlayer(GameTestHelper helper, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void stageTotalThenThirdPartyAddAndMultiplierStack(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(player);
        double plain = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(plain - 1500.0D) < 0.01D,
                "stage 7 baseline total must be 1500 (value=" + plain + ")");

        // 第三方 +7 ADD：叠加在阶段基线之上 → 1507。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_ADD, 7.0D, AttributeModifier.Operation.ADD_VALUE));
        GrowthService.syncAfterChange(player);
        double withAdd = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(withAdd - 1507.0D) < 0.01D,
                "third-party ADD must stack on top of the stage total (value=" + withAdd + ", expected=1507)");

        // 第三方 1.5× TOTAL 乘法：必须是 (阶段总值+第三方ADD)*1.5，而不是把乘法结果减掉。
        // 注意 ATTACK_DAMAGE 是 RangedAttribute，上限 2048；为了不触发钳制，这里用阶段 3（48）。
        ServerPlayer mid = stagePlayer(helper, 350L, 1L);
        mid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(mid);
        double midBase = mid.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(midBase - 48.0D) < 0.01D,
                "sanity: stage 3 total must be 48 (value=" + midBase + ")");
        mid.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_ADD, 7.0D, AttributeModifier.Operation.ADD_VALUE));
        GrowthService.syncAfterChange(mid);
        helper.assertTrue(Math.abs(mid.getAttributeValue(Attributes.ATTACK_DAMAGE) - 55.0D) < 0.01D,
                "third-party ADD must stack on the stage 3 total (value="
                        + mid.getAttributeValue(Attributes.ATTACK_DAMAGE) + ", expected=55)");
        mid.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_MULT, 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        GrowthService.syncAfterChange(mid);
        double withMult = mid.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(withMult - 55.0D * 1.5D) < 0.01D,
                "a third-party 1.5x multiplier must multiply the whole total (value=" + withMult
                        + ", expected=" + (55.0D * 1.5D) + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void repeatedReconcileAndEquipNeverAccumulate(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        ItemStack stack = new ItemStack(EBItems.starEater());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (int i = 0; i < 5; i++) {
            GrowthService.syncAfterChange(player);
        }
        player.doTick();
        GrowthService.syncAfterChange(player);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(damage - 1500.0D) < 0.01D && Math.abs(speed - 2.4D) < 0.01D,
                "repeated reconcile/equip must not accumulate (damage=" + damage + ", speed=" + speed + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void customBaseValueIsPreservedAndStageStillRelative(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(player);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(damage - 1509.0D) < 0.01D,
                "a custom base attack value must not be reset, and the stage curve stays relative"
                        + " (value=" + damage + ", expected=1509 = 1500 + (10-1))");
        helper.assertTrue(Math.abs(player.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() - 10.0D) < 1.0E-6D,
                "our reconciliation must never rewrite the attribute base value");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void stageZeroRemovesOnlyOurModifierAndKeepsBaseline(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, 0L, 0L);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        // 阶段 A：原版装备 tick 还没跑（保留槽位缺席）。
        // 此时"当前物品声明的那一份基线"还没进总值，本模组用<b>独立的补偿 id</b> 补上；
        // 阶段增量必须为 0（不得存在 STAGE_ID 修饰符）。
        StageAttributes.reconcile(player);
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(StageAttributes.DAMAGE_ID) == null,
                "stage 0 must never carry a stage-delta modifier (delta = 0)");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_SPEED)
                        .getModifier(StageAttributes.SPEED_ID) == null,
                "stage 0 must never carry a speed stage-delta modifier");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(StageAttributes.BASELINE_DAMAGE_ID) != null,
                "before the equip tick the missing baseline must be compensated under OUR OWN id");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7.0D) < 0.01D,
                "stage 0 total must already be 7 before the equip tick (value="
                        + player.getAttributeValue(Attributes.ATTACK_DAMAGE) + ")");

        // 阶段 B：跑一次真实装备 tick（原版挂上保留槽位），再按正常服务器调度协调一次。
        player.doTick();
        StageAttributes.reconcile(player);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(damage - 7.0D) < 0.01D && Math.abs(speed - 1.6D) < 0.01D,
                "stage 0 must be exactly the vanilla baseline once the slot is settled (damage=" + damage
                        + ", speed=" + speed + ")");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(StageAttributes.DAMAGE_ID) == null,
                "stage 0 must not keep our stage modifier (delta 0)");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(StageAttributes.BASELINE_DAMAGE_ID) == null,
                "our baseline compensation must be gone once vanilla supplied the slot"
                        + " (we must never leave a duplicate baseline)");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID) != null,
                "stage 0 must keep the vanilla reserved baseline modifier");
        helper.assertTrue(Math.abs(player.getAttribute(Attributes.ATTACK_DAMAGE)
                        .getModifier(net.minecraft.world.item.Item.BASE_ATTACK_DAMAGE_ID).amount()
                        - com.evolvingblade.item.StarEaterItem.BASE_DAMAGE_MODIFIER) < 1.0E-6D,
                "the vanilla reserved slot must hold the item's own declared baseline");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void speedMultiplierAppliesNormallyOnTopOfStageCurve(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(player);
        player.getAttribute(Attributes.ATTACK_SPEED).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_MULT, 0.2D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        GrowthService.syncAfterChange(player);
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(speed - 2.4D * 1.2D) < 0.01D,
                "attack speed multipliers must apply normally on top of the stage curve (value=" + speed
                        + ", expected=" + (2.4D * 1.2D) + ")");
        helper.succeed();
    }
}
