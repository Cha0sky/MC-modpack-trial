package com.evolvingblade.gametest;

import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.AbilityState;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthThresholds;
import com.evolvingblade.growth.GiftState;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBAttachments;
import com.evolvingblade.registry.EBItems;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * PERSISTENCE-ACCEPTANCE：真实玩家 attachment 路径上的三模式八阶段门槛、双门槛、死亡克隆 / 末地返回复制、
 * 权威快照编解码有界性，以及"选择意图只接受模式枚举"。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class PersistenceAcceptanceGameTests {

    private PersistenceAcceptanceGameTests() {}

    private static ServerPlayer fresh(GameTestHelper helper, GrowthMode mode, long points, long bosses) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, mode);
        GrowthService.setCounters(player, points, bosses);
        return player;
    }

    /** 用 attachment 里<b>真实算出来</b>的成长点/击杀数推断期望阶段（双门槛同时满足）。 */
    private static int expectedStage(GrowthSettingsView view, long points, long bosses) {
        int stage = 0;
        for (int s = 0; s < GrowthMode.STAGE_COUNT; s++) {
            if (points >= view.pointAt(s) && bosses >= view.bossAt(s)) {
                stage = s;
            }
        }
        return stage;
    }

    private record GrowthSettingsView(GrowthThresholds thresholds) {
        long pointAt(int s) {
            return this.thresholds.pointAt(s);
        }

        long bossAt(int s) {
            return this.thresholds.bossAt(s);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void allThreeModesEightStagesThroughRealAttachments(GameTestHelper helper) {
        var settings = EBConfig.settings();
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds thresholds = settings.thresholds(mode);
            GrowthSettingsView view = new GrowthSettingsView(thresholds);
            for (int stage = 1; stage < GrowthMode.STAGE_COUNT; stage++) {
                long points = thresholds.pointAt(stage);
                long bosses = thresholds.bossAt(stage);
                ServerPlayer player = fresh(helper, mode, points, bosses);
                GrowthData data = GrowthService.data(player);
                int actual = GrowthService.stage(player);
                int expected = expectedStage(view, data.growthPoints(), data.bossKills());
                helper.assertTrue(data.mode() == mode, "the mode must be the requested one (" + mode + ")");
                helper.assertTrue(actual == expected,
                        "stage must follow the real attachment values (mode=" + mode + ", wanted>=" + stage
                                + ", actual=" + actual + ", expected=" + expected + ", points="
                                + data.growthPoints() + ", bosses=" + data.bossKills() + ")");
                helper.assertTrue(actual >= stage,
                        "the generated legal counts must satisfy both gates at stage " + stage + " (mode=" + mode
                                + ", actual=" + actual + ")");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void pointGateAndBossGateAreBothRequired(GameTestHelper helper) {
        var settings = EBConfig.settings();
        GrowthThresholds thresholds = settings.thresholds(GrowthMode.STANDARD);
        int top = GrowthMode.STAGE_COUNT - 1;
        long neededPoints = thresholds.pointAt(top); // 3000
        long neededBosses = thresholds.bossAt(top);  // 20
        long bossContribution = neededBosses * settings.bossPointWeight(); // 2000

        // 1) 两个门槛都满足 → 终阶。
        ServerPlayer ok = fresh(helper, GrowthMode.STANDARD, neededPoints - bossContribution, neededBosses);
        helper.assertTrue(GrowthService.stage(ok) == top,
                "both gates satisfied must reach the final stage (stage=" + GrowthService.stage(ok) + ")");

        // 2) 成长点差 1（Boss 满足）→ 不得进入终阶。
        ServerPlayer shortPoints = fresh(helper, GrowthMode.STANDARD,
                neededPoints - 1 - bossContribution, neededBosses);
        helper.assertTrue(GrowthService.stage(shortPoints) < top,
                "one point below the threshold must NOT advance (points="
                        + GrowthService.data(shortPoints).growthPoints() + ", stage="
                        + GrowthService.stage(shortPoints) + ")");

        // 3) Boss 差 1（成长点满足）→ 不得进入终阶。
        ServerPlayer shortBosses = fresh(helper, GrowthMode.STANDARD,
                neededPoints - bossContribution, neededBosses - 1);
        helper.assertTrue(GrowthService.stage(shortBosses) < top,
                "one boss below the threshold must NOT advance (bosses="
                        + GrowthService.data(shortBosses).bossKills() + ", stage="
                        + GrowthService.stage(shortBosses) + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void finalAttackCurveIsIdenticalAcrossAllThreeModes(GameTestHelper helper) {
        var settings = EBConfig.settings();
        double damage = settings.stageAttackDamage(GrowthMode.STAGE_COUNT - 1);
        double speed = settings.stageAttackSpeed(GrowthMode.STAGE_COUNT - 1);
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds thresholds = settings.thresholds(mode);
            long bossWeight = settings.bossPointWeight();
            ServerPlayer player = fresh(helper, mode,
                    thresholds.pointAt(GrowthMode.STAGE_COUNT - 1) - thresholds.bossAt(GrowthMode.STAGE_COUNT - 1) * bossWeight,
                    thresholds.bossAt(GrowthMode.STAGE_COUNT - 1));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
            GrowthService.syncAfterChange(player);
            helper.assertTrue(GrowthService.stage(player) == GrowthMode.STAGE_COUNT - 1,
                    "sanity: final stage per mode (" + mode + ")");
            helper.assertTrue(Math.abs(player.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - damage) < 0.01D,
                    "the final attack curve must be identical across modes (mode=" + mode + ", value="
                            + player.getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                            + ", expected=" + damage + ")");
            helper.assertTrue(Math.abs(player.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) - speed) < 0.01D,
                    "the final attack speed must be identical across modes (mode=" + mode + ")");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void deathCloneAndEndReturnCopyRealAttachments(GameTestHelper helper) {
        ServerPlayer original = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(original, GrowthMode.LONG);
        GrowthService.setCounters(original, 500L, 2L);
        GrowthService.requestGift(original);
        long deadline = StarEaterAbilities.clock(original) + 400L;
        GrowthService.set(original, GrowthService.data(original).withCooldowns(deadline, deadline + 20L));
        UUID target = UUID.randomUUID();
        original.getData(EBAttachments.BOSS_RATE).put(target, deadline + 5L);
        GrowthData before = GrowthService.data(original);
        int swordsBefore = countSwords(original);
        helper.assertTrue(before.mode() == GrowthMode.LONG && before.giftState() == GiftState.GRANTED,
                "sanity: the original player must be locked with a granted gift");

        // 1) 死亡克隆（copyOnDeath 路径）：wasDeath=true → 只复制声明了 copyOnDeath 的类型。
        ServerPlayer afterDeath = helper.makeMockServerPlayerInLevel();
        afterDeath.restoreFrom(original, false);
        GrowthData deathCopy = GrowthService.data(afterDeath);
        helper.assertTrue(deathCopy.growthPoints() == before.growthPoints()
                        && deathCopy.hostileKills() == before.hostileKills()
                        && deathCopy.bossKills() == before.bossKills(),
                "death clone must copy counters (points=" + deathCopy.growthPoints() + ")");
        helper.assertTrue(deathCopy.mode() == GrowthMode.LONG, "death clone must copy the locked mode");
        helper.assertTrue(deathCopy.giftState() == GiftState.GRANTED, "death clone must copy the gift state");
        helper.assertTrue(deathCopy.waveReadyAt() == before.waveReadyAt()
                        && deathCopy.burstReadyAt() == before.burstReadyAt(),
                "death clone must copy both cooldown deadlines");
        helper.assertTrue(afterDeath.getData(EBAttachments.BOSS_RATE).containsKey(target),
                "death clone must copy the per-target boss rate table");
        // 已经发过剑：不能再发第二把。
        helper.assertTrue(GrowthService.requestGift(afterDeath), "a granted player keeps GRANTED");
        helper.assertTrue(countSwords(afterDeath) == 0,
                "the copied player must not receive a second sword (the copy has no inventory copy)");

        // 2) 末地返回克隆（keepEverything=true）：所有可序列化 attachment 都复制。
        ServerPlayer afterEnd = helper.makeMockServerPlayerInLevel();
        afterEnd.restoreFrom(original, true);
        GrowthData endCopy = GrowthService.data(afterEnd);
        helper.assertTrue(endCopy.growthPoints() == before.growthPoints()
                        && endCopy.mode() == GrowthMode.LONG
                        && endCopy.giftState() == GiftState.GRANTED
                        && endCopy.waveReadyAt() == before.waveReadyAt()
                        && endCopy.burstReadyAt() == before.burstReadyAt(),
                "the End-return clone must copy growth, mode, gift and cooldowns");
        helper.assertTrue(afterEnd.getData(EBAttachments.BOSS_RATE).containsKey(target),
                "the End-return clone must copy the boss rate table");

        // 3) 生命周期之后不得重置、不得多发剑；管理员改模式也不多发剑。
        helper.assertFalse(GrowthService.chooseMode(afterDeath, GrowthMode.QUICK),
                "a locked (copied) player must reject a new choice");
        GrowthService.forceMode(afterDeath, GrowthMode.STANDARD);
        helper.assertTrue(countSwords(afterDeath) == 0,
                "an admin mode change must never grant another sword");
        helper.assertTrue(countSwords(original) == swordsBefore,
                "the original player's sword count must be unchanged (" + swordsBefore + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void stateSnapshotCodecRoundTripIsBoundedAndComplete(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, 700L, 3L);
        EBPayloads.StateSnapshot snapshot = EBPayloads.snapshotFor(player, true);
        helper.assertTrue(snapshot.thresholdsPoints().size() == 8 && snapshot.thresholdsBosses().size() == 8,
                "the current-mode thresholds must have exactly 8 entries");
        helper.assertTrue(snapshot.modePoints().size() == 3 * 8 && snapshot.modeBosses().size() == 3 * 8,
                "all three modes must ship 8 thresholds each (size=" + snapshot.modePoints().size() + ")");
        helper.assertTrue(snapshot.ability().size() == EBPayloads.StateSnapshot.ABILITY_COUNT
                        && EBPayloads.StateSnapshot.ABILITY_COUNT == 18,
                "the ability array must carry exactly the declared 18 values");
        helper.assertTrue(snapshot.hasMode() && snapshot.modeId().equals(GrowthMode.STANDARD.id()),
                "the snapshot must carry the authoritative mode");

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        EBPayloads.StateSnapshot.CODEC.encode(buffer, snapshot);
        EBPayloads.StateSnapshot decoded = EBPayloads.StateSnapshot.CODEC.decode(buffer);
        helper.assertTrue(decoded.modeId().equals(snapshot.modeId())
                        && decoded.stage() == snapshot.stage()
                        && decoded.growthPoints() == snapshot.growthPoints()
                        && decoded.hostileKills() == snapshot.hostileKills()
                        && decoded.bossKills() == snapshot.bossKills()
                        && decoded.waveUnlockStage() == snapshot.waveUnlockStage(),
                "the codec round trip must preserve every scalar field");
        helper.assertTrue(decoded.modePoints().equals(snapshot.modePoints())
                        && decoded.modeBosses().equals(snapshot.modeBosses())
                        && decoded.ability().equals(snapshot.ability()),
                "the codec round trip must preserve the bounded lists exactly");
        helper.assertTrue(decoded.stagePoints(GrowthMode.LONG, 7) == snapshot.stagePoints(GrowthMode.LONG, 7),
                "per-mode threshold accessors must survive the round trip");
        // 1.1.0：新增的"阶段基础攻击 / 当前持剑有效攻击 / 是否持剑 / 技能是否继承攻击"必须一起过编解码。
        helper.assertTrue(decoded.holdingStarEater() == snapshot.holdingStarEater()
                        && decoded.abilityDamageFromAttack() == snapshot.abilityDamageFromAttack(),
                "the 1.1.0 booleans must survive the codec round trip");
        helper.assertTrue(java.util.Objects.equals(decoded.effectiveHeldAttack(), snapshot.effectiveHeldAttack())
                        && decoded.stageBaseAttack() == snapshot.stageBaseAttack(),
                "the 1.1.0 attack-value fields must survive the codec round trip exactly");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void chooseIntentOnlyCarriesAModeAndDuplicatesAreRejected(GameTestHelper helper) {
        // C2S 载荷只带一个 GrowthMode；非法 id 无法解析（严格解析会抛错）。
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        EBPayloads.ChooseMode.CODEC.encode(buffer, new EBPayloads.ChooseMode(GrowthMode.QUICK));
        helper.assertTrue(EBPayloads.ChooseMode.CODEC.decode(buffer).mode() == GrowthMode.QUICK,
                "the choose payload must round trip exactly one mode");
        boolean threw = false;
        try {
            GrowthMode.require("definitely-not-a-mode");
        } catch (RuntimeException expected) {
            threw = true;
        }
        helper.assertTrue(threw, "an invalid mode id must be rejected by strict parsing");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(GrowthService.chooseMode(player, GrowthMode.QUICK), "the first choice must succeed");
        helper.assertFalse(GrowthService.chooseMode(player, GrowthMode.LONG),
                "a duplicate choice must be rejected (the path is locked)");
        helper.assertTrue(GrowthService.data(player).mode() == GrowthMode.QUICK,
                "the locked mode must remain the first one");
        helper.assertTrue(AbilityState.DEFAULT_MAX_RATE_ENTRIES == 256,
                "sanity: bounded rate table constant");
        helper.succeed();
    }

    private static int countSwords(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(EBItems.starEater())) {
                count++;
            }
        }
        return count;
    }
}
