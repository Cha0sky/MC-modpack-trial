package com.evolvingblade.growth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * 成长模式。三种模式只在线性门槛上不同，攻击/攻速等数值完全一致。
 *
 * <p>这个类刻意保持"零 Minecraft 依赖"（只用 DataFixerUpper 的 {@link Codec}），
 * 因此可以用纯 JVM 单元测试覆盖全部 24 个阶段边界。
 */
public enum GrowthMode {
    /** 快速：门槛最低。 */
    QUICK("quick",
            longs(0, 10, 30, 70, 140, 240, 400, 600),
            longs(0, 0, 0, 1, 1, 2, 3, 5)),
    /** 标准：默认推荐。 */
    STANDARD("standard",
            longs(0, 50, 150, 350, 700, 1200, 2000, 3000),
            longs(0, 0, 0, 1, 3, 6, 12, 20)),
    /** 长期：门槛最高。 */
    LONG("long",
            longs(0, 300, 1000, 2400, 4800, 8000, 13000, 20000),
            longs(0, 0, 0, 5, 15, 30, 60, 100));

    /** 阶段总数（0..7）。 */
    public static final int STAGE_COUNT = 8;

    /** 稳定的序列化 / 命令 ID。 */
    private final String id;
    private final List<Long> defaultPoints;
    private final List<Long> defaultBosses;

    GrowthMode(String id, List<Long> defaultPoints, List<Long> defaultBosses) {
        this.id = id;
        this.defaultPoints = defaultPoints;
        this.defaultBosses = defaultBosses;
    }

    public String id() {
        return this.id;
    }

    /** 默认的累计成长点门槛，长度恒为 {@value #STAGE_COUNT}。 */
    public List<Long> defaultPoints() {
        return this.defaultPoints;
    }

    /** 默认的累计 Boss 击杀门槛，长度恒为 {@value #STAGE_COUNT}。 */
    public List<Long> defaultBosses() {
        return this.defaultBosses;
    }

    /** 默认门槛快照。 */
    public GrowthThresholds defaultThresholds() {
        return new GrowthThresholds(this.defaultPoints, this.defaultBosses);
    }

    /** 按 ID 解析，忽略大小写与首尾空白；未知返回空。 */
    public static Optional<GrowthMode> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (GrowthMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }

    /**
     * 严格解析：未知 ID 抛出 {@link IllegalArgumentException}。
     * 供命令与配置读取使用，避免静默吞掉拼写错误。
     */
    public static GrowthMode require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown growth mode: " + id));
    }

    /** 字符串编解码器；非法 ID 产生 codec 错误而不是异常。 */
    public static final Codec<GrowthMode> CODEC = Codec.STRING.flatXmap(
            id -> byId(id)
                    .<DataResult<? extends GrowthMode>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown growth mode: " + id)),
            mode -> DataResult.success(mode.id()));

    /** 命令用的候选列表（quick/standard/long）。 */
    public static List<String> ids() {
        List<String> result = new ArrayList<>(values().length);
        for (GrowthMode mode : values()) {
            result.add(mode.id);
        }
        return List.copyOf(result);
    }

    private static List<Long> longs(long... values) {
        List<Long> boxed = new ArrayList<>(values.length);
        for (long value : values) {
            boxed.add(value);
        }
        return List.copyOf(boxed);
    }
}
