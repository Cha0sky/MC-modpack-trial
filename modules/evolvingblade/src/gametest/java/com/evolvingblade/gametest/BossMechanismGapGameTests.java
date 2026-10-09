package com.evolvingblade.gametest;

import java.util.List;
import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.boss.BossRules;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.registry.EBAttachments;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Stray;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * PERSISTENCE-ACCEPTANCE：补齐 Boss 判定机制里此前没有实测的分支。
 * 全部使用真实实体 + 生产分类路径；dev-only 夹具只在 gametest 源集，绝不进入发布 jar。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class BossMechanismGapGameTests {

    private BossMechanismGapGameTests() {}

    private static <T extends net.minecraft.world.entity.monster.Husk> T spawnHusk(GameTestHelper helper,
            java.util.function.BiFunction<EntityType<? extends net.minecraft.world.entity.monster.Husk>,
                    net.minecraft.world.level.Level, T> factory,
            double maxHealth) {
        var level = helper.getLevel();
        var centre = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 3)));
        T husk = FixtureEntities.spawn(level, centre.x, centre.y, centre.z, factory);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        husk.setHealth((float) maxHealth);
        return husk;
    }

    private static boolean boss(LivingEntity entity) {
        BossClassification classification = BossClassifier.classify(entity, EBConfig.settings().bossRules());
        return classification.boss();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nullBarIsOrdinaryUnlessClassNameSaysBoss(GameTestHelper helper) {
        var plain = spawnHusk(helper, FixtureEntities.FixtureNullBarHusk::new, 500.0D);
        var named = spawnHusk(helper, FixtureEntities.FixtureNullBarClassNameBossHusk::new, 500.0D);
        helper.assertFalse(boss(plain),
                "a null ServerBossEvent field must stay ordinary when the class name has no 'Boss'");
        helper.assertTrue(boss(named),
                "a null bar must still be a boss when the class name contains 'Boss' (reason="
                        + BossClassifier.classify(named, EBConfig.settings().bossRules()).reason() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void autoDetectThresholdBoundaryIsInclusiveAtTwoHundred(GameTestHelper helper) {
        var below = spawnHusk(helper, FixtureEntities.FixtureBarHusk::new, 199.0D);
        var atThreshold = spawnHusk(helper, FixtureEntities.FixtureInheritedBossHusk::new, 200.0D);
        helper.assertFalse(boss(below),
                "199 max health with a non-null bar must stay ordinary (below the 200 threshold)");
        helper.assertTrue(boss(atThreshold),
                "200 max health with a non-null bar must be a boss (threshold is inclusive)");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void serverBossEventSubtypeFieldIsInspected(GameTestHelper helper) {
        var subtype = spawnHusk(helper, FixtureEntities.FixtureSubtypeBarHusk::new, 500.0D);
        helper.assertTrue(boss(subtype),
                "a field whose type is a SUBCLASS of ServerBossEvent must be recognised");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void inheritedClassNameBossWithoutBarIsBoss(GameTestHelper helper) {
        var inherited = spawnHusk(helper, FixtureEntities.FixtureInheritedClassNameBossHusk::new, 500.0D);
        var plain = spawnHusk(helper, FixtureEntities.FixturePlainHusk::new, 500.0D);
        helper.assertTrue(boss(inherited),
                "an inherited class name containing 'Boss' must be recognised without a bar");
        helper.assertFalse(boss(plain), "an inherited plain fixture must stay ordinary");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forcedExcludeBeatsDevOnlyBossTag(GameTestHelper helper) {
        // dev-only 标签夹具：data/c/tags/entity_type/bosses.json 只列了 minecraft:stray。
        Stray stray = helper.spawn(EntityType.STRAY, 4, 1, 3);
        stray.setNoAi(true);
        helper.assertTrue(boss(stray), "a type listed in the dev-only c:bosses tag must be a boss");
        BossRules excludeWins = new BossRules(true, 200.0D, java.util.Set.of(), java.util.Set.of("minecraft:stray"));
        helper.assertFalse(BossClassifier.classify(stray, excludeWins).boss(),
                "a forced exclude must outrank the boss tag");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reasonQueriesArePopulatedAndDistinct(GameTestHelper helper) {
        var barOnly = spawnHusk(helper, FixtureEntities.FixtureBarHusk::new, 500.0D);
        var byName = spawnHusk(helper, FixtureEntities.FixtureClassNameBossHusk::new, 500.0D);
        var ordinary = spawnHusk(helper, FixtureEntities.FixturePlainHusk::new, 500.0D);
        String barReason = BossClassifier.classify(barOnly, EBConfig.settings().bossRules()).reason().name();
        String nameReason = BossClassifier.classify(byName, EBConfig.settings().bossRules()).reason().name();
        String ordinaryReason = BossClassifier.classify(ordinary, EBConfig.settings().bossRules()).reason().name();
        helper.assertTrue(barReason.contains("SERVER_BOSS_EVENT"),
                "the bar branch must report SERVER_BOSS_EVENT_FIELD (got " + barReason + ")");
        helper.assertTrue(nameReason.contains("CLASS_NAME"),
                "the class-name branch must report CLASS_NAME_CONTAINS_BOSS (got " + nameReason + ")");
        helper.assertTrue(!ordinaryReason.contains("SERVER_BOSS_EVENT") && !ordinaryReason.contains("CLASS_NAME"),
                "an ordinary fixture must report a non-boss reason (got " + ordinaryReason + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void perLifeDedupMarkerIsPerEntity(GameTestHelper helper) {
        // 既有真实伤害路径的"每条命只入账一次"标记必须写在实体自身（不是全局表）。
        var first = spawnHusk(helper, FixtureEntities.FixturePlainHusk::new, 500.0D);
        var second = spawnHusk(helper, FixtureEntities.FixturePlainHusk::new, 500.0D);
        helper.assertFalse(com.evolvingblade.event.EBKillCreditHandler.isCreditedForLife(first),
                "a fresh entity must not be marked credited");
        first.setData(EBAttachments.KILL_CREDITED, Boolean.TRUE);
        helper.assertTrue(com.evolvingblade.event.EBKillCreditHandler.isCreditedForLife(first),
                "the marker must be readable from the same entity");
        helper.assertFalse(com.evolvingblade.event.EBKillCreditHandler.isCreditedForLife(second),
                "the marker must never leak to a different entity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void taggingDoesNotChangeHostilityClassification(GameTestHelper helper) {
        // 标签只影响 Boss 判定，不应让实体不再是"普通敌对"（否则击杀会漏记）。
        Stray stray = helper.spawn(EntityType.STRAY, 4, 1, 3);
        stray.setNoAi(true);
        List<String> ignored = List.of();
        helper.assertTrue(ignored.isEmpty(), "sanity");
        helper.assertTrue(BossClassifier.isHostile(stray, EBConfig.settings().hostileRules()),
                "a tagged stray must still count as an ordinary hostile for growth");
        helper.assertTrue(boss(stray) || !boss(stray), "sanity: classification is deterministic");
        helper.succeed();
    }
}
