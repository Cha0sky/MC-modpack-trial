package com.evolvingblade.gametest;

import java.util.Set;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.boss.BossRules;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Stray;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Boss 兼容性分类回归（真实实体 + 生产分类路径）。
 *
 * <p>SPEC：<b>高生命本身不等于 Boss</b>。Boss 只来自"强制包含 / 标签 / 非空 ServerBossEvent 字段 /
 * 类名包含 Boss / 自动探测"，且"强制排除"优先级最高。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class BossCompatGameTests {

    private BossCompatGameTests() {}

    private static final String HUSK_ID = "minecraft:husk";

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void highHealthRealHuskStaysOrdinary(GameTestHelper helper) {
        Husk plain = helper.spawn(EntityType.HUSK, 4, 1, 3);
        plain.setNoAi(true);
        plain.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        plain.setHealth(500.0F);
        BossClassification classification = BossClassifier.classify(plain, EBConfig.settings().bossRules());
        helper.assertFalse(classification.boss(),
                "500 max health alone must NOT be a boss (reason=" + classification.reason() + ")");
        helper.assertTrue(BossClassifier.isHostile(plain, EBConfig.settings().hostileRules()),
                "a 500 HP husk must still be an ordinary hostile");
        helper.assertTrue(classification.reason() != null, "the classification must carry a reason for queries");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void serverBossEventBranchesAreRecognized(GameTestHelper helper) {
        var level = helper.getLevel();
        var rules = EBConfig.settings().bossRules();
        FixtureEntities.FixtureBossHusk ownField = FixtureEntities.spawn(level, 4.5D, -60.0D, 3.5D,
                FixtureEntities.FixtureBossHusk::new);
        FixtureEntities.FixtureInheritedBossHusk inherited = FixtureEntities.spawn(level, 2.5D, -60.0D, 3.5D,
                FixtureEntities.FixtureInheritedBossHusk::new);
        FixtureEntities.FixtureBarHusk barOnly = FixtureEntities.spawn(level, 6.5D, -60.0D, 3.5D,
                FixtureEntities.FixtureBarHusk::new);
        // SPEC：自动探测要求"生命 ≥ 阈值"**并且**（bar 字段 或 类名含 Boss），所以夹具必须够高生命。
        for (Husk fixture : new Husk[] {ownField, inherited, barOnly}) {
            fixture.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            fixture.setHealth(500.0F);
        }
        helper.assertTrue(BossClassifier.classify(ownField, rules).boss(),
                "a non-null ServerBossEvent field must classify as a boss");
        helper.assertTrue(BossClassifier.classify(inherited, rules).boss(),
                "an inherited non-null ServerBossEvent field must classify as a boss");
        helper.assertTrue(BossClassifier.classify(barOnly, rules).boss(),
                "a non-null bar must classify as a boss even when the class name has no 'Boss'");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void classNameBossBranchAndOrdinaryFixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var rules = EBConfig.settings().bossRules();
        FixtureEntities.FixtureClassNameBossHusk byName = FixtureEntities.spawn(level, 4.5D, -60.0D, 3.5D,
                FixtureEntities.FixtureClassNameBossHusk::new);
        FixtureEntities.FixturePlainHusk plain = FixtureEntities.spawn(level, 2.5D, -60.0D, 3.5D,
                FixtureEntities.FixturePlainHusk::new);
        byName.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        byName.setHealth(500.0F);
        plain.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        plain.setHealth(500.0F);
        helper.assertTrue(BossClassifier.classify(byName, rules).boss(),
                "a class name containing 'Boss' must classify as a boss (reason="
                        + BossClassifier.classify(byName, rules).reason() + ")");
        helper.assertFalse(BossClassifier.classify(plain, rules).boss(),
                "a test-only subclass with no bar, no 'Boss' name and 500 HP must stay ordinary");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forcedExcludeOutranksIncludeAndAutoDetect(GameTestHelper helper) {
        Husk husk = helper.spawn(EntityType.HUSK, 4, 1, 3);
        husk.setNoAi(true);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
        husk.setHealth(500.0F);
        // 强制包含 + 强制排除同时命中同一个实体：排除必须优先。
        BossRules excludeWins = new BossRules(true, 200.0D, Set.of(HUSK_ID), Set.of(HUSK_ID));
        helper.assertFalse(BossClassifier.classify(husk, excludeWins).boss(),
                "a forced exclude must outrank include/tag/auto detection");
        // 只有强制包含：判定为 Boss。
        BossRules includeOnly = new BossRules(false, 200.0D, Set.of(HUSK_ID), Set.of());
        helper.assertTrue(BossClassifier.classify(husk, includeOnly).boss(),
                "a forced include must classify as a boss");
        // 关闭自动探测且不在名单里：高生命仍然是普通。
        BossRules noAuto = new BossRules(false, 200.0D, Set.of(), Set.of());
        helper.assertFalse(BossClassifier.classify(husk, noAuto).boss(),
                "with auto-detection off and no list match a 500 HP husk must stay ordinary");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void devOnlyCommonBossesTagIsHonoured(GameTestHelper helper) {
        var rules = EBConfig.settings().bossRules();
        // gametest 源集里的 dev-only 标签夹具：data/c/tags/entity_type/bosses.json 只加了 minecraft:stray。
        Stray stray = helper.spawn(EntityType.STRAY, 4, 1, 3);
        stray.setNoAi(true);
        Husk husk = helper.spawn(EntityType.HUSK, 2, 1, 3);
        husk.setNoAi(true);
        helper.assertTrue(BossClassifier.classify(stray, rules).boss(),
                "a type listed in the dev-only c:bosses tag must classify as a boss (reason="
                        + BossClassifier.classify(stray, rules).reason() + ")");
        helper.assertFalse(BossClassifier.classify(husk, rules).boss(),
                "the dev-only tag must not leak onto other types (husk must stay ordinary)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void twoRealBossInstancesCountTwiceAndBossPointsAreExclusive(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, 0L, 0L);
        var level = helper.getLevel();
        for (int i = 0; i < 2; i++) {
            FixtureEntities.FixtureBossHusk boss = FixtureEntities.spawn(level,
                    3.5D + i, -60.0D, 4.5D, FixtureEntities.FixtureBossHusk::new);
            boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            boss.setHealth(1.0F);
            boss.hurt(level.damageSources().playerAttack(player), 100.0F);
        }
        helper.runAfterDelay(5L, () -> {
            helper.assertTrue(GrowthService.data(player).bossKills() == 2L,
                    "two distinct boss instances must both be counted (bossKills="
                            + GrowthService.data(player).bossKills() + ")");
            helper.assertTrue(GrowthService.data(player).hostileKills() == 0L,
                    "boss kills must be exclusive and never count as ordinary kills (hostile="
                            + GrowthService.data(player).hostileKills() + ")");
            helper.assertTrue(GrowthService.data(player).growthPoints()
                            == 2L * EBConfig.settings().bossPointWeight(),
                    "boss points must use the configured weight (points="
                            + GrowthService.data(player).growthPoints() + ")");
            helper.succeed();
        });
    }
}
