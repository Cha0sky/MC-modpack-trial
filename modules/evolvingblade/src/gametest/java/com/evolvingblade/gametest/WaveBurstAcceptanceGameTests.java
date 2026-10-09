package com.evolvingblade.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.AbilityState;
import com.evolvingblade.combat.CombatMath;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WAVE-BURST-ACCEPTANCE：剑气与爆发的真实实体集成验收（伤害/射程/数量/冷却/保护/取消/UI 无关）。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class WaveBurstAcceptanceGameTests {

    private static final long STAGE2_POINTS = 150L;
    private static final long STAGE3_POINTS = 350L;
    private static final long STAGE3_BOSSES = 1L;
    private static final long STAGE4_POINTS = 700L;
    private static final long STAGE4_BOSSES = 3L;
    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;

    private WaveBurstAcceptanceGameTests() {}

    /** 按 UUID + 类型作用域的伤害日志。 */
    public static final class DamageLog {
        volatile UUID victim;
        volatile String typePart;
        volatile boolean cancel;
        final List<Float> amounts = java.util.Collections.synchronizedList(new ArrayList<>());
        volatile String lastType;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(LivingIncomingDamageEvent event) {
            UUID current = this.victim;
            if (current == null || !current.equals(event.getEntity().getUUID())) {
                return;
            }
            String id = event.getSource().typeHolder().unwrapKey()
                    .map(k -> k.location().toString()).orElse("unknown");
            this.lastType = id;
            String part = this.typePart;
            if (part != null && !id.contains(part)) {
                return;
            }
            this.amounts.add(event.getAmount());
            if (this.cancel) {
                event.setCanceled(true);
            }
        }

        void watch(UUID victim, String typePart) {
            this.victim = victim;
            this.typePart = typePart;
            this.amounts.clear();
            this.lastType = null;
        }

        int count() {
            return this.amounts.size();
        }

        float first() {
            return this.amounts.isEmpty() ? Float.NaN : this.amounts.get(0);
        }

        void reset() {
            this.victim = null;
            this.typePart = null;
            this.cancel = false;
            this.amounts.clear();
            this.lastType = null;
        }
    }

    private static final DamageLog LOG = new DamageLog();

    static {
        NeoForge.EVENT_BUS.register(LOG);
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private static ServerPlayer staged(GameTestHelper helper, long points, long bosses, int relX, int relZ) {
        return staged(helper, points, bosses, relX, 4, relZ);
    }

    private static ServerPlayer staged(GameTestHelper helper, long points, long bosses, int relX, int relY,
            int relZ) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, points, bosses);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ)));
        player.moveTo(centre.x, centre.y, centre.z, 0.0F, 0.0F);
        return player;
    }

    /**
     * 对准某个相对坐标，并抬高到实体眼睛高度（+1.6）：射线与目标眼睛齐平，
     * 既不会被地板提前截断，也不需要假设结构朝向。
     */
    private static void face(GameTestHelper helper, ServerPlayer player, int relX, int relY, int relZ) {
        Vec3 target = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ))).add(0.0D, 1.6D, 0.0D);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
    }

    /** 对准某个实体的**眼睛**：保证射线水平，不会被地板/台阶提前截断。 */
    private static void faceEntity(ServerPlayer player, LivingEntity target) {
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
    }

    private static Husk huskAt(GameTestHelper helper, int relX, int relY, int relZ, double maxHealth) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ)));
        Husk husk = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y, centre.z,
                (type, level) -> new Husk(type, level));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        husk.setHealth((float) maxHealth);
        return husk;
    }

    /** 无甲、无抗性、超大生命的 Boss 夹具（真实受伤路径，绝不覆写 hurt/setHealth）。 */
    private static FixtureEntities.LargeHealthFixtureHusk noArmorBoss(GameTestHelper helper, int relX, int relY,
            int relZ, double maxHealth) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(relX, relY, relZ)));
        FixtureEntities.LargeHealthFixtureHusk boss = FixtureEntities.spawn(helper.getLevel(), centre.x, centre.y,
                centre.z, FixtureEntities.LargeHealthFixtureHusk::new).withFixtureMaxHealth(maxHealth);
        boss.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        boss.setHealth((float) maxHealth);
        return boss;
    }

    private static int stage(ServerPlayer player) {
        return GrowthService.stage(player);
    }

    private static long waveRemaining(ServerPlayer player) {
        return AbilityState.remaining(StarEaterAbilities.clock(player), GrowthService.data(player).waveReadyAt());
    }

    private static long burstRemaining(ServerPlayer player) {
        return AbilityState.remaining(StarEaterAbilities.clock(player), GrowthService.data(player).burstReadyAt());
    }

    // ------------------------------------------------------------------
    // 剑气：背后目标 / 射程边界 / 墙 / 数量 / 冷却
    // ------------------------------------------------------------------

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void waveMidStageBehindTargetRangeBoundaryAndCooldown(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, STAGE2_POINTS, 0L, 24, 4, 6);
        helper.assertTrue(stage(player) == 2, "sanity: stage 2 (stage=" + stage(player) + ")");
        // 故意先放远的、再放近的：命中顺序必须按"沿射线距离"，与世界实体插入顺序无关。
        Husk farBoundary = huskAt(helper, 24, 4, 29, 500.0D);   // 23 格（射程 24 内）
        Husk inside = huskAt(helper, 24, 4, 12, 500.0D);        // 6 格
        Husk behindOne = huskAt(helper, 24, 4, 5, 500.0D);      // **玩家背后 1 格**
        Husk outside = huskAt(helper, 24, 4, 31, 500.0D);       // 25 格（射程外）
        face(helper, player, 24, 4, 12);

        double insideHp = inside.getHealth();
        double boundaryHp = farBoundary.getHealth();
        double behindHp = behindOne.getHealth();
        double outsideHp = outside.getHealth();
        LOG.watch(inside.getUUID(), "sword_ability");
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the wave must fire");

        helper.assertTrue(inside.getHealth() < insideHp,
                "the nearest in-range target must be hit first (hp=" + inside.getHealth() + ")");
        helper.assertTrue(Math.abs(LOG.first() - 24.0F) < 0.01F,
                "stage-2 wave raw damage must be exactly 24 (raw=" + LOG.first() + ")");
        helper.assertTrue(Math.abs(behindHp - behindOne.getHealth()) < 0.01D,
                "a target 1 block BEHIND the player must never be hit (hp=" + behindOne.getHealth() + ")");
        helper.assertTrue(Math.abs(boundaryHp - farBoundary.getHealth()) < 0.01D,
                "stage 2 hits only ONE target: the 23-block target must stay untouched");
        helper.assertTrue(Math.abs(outsideHp - outside.getHealth()) < 0.01D,
                "a target beyond the 24-block range must not be hit");

        long remaining = waveRemaining(player);
        helper.assertTrue(remaining == 160L, "stage-2 wave cooldown must be 160 ticks (got " + remaining + ")");
        EBPayloads.StateSnapshot snapshot = EBPayloads.snapshotFor(player, false);
        helper.assertTrue(Math.abs(snapshot.waveRemaining() - 160L) <= 1L,
                "the snapshot taken right after casting must show the cooldown (got "
                        + snapshot.waveRemaining() + ")");
        helper.assertFalse(StarEaterAbilities.tryWave(player), "the wave must be locked while cooling down");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void waveFinalHitsExactlyThreeNearestRegardlessOfSpawnOrder(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 6);
        helper.assertTrue(stage(player) == 7, "sanity: final stage (stage=" + stage(player) + ")");
        // 先远后近地生成，验证"按沿射线距离排序"而不是按实体列表顺序。
        Husk fourth = huskAt(helper, 24, 4, 21, 500.0D);
        Husk third = huskAt(helper, 24, 4, 18, 500.0D);
        Husk second = huskAt(helper, 24, 4, 15, 500.0D);
        Husk first = huskAt(helper, 24, 4, 12, 500.0D);
        Husk behind = huskAt(helper, 24, 4, 5, 500.0D);
        face(helper, player, 24, 4, 12);
        double[] before = {first.getHealth(), second.getHealth(), third.getHealth(), fourth.getHealth()};
        double behindHp = behind.getHealth();
        LOG.watch(first.getUUID(), "sword_ability");
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the final wave must fire");

        helper.assertTrue(first.getHealth() < before[0] && second.getHealth() < before[1]
                        && third.getHealth() < before[2],
                "the three NEAREST targets must be hit (hp=" + first.getHealth() + "/" + second.getHealth() + "/"
                        + third.getHealth() + ")");
        helper.assertTrue(Math.abs(fourth.getHealth() - before[3]) < 0.01D,
                "the 4th (farthest) target must stay untouched: at most 3 TOTAL targets");
        helper.assertTrue(Math.abs(behindHp - behind.getHealth()) < 0.01D, "the behind target must stay untouched");
        helper.assertTrue(Math.abs(LOG.first() - 1_500.0F) < 1.0F,
                "final wave raw damage must be exactly 1500 (raw=" + LOG.first() + ")");
        helper.assertTrue(waveRemaining(player) == 80L,
                "final wave cooldown must be 80 ticks (got " + waveRemaining(player) + ")");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void waveWallBlocksIncludingNearCorner(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, STAGE2_POINTS, 0L, 24, 4, 6);
        for (int x = 22; x <= 26; x++) {
            for (int y = 4; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
            }
        }
        Husk behindWall = huskAt(helper, 24, 4, 12, 500.0D);
        Husk nearCorner = huskAt(helper, 26, 4, 10, 500.0D);
        face(helper, player, 24, 4, 9);
        double behindHp = behindWall.getHealth();
        double cornerHp = nearCorner.getHealth();
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the wave must fire");
        helper.assertTrue(Math.abs(behindHp - behindWall.getHealth()) < 0.01D,
                "a wall must stop the wave (target behind wall hp=" + behindWall.getHealth() + ")");
        helper.assertTrue(Math.abs(cornerHp - nearCorner.getHealth()) < 0.01D,
                "a target behind the wall corner must not be hit");
        helper.assertTrue(helper.getBlockState(new BlockPos(24, 4, 9)).is(Blocks.STONE),
                "the wave must not modify terrain");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void waveLockedAtStageZeroAndOne(GameTestHelper helper) {
        LOG.reset();
        for (long[] points : new long[][] {{0L, 0L}, {50L, 0L}}) {
            ServerPlayer player = staged(helper, points[0], points[1], 24, 4, 6);
            helper.assertTrue(stage(player) <= 1, "sanity: stage 0/1 (stage=" + stage(player) + ")");
            Husk target = huskAt(helper, 24, 4, 12, 500.0D);
            face(helper, player, 24, 4, 12);
            double hp = target.getHealth();
            helper.assertFalse(StarEaterAbilities.tryWave(player), "the wave must be locked before stage 2");
            helper.assertTrue(Math.abs(target.getHealth() - hp) < 0.01D, "a locked wave deals no damage");
            helper.assertTrue(GrowthService.data(player).waveReadyAt() == 0L,
                    "a locked wave must not consume the cooldown");
        }
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void waveCooldownIsPerPlayerNotPerItemStack(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, STAGE2_POINTS, 0L, 24, 4, 6);
        face(helper, player, 24, 4, 12);
        huskAt(helper, 24, 4, 12, 500.0D);
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the first cast must succeed");
        long deadline = GrowthService.data(player).waveReadyAt();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        helper.assertFalse(StarEaterAbilities.tryWave(player),
                "swapping to another sword stack must not reset the cooldown");
        helper.assertTrue(GrowthService.data(player).waveReadyAt() == deadline,
                "the stored deadline must be unchanged");
        LOG.reset();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 真实伤害（无甲、无抗性、超大生命的活目标；生命差必须等于配置值）
    // ------------------------------------------------------------------

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void waveRealHealthDamageOnLivingVictim(GameTestHelper helper) {
        // 阶段 2：24；终阶：1500。目标 10000 HP 且无甲 → 生命差必须精确等于伤害，且目标存活、玩家不回血。
        ServerPlayer mid = staged(helper, STAGE2_POINTS, 0L, 24, 4, 6);
        FixtureEntities.LargeHealthFixtureHusk boss = noArmorBoss(helper, 24, 4, 12, 10_000.0D);
        helper.assertTrue(boss.getArmorValue() == 0, "sanity: the fixture must have 0 armor (armor="
                + boss.getArmorValue() + ")");
        face(helper, mid, 24, 4, 12);
        double hp0 = boss.getHealth();
        float playerHp0 = mid.getHealth();
        helper.assertTrue(StarEaterAbilities.tryWave(mid), "the wave must fire");
        helper.assertTrue(Math.abs((hp0 - boss.getHealth()) - 24.0D) < 0.01D,
                "stage-2 wave must remove exactly 24 health from a 0-armor living target (loss="
                        + (hp0 - boss.getHealth()) + ")");
        helper.assertTrue(boss.isAlive(), "the target must survive (no kill heal confound)");
        helper.assertTrue(Math.abs(mid.getHealth() - playerHp0) < 0.01D, "the wave must not lifesteal");

        ServerPlayer fin = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 6);
        FixtureEntities.LargeHealthFixtureHusk boss2 = noArmorBoss(helper, 24, 4, 18, 10_000.0D);
        face(helper, fin, 24, 4, 18);
        double hp1 = boss2.getHealth();
        float playerHp1 = fin.getHealth();
        helper.assertTrue(StarEaterAbilities.tryWave(fin), "the final wave must fire");
        helper.assertTrue(Math.abs((hp1 - boss2.getHealth()) - 1_500.0D) < 0.5D,
                "final wave must remove exactly 1500 health (loss=" + (hp1 - boss2.getHealth()) + ")");
        helper.assertTrue(boss2.isAlive(), "the target must survive (10000 > 1500)");
        helper.assertTrue(Math.abs(fin.getHealth() - playerHp1) < 0.01D, "the wave must not lifesteal");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 爆发：数值 / 半径 / 上限 / 排除 / 墙 / 方块不变
    // ------------------------------------------------------------------

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void burstRealHealthDamageRadiusAndCooldown(GameTestHelper helper) {
        ServerPlayer mid = staged(helper, STAGE4_POINTS, STAGE4_BOSSES, 24, 4, 6);
        FixtureEntities.LargeHealthFixtureHusk boss = noArmorBoss(helper, 24, 4, 11, 10_000.0D);
        double hp0 = boss.getHealth();
        float playerHp0 = mid.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(mid), "the stage-4 burst must fire");
        helper.assertTrue(Math.abs((hp0 - boss.getHealth()) - 180.0D) < 0.01D,
                "stage-4 burst must remove exactly 180 health from a 0-armor living target (loss="
                        + (hp0 - boss.getHealth()) + ")");
        helper.assertTrue(boss.isAlive(), "the target must survive");
        helper.assertTrue(Math.abs(mid.getHealth() - playerHp0) < 0.01D, "the burst must not lifesteal");
        helper.assertTrue(burstRemaining(mid) == 400L, "stage-4 burst cooldown must be 400 (got "
                + burstRemaining(mid) + ")");

        ServerPlayer fin = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 6);
        // 与玩家同一 y、正东方 2 格，且不会被玩家自身包围盒包住（三维球心在躯干，水平距离 2 « 终阶半径 8）。
        FixtureEntities.LargeHealthFixtureHusk boss2 = noArmorBoss(helper, 26, 4, 6, 10_000.0D);
        double hp1 = boss2.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(fin), "the final burst must fire");
        helper.assertTrue(Math.abs((hp1 - boss2.getHealth()) - 2_250.0D) < 1.0D,
                "final burst must remove exactly 2250 health (loss=" + (hp1 - boss2.getHealth()) + ")");
        helper.assertTrue(boss2.isAlive(), "the target must survive (10000 > 2250)");
        helper.assertTrue(burstRemaining(fin) == 200L, "final burst cooldown must be 200 (got "
                + burstRemaining(fin) + ")");
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 600)
    public static void burstFinalHitsExactlyThirtyTwoAndKeepsBlocks(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 12);
        helper.assertTrue(stage(player) == 7, "sanity: final stage (stage=" + stage(player) + ")");
        Husk nearest = huskAt(helper, 24, 4, 13, 1_024.0D);
        List<Husk> inside = new ArrayList<>();
        inside.add(nearest);
        for (int dx = -5; dx <= 5 && inside.size() < 34; dx += 2) {
            for (int dz = -5; dz <= 5 && inside.size() < 34; dz += 2) {
                inside.add(huskAt(helper, 24 + dx, 4, 12 + dz, 500.0D));
            }
        }
        helper.assertTrue(inside.size() == 34, "sanity: 34 in-radius targets (got " + inside.size() + ")");
        Husk outsideA = huskAt(helper, 24, 4, 25, 500.0D);
        Husk outsideB = huskAt(helper, 24, 4, 2, 500.0D);
        helper.setBlock(new BlockPos(24, 4, 20), Blocks.STONE);
        helper.setBlock(new BlockPos(30, 4, 12), Blocks.STONE);
        BlockState beforeA = helper.getBlockState(new BlockPos(24, 4, 20));
        BlockState beforeB = helper.getBlockState(new BlockPos(30, 4, 12));
        double[] before = new double[inside.size()];
        for (int i = 0; i < inside.size(); i++) {
            before[i] = inside.get(i).getHealth();
        }
        LOG.watch(nearest.getUUID(), "sword_ability");
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the final burst must fire");
        int damaged = 0;
        for (int i = 0; i < inside.size(); i++) {
            if (inside.get(i).getHealth() < before[i]) {
                damaged++;
            }
        }
        helper.assertTrue(damaged == 32, "exactly 32 of the 34 in-radius targets must be hit (damaged="
                + damaged + ")");
        helper.assertTrue(Math.abs(LOG.first() - 2_250.0F) < 1.0F,
                "the final burst raw damage must be exactly 2250 (raw=" + LOG.first() + ")");
        helper.assertTrue(outsideA.getHealth() == outsideA.getMaxHealth()
                        && outsideB.getHealth() == outsideB.getMaxHealth(),
                "targets outside the radius (13 and 10 blocks) must be untouched");
        helper.assertTrue(helper.getBlockState(new BlockPos(24, 4, 20)).equals(beforeA)
                        && helper.getBlockState(new BlockPos(30, 4, 12)).equals(beforeB),
                "the burst must not modify any block (no explosion)");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void burstExcludesPlayersAlliesAndAllKindsOfPets(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer caster = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 12);
        // 玩家：用**非创造**真实玩家（排除不能是因为创造模式/无敌）。
        ServerPlayer nonCreative = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "eb-burst-target"),
                net.minecraft.server.level.ClientInformation.createDefault());
        nonCreative.getAbilities().instabuild = false;
        Vec3 ncPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(26, 4, 12)));
        nonCreative.moveTo(ncPos.x, ncPos.y, ncPos.z, 0.0F, 0.0F);
        nonCreative.setHealth(20.0F);
        double ncHp = nonCreative.getHealth();
        // 队友：把一个本来合法的敌对 Husk 放进同一支队伍。
        Husk teammate = huskAt(helper, 24, 4, 14, 500.0D);
        var scoreboard = helper.getLevel().getScoreboard();
        net.minecraft.world.scores.PlayerTeam team = scoreboard.addPlayerTeam("eb-test-team");
        scoreboard.addPlayerToTeam(caster.getGameProfile().getName(), team);
        scoreboard.addPlayerToTeam(teammate.getStringUUID(), team);
        double teammateHp = teammate.getHealth();
        // 宠物：测试用 Boss 狼（**本来合法**）——未驯服对照组必须真的受伤。
        FixtureEntities.FixtureBossWolf controlWolf = FixtureEntities.spawnBossWolf(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 12))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 12))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 12))).z);
        // 狼的默认最大生命只有 8，达不到自动探测阈值 200；抬到 500 后它凭"非空 bar 字段"成为
        // **本来合法**的技能目标——这样"宠物被排除"才是有效断言（对照必须真的能被打到）。
        controlWolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        controlWolf.setHealth(500.0F);
        FixtureEntities.FixtureBossWolf ownPet = FixtureEntities.spawnBossWolf(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(24, 4, 10))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(24, 4, 10))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(24, 4, 10))).z);
        ownPet.setTame(true, false);
        ownPet.setOwnerUUID(caster.getUUID());
        FixtureEntities.FixtureBossWolf otherPet = FixtureEntities.spawnBossWolf(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(26, 4, 14))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(26, 4, 14))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(26, 4, 14))).z);
        otherPet.setTame(true, false);
        otherPet.setOwnerUUID(UUID.randomUUID());
        FixtureEntities.FixtureBossWolf ownerlessTame = FixtureEntities.spawnBossWolf(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 15))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 15))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(22, 4, 15))).z);
        ownerlessTame.setTame(true, false);
        ownerlessTame.setOwnerUUID(null);
        // 一个完全合法的敌对 Husk 作为"确实受伤"的对照。
        Husk open = huskAt(helper, 24, 4, 16, 500.0D);
        double openHp = open.getHealth();
        double controlWolfHp = controlWolf.getHealth();
        double ownPetHp = ownPet.getHealth();
        double otherPetHp = otherPet.getHealth();
        double tameHp = ownerlessTame.getHealth();

        helper.assertTrue(StarEaterAbilities.tryBurst(caster), "the burst must fire");
        helper.assertTrue(open.getHealth() < openHp, "a plain hostile in radius must be hit (control)");
        helper.assertTrue(controlWolf.getHealth() < controlWolfHp,
                "an UNTAMED ownerless boss-wolf must be hit (control proving pets are legitimate targets)");
        helper.assertTrue(Math.abs(ownPet.getHealth() - ownPetHp) < 0.01D, "own pet must be excluded");
        helper.assertTrue(Math.abs(otherPet.getHealth() - otherPetHp) < 0.01D,
                "another player's pet must be excluded");
        helper.assertTrue(Math.abs(ownerlessTame.getHealth() - tameHp) < 0.01D,
                "a tamed animal with a null owner must be excluded");
        helper.assertTrue(Math.abs(nonCreative.getHealth() - ncHp) < 0.01D,
                "a non-creative player must be excluded (control: the open husk was hit)");
        helper.assertTrue(Math.abs(teammate.getHealth() - teammateHp) < 0.01D,
                "a same-team hostile must be excluded (it is otherwise a legitimate target)");
        scoreboard.removePlayerFromTeam(teammate.getStringUUID());
        scoreboard.removePlayerFromTeam(caster.getGameProfile().getName());
        scoreboard.removePlayerTeam(team);
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void burstWallBlocksAndLockedStagesCannotCast(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, STAGE4_POINTS, STAGE4_BOSSES, 24, 4, 6);
        for (int x = 22; x <= 26; x++) {
            for (int y = 4; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.STONE);
            }
        }
        Husk behindWall = huskAt(helper, 24, 4, 10, 500.0D);
        double wallHp = behindWall.getHealth();
        helper.assertTrue(StarEaterAbilities.tryBurst(player), "the burst must fire");
        helper.assertTrue(Math.abs(behindWall.getHealth() - wallHp) < 0.01D,
                "a wall must stop the burst (no line of sight)");

        for (long[] points : new long[][] {{0L, 0L}, {50L, 0L}, {150L, 0L}, {350L, 1L}}) {
            ServerPlayer locked = staged(helper, points[0], points[1], 24, 4, 6);
            helper.assertTrue(stage(locked) < 4, "sanity: burst must be locked (stage=" + stage(locked) + ")");
            Husk victim = huskAt(helper, 24, 4, 11, 500.0D);
            double victimHp = victim.getHealth();
            helper.assertFalse(StarEaterAbilities.tryBurst(locked), "locked stage must not cast");
            helper.assertTrue(Math.abs(victim.getHealth() - victimHp) < 0.01D, "locked burst deals no damage");
            helper.assertTrue(GrowthService.data(locked).burstReadyAt() == 0L,
                    "a locked burst must not consume the cooldown");
        }
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 300)
    public static void skillCancellationAndInvulnerableTimeRespected(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 6);
        Husk cancelled = huskAt(helper, 24, 4, 12, 1_000.0D);
        LOG.watch(cancelled.getUUID(), "sword_ability");
        LOG.cancel = true;
        double hp = cancelled.getHealth();
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the wave must still fire (cooldown consumed)");
        helper.assertTrue(LOG.count() > 0, "the cancellation listener must have seen the ability damage");
        helper.assertTrue(Math.abs(cancelled.getHealth() - hp) < 0.01D,
                "a cancelled ability must cause no real health loss");
        LOG.cancel = false;

        ServerPlayer second = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 6);
        Husk open = huskAt(helper, 24, 4, 12, 1_000.0D);
        open.invulnerableTime = 0;
        helper.assertTrue(StarEaterAbilities.tryWave(second), "the second caster's wave must fire");
        helper.assertTrue(open.getHealth() < open.getMaxHealth(), "the open target must be hit");
        helper.assertTrue(open.invulnerableTime > 0,
                "vanilla sets the hurt cooldown and our code must not clear it (value="
                        + open.invulnerableTime + ")");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 600)
    public static void waveHitsDragonParentExactlyOnce(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = staged(helper, FINAL_POINTS, FINAL_BOSSES, 24, 4, 8);
        net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon =
                helper.spawn(EntityType.ENDER_DRAGON, 24, 12, 24);
        helper.assertFalse(LivingEntity.class.isAssignableFrom(EnderDragonPart.class)
                        || EnderDragonPart.class.isAssignableFrom(LivingEntity.class),
                "sanity: EnderDragonPart is not a LivingEntity, so parts can never be targeted directly");
        face(helper, player, 24, 12, 24);
        double hp = dragon.getHealth();
        LOG.watch(dragon.getUUID(), "sword_ability");
        helper.assertTrue(StarEaterAbilities.tryWave(player), "the wave must fire at the dragon");
        helper.assertTrue(LOG.count() == 1,
                "the dragon parent must receive exactly ONE ability damage event (events=" + LOG.count() + ")");
        helper.assertTrue(dragon.getHealth() < hp,
                "the dragon must really lose health through the normal hurt path (hp=" + dragon.getHealth()
                        + ")");
        LOG.reset();
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 冷却边界（真实世界时钟，通过现有入口）
    // ------------------------------------------------------------------

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void waveCooldownDeadlineUsesRealWorldTicks(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE2_POINTS, 0L, 24, 4, 6);
        face(helper, player, 24, 4, 12);
        helper.runAfterDelay(1L, () -> {
            long start = StarEaterAbilities.clock(player);
            helper.assertTrue(StarEaterAbilities.tryWave(player), "the first cast must succeed");
            helper.assertTrue(GrowthService.data(player).waveReadyAt() == start + 160L,
                    "the stored deadline must be exactly now+160 (deadline="
                            + GrowthService.data(player).waveReadyAt() + ", start=" + start + ")");
            helper.runAfterDelay(159L, () -> {
                long now = StarEaterAbilities.clock(player);
                helper.assertTrue(now - start == 159L, "the world clock must have advanced 159 ticks (got "
                        + (now - start) + ")");
                helper.assertFalse(StarEaterAbilities.tryWave(player),
                        "the wave must still be locked one tick before the deadline");
                helper.runAfterDelay(1L, () -> {
                    long later = StarEaterAbilities.clock(player);
                    helper.assertTrue(later - start == 160L, "the world clock must have advanced 160 ticks (got "
                            + (later - start) + ")");
                    helper.assertTrue(StarEaterAbilities.tryWave(player),
                            "the wave must be accepted exactly at its deadline");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "boss_arena", timeoutTicks = 800)
    public static void burstCooldownDeadlineUsesRealWorldTicks(GameTestHelper helper) {
        ServerPlayer player = staged(helper, STAGE4_POINTS, STAGE4_BOSSES, 24, 4, 6);
        helper.runAfterDelay(1L, () -> {
            long start = StarEaterAbilities.clock(player);
            helper.assertTrue(StarEaterAbilities.tryBurst(player), "the first cast must succeed");
            helper.assertTrue(GrowthService.data(player).burstReadyAt() == start + 400L,
                    "the stored deadline must be exactly now+400");
            helper.runAfterDelay(399L, () -> {
                long now = StarEaterAbilities.clock(player);
                helper.assertTrue(now - start == 399L, "the world clock must have advanced 399 ticks (got "
                        + (now - start) + ")");
                helper.assertFalse(StarEaterAbilities.tryBurst(player),
                        "the burst must still be locked one tick before the deadline");
                helper.runAfterDelay(1L, () -> {
                    long later = StarEaterAbilities.clock(player);
                    helper.assertTrue(later - start == 400L, "the world clock must have advanced 400 ticks (got "
                            + (later - start) + ")");
                    helper.assertTrue(StarEaterAbilities.tryBurst(player),
                            "the burst must be accepted exactly at its deadline");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void configuredNumbersMatchSpec(GameTestHelper helper) {
        var settings = EBConfig.settings();
        helper.assertTrue(Math.abs(CombatMath.waveDamage(settings, 2) - 24.0D) < 0.001D
                        && Math.abs(CombatMath.waveDamage(settings, 7) - 1500.0D) < 0.001D,
                "wave damage must be 24 / 1500");
        helper.assertTrue(CombatMath.waveCooldownTicks(settings, 2) == 160
                        && CombatMath.waveCooldownTicks(settings, 7) == 80, "wave cooldowns must be 160 / 80");
        helper.assertTrue(CombatMath.waveMaxTargets(settings, 2) == 1
                        && CombatMath.waveMaxTargets(settings, 7) == 3, "wave max targets must be 1 / 3");
        helper.assertTrue(Math.abs(CombatMath.burstDamage(settings, 4) - 180.0D) < 0.001D
                        && Math.abs(CombatMath.burstDamage(settings, 7) - 2250.0D) < 0.001D,
                "burst damage must be 180 / 2250");
        helper.assertTrue(CombatMath.burstCooldownTicks(settings, 4) == 400
                        && CombatMath.burstCooldownTicks(settings, 7) == 200, "burst cooldowns must be 400 / 200");
        helper.assertTrue(Math.abs(settings.abilities().burstRadiusFor(4) - 6.0D) < 0.001D
                        && Math.abs(settings.abilities().burstRadiusFor(7) - 8.0D) < 0.001D,
                "burst radii must be 6 / 8");
        helper.succeed();
    }
}



