package com.evolvingblade.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * {@code evolvingblade:boss_rate} 的持久化编解码（纯 JVM，可单测）。
 *
 * <p>为什么不用裸的 {@code Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), ...)}：
 * {@link UUID#fromString} 对畸形字符串会抛<b>未检查异常</b>，在存档损坏时会变成"加载玩家数据即崩服"。
 * 这里把每一步都变成 {@link DataResult}：
 * <ul>
 *   <li>UUID 解析失败 → 返回错误（由 NeoForge 作为加载失败处理并记录），绝不抛出未检查异常；</li>
 *   <li>负的截止时间 → 视为畸形数据直接丢弃（不写入任何状态，因此不会造成"提前放行"）；</li>
 *   <li>条目数超过 {@link #MAX_ENTRIES} → 只保留<b>截止时间最晚</b>的那些条目。
 *       这是保守方向：保留得越久 = 限速越严，绝不会因为裁剪而让某个目标提前恢复加成。</li>
 * </ul>
 */
public final class BossRateCodec {

    /** 与运行时相同的上限（严格）。 */
    public static final int MAX_ENTRIES = AbilityState.DEFAULT_MAX_RATE_ENTRIES;

    /** 存档/网络用的编解码器。 */
    public static final Codec<Map<UUID, Long>> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG).flatXmap(BossRateCodec::sanitize, BossRateCodec::encode);

    private BossRateCodec() {}

    private static DataResult<Map<String, Long>> encode(Map<UUID, Long> value) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (value != null) {
            for (Map.Entry<UUID, Long> entry : value.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.put(entry.getKey().toString(), entry.getValue() == null ? 0L : entry.getValue());
            }
        }
        return DataResult.success(out);
    }

    /** 校验 + 归一化解码结果。 */
    public static DataResult<Map<UUID, Long>> sanitize(Map<String, Long> raw) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        if (raw == null) {
            return DataResult.success(out);
        }
        for (Map.Entry<String, Long> entry : raw.entrySet()) {
            UUID id;
            try {
                id = UUID.fromString(String.valueOf(entry.getKey()));
            } catch (IllegalArgumentException ex) {
                return DataResult.error(() -> "evolvingblade:boss_rate 含有非法 UUID 键: " + entry.getKey());
            }
            Long deadline = entry.getValue();
            if (deadline == null || deadline < 0L) {
                // 畸形/负数截止时间：丢弃该条目，不写入任何可被解释为"早已就绪"的状态。
                continue;
            }
            out.put(id, deadline);
        }
        if (out.size() > MAX_ENTRIES) {
            List<Map.Entry<UUID, Long>> entries = new ArrayList<>(out.entrySet());
            entries.sort(Comparator.comparingLong((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
            Map<UUID, Long> trimmed = new LinkedHashMap<>();
            for (int i = 0; i < MAX_ENTRIES; i++) {
                trimmed.put(entries.get(i).getKey(), entries.get(i).getValue());
            }
            out = trimmed;
        }
        return DataResult.success(out);
    }
}
