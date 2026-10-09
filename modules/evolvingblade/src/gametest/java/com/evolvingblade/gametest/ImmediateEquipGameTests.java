package com.evolvingblade.gametest;

import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StageAttributes;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBDataComponents;
import com.evolvingblade.registry.EBItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * "换到本剑后<b>立刻</b>攻击"的真实路径回归（快捷栏切换 + 同 tick 攻击，没有装备 tick 的中间状态）。
 *
 * <p>真实路径：`ServerGamePacketListenerImpl` 处理快捷栏切换 → 同一 tick 内处理攻击 →
 * `Player.attack()` 读取属性。此时原版物品自带的阶段 0 基线（+6 / −2.4）**还没挂到属性上**，
 * 所以"按差值施加"的旧实现会让第一次攻击读到 1494 而不是 1500。这些测试直接断言攻击时
 * `LivingIncomingDamageEvent` 记录到的真实数值，并验证第三方修饰符被保留、伪造的显示组件不改变强度。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class ImmediateEquipGameTests {

    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;
    private static final ResourceLocation THIRD_PARTY =
            ResourceLocation.fromNamespaceAndPath("ebtest", "third_party_immediate");

    private ImmediateEquipGameTests() {}

    /** 按受害者/攻击者 UUID 作用域的真实数值捕获监听器。 */
    public static final class DamageCapture {
        volatile UUID victim;
        volatile float amount = Float.NaN;
        volatile UUID attacker;
        volatile float attackDamage = Float.NaN;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(LivingIncomingDamageEvent event) {
            UUID current = this.victim;
            if (current != null && current.equals(event.getEntity().getUUID())) {
                this.amount = event.getAmount();
            }
        }

        /**
         * 在 AttackEntityEvent 的 **LOWEST** 优先级读取攻击力：本模组的协调在 HIGHEST 完成，
         * 而原版在事件返回之后才读取该属性，因此这里读到的就是这次攻击真正会用的数值。
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
            UUID current = this.attacker;
            if (current != null && event.getEntity() instanceof ServerPlayer player
                    && current.equals(player.getUUID())) {
                this.attackDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }
        }

        void reset() {
            this.victim = null;
            this.amount = Float.NaN;
            this.attacker = null;
            this.attackDamage = Float.NaN;
        }
    }

    private static final DamageCapture CAPTURE = new DamageCapture();

    static {
        NeoForge.EVENT_BUS.register(CAPTURE);
    }

    private static ServerPlayer stagePlayer(GameTestHelper helper, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        return player;
    }

    private static Husk tankyVictim(GameTestHelper helper) {
        Husk victim = helper.spawn(EntityType.HUSK, 4, 1, 3);
        victim.setNoAi(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0D);
        victim.setHealth(100_000.0F);
        return victim;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void immediateAttackAfterSwapToSwordUsesFullStageTotal(GameTestHelper helper) {
        ServerPlayer player = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        // 手上先拿铁剑并让它完成一次装备 tick（模拟"换剑之前"的真实状态）。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.doTick();
        // 第三方修饰符：必须被保留。
        player.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(THIRD_PARTY, 7.0D, AttributeModifier.Operation.ADD_VALUE));
        Husk victim = tankyVictim(helper);
        CAPTURE.reset();
        CAPTURE.victim = victim.getUUID();
        CAPTURE.attacker = player.getUUID();

        // 同一 tick 内切换到本剑并立刻攻击：没有装备 tick，物品基线尚未挂上。
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        player.attack(victim);

        double expected = com.evolvingblade.config.EBConfig.settings().stageAttackDamage(7) + 7.0D;
        helper.assertTrue(!Float.isNaN(CAPTURE.attackDamage),
                "sanity: the attack must fire AttackEntityEvent");
        helper.assertTrue(Math.abs(CAPTURE.attackDamage - expected) < 0.01D,
                "the attribute read by the attack must already be the full stage total without an equipment tick"
                        + " (attackDamage=" + CAPTURE.attackDamage + ", expected=" + expected + ")");
        helper.assertTrue(!Float.isNaN(CAPTURE.amount) && CAPTURE.amount > 0.0F,
                "sanity: the attack must actually deal damage (dealt=" + CAPTURE.amount + ")");
        double value = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(value - expected) < 0.01D,
                "the attribute must be correct right after the swap, without any equipment tick (value=" + value
                        + ", expected=" + expected + ")");
        // 再推进一次装备 tick：原版基线挂上后总值必须仍然正确（不能变成 1506/1513）。
        player.doTick();
        GrowthService.syncAfterChange(player);
        double afterTick = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(afterTick - expected) < 0.01D,
                "after the equipment tick the total must stay correct (value=" + afterTick + ", expected=" + expected + ")");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(THIRD_PARTY),
                "the third-party modifier must survive our reconciliation");
        CAPTURE.reset();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sameStackUsesEachHolderStageWithoutDoubleCounting(GameTestHelper helper) {
        ServerPlayer advanced = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        ServerPlayer beginner = stagePlayer(helper, 0L, 0L);
        ItemStack shared = new ItemStack(EBItems.starEater());

        advanced.setItemInHand(InteractionHand.MAIN_HAND, shared);
        GrowthService.syncAfterChange(advanced);
        double advancedDamage = advanced.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double advancedSpeed = advanced.getAttributeValue(Attributes.ATTACK_SPEED);
        advanced.doTick();
        GrowthService.syncAfterChange(advanced);
        helper.assertTrue(Math.abs(advancedDamage - 1500.0D) < 0.01D && Math.abs(advancedSpeed - 2.4D) < 0.01D,
                "stage 7 holder must read 1500 / 2.4 immediately (damage=" + advancedDamage + ", speed=" + advancedSpeed + ")");
        helper.assertTrue(Math.abs(advanced.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1500.0D) < 0.01D,
                "stage 7 total must stay 1500 after the equipment tick (value="
                        + advanced.getAttributeValue(Attributes.ATTACK_DAMAGE) + ")");

        // 同一个物品栈交给阶段 0 的玩家：强度必须变成 7 / 1.6（不带走旧持有者的阶段）。
        advanced.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        StageAttributes.clear(advanced);
        beginner.setItemInHand(InteractionHand.MAIN_HAND, shared);
        GrowthService.syncAfterChange(beginner);
        double beginnerDamage = beginner.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double beginnerSpeed = beginner.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(beginnerDamage - 7.0D) < 0.01D && Math.abs(beginnerSpeed - 1.6D) < 0.01D,
                "the same stack must adopt the new holder's stage 0 (damage=" + beginnerDamage
                        + ", speed=" + beginnerSpeed + ")");
        beginner.doTick();
        GrowthService.syncAfterChange(beginner);
        helper.assertTrue(Math.abs(beginner.getAttributeValue(Attributes.ATTACK_DAMAGE) - 7.0D) < 0.01D
                        && Math.abs(beginner.getAttributeValue(Attributes.ATTACK_SPEED) - 1.6D) < 0.01D,
                "stage 0 totals must remain 7 / 1.6 after the equipment tick (damage="
                        + beginner.getAttributeValue(Attributes.ATTACK_DAMAGE) + ", speed="
                        + beginner.getAttributeValue(Attributes.ATTACK_SPEED) + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forgedStageComponentNeverChangesStrength(GameTestHelper helper) {
        ServerPlayer beginner = stagePlayer(helper, 0L, 0L);
        ItemStack forged = new ItemStack(EBItems.starEater());
        forged.set(EBDataComponents.STAGE.get(), 7); // 伪造显示组件
        beginner.setItemInHand(InteractionHand.MAIN_HAND, forged);
        GrowthService.syncAfterChange(beginner);
        double damage = beginner.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(damage - 7.0D) < 0.01D,
                "a forged stage component must never grant strength (damage=" + damage + ", expected=7.0)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void freshCraftAdoptsCurrentOwnerStage(GameTestHelper helper) {
        ServerPlayer advanced = stagePlayer(helper, FINAL_POINTS, FINAL_BOSSES);
        advanced.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.syncAfterChange(advanced);
        double damage = advanced.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = advanced.getAttributeValue(Attributes.ATTACK_SPEED);
        helper.assertTrue(Math.abs(damage - 1500.0D) < 0.01D && Math.abs(speed - 2.4D) < 0.01D,
                "a freshly crafted sword must adopt its owner's stage (damage=" + damage + ", speed=" + speed + ")");
        helper.succeed();
    }
}
