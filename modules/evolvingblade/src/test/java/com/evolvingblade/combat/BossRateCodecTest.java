package com.evolvingblade.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

/**
 * {@code evolvingblade:boss_rate} 持久化编解码回归：损坏数据不崩服、严格有界、保守裁剪。
 */
class BossRateCodecTest {

    @Test
    void roundTripsValidTable() {
        Map<UUID, Long> table = new LinkedHashMap<>();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        table.put(a, 1_000L);
        table.put(b, 2_000L);
        DataResult<com.google.gson.JsonElement> encoded = BossRateCodec.CODEC.encodeStart(JsonOps.INSTANCE, table);
        assertTrue(encoded.result().isPresent(), "encoding a valid table must succeed");
        Map<UUID, Long> decoded = BossRateCodec.CODEC.parse(JsonOps.INSTANCE, encoded.result().get()).result()
                .orElseThrow();
        assertEquals(table, decoded, "round trip must preserve targets and deadlines");
    }

    @Test
    void malformedUuidBecomesDataErrorInsteadOfCrash() {
        DataResult<Map<UUID, Long>> result = BossRateCodec.sanitize(Map.of("not-a-uuid", 10L));
        assertTrue(result.error().isPresent(), "畸形 UUID 必须变成 DataResult 错误而不是抛出未检查异常");
    }

    @Test
    void negativeDeadlineIsDropped() {
        UUID good = UUID.randomUUID();
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put(good.toString(), 50L);
        raw.put(UUID.randomUUID().toString(), -1L);
        Map<UUID, Long> sanitized = BossRateCodec.sanitize(raw).result().orElseThrow();
        assertEquals(1, sanitized.size(), "负的截止时间是畸形数据，必须被丢弃");
        assertEquals(50L, sanitized.get(good));
    }

    @Test
    void oversizedTableIsTrimmedConservatively() {
        Map<String, Long> raw = new LinkedHashMap<>();
        int total = BossRateCodec.MAX_ENTRIES + 25;
        long latest = 0L;
        for (int i = 0; i < total; i++) {
            raw.put(UUID.randomUUID().toString(), (long) i);
            latest = i;
        }
        Map<UUID, Long> sanitized = BossRateCodec.sanitize(raw).result().orElseThrow();
        assertEquals(BossRateCodec.MAX_ENTRIES, sanitized.size(), "加载后必须严格有界");
        assertTrue(sanitized.containsValue(latest),
                "裁剪必须保留截止时间最晚（限速最严）的条目，绝不能让目标提前恢复加成");
        long min = sanitized.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        assertEquals(latest - BossRateCodec.MAX_ENTRIES + 1, min, "保留的是最晚的一批截止时间");
    }
}
