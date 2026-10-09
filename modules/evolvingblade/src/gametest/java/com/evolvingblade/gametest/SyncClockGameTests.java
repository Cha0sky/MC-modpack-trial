package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 同步与时钟回归：计数/模式/阶段变化必须<b>立刻</b>协调持有者属性并下发权威快照；
 * 快照里的冷却剩余必须与冷却创建/比较使用<b>同一个时钟</b>（主世界 {@code gameTime}）。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class SyncClockGameTests {

    private SyncClockGameTests() {}

    /** 标准模式终阶门槛（成长点 3000 / Boss 20）。 */
    private static final long FINAL_POINTS = 3000L;
    private static final long FINAL_BOSSES = 20L;

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void counterChangeReconcilesHeldSwordStatsImmediately(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        GrowthService.setCounters(player, 0L, 0L);

        int stageBefore = GrowthService.stage(player);
        double damageBefore = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speedBefore = player.getAttributeValue(Attributes.ATTACK_SPEED);

        // 一次纯粹的管理员计数改动（不经过任何 tick）：属性必须当场跟随。
        GrowthService.setCounters(player, FINAL_POINTS, FINAL_BOSSES);

        int stageAfter = GrowthService.stage(player);
        double damageAfter = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speedAfter = player.getAttributeValue(Attributes.ATTACK_SPEED);

        helper.assertTrue(stageAfter > stageBefore,
                "sanity: the counters must raise the stage (" + stageBefore + " -> " + stageAfter + ")");
        double expectedDamageDelta = EBConfig.settings().stageAttackDamage(stageAfter)
                - EBConfig.settings().stageAttackDamage(stageBefore);
        double expectedSpeedDelta = EBConfig.settings().stageAttackSpeed(stageAfter)
                - EBConfig.settings().stageAttackSpeed(stageBefore);
        helper.assertTrue(Math.abs((damageAfter - damageBefore) - expectedDamageDelta) < 0.001D,
                "counter change must immediately reconcile held sword attack damage (delta="
                        + (damageAfter - damageBefore) + ", expected=" + expectedDamageDelta + ")");
        helper.assertTrue(Math.abs((speedAfter - speedBefore) - expectedSpeedDelta) < 0.001D,
                "counter change must immediately reconcile held sword attack speed (delta="
                        + (speedAfter - speedBefore) + ", expected=" + expectedSpeedDelta + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ordinaryKillUpdatesSnapshotCountersImmediately(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, 0L, 0L);
        long pointsBefore = EBPayloads.snapshotFor(player, false).growthPoints();
        long killsBefore = EBPayloads.snapshotFor(player, false).hostileKills();

        // 不属于任何"升阶"的普通击杀：快照内容必须立刻反映新计数。
        GrowthService.creditHostileKill(player);
        EBPayloads.StateSnapshot snapshot = EBPayloads.snapshotFor(player, false);
        helper.assertTrue(snapshot.hostileKills() == killsBefore + 1L,
                "an ordinary kill must be visible in the authoritative snapshot immediately (kills="
                        + snapshot.hostileKills() + ")");
        helper.assertTrue(snapshot.growthPoints() == pointsBefore
                        + EBConfig.settings().hostilePointWeight(),
                "growth points must be visible in the authoritative snapshot immediately (points="
                        + snapshot.growthPoints() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void snapshotCooldownUsesOverworldClockAcrossDimensions(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, FINAL_POINTS, FINAL_BOSSES);

        MinecraftServer server = helper.getLevel().getServer();
        long overworldNow = server.overworld().getGameTime();
        long levelNow = player.level().getGameTime();
        long deadline = overworldNow + 100L;
        // 直接用"绝对到期 tick"写入冷却（与技能释放时的写入方式一致）。
        GrowthService.set(player, GrowthService.data(player).withCooldowns(deadline, deadline));

        long remaining = EBPayloads.snapshotFor(player, false).waveRemaining();
        helper.assertTrue(Math.abs(remaining - (deadline - overworldNow)) <= 2L,
                "snapshot must derive cooldown remaining from the overworld clock (overworldNow=" + overworldNow
                        + ", levelNow=" + levelNow + ", remaining=" + remaining + ", expected="
                        + (deadline - overworldNow) + ")");

        // 换到另一个维度（另一个 ServerLevel，拥有自己的 gameTime）：剩余时间必须仍然按同一权威时钟计算。
        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "sanity: the nether level must exist in the GameTest server");
        player.teleportTo(nether, 0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
        helper.assertTrue(player.level() == nether, "sanity: the player must now be in the nether");
        long netherNow = nether.getGameTime();
        long remainingNether = EBPayloads.snapshotFor(player, false).waveRemaining();
        helper.assertTrue(Math.abs(remainingNether - (deadline - overworldNow)) <= 4L,
                "after a dimension change the remaining cooldown must still use the overworld clock"
                        + " (overworldNow=" + overworldNow + ", netherNow=" + netherNow
                        + ", remaining=" + remainingNether + ", expected≈" + (deadline - overworldNow) + ")");
        helper.succeed();
    }
}
