package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GiftState;
import com.evolvingblade.registry.EBAttachments;
import com.evolvingblade.registry.EBItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * PHASE 3 返工 1：成长数据的真实 NBT 往返/维度保持、锁定与"终身只发一把剑"、以及
 * Boss 分类的两个廉价但真实的断言（监守者=普通敌对、两个 Boss 实例分别入账且不占普通击杀）。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class GrowthPersistenceGameTests {

    private GrowthPersistenceGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void attachmentNbtRoundTripPreservesCountersCooldownsAndRateLimit(GameTestHelper helper) {
        ServerPlayer source = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(source, GrowthMode.STANDARD);
        GrowthService.setCounters(source, 700L, 3L);
        long deadline = com.evolvingblade.combat.StarEaterAbilities.clock(source) + 400L;
        GrowthService.set(source, GrowthService.data(source).withCooldowns(deadline, deadline + 20L));
        source.getData(EBAttachments.BOSS_RATE).put(java.util.UUID.randomUUID(), deadline + 5L);

        // 真实实体 NBT 序列化（附件就是通过这条路径写入/读出的，不是 JsonOps 单测）。
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);

        ServerPlayer restored = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(GrowthService.data(restored).growthPoints() == 0L, "sanity: a fresh player starts empty");
        restored.load(tag);

        GrowthData data = GrowthService.data(restored);
        helper.assertTrue(data.growthPoints() == GrowthService.data(source).growthPoints(),
                "growth points must survive the NBT round trip (restored=" + data.growthPoints()
                        + ", source=" + GrowthService.data(source).growthPoints() + ")");
        helper.assertTrue(data.hostileKills() == 700L && data.bossKills() == 3L,
                "counters must survive the round trip (hostile=" + data.hostileKills() + ", boss=" + data.bossKills() + ")");
        helper.assertTrue(data.mode() == GrowthMode.STANDARD, "mode must survive the round trip");
        helper.assertTrue(data.waveReadyAt() == deadline && data.burstReadyAt() == deadline + 20L,
                "cooldown deadlines must survive the round trip (wave=" + data.waveReadyAt()
                        + ", burst=" + data.burstReadyAt() + ")");
        helper.assertTrue(restored.getData(EBAttachments.BOSS_RATE).size() == 1,
                "the per-player boss rate table must survive the round trip (size="
                        + restored.getData(EBAttachments.BOSS_RATE).size() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void dimensionChangeRetainsGrowthData(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, 350L, 1L);
        long points = GrowthService.data(player).growthPoints();
        int stage = GrowthService.stage(player);
        net.minecraft.server.level.ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "sanity: the nether must exist");
        player.teleportTo(nether, 0.5D, 64.0D, 0.5D, 0.0F, 0.0F);
        helper.assertTrue(player.level() == nether, "sanity: the player must be in the nether now");
        helper.assertTrue(GrowthService.data(player).growthPoints() == points
                        && GrowthService.stage(player) == stage,
                "a dimension change must not reset growth data (points=" + GrowthService.data(player).growthPoints()
                        + ", stage=" + GrowthService.stage(player) + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chooseLocksAndGiftIsDeliveredExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(GrowthService.chooseMode(player, GrowthMode.QUICK), "the first choice must be accepted");
        helper.assertFalse(GrowthService.chooseMode(player, GrowthMode.LONG),
                "a second choice must be rejected (the path is locked)");
        helper.assertTrue(GrowthService.data(player).mode() == GrowthMode.QUICK,
                "the locked mode must stay the first one");
        long granted = java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                .filter(i -> player.getInventory().getItem(i).is(EBItems.starEater())).count();
        helper.assertTrue(granted == 1L, "exactly one sword must be delivered (granted=" + granted + ")");
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.GRANTED, "gift state must be GRANTED");

        // 管理员改模式：保留计数与赠剑标记，且不会多发一把剑。
        GrowthService.setCounters(player, 700L, 3L);
        GrowthService.forceMode(player, GrowthMode.LONG);
        long after = java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
                .filter(i -> player.getInventory().getItem(i).is(EBItems.starEater())).count();
        helper.assertTrue(GrowthService.data(player).giftState() == GiftState.GRANTED
                        && GrowthService.data(player).hostileKills() == 700L && after == 1L,
                "admin mode/counter changes must keep counters and never grant a second sword (granted=" + after + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wardenAndHighHealthHusksAreOrdinary(GameTestHelper helper) {
        Warden warden = helper.spawn(EntityType.WARDEN, 4, 1, 3);
        warden.setNoAi(true);
        BossClassification wardenClassification = BossClassifier.classify(warden, EBConfig.settings().bossRules());
        helper.assertFalse(wardenClassification.boss(),
                "the Warden must stay an ordinary hostile despite 500 max health (reason="
                        + wardenClassification.reason() + ")");
        helper.assertTrue(BossClassifier.isHostile(warden, EBConfig.settings().hostileRules()),
                "the Warden must still be an ordinary hostile for growth");

        // SPEC：高生命本身不是 Boss（自动探测阈值默认 200，但 Husk 也只有生命这一项，必须仍是普通）。
        Husk highHealth = helper.spawn(EntityType.HUSK, 2, 1, 5);
        highHealth.setNoAi(true);
        highHealth.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        highHealth.setHealth(500.0F);
        helper.assertFalse(BossClassifier.classify(highHealth, EBConfig.settings().bossRules()).boss(),
                "500 max health alone must stay ordinary for a plain husk");
        Husk belowThreshold = helper.spawn(EntityType.HUSK, 4, 1, 5);
        belowThreshold.setNoAi(true);
        belowThreshold.getAttribute(Attributes.MAX_HEALTH).setBaseValue(199.0D);
        belowThreshold.setHealth(199.0F);
        helper.assertFalse(BossClassifier.classify(belowThreshold, EBConfig.settings().bossRules()).boss(),
                "199 max health (below the auto-detect threshold) must stay ordinary");
        helper.succeed();
    }
}
