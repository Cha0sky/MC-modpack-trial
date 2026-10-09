package com.evolvingblade.gametest;

import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.boss.BossReason;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.event.EBKillCreditHandler;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthService;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 击杀入账的<b>服务器集成</b>测试（跑在 {@code runGameTestServer} 的无头服务器上）。
 *
 * <p>覆盖审查要求的关键场景：
 * <ul>
 *   <li>真实终局死亡恰好入账一次；重复的留存尸体通知不得二次入账（含远超 20 tick、且期间有大量其它击杀）；</li>
 *   <li>被取消的死亡不得入账：LOW 优先级取消，以及<b>同优先级（LOWEST）更晚取消且血量已归零</b>；</li>
 *   <li>活体合成死亡事件不得入账；</li>
 *   <li>模组 Boss 的两种合法死亡：<b>不使用原版 DYING 姿态</b>，以及<b>接受死亡后立即以 KILLED 移除</b>；</li>
 *   <li><b>原版飞行末影龙</b>：致命玩家伤害 → 血量被恢复为 1 并进入 DYING 阶段 → 死亡阶段血量归零 →
 *       200 tick 后 KILLED 移除，全程只入账一次（默认 100 点 / 1 Boss / 0 普通击杀）；</li>
 *   <li>默认凋灵：分类为 Boss、理由是默认包含名单、击杀后 100 点 / 1 Boss / 0 普通击杀。</li>
 * </ul>
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class KillCreditGameTests {

    private static final String TEMPLATE = "empty";

    /** 在 LOW 优先级（早于本模组的 LOWEST）取消指定实体的死亡。 */
    public static final class LowPriorityCanceller {
        static volatile UUID target;

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onLivingDeath(LivingDeathEvent event) {
            cancelIfTarget(event, target);
        }
    }

    /**
     * 在 LOWEST 优先级（与本模组<b>同优先级</b>、注册更晚）取消指定实体的死亡。
     * 用来复现"血量已归零，但事件随后被同优先级取消"的场景。
     */
    public static final class LowestPriorityCanceller {
        static volatile UUID target;

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onLivingDeath(LivingDeathEvent event) {
            cancelIfTarget(event, target);
        }
    }

    private static final LowPriorityCanceller LOW_CANCELLER = new LowPriorityCanceller();
    private static final LowestPriorityCanceller LOWEST_CANCELLER = new LowestPriorityCanceller();

    static {
        NeoForge.EVENT_BUS.register(LOW_CANCELLER);
        NeoForge.EVENT_BUS.register(LOWEST_CANCELLER);
    }

    private KillCreditGameTests() {}

    private static void cancelIfTarget(LivingDeathEvent event, UUID target) {
        if (target != null && event.getEntity() != null && target.equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * 显式把测试结构覆盖到的区块（外扩 1 圈）设为强制加载。
     *
     * <p><b>实测症状</b>：GameTest 会把同一批的多个结构按网格放在相邻区块里；当某个结构的
     * 随机位置刚好跨区块边界时，结构覆盖到的那个"远端"区块在测试运行期间可能不是 entity-ticking
     * （{@code ServerLevel#isPositionEntityTicking} 返回 false，而结构原点所在区块返回 true）。
     * 于是测试里 spawn 的实体 {@code tickCount} 恒为 0，所有依赖实体 tick 的断言
     * （末影龙的死亡阶段推进、尸体 20 tick 后移除）就会随机失败。
     *
     * <p>这里在测试开始处显式强制加载结构覆盖的区块；区块一旦进入 entity-ticking，
     * {@code PersistentEntitySectionManager#updateChunkStatus} 会把已在区块内的实体补登记进 tick 列表，
     * 因此实体随后会正常被 tick。这是<b>测试夹具</b>层面的确定性保障，不改变模组行为。
     */
    static void ensureTestChunksTicking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var bounds = helper.getBounds();
        ChunkPos min = new ChunkPos(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ));
        ChunkPos max = new ChunkPos(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
        for (int cx = min.x - 1; cx <= max.x + 1; cx++) {
            for (int cz = min.z - 1; cz <= max.z + 1; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    // ------------------------------------------------------------------
    // 普通终局死亡
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void terminalDeathCreditsExactlyOnce(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);

        zombie.hurt(level.damageSources().playerAttack(player), 1000.0F);

        helper.runAfterDelay(4L, () -> {
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 1L, "真实终局死亡应恰好入账 1 次，实际=" + kills);
            helper.assertTrue(zombie.isDeadOrDying(), "受害者应处于死亡状态");

            // 对同一具仍在场的尸体再补发一次死亡通知：绝不能二次入账。
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie, level.damageSources().playerAttack(player)));
            helper.runAfterDelay(3L, () -> {
                long again = GrowthService.data(player).hostileKills();
                helper.assertTrue(again == 1L, "重复的死亡通知不得二次入账，实际=" + again);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    @SuppressWarnings("removal")
    public static void duplicateCorpseAfterManyKillsIsNotCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        var attack = level.damageSources().playerAttack(player);
        Zombie target = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        target.hurt(attack, 1000.0F);

        helper.runAfterDelay(25L, () -> {
            // 远超 20 tick：原版僵尸尸体早已被移除，但"这条命已入账"的标记仍然有效。
            helper.assertTrue(target.isRemoved(), "原版僵尸尸体应在 20 tick 后被移除");

            // 期间制造大量其它击杀，证明去重不依赖任何全局容量上限/清理窗口。
            int extra = 30;
            for (int i = 0; i < extra; i++) {
                Zombie other = helper.spawn(EntityType.ZOMBIE, 2 + (i % 5), 2, 3);
                other.hurt(attack, 1000.0F);
            }

            helper.runAfterDelay(4L, () -> {
                long before = GrowthService.data(player).hostileKills();
                helper.assertTrue(before == 1L + extra,
                        "目标 + " + extra + " 个其它击杀应全部入账，实际=" + before);

                // 对已移除的第一具尸体再补发死亡通知
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(target, attack));

                helper.runAfterDelay(4L, () -> {
                    long after = GrowthService.data(player).hostileKills();
                    helper.assertTrue(after == before,
                            "远超 20 tick 且经历大量其它击杀后，重复尸体通知仍不得入账（前=" + before + " 后=" + after + "）");
                    helper.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------
    // 取消 / 合成事件
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void cancelledDeathAtLowPriorityIsNotCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        LowPriorityCanceller.target = zombie.getUUID();

        zombie.hurt(level.damageSources().playerAttack(player), 1000.0F);

        helper.runAfterDelay(4L, () -> {
            LowPriorityCanceller.target = null;
            helper.assertFalse(EBKillCreditHandler.hasPendingCandidate(level.getServer(), zombie.getUUID()),
                    "LOW 取消的死亡不应产生候选");
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 0L, "被取消的死亡不得入账，实际=" + kills);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void lateLowestCancelWithZeroHealthIsNotCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        UUID zombieId = zombie.getUUID();

        // 致死伤害会先把血量打到 0，再触发 die() → LivingDeathEvent。
        // 同优先级的取消器在本模组处理器之后取消该事件，因此"血量 0"并不代表死亡成立。
        LowestPriorityCanceller.target = zombieId;
        zombie.hurt(level.damageSources().playerAttack(player), 1000.0F);
        LowestPriorityCanceller.target = null;

        helper.assertTrue(zombie.getHealth() == 0.0F,
                "致死伤害应已把血量归零（这正是「血量 0 不等于死亡成立」的场景），实际=" + zombie.getHealth());
        // 前提校验：本模组的 LOWEST 处理器确实先接受了候选（若顺序相反，事件会被跳过，这里会是 false）。
        helper.assertTrue(EBKillCreditHandler.hasPendingCandidate(level.getServer(), zombieId),
                "本模组的 LOWEST 处理器应先接受候选");

        helper.runAfterDelay(4L, () -> {
            helper.assertFalse(EBKillCreditHandler.hasPendingCandidate(level.getServer(), zombieId),
                    "被同优先级取消的候选应被撤销");
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 0L, "同优先级更晚取消的死亡不得入账，实际=" + kills);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void liveEntitySyntheticDeathIsNotCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        UUID zombieId = zombie.getUUID();

        NeoForge.EVENT_BUS.post(new LivingDeathEvent(zombie, level.damageSources().playerAttack(player)));

        helper.runAfterDelay(10L, () -> {
            helper.assertTrue(zombie.isAlive(), "合成事件不应真的杀死实体");
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 0L, "活体的合成死亡事件不得入账，实际=" + kills);
            // 该候选会一直"保留等待"直到保留上限；把它移出世界以便验证"非 KILLED 移除即撤销"并清理状态。
            zombie.discard();
            helper.runAfterDelay(4L, () -> {
                helper.assertFalse(EBKillCreditHandler.hasPendingCandidate(level.getServer(), zombieId),
                        "被移出世界但不是死亡的候选应被撤销");
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void environmentalDeathIsNotCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);

        zombie.hurt(level.damageSources().genericKill(), 1000.0F);

        helper.runAfterDelay(4L, () -> {
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 0L, "无归属的环境死亡不得入账，实际=" + kills);
            helper.assertTrue(zombie.isDeadOrDying(), "环境死亡本身应正常发生");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 模组 Boss 的合法死亡形态（不强制原版 DYING 姿态 / 立即 KILLED 移除）
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void customBossWithoutDyingPoseIsCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();

        // 自定义 LivingEntity fixture：die() 完成后不保留原版 Pose.DYING 终局姿态。
        Zombie custom = new Zombie(EntityType.ZOMBIE, level) {
            @Override
            public void die(DamageSource source) {
                super.die(source);
                this.setPose(Pose.STANDING);
            }
        };
        addFresh(helper, custom, 4, 2, 4);

        custom.hurt(level.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(custom.getPose() != Pose.DYING,
                "该 fixture 不应处于原版 DYING 姿态，实际=" + custom.getPose());

        helper.runAfterDelay(4L, () -> {
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 1L,
                    "模组 Boss 的合法死亡不应因为缺少原版 DYING 姿态而被丢弃，实际=" + kills);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void immediateKilledRemovalBossIsCredited(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();

        // 自定义 LivingEntity fixture：接受死亡后立即把自己以 KILLED 移出世界。
        Zombie custom = new Zombie(EntityType.ZOMBIE, level) {
            @Override
            public void die(DamageSource source) {
                super.die(source);
                this.remove(Entity.RemovalReason.KILLED);
            }
        };
        addFresh(helper, custom, 4, 2, 4);

        custom.hurt(level.damageSources().playerAttack(player), 1000.0F);
        helper.assertTrue(custom.isRemoved(), "该 fixture 应在 die() 内立即移除自身");
        helper.assertTrue(custom.getRemovalReason() == Entity.RemovalReason.KILLED, "移除原因应为 KILLED");

        helper.runAfterDelay(4L, () -> {
            long kills = GrowthService.data(player).hostileKills();
            helper.assertTrue(kills == 1L,
                    "被接受且以 KILLED 立即移除的死亡应当入账，实际=" + kills);
            helper.succeed();
        });
    }

    private static void addFresh(GameTestHelper helper, Zombie entity, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(entity);
    }

    // ------------------------------------------------------------------
    // 默认 Boss：飞行末影龙 + 凋灵
    // ------------------------------------------------------------------

    /**
     * 原版<b>飞行（非坐姿）</b>末影龙的完整死亡流程。
     *
     * <p>关键点：{@code EnderDragon.hurt} 在非坐姿阶段受到致命玩家伤害时，会先把血量恢复成 1
     * 并转入 {@code DYING} 阶段（源码 EnderDragon.java:532-538）。因此"血量 1"时既不能提前入账，
     * 也不能丢弃候选；随后 {@code DragonDeathPhase} 在抵达目标附近时把血量归零，才应入账，
     * 最后 {@code tickDeath()} 在 200 tick 后以 KILLED 移除实体。
     */
    @GameTest(template = "boss_arena", timeoutTicks = 1200)
    @SuppressWarnings("removal")
    public static void flyingDragonLethalAttackCreditsOnce(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());

        // 显式加入世界并校验，避免"实体没进世界因此从不 tick"被误当成"死亡阶段没推进"。
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(level);
        // Codex QA: a 16-wide dragon needs an arena centre away from adjacent structure cleanup.
        BlockPos spawnPos = helper.absolutePos(new BlockPos(24, 3, 24));
        dragon.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(dragon), "末影龙必须被成功加入世界");
        helper.assertTrue(level.getEntity(dragon.getUUID()) != null, "末影龙必须出现在世界的实体列表中");
        // 对照实体：同一结构、同一区块、相邻 y，用来区分"位置/区块不 tick"与"末影龙自身不 tick"。
        Zombie control = helper.spawn(EntityType.ZOMBIE, 24, 2, 24);
        // 明确置于"飞行/非坐姿"阶段（新建的龙默认是 HOVERING，其 isSitting() == true，会掩盖该缺陷）。
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        helper.assertTrue(!dragon.getPhaseManager().getCurrentPhase().isSitting(),
                "测试前提：末影龙必须处于非坐姿阶段");

        // 玩家攻击头部：只有玩家伤害（或 ALWAYS_HURTS_ENDER_DRAGONS）才会走 reallyHurt 分支。
        DamageSource source = level.damageSources().playerAttack(player);
        dragon.head.hurt(source, 1000.0F);

        helper.assertTrue(dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING,
                "致命一击后应进入 DYING 阶段，实际=" + dragon.getPhaseManager().getCurrentPhase().getPhase());
        helper.assertTrue(dragon.getHealth() == 1.0F,
                "原版飞行龙在 DYING 阶段会被恢复为 1 血，实际=" + dragon.getHealth());
        helper.assertTrue(GrowthService.data(player).bossKills() == 0L,
                "DYING 阶段（血量 1）绝不能提前入账");

        // 死亡阶段会把血量归零 → 此时才入账；随后还要等 200 tick 的死亡动画结束。
        awaitDragonCredit(helper, player, dragon, control, 0);
    }

    private static void awaitDragonCredit(GameTestHelper helper, ServerPlayer player, EnderDragon dragon,
            Zombie control, int waited) {
        if (waited == 0) {
            // 前提校验：龙必须真的在被 tick，否则"阶段没有推进"会被误读。
            helper.runAfterDelay(8L, () -> {
                var byId = helper.getLevel().getEntities().get(dragon.getId());
                helper.assertTrue(control.tickCount >= 8,
                        "对照僵尸（同一结构/区块）必须 tick，实际=" + control.tickCount
                                + ", tickingAtControl=" + helper.getLevel().isPositionEntityTicking(control.blockPosition())
                                + ", tickingAtDragon=" + helper.getLevel().isPositionEntityTicking(dragon.blockPosition())
                                + ", tickingAtOrigin=" + helper.getLevel().isPositionEntityTicking(helper.absolutePos(BlockPos.ZERO))
                                + ", testPos=" + helper.absolutePos(BlockPos.ZERO));
                helper.assertTrue(dragon.tickCount >= 8,
                        "末影龙必须 tick：tickCount=" + dragon.tickCount
                                + ", id=" + dragon.getId()
                                + ", idOwner=" + (byId == null ? "none" : (byId == dragon ? "dragon" : byId.getType().toString()))
                                + ", inLevel=" + (helper.getLevel().getEntity(dragon.getUUID()) != null)
                                + ", removed=" + dragon.isRemoved()
                                + ", noAi=" + dragon.isNoAi()
                                + ", pos=" + dragon.position());
            });
        }
        if (GrowthService.data(player).bossKills() == 1L) {
            GrowthData data = GrowthService.data(player);
            helper.assertTrue(data.hostileKills() == 0L,
                    "Boss 击杀不应同时算作普通击杀，实际=" + data.hostileKills());
            helper.assertTrue(data.growthPoints() == 100L,
                    "默认 Boss 权重 100，实际=" + data.growthPoints());
            // 等死亡动画（200 tick）结束、实体被 KILLED 移除后仍然只有一次
            helper.runAfterDelay(220L, () -> {
                GrowthData after = GrowthService.data(player);
                helper.assertTrue(dragon.isRemoved(), "死亡动画结束后末影龙应已被移除");
                helper.assertTrue(after.bossKills() == 1L,
                        "完整死亡流程结束后仍应恰好 1 次，实际=" + after.bossKills());
                helper.assertTrue(after.growthPoints() == 100L,
                        "完整死亡流程结束后成长点仍应为 100，实际=" + after.growthPoints());
                helper.succeed();
            });
            return;
        }
        if (waited >= 240) {
            helper.fail("末影龙在 240 tick 内没有入账：health=" + dragon.getHealth()
                    + ", phase=" + dragon.getPhaseManager().getCurrentPhase().getPhase()
                    + ", removed=" + dragon.isRemoved()
                    + ", tickCount=" + dragon.tickCount
                    + ", noAi=" + dragon.isNoAi()
                    + ", inLevel=" + (helper.getLevel().getEntity(dragon.getUUID()) != null)
                    + ", pos=" + dragon.position()
                    + ", pending=" + EBKillCreditHandler.hasPendingCandidate(
                            helper.getLevel().getServer(), dragon.getUUID()));
            return;
        }
        helper.runAfterDelay(5L, () -> awaitDragonCredit(helper, player, dragon, control, waited + 5));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    @SuppressWarnings("removal")
    public static void defaultWitherIsBossAndCreditsExactly100Once(GameTestHelper helper) {
        ensureTestChunksTicking(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());

        WitherBoss wither = helper.spawn(EntityType.WITHER, 4, 2, 4);
        BossClassification classification = BossClassifier.classify(wither, EBConfig.settings().bossRules());
        helper.assertTrue(classification.boss(), "默认配置应把凋灵判定为 Boss：" + classification.describe());
        helper.assertTrue(classification.reason() == BossReason.INCLUDED_BY_CONFIG,
                "凋灵应命中默认包含名单，实际理由=" + classification.reason());

        // 跳过凋灵的 220 tick 出生无敌，直接用玩家伤害击杀。
        wither.setInvulnerableTicks(0);
        wither.hurt(level.damageSources().playerAttack(player), 10000.0F);

        helper.runAfterDelay(6L, () -> {
            GrowthData data = GrowthService.data(player);
            helper.assertTrue(data.bossKills() == 1L, "凋灵击杀应入账 1 次，实际=" + data.bossKills());
            helper.assertTrue(data.hostileKills() == 0L,
                    "Boss 击杀是排他的：不得同时算作普通击杀，实际=" + data.hostileKills());
            helper.assertTrue(data.growthPoints() == 100L,
                    "默认 Boss 权重 100，实际=" + data.growthPoints());
            // 立即移除，避免 200 tick 后的凋灵死亡爆炸影响相邻测试结构。
            wither.discard();
            helper.succeed();
        });
    }
}
