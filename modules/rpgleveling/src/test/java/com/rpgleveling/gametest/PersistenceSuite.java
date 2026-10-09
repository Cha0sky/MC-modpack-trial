package com.rpgleveling.gametest;

import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.service.RpgService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 6: the persisted state round-trips exactly, a malformed payload degrades
 * safely instead of corrupting the save, and a clone (death / End return) carries the
 * data across.
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class PersistenceSuite {

    private PersistenceSuite() {}

    @GameTest(template = "empty", timeoutTicks = 200, batch = "persistence")
    public static void serializeRoundTripIsLossless(GameTestHelper helper) {
        PlayerRpgData source = new PlayerRpgData();
        source.setTotalXpExact(1_234_567L);
        source.respecAll();                       // bump respecCount first: it must survive too
        source.setPointsExact(StatLine.ARMOR_FLAT, 12);
        source.setPointsExact(StatLine.XP_GAIN_PCT, 7);
        source.setPointsExact(StatLine.CRIT_CHANCE_FLAT, 5);

        CompoundTag tag = source.serializeNBT(null);
        PlayerRpgData restored = new PlayerRpgData();
        restored.deserializeNBT(null, tag);

        helper.assertTrue(restored.totalXp() == source.totalXp(), "totalXp did not round trip");
        helper.assertTrue(restored.level() == source.level(), "derived level did not round trip");
        helper.assertTrue(restored.points(StatLine.ARMOR_FLAT) == 12, "armor points did not round trip");
        helper.assertTrue(restored.points(StatLine.XP_GAIN_PCT) == 7, "xp_gain points did not round trip");
        helper.assertTrue(restored.points(StatLine.CRIT_CHANCE_FLAT) == 5, "crit points did not round trip");
        helper.assertTrue(source.respecCount() == 1, "setup: respecCount should be 1");
        helper.assertTrue(restored.respecCount() == source.respecCount(), "respecCount did not round trip");
        helper.assertTrue(restored.earnedPoints() == source.earnedPoints(), "earned points diverged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "persistence")
    public static void malformedPointTableDegradesSafely(GameTestHelper helper) {
        CompoundTag broken = new CompoundTag();
        broken.putLong("total_xp", 5000L);
        broken.putIntArray("points", new int[] { 1, 2, 3 });   // wrong length on purpose
        PlayerRpgData data = new PlayerRpgData();
        data.deserializeNBT(null, broken);

        helper.assertTrue(data.totalXp() == 5000L, "a short point table must not discard the xp total");
        for (StatLine line : StatLine.VALUES) {
            helper.assertTrue(data.points(line) == 0, "short table left a point in " + line.id());
        }
        helper.assertTrue(data.spentPoints() == 0L, "short table produced phantom spent points");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "persistence")
    public static void cloneCarriesLevelAndPoints(GameTestHelper helper) {
        ServerPlayer original = GameTestSupport.join(helper);
        RpgService.setLevel(original, 40);
        RpgService.setPoints(original, StatLine.ARMOR_FLAT, 9);
        RpgService.setPoints(original, StatLine.DROP_RATE_PCT, 4);

        long expectedXp = GameTestSupport.data(original).totalXp();
        int expectedArmor = GameTestSupport.data(original).points(StatLine.ARMOR_FLAT);

        ServerPlayer reborn = helper.makeMockServerPlayerInLevel();
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(reborn, original, true));

        PlayerRpgData data = GameTestSupport.data(reborn);
        helper.assertTrue(data.totalXp() == expectedXp,
                "clone lost experience: " + data.totalXp() + " != " + expectedXp);
        helper.assertTrue(data.points(StatLine.ARMOR_FLAT) == expectedArmor,
                "clone lost points: " + data.points(StatLine.ARMOR_FLAT) + " != " + expectedArmor);
        helper.assertTrue(data.level() == GameTestSupport.data(original).level(), "clone lost the level");
        helper.succeed();
    }
}