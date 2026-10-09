package com.evolvingblade.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模式枚举：ID 解析、编解码器容错、赠剑状态编解码。
 */
class GrowthModeTest {

    @Test
    @DisplayName("ID 解析：大小写与空白容错，未知为空")
    void byId() {
        assertEquals(GrowthMode.QUICK, GrowthMode.byId("quick").orElseThrow());
        assertEquals(GrowthMode.STANDARD, GrowthMode.byId("  STANDARD ").orElseThrow());
        assertEquals(GrowthMode.LONG, GrowthMode.byId("Long").orElseThrow());
        assertTrue(GrowthMode.byId("ultra").isEmpty());
        assertTrue(GrowthMode.byId(null).isEmpty());
        assertTrue(GrowthMode.byId("").isEmpty());
    }

    @Test
    @DisplayName("require：未知 ID 直接抛异常，供命令层回显")
    void requireThrows() {
        assertEquals(GrowthMode.QUICK, GrowthMode.require("quick"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> GrowthMode.require("nope"));
    }

    @Test
    @DisplayName("模式 Codec：合法 ID 往返，非法 ID 产生错误而不是异常")
    void modeCodec() {
        for (GrowthMode mode : GrowthMode.values()) {
            var encoded = GrowthMode.CODEC.encodeStart(JsonOps.INSTANCE, mode).getOrThrow();
            assertEquals(mode, GrowthMode.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
        DataResult<GrowthMode> bad = GrowthMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("nope"));
        assertTrue(bad.error().isPresent());
        assertTrue(bad.error().get().message().contains("Unknown growth mode"));
    }

    @Test
    @DisplayName("赠剑状态 Codec：三种状态往返，非法值被拒绝")
    void giftStateCodec() {
        for (GiftState state : GiftState.values()) {
            var encoded = GiftState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
            assertEquals(state, GiftState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
        assertTrue(GiftState.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("MAYBE")).error().isPresent());
        assertEquals(GiftState.PENDING, GiftState.parse("pending").orElseThrow());
        assertFalse(GiftState.parse("nope").isPresent());
    }
}
