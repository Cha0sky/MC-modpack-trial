package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 实体级回归：剑气的<b>真实墙体阻挡</b>，以及"同一把剑在不同持有者手上用谁的阶段" +
 * "卸下剑只移除本模组的修饰符"。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class StageAndWaveGameTests {

    /** 标准模式终阶门槛。 */
    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;

    private StageAndWaveGameTests() {}

    private static ServerPlayer swordPlayerAt(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, FINAL_POINTS, FINAL_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        // 站在指定位置、朝 +Z 方向平视（yaw=0 → look=(0,0,1)）。
        net.minecraft.world.phys.Vec3 pos = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(
                BlockPos.containing(x, y, z)));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        return player;
    }

    /** 记录"某个受害者受到过的伤害来源"，只按 UUID 作用域。 */
    public static final class SourceTrace {
        volatile java.util.UUID victim;
        volatile String source = null;
        volatile float amount;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
            java.util.UUID current = this.victim;
            if (current != null && current.equals(event.getEntity().getUUID())) {
                this.source = event.getSource().typeHolder().unwrapKey()
                        .map(key -> key.location().toString()).orElse("unknown");
                this.amount = event.getAmount();
            }
        }
    }

    private static final SourceTrace TRACE = new SourceTrace();

    static {
        NeoforgeHolder.ensureRegistered();
    }

    /** 单独持有注册逻辑，避免在测试类静态初始化里直接写副作用。 */
    private static final class NeoforgeHolder {
        private static void ensureRegistered() {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(TRACE);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void waveStopsAtWallAndNeverHitsWhatIsBehindIt(GameTestHelper helper) {
        ServerPlayer player = swordPlayerAt(helper, 4, 2, 1);
        // 一堵 5x3 的实心墙，位于玩家正前方（z=3）。
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        // 用尸壳（亡灵但不会被阳光点燃）避免"夹具在等待窗口里被环境伤害"，
        // 这正是之前墙后目标掉 1 点血的真实原因。
        net.minecraft.world.entity.monster.Husk inFront = helper.spawn(EntityType.HUSK, 4, 1, 2);
        inFront.setNoAi(true);
        net.minecraft.world.entity.monster.Husk behindWall = helper.spawn(EntityType.HUSK, 4, 1, 4);
        behindWall.setNoAi(true);
        behindWall.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        behindWall.setHealth(500.0F);
        behindWall.setRemainingFireTicks(0);
        inFront.setRemainingFireTicks(0);

        helper.runAfterDelay(2L, () -> {
            // 施法前夹具自检：两个目标都必须满血、未着火（证明没有任何环境伤害先发生）。
            helper.assertTrue(Math.abs(behindWall.getHealth() - 500.0F) < 0.01F
                            && Math.abs(inFront.getHealth() - inFront.getMaxHealth()) < 0.01F,
                    "sanity: both targets must be undamaged before the wave (behind=" + behindWall.getHealth()
                            + ", front=" + inFront.getHealth() + ")");
            helper.assertTrue(!behindWall.isOnFire() && behindWall.getRemainingFireTicks() == 0,
                    "sanity: the fixture must not be burning (fireTicks=" + behindWall.getRemainingFireTicks() + ")");
            TRACE.victim = inFront.getUUID();
            TRACE.source = null;
            helper.assertTrue(StarEaterAbilities.tryWave(player), "sanity: the wave must actually fire");
            helper.runAfterDelay(2L, () -> {
                helper.assertTrue(!inFront.isAlive() || inFront.getHealth() < inFront.getMaxHealth(),
                        "the wave must damage the target in front of the player (hp=" + inFront.getHealth() + ")");
                helper.assertTrue("evolvingblade:sword_ability".equals(TRACE.source),
                        "the only damage source on the front target must be our wave ability (source="
                                + TRACE.source + ", amount=" + TRACE.amount + ")");
                helper.assertTrue(Math.abs(behindWall.getHealth() - 500.0F) < 0.01F,
                        "a wall must stop the wave: the target behind it must be untouched (hp="
                                + behindWall.getHealth() + ")");
                // 墙体本身不被破坏。
                helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.STONE),
                        "the wave must not modify terrain");
                TRACE.victim = null;
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sameStackUsesHolderStageAndUnequipRemovesOnlyOurModifier(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(first, GrowthMode.STANDARD);
        GrowthService.setCounters(first, 0L, 0L); // 阶段 0
        GrowthService.setCounters(first, FINAL_POINTS, FINAL_BOSSES); // 阶段 7（同一实例，相同物品栈）
        ItemStack stack = new ItemStack(EBItems.starEater());
        first.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // 原版在实体 tick 里才把物品自带的属性修饰符挂到装备槽上（detectEquipmentUpdates）。
        // 真实游戏里这发生在同一 tick 内、任何战斗读取之前；测试里必须真正推进一次玩家 tick，
        // 否则读到的是"物品修饰符还没生效"的中间状态。（ServerPlayer.tick() 不会走到 Player.tick()，用 doTick()。）
        first.doTick();
        GrowthService.syncAfterChange(first);
        int stage = GrowthService.stage(first);
        double damage = first.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = first.getAttributeValue(Attributes.ATTACK_SPEED);
        double expectedDamage = com.evolvingblade.config.EBConfig.settings().stageAttackDamage(stage);
        double expectedSpeed = com.evolvingblade.config.EBConfig.settings().stageAttackSpeed(stage);
        helper.assertTrue(Math.abs(damage - expectedDamage) < 0.001D,
                "the holder's stage must decide the real attribute value (stage=" + stage + ", damage=" + damage
                        + ", expected=" + expectedDamage + ")");
        helper.assertTrue(Math.abs(speed - expectedSpeed) < 0.001D,
                "attack speed must follow the holder stage (speed=" + speed + ", expected=" + expectedSpeed + ")");

        // 第三方（其它模组）修饰符：卸下本剑后必须原样保留，只移除我们自己的 id。
        ResourceLocation thirdPartyId = ResourceLocation.fromNamespaceAndPath("ebtest", "third_party");
        first.getAttribute(Attributes.ATTACK_DAMAGE).addOrUpdateTransientModifier(
                new AttributeModifier(thirdPartyId, 3.0D, AttributeModifier.Operation.ADD_VALUE));
        first.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        first.doTick();
        com.evolvingblade.combat.StageAttributes.clear(first);
        double afterUnequip = first.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double thirdPartyOnly = 1.0D + 3.0D; // 玩家基础 1.0 + 第三方 3.0
        helper.assertTrue(Math.abs(afterUnequip - thirdPartyOnly) < 0.001D,
                "unequipping must remove only our own modifiers (value=" + afterUnequip
                        + ", expected=" + thirdPartyOnly + ")");
        helper.assertTrue(first.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(thirdPartyId) != null
                        || first.getAttribute(Attributes.ATTACK_DAMAGE).hasModifier(thirdPartyId),
                "the third-party modifier must survive our cleanup");
        helper.succeed();
    }
}
