package com.evolvingblade.growth;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 每个玩家、每个存档独立持久化的成长数据（NeoForge Data Attachment 的载荷）。
 *
 * <p>不可变记录：任何修改都通过 {@code withXxx} 产生新实例，避免共享可变状态。
 * 字段：
 * <ul>
 *   <li>{@code hostile_kills} / {@code boss_kills}：原始计数（未选模式也照常累积）；</li>
 *   <li>{@code growth_points}：按击杀当时的配置权重累计的成长点；</li>
 *   <li>{@code mode}：可空的模式选择；未选择时阶段恒为 0；</li>
 *   <li>{@code gift_state}：赠剑状态；</li>
 *   <li>{@code wave_ready_at} / {@code burst_ready_at}：技能冷却的<b>绝对到期 tick</b>
 *       （服务器主世界 tick）。冷却因此与剑的副本、重连、死亡无关，换剑也无法绕过。</li>
 * </ul>
 *
 * <p>校验：负数字段会被 codec 拒绝；运行时加法一律走饱和算术。
 */
public record GrowthData(long hostileKills, long bossKills, long growthPoints, GrowthMode mode, GiftState giftState,
        long waveReadyAt, long burstReadyAt) {

    /** 全零初始值：未选模式、未发剑、技能就绪。 */
    public static final GrowthData EMPTY = new GrowthData(0L, 0L, 0L, null, GiftState.NOT_GRANTED, 0L, 0L);

    private static final Codec<Long> NON_NEGATIVE_LONG = Codec.LONG.validate(value -> value < 0L
            ? DataResult.error(() -> "Counter must be >= 0 but was " + value)
            : DataResult.success(value));

    public static final Codec<GrowthData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NON_NEGATIVE_LONG.optionalFieldOf("hostile_kills", 0L).forGetter(GrowthData::hostileKills),
            NON_NEGATIVE_LONG.optionalFieldOf("boss_kills", 0L).forGetter(GrowthData::bossKills),
            NON_NEGATIVE_LONG.optionalFieldOf("growth_points", 0L).forGetter(GrowthData::growthPoints),
            GrowthMode.CODEC.optionalFieldOf("mode").forGetter(data -> Optional.ofNullable(data.mode())),
            GiftState.CODEC.optionalFieldOf("gift_state", GiftState.NOT_GRANTED).forGetter(GrowthData::giftState),
            NON_NEGATIVE_LONG.optionalFieldOf("wave_ready_at", 0L).forGetter(GrowthData::waveReadyAt),
            NON_NEGATIVE_LONG.optionalFieldOf("burst_ready_at", 0L).forGetter(GrowthData::burstReadyAt)
    ).apply(instance, (hostileKills, bossKills, growthPoints, mode, giftState, waveReadyAt, burstReadyAt) ->
            new GrowthData(hostileKills, bossKills, growthPoints, mode.orElse(null), giftState, waveReadyAt, burstReadyAt)));

    public GrowthData {
        hostileKills = Math.max(0L, hostileKills);
        bossKills = Math.max(0L, bossKills);
        growthPoints = Math.max(0L, growthPoints);
        waveReadyAt = Math.max(0L, waveReadyAt);
        burstReadyAt = Math.max(0L, burstReadyAt);
        if (giftState == null) {
            giftState = GiftState.NOT_GRANTED;
        }
    }

    /** 兼容旧构造：冷却默认就绪。 */
    public GrowthData(long hostileKills, long bossKills, long growthPoints, GrowthMode mode, GiftState giftState) {
        this(hostileKills, bossKills, growthPoints, mode, giftState, 0L, 0L);
    }

    public boolean hasMode() {
        return this.mode != null;
    }

    public Optional<GrowthMode> modeOptional() {
        return Optional.ofNullable(this.mode);
    }

    /** 当前阶段。未选择模式时恒为 0（但仍保留原始计数）。 */
    public int stage(GrowthSettings settings) {
        if (this.mode == null || settings == null) {
            return 0;
        }
        return StageCalculator.stageFor(this.growthPoints, this.bossKills, settings.thresholds(this.mode));
    }

    public GrowthData withHostileKills(long value) {
        return new GrowthData(value, this.bossKills, this.growthPoints, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }

    public GrowthData withBossKills(long value) {
        return new GrowthData(this.hostileKills, value, this.growthPoints, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }

    public GrowthData withGrowthPoints(long value) {
        return new GrowthData(this.hostileKills, this.bossKills, value, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }

    /**
     * 选择/设定模式。
     *
     * @throws NullPointerException {@code value} 为 null。"未选择"只存在于初始状态，
     *                              不允许通过公开的修改入口把已选模式改回 null
     */
    public GrowthData withMode(GrowthMode value) {
        return new GrowthData(this.hostileKills, this.bossKills, this.growthPoints,
                Objects.requireNonNull(value, "mode"), this.giftState, this.waveReadyAt, this.burstReadyAt);
    }

    public GrowthData withGiftState(GiftState value) {
        return new GrowthData(this.hostileKills, this.bossKills, this.growthPoints, this.mode, value,
                this.waveReadyAt, this.burstReadyAt);
    }

    /** 写入技能冷却到期时刻（服务器主世界 tick）。 */
    public GrowthData withCooldowns(long newWaveReadyAt, long newBurstReadyAt) {
        return new GrowthData(this.hostileKills, this.bossKills, this.growthPoints, this.mode, this.giftState,
                newWaveReadyAt, newBurstReadyAt);
    }

    /** 记一次普通敌对击杀：计数 +1，成长点 +{@code hostilePointWeight}（饱和）。 */
    public GrowthData creditHostileKill(long hostilePointWeight) {
        long kills = StageCalculator.saturatedAdd(this.hostileKills, 1L);
        long points = StageCalculator.saturatedAdd(this.growthPoints, Math.max(0L, hostilePointWeight));
        return new GrowthData(kills, this.bossKills, points, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }

    /** 记一次 Boss 击杀：Boss 计数 +1，成长点 +{@code bossPointWeight}（饱和）；不会同时算普通击杀。 */
    public GrowthData creditBossKill(long bossPointWeight) {
        long kills = StageCalculator.saturatedAdd(this.bossKills, 1L);
        long points = StageCalculator.saturatedAdd(this.growthPoints, Math.max(0L, bossPointWeight));
        return new GrowthData(this.hostileKills, kills, points, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }

    /** 管理员 {@code set}：重设原始计数并按当前配置权重重算成长点；保留模式、赠剑与冷却。 */
    public GrowthData withCountersRecomputed(long newHostileKills, long newBossKills, long hostilePointWeight,
            long bossPointWeight) {
        long points = StageCalculator.computePoints(newHostileKills, hostilePointWeight, newBossKills, bossPointWeight);
        return new GrowthData(newHostileKills, newBossKills, points, this.mode, this.giftState,
                this.waveReadyAt, this.burstReadyAt);
    }
}
