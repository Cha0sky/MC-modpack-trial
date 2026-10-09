package com.evolvingblade.gametest;

import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.event.EBKillCreditHandler;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.registry.EBItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 来源事实（击杀回血 / 吸血）的<b>实体级</b>回归：用真实 {@code player.attack(...)}、真实弹射物与真实事件取消验证。
 *
 * <p>覆盖独立复核指出的缺陷：
 * <ul>
 *   <li>用剑砍一下（非致命）后用弓射杀 → <b>不能</b>拿到击杀回血（旧实现会，因为剑来源事实没被清除）；</li>
 *   <li>真实用剑致命一击 → 回血 2；技能致命一击 → 回血 2；</li>
 *   <li>被事件取消的一击不留来源事实 → 之后用弓射杀不回血；</li>
 *   <li>满蓄力一击吸血，紧接着的同 tick 未满蓄力追击<b>不会</b>复用旧的满蓄力上下文；</li>
 *   <li>被驯服的宠物（含 owner 为空但 {@code isTame()} 为真）永远不是合法目标；</li>
 *   <li>FakePlayer 的击杀不给任何成长，也不回血。</li>
 * </ul>
 *
 * <p>夹具约定（沿用 PHASE 1 的教训）：只用 {@code empty} 结构、单一 defaultBatch、
 * 断言都在 {@code runAfterDelay} 之后读取、事件监听器按目标 UUID 限定作用域。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class SourceFactGameTests {

    /** 默认击杀回血值（读配置默认快照，避免测试里出现漂移的数字）。 */
    private static final float KILL_HEAL = (float) GrowthSettings.defaults().abilities().killHeal();
    /** 标准模式阶段 1 的门槛（成长点 50 / Boss 0）。 */
    private static final long STAGE1_POINTS = 50L;
    /** 标准模式阶段 3 的门槛（成长点 350 / Boss 1）。 */
    private static final long STAGE3_POINTS = 350L;

    private SourceFactGameTests() {}

    /**
     * 只取消指定受害者受到的伤害（按 UUID 限定作用域），用于复现"被取消的一击"。
     */
    public static final class OneShotDamageCanceller {
        volatile UUID target;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(LivingIncomingDamageEvent event) {
            UUID current = this.target;
            if (current != null && current.equals(event.getEntity().getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    private static final OneShotDamageCanceller CANCELLER = new OneShotDamageCanceller();

    static {
        NeoForge.EVENT_BUS.register(CANCELLER);
    }

    private static ServerPlayer swordPlayer(GameTestHelper helper, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        player.setHealth(10.0F);
        return player;
    }

    /** 高血量僵尸：保证非致命、且不会误触发击杀入账。 */
    private static Zombie tankyZombie(GameTestHelper helper, float health) {
        Zombie victim = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        victim.setNoAi(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        victim.setHealth(health);
        return victim;
    }

    /**
     * 一击必杀的受害者夹具。
     *
     * <p>用 {@link net.minecraft.world.entity.monster.Husk} 而不是僵尸：尸壳同属亡灵敌对生物，
     * 但<b>不会在白天被阳光点燃</b>。之前的僵尸夹具在 1 HP 的窗口里可能先被环境伤害（阳光/火焰）
     * 杀死，于是"我的致命一击"打在已经死掉的实体上、不再产生死亡事件——表现为
     * "victim.isAlive()==false 但击杀数没涨"的偶发失败（已在三次独立运行的第 1 次复现过该签名）。
     * 血量在<b>同一回调内、出手前</b>才设为 1，进一步消除环境伤害窗口。
     */
    private static Zombie lethalVictim(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Husk victim = helper.spawn(EntityType.HUSK, 3, 2, 3);
        victim.setNoAi(true);
        victim.setRemainingFireTicks(0);
        return victim;
    }

    /** 出手前的夹具自检：活着、正好 1 血、没有着火。失败信息里带上完整环境状态。 */
    private static void assertOneHealthTarget(GameTestHelper helper, net.minecraft.world.entity.LivingEntity victim,
            String phase) {
        helper.assertTrue(victim.isAlive() && Math.abs(victim.getHealth() - 1.0F) < 0.01F,
                "sanity(" + phase + "): the victim must be alive at exactly 1 HP"
                        + " (alive=" + victim.isAlive() + ", hp=" + victim.getHealth()
                        + ", onFire=" + victim.isOnFire() + ", fireTicks=" + victim.getRemainingFireTicks()
                        + ", seeSky=" + helper.getLevel().canSeeSky(victim.blockPosition())
                        + ", day=" + helper.getLevel().isDay() + ")");
        helper.assertTrue(!victim.isOnFire(),
                "sanity(" + phase + "): the fixture victim must not be burning"
                        + " (fireTicks=" + victim.getRemainingFireTicks() + ")");
    }

    /** 由该玩家射出的箭造成的伤害（真实弹射物来源，不是剑）。 */
    private static net.minecraft.world.damagesource.DamageSource arrowDamage(GameTestHelper helper,
            ServerPlayer shooter) {
        Arrow arrow = new Arrow(helper.getLevel(), shooter, new ItemStack(Items.ARROW), null);
        arrow.setPos(shooter.getX(), shooter.getY(), shooter.getZ());
        helper.getLevel().addFreshEntity(arrow);
        return helper.getLevel().damageSources().arrow(arrow, shooter);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void swordNonlethalThenBowKillGivesNoKillHeal(GameTestHelper helper) {
        ServerPlayer player = swordPlayer(helper, STAGE1_POINTS, 0L);
        Zombie victim = tankyZombie(helper, 200.0F);
        long killsBefore = GrowthService.data(player).hostileKills();
        helper.runAfterDelay(2L, () -> {
            player.attack(victim);
            helper.runAfterDelay(3L, () -> {
                helper.assertTrue(victim.isAlive(), "sanity: the sword hit must be nonlethal");
                float healthAfterSword = player.getHealth();
                // 换下剑再用弓射杀：击杀归属仍是这名玩家，但来源不是剑。
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                victim.hurt(arrowDamage(helper, player), 1000.0F);
                helper.runAfterDelay(5L, () -> {
                    helper.assertFalse(victim.isAlive(), "the arrow must kill the victim");
                    helper.assertTrue(GrowthService.data(player).hostileKills() == killsBefore + 1L,
                            "the arrow kill is still credited to the shooter (kills="
                                    + GrowthService.data(player).hostileKills() + ", expected=" + (killsBefore + 1L) + ")");
                    helper.assertTrue(Math.abs(player.getHealth() - healthAfterSword) < 0.01F,
                            "a bow kill after a nonlethal sword hit must NOT grant the sword kill heal (health="
                                    + player.getHealth() + ", expected=" + healthAfterSword + ")");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void swordLethalHitGivesKillHeal(GameTestHelper helper) {
        ServerPlayer player = swordPlayer(helper, STAGE1_POINTS, 0L);
        Zombie victim = lethalVictim(helper);
        float before = player.getHealth();
        long killsBefore = GrowthService.data(player).hostileKills();
        helper.runAfterDelay(2L, () -> {
            victim.setHealth(1.0F);
            assertOneHealthTarget(helper, victim, "sword lethal, before hit");
            player.attack(victim);
            helper.runAfterDelay(5L, () -> {
                helper.assertFalse(victim.isAlive(), "the sword hit must be lethal");
                long kills = GrowthService.data(player).hostileKills();
                helper.assertTrue(kills == killsBefore + 1L,
                        "kill is credited (kills=" + kills + ", expected=" + (killsBefore + 1L)
                                + ", bossKills=" + GrowthService.data(player).bossKills()
                                + ", points=" + GrowthService.data(player).growthPoints()
                                + ", creditedForLife=" + EBKillCreditHandler.isCreditedForLife(victim)
                                + ", pending=" + EBKillCreditHandler.pendingCount(helper.getLevel().getServer())
                                + ", removal=" + victim.getRemovalReason()
                                + ", hp=" + victim.getHealth() + "/" + victim.getMaxHealth() + ")");
                helper.assertTrue(Math.abs(player.getHealth() - (before + KILL_HEAL)) < 0.01F,
                        "a real sword kill grants the kill heal " + KILL_HEAL + " (health=" + player.getHealth()
                                + ", expected=" + (before + KILL_HEAL) + ")");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void abilityLethalHitGivesKillHeal(GameTestHelper helper) {
        ServerPlayer player = swordPlayer(helper, STAGE1_POINTS, 0L);
        Zombie victim = lethalVictim(helper);
        float before = player.getHealth();
        long killsBefore = GrowthService.data(player).hostileKills();
        helper.runAfterDelay(2L, () -> {
            victim.setHealth(1.0F);
            assertOneHealthTarget(helper, victim, "ability lethal, before hit");
            // 分类前提：尸壳必须是"普通敌对"而不是 Boss，否则会走 creditBossKill（击杀数不涨）。
            helper.assertTrue(com.evolvingblade.boss.BossClassifier.isHostile(
                            victim, com.evolvingblade.config.EBConfig.settings().hostileRules()),
                    "sanity: the ability-test victim must be classified as an ordinary hostile");
            helper.assertFalse(com.evolvingblade.boss.BossClassifier.classify(
                            victim, com.evolvingblade.config.EBConfig.settings().bossRules()).boss(),
                    "sanity: the ability-test victim must not be classified as a boss");
            victim.hurt(helper.getLevel().damageSources()
                    .source(StarEaterAbilities.SWORD_ABILITY, player, player), 100.0F);
            helper.runAfterDelay(5L, () -> {
                helper.assertFalse(victim.isAlive(), "the sword ability must be lethal here");
                long kills = GrowthService.data(player).hostileKills();
                long bosses = GrowthService.data(player).bossKills();
                helper.assertTrue(kills == killsBefore + 1L,
                        "ability kill is credited (kills=" + kills + ", expected=" + (killsBefore + 1L)
                                + ", bossKills=" + bosses + ", points=" + GrowthService.data(player).growthPoints()
                                + ", creditedForLife=" + EBKillCreditHandler.isCreditedForLife(victim)
                                + ", pending=" + EBKillCreditHandler.pendingCount(helper.getLevel().getServer())
                                + ", removal=" + victim.getRemovalReason()
                                + ", hp=" + victim.getHealth() + "/" + victim.getMaxHealth() + ")");
                helper.assertTrue(Math.abs(player.getHealth() - (before + KILL_HEAL)) < 0.01F,
                        "an ability kill counts as a sword kill for the kill heal (health=" + player.getHealth()
                                + ", expected=" + (before + KILL_HEAL) + ")");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cancelledSwordHitLeavesNoSourceFact(GameTestHelper helper) {
        ServerPlayer player = swordPlayer(helper, STAGE1_POINTS, 0L);
        Zombie victim = tankyZombie(helper, 200.0F);
        helper.runAfterDelay(2L, () -> {
            // 这一击被事件取消：没有任何被接受的伤害，因此不能留下剑来源事实。
            CANCELLER.target = victim.getUUID();
            float healthBefore = victim.getHealth();
            player.attack(victim);
            CANCELLER.target = null;
            helper.assertTrue(Math.abs(victim.getHealth() - healthBefore) < 0.01F,
                    "sanity: the cancelled hit must not deal damage (health=" + victim.getHealth() + ")");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            float playerHealth = player.getHealth();
            victim.hurt(arrowDamage(helper, player), 1000.0F);
            helper.runAfterDelay(5L, () -> {
                helper.assertFalse(victim.isAlive(), "the arrow must kill the victim");
                helper.assertTrue(Math.abs(player.getHealth() - playerHealth) < 0.01F,
                        "a cancelled (no-damage) sword swing must not leave a source fact behind (health="
                                + player.getHealth() + ")");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void underchargedFollowUpGivesNoStaleLifesteal(GameTestHelper helper) {
        ServerPlayer player = swordPlayer(helper, STAGE3_POINTS, 1L);
        Zombie victim = tankyZombie(helper, 500.0F);
        helper.runAfterDelay(2L, () -> {
            // 手动推进攻击条：GameTest 的模拟玩家不保证被连接驱动。
            // 必须调用 doTick()——ServerPlayer.tick() 不会走到 Player.tick()（攻击条在那里自增）。
            for (int i = 0; i < 40; i++) {
                player.doTick();
            }
            // 真实服务器上，玩家属性由 PlayerTickEvent.Post / AttackEntityEvent 在每次读取之前协调；
            // 模拟玩家的 doTick() 不会触发这些事件，于是装备 tick 刚挂上的保留槽位不会被观察到、
            // 本模组的缺席补偿也不会被撤销（会双算一份基线，实测 attackSpeed=0）。
            // 这里显式补一次"正常服务器调度"的协调：只复现真实事件顺序，
            // 不修改 attackSpeed、不绕过任何断言。
            GrowthService.syncAfterChange(player);
            float charge = player.getAttackStrengthScale(0.5F);
            helper.assertTrue(charge >= 0.9F, "sanity: the primary hit must be fully charged (charge=" + charge
                    + ", attackSpeed=" + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                    + ", attackDamage=" + player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    + ", item=" + player.getMainHandItem() + ")");
            float before = player.getHealth();
            player.attack(victim);
            float afterCharged = player.getHealth();
            helper.assertTrue(afterCharged > before,
                    "a fully charged primary sword hit must lifesteal at stage 3+ (health " + before + " -> "
                            + afterCharged + ")");
            // 同 tick 的追击：原版在攻击事件之后重置了攻击计时器 => 未满蓄力，不能复用旧上下文吸血。
            victim.invulnerableTime = 0;
            player.attack(victim);
            float afterUndercharged = player.getHealth();
            helper.assertTrue(Math.abs(afterUndercharged - afterCharged) < 0.01F,
                    "an undercharged follow-up must not lifesteal from a stale charged context (health "
                            + afterCharged + " -> " + afterUndercharged + ")");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tamedTargetsAreExcludedEvenWithoutOwner(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Wolf wolf = helper.spawn(EntityType.WOLF, 3, 2, 3);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(null);
        helper.assertFalse(StarEaterAbilities.isValidTarget(player, wolf),
                "a tamed animal with a null owner must still be excluded");
        wolf.setOwnerUUID(UUID.randomUUID());
        helper.assertFalse(StarEaterAbilities.isValidTarget(player, wolf),
                "another player's pet must be excluded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fakePlayerSwordKillGivesNoGrowthOrHeal(GameTestHelper helper) {
        FakePlayer fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        fake.setHealth(10.0F);
        Zombie victim = lethalVictim(helper);
        helper.runAfterDelay(2L, () -> {
            victim.setHealth(1.0F);
            assertOneHealthTarget(helper, victim, "fake player kill, before hit");
            victim.hurt(helper.getLevel().damageSources().playerAttack(fake), 1000.0F);
            helper.runAfterDelay(5L, () -> {
                helper.assertFalse(victim.isAlive(), "the fake player's hit is lethal here");
                helper.assertFalse(EBKillCreditHandler.isCreditedForLife(victim),
                        "a FakePlayer kill must not be credited to anyone");
                helper.assertTrue(GrowthService.data(fake).hostileKills() == 0L,
                        "a FakePlayer must never earn growth");
                helper.assertTrue(Math.abs(fake.getHealth() - 10.0F) < 0.01F,
                        "a FakePlayer must never receive the kill heal (health=" + fake.getHealth() + ")");
                helper.succeed();
            });
        });
    }
}
