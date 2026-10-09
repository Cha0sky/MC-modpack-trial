package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 成长数据的序列化测试：JSON round-trip、缺省字段、畸形输入拒绝、
 * 以及"未选择模式阶段恒为 0"与"溢出饱和"的语义。
 *
 * <p>全部使用纯 JVM 的 {@link JsonOps}，不需要启动 Minecraft。
 */
class GrowthDataTest {

    @Test
    @DisplayName("空数据的 round-trip：缺省字段全部回落为 0 / 未选择")
    void emptyRoundTrip() {
        JsonElement encoded = GrowthData.CODEC.encodeStart(JsonOps.INSTANCE, GrowthData.EMPTY).getOrThrow();
        assertEquals(GrowthData.EMPTY, GrowthData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(GrowthData.EMPTY, GrowthData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
        assertFalse(GrowthData.EMPTY.hasMode());
        assertNull(GrowthData.EMPTY.mode());
        assertEquals(GiftState.NOT_GRANTED, GrowthData.EMPTY.giftState());
    }

    @Test
    @DisplayName("完整数据的 round-trip：计数、模式、赠剑状态一一保留")
    void fullRoundTrip() {
        GrowthData data = new GrowthData(1234L, 56L, 7890L, GrowthMode.LONG, GiftState.PENDING);
        JsonElement encoded = GrowthData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertTrue(encoded.isJsonObject());
        JsonObject object = encoded.getAsJsonObject();
        assertTrue(object.has("hostile_kills"));
        assertTrue(object.has("boss_kills"));
        assertTrue(object.has("growth_points"));
        assertTrue(object.has("mode"));
        assertTrue(object.has("gift_state"));
        assertEquals(data, GrowthData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    @DisplayName("畸形输入被拒绝：负数、未知模式、未知赠剑状态")
    void malformedInputRejected() {
        JsonObject negative = new JsonObject();
        negative.addProperty("hostile_kills", -1L);
        DataResult<GrowthData> negativeResult = GrowthData.CODEC.parse(JsonOps.INSTANCE, negative);
        assertTrue(negativeResult.error().isPresent(), "负计数必须被拒绝");
        assertTrue(negativeResult.error().get().message().contains(">= 0"));

        JsonObject badMode = new JsonObject();
        badMode.addProperty("mode", "ultra");
        assertTrue(GrowthData.CODEC.parse(JsonOps.INSTANCE, badMode).error().isPresent(), "未知模式必须被拒绝");

        JsonObject badGift = new JsonObject();
        badGift.addProperty("gift_state", "MAYBE");
        assertTrue(GrowthData.CODEC.parse(JsonOps.INSTANCE, badGift).error().isPresent(), "未知赠剑状态必须被拒绝");

        JsonObject wrongType = new JsonObject();
        wrongType.addProperty("hostile_kills", "many");
        assertTrue(GrowthData.CODEC.parse(JsonOps.INSTANCE, wrongType).error().isPresent(), "错误类型必须被拒绝");
    }

    @Test
    @DisplayName("未选择模式：即使点数与 Boss 数远超门槛，阶段仍为 0")
    void unselectedStaysAtStageZero() {
        GrowthSettings settings = GrowthSettings.defaults();
        GrowthData unselected = new GrowthData(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, null, GiftState.NOT_GRANTED);
        assertEquals(0, unselected.stage(settings));
        assertEquals(7, unselected.withMode(GrowthMode.QUICK).stage(settings));
    }

    @Test
    @DisplayName("记击杀：普通击杀 +1 计数 +1 点，Boss 击杀只加 Boss 计数，且都会饱和")
    void killCreditingSaturates() {
        GrowthData data = GrowthData.EMPTY;
        GrowthData afterHostile = data.creditHostileKill(1L);
        assertEquals(1L, afterHostile.hostileKills());
        assertEquals(0L, afterHostile.bossKills());
        assertEquals(1L, afterHostile.growthPoints());

        GrowthData afterBoss = data.creditBossKill(100L);
        assertEquals(0L, afterBoss.hostileKills(), "Boss 击杀不应同时算作普通击杀");
        assertEquals(1L, afterBoss.bossKills());
        assertEquals(100L, afterBoss.growthPoints());

        GrowthData saturated = new GrowthData(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, GrowthMode.QUICK, GiftState.GRANTED)
                .creditHostileKill(1L)
                .creditBossKill(100L);
        assertEquals(Long.MAX_VALUE, saturated.hostileKills());
        assertEquals(Long.MAX_VALUE, saturated.bossKills());
        assertEquals(Long.MAX_VALUE, saturated.growthPoints());
    }

    @Test
    @DisplayName("管理员重算：按权重重算成长点，但保留模式与赠剑标记")
    void recomputePreservesModeAndGift() {
        GrowthData data = new GrowthData(0L, 0L, 0L, null, GiftState.NOT_GRANTED)
                .withMode(GrowthMode.STANDARD)
                .withGiftState(GiftState.GRANTED);
        GrowthData recomputed = data.withCountersRecomputed(10L, 2L, 1L, 100L);
        assertEquals(210L, recomputed.growthPoints());
        assertEquals(GrowthMode.STANDARD, recomputed.mode());
        assertEquals(GiftState.GRANTED, recomputed.giftState());
    }

    @Test
    @DisplayName("构造器钳制：负数字段不会进入数据对象")
    void constructorClampsNegativeValues() {
        GrowthData data = new GrowthData(-5L, -6L, -7L, GrowthMode.QUICK, null);
        assertEquals(0L, data.hostileKills());
        assertEquals(0L, data.bossKills());
        assertEquals(0L, data.growthPoints());
        assertEquals(GiftState.NOT_GRANTED, data.giftState());
    }
}
