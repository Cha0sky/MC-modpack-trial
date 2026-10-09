package com.evolvingblade.network;

import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.CombatMath;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.Abilities;
import com.evolvingblade.growth.GiftState;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.growth.GrowthThresholds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 网络包定义与注册。
 *
 * <p>安全边界（SPEC）：客户端只能发送<b>一个枚举意图</b>；所有计数、阶段、伤害、赠剑标记、
 * 配置数值一律由服务器推导并下发。服务器在收到意图时校验"是否已锁定"与"是否重复"，
 * 并在<b>主线程</b>原子地完成"选择 + 发剑"。
 */
public final class EBPayloads {

    public static final ResourceLocation CHOOSE_MODE_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "choose_mode");
    public static final ResourceLocation STATE_ID =
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "state");

    /** C2S：唯一的合法意图——选择 quick/standard/long。 */
    public record ChooseMode(GrowthMode mode) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChooseMode> TYPE = new CustomPacketPayload.Type<>(CHOOSE_MODE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseMode> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.mode().id(), 16),
                buffer -> new ChooseMode(GrowthMode.require(buffer.readUtf(16))));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C：服务器权威状态 + 由服务器配置推导出的展示数值（阶段、门槛、数值、冷却）。
     * 客户端从不自行计算成长或伤害。
     */
    public record StateSnapshot(
            String modeId,
            int stage,
            String giftState,
            long growthPoints,
            long hostileKills,
            long bossKills,
            long waveRemaining,
            long burstRemaining,
            double stageAttackDamage,
            double stageAttackSpeed,
            long nextPoints,
            long nextBosses,
            List<Long> thresholdsPoints,
            List<Long> thresholdsBosses,
            List<Long> modeFinalPoints,
            List<Long> modeFinalBosses,
            int waveUnlockStage,
            int burstUnlockStage,
            int lifestealUnlockStage,
            int bossBonusUnlockStage,
            double killHeal,
            double waveRange,
            double waveDamageMultiplier,
            double burstRadius,
            double burstDamageMultiplier,
            boolean giftDelivered,
            /** 3 个模式 × 8 阶段的成长点门槛，索引 = 模式序数 × 8 + 阶段。 */
            List<Long> modePoints,
            /** 3 个模式 × 8 阶段的 Boss 门槛，索引同上。 */
            List<Long> modeBosses,
            /** 能力的真实数值（伤害/射程/冷却/比例/上限），索引见 {@link #A_WAVE_DAMAGE} 等常量。 */
            List<Double> ability,
            /**
             * <b>阶段基础攻击</b>：服务器配置里第 {@code stage} 阶段的总攻击（不含任何第三方加伤）。
             * 它只是配置口径，<b>不是</b>玩家当前的真实伤害。
             */
            double stageBaseAttack,
            /**
             * <b>当前持剑有效攻击</b>：服务器读取的持有者 {@code ATTACK_DAMAGE} 属性最终值，
             * 已包含标准属性 base、物品基线、第三方 flat 与百分比加伤，并被原版钳到 2048。
             * 仅在 {@link #holdingStarEater} 为 true 时有意义；否则服务器下发的是 NaN，
             * UI 必须显示"未持剑"而不是借用别的武器的加成。
             */
            double effectiveHeldAttack,
            /** 当前是否真的主手持着本剑（决定 UI 是否显示有效攻击）。 */
            boolean holdingStarEater,
            /** 服务器配置：技能伤害是否继承持有者有效攻击（默认 true）。 */
            boolean abilityDamageFromAttack) implements CustomPacketPayload {

        /** 每个模式 8 个阶段。 */
        public static final int STAGES = 8;
        /** {@link #ability} 的长度与索引定义。 */
        public static final int ABILITY_COUNT = 18;
        public static final int A_WAVE_DAMAGE = 0;
        public static final int A_WAVE_RANGE = 1;
        public static final int A_WAVE_COOLDOWN = 2;
        public static final int A_WAVE_FINAL_COOLDOWN = 3;
        public static final int A_WAVE_MAX_TARGETS = 4;
        public static final int A_LIFESTEAL_FRACTION = 5;
        public static final int A_LIFESTEAL_CAP = 6;
        public static final int A_LIFESTEAL_HIGH_FRACTION = 7;
        public static final int A_LIFESTEAL_HIGH_CAP = 8;
        public static final int A_BURST_DAMAGE = 9;
        public static final int A_BURST_COOLDOWN = 10;
        public static final int A_BURST_FINAL_COOLDOWN = 11;
        public static final int A_BURST_FINAL_RADIUS = 12;
        public static final int A_BURST_MAX_TARGETS = 13;
        public static final int A_BOSS_FRACTION = 14;
        public static final int A_BOSS_CAP = 15;
        public static final int A_BOSS_FINAL_FRACTION = 16;
        public static final int A_BOSS_FINAL_CAP = 17;

        /** 某个能力数值（越界返回 0，客户端永远不会因此崩溃）。 */
        public double abilityValue(int index) {
            return index >= 0 && index < this.ability.size() ? this.ability.get(index) : 0.0D;
        }

        /** 指定模式、指定阶段的成长点门槛（服务器配置推导，客户端不计算）。 */
        public long stagePoints(GrowthMode mode, int stage) {
            return index(mode, stage, this.modePoints);
        }

        /** 指定模式、指定阶段的 Boss 门槛。 */
        public long stageBosses(GrowthMode mode, int stage) {
            return index(mode, stage, this.modeBosses);
        }

        private static long index(GrowthMode mode, int stage, List<Long> values) {
            int at = mode.ordinal() * STAGES + stage;
            return at >= 0 && at < values.size() ? values.get(at) : 0L;
        }

        public static final CustomPacketPayload.Type<StateSnapshot> TYPE = new CustomPacketPayload.Type<>(STATE_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, StateSnapshot> CODEC = StreamCodec.of(
                EBPayloads::writeSnapshot, EBPayloads::readSnapshot);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public boolean hasMode() {
            return !this.modeId.isEmpty();
        }

        /** 当前模式的门槛（客户端只用于显示，不参与任何结算）。 */
        public GrowthThresholds currentThresholds() {
            GrowthMode mode = GrowthMode.byId(this.modeId).orElse(GrowthMode.STANDARD);
            return new GrowthThresholds(this.thresholdsPoints, this.thresholdsBosses);
        }
    }

    private EBPayloads() {}

    private static void writeLongList(RegistryFriendlyByteBuf buffer, List<Long> values) {
        buffer.writeVarInt(values.size());
        for (long value : values) {
            buffer.writeLong(value);
        }
    }

    private static List<Long> readLongList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Long> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readLong());
        }
        return List.copyOf(values);
    }

    private static void writeSnapshot(RegistryFriendlyByteBuf buffer, StateSnapshot snapshot) {
        buffer.writeUtf(snapshot.modeId(), 16);
        buffer.writeVarInt(snapshot.stage());
        buffer.writeUtf(snapshot.giftState(), 24);
        buffer.writeLong(snapshot.growthPoints());
        buffer.writeLong(snapshot.hostileKills());
        buffer.writeLong(snapshot.bossKills());
        buffer.writeLong(snapshot.waveRemaining());
        buffer.writeLong(snapshot.burstRemaining());
        buffer.writeDouble(snapshot.stageBaseAttack());
        buffer.writeDouble(snapshot.stageAttackSpeed());
        buffer.writeLong(snapshot.nextPoints());
        buffer.writeLong(snapshot.nextBosses());
        writeLongList(buffer, snapshot.thresholdsPoints());
        writeLongList(buffer, snapshot.thresholdsBosses());
        writeLongList(buffer, snapshot.modeFinalPoints());
        writeLongList(buffer, snapshot.modeFinalBosses());
        buffer.writeVarInt(snapshot.waveUnlockStage());
        buffer.writeVarInt(snapshot.burstUnlockStage());
        buffer.writeVarInt(snapshot.lifestealUnlockStage());
        buffer.writeVarInt(snapshot.bossBonusUnlockStage());
        buffer.writeDouble(snapshot.killHeal());
        buffer.writeDouble(snapshot.waveRange());
        buffer.writeDouble(snapshot.waveDamageMultiplier());
        buffer.writeDouble(snapshot.burstRadius());
        buffer.writeDouble(snapshot.burstDamageMultiplier());
        buffer.writeBoolean(snapshot.giftDelivered());
        writeLongList(buffer, snapshot.modePoints());
        writeLongList(buffer, snapshot.modeBosses());
        writeDoubleList(buffer, snapshot.ability());
        buffer.writeDouble(snapshot.stageBaseAttack());
        buffer.writeDouble(snapshot.effectiveHeldAttack());
        buffer.writeBoolean(snapshot.holdingStarEater());
        buffer.writeBoolean(snapshot.abilityDamageFromAttack());
    }

    private static void writeDoubleList(RegistryFriendlyByteBuf buffer, List<Double> values) {
        buffer.writeVarInt(values.size());
        for (double value : values) {
            buffer.writeDouble(value);
        }
    }

    private static List<Double> readDoubleList(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Double> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buffer.readDouble());
        }
        return List.copyOf(values);
    }

    private static StateSnapshot readSnapshot(RegistryFriendlyByteBuf buffer) {
        String modeId = buffer.readUtf(16);
        int stage = buffer.readVarInt();
        String giftState = buffer.readUtf(24);
        long points = buffer.readLong();
        long hostile = buffer.readLong();
        long boss = buffer.readLong();
        long waveRemaining = buffer.readLong();
        long burstRemaining = buffer.readLong();
        double attack = buffer.readDouble();
        double speed = buffer.readDouble();
        long nextPoints = buffer.readLong();
        long nextBosses = buffer.readLong();
        List<Long> pointsList = readLongList(buffer);
        List<Long> bossesList = readLongList(buffer);
        List<Long> finalPoints = readLongList(buffer);
        List<Long> finalBosses = readLongList(buffer);
        int waveUnlock = buffer.readVarInt();
        int burstUnlock = buffer.readVarInt();
        int lifestealUnlock = buffer.readVarInt();
        int bossBonusUnlock = buffer.readVarInt();
        double killHeal = buffer.readDouble();
        double waveRange = buffer.readDouble();
        double waveDamage = buffer.readDouble();
        double burstRadius = buffer.readDouble();
        double burstDamage = buffer.readDouble();
        boolean giftDelivered = buffer.readBoolean();
        List<Long> modePoints = readLongList(buffer);
        List<Long> modeBosses = readLongList(buffer);
        List<Double> ability = readDoubleList(buffer);
        double stageBase = buffer.readDouble();
        double effectiveHeld = buffer.readDouble();
        boolean holdingSword = buffer.readBoolean();
        boolean abilityFromAttack = buffer.readBoolean();
        return new StateSnapshot(modeId, stage, giftState, points, hostile, boss, waveRemaining, burstRemaining,
                attack, speed, nextPoints, nextBosses, pointsList, bossesList, finalPoints, finalBosses,
                waveUnlock, burstUnlock, lifestealUnlock, bossBonusUnlock, killHeal, waveRange, waveDamage,
                burstRadius, burstDamage, giftDelivered, modePoints, modeBosses, ability,
                stageBase, effectiveHeld, holdingSword, abilityFromAttack);
    }

    /** 用服务器权威数据构建快照。 */
    public static StateSnapshot snapshotFor(ServerPlayer player, boolean giftDelivered) {
        GrowthSettings settings = EBConfig.settings();
        GrowthData data = GrowthService.data(player);
        int stage = data.stage(settings);
        GrowthMode mode = data.mode() == null ? GrowthMode.STANDARD : data.mode();
        GrowthThresholds thresholds = settings.thresholds(mode);
        // 统一时钟：与冷却的创建/比较/清理完全一致（主世界 gameTime）。
        // 之前这里用 player.level().getGameTime()，换维度后会因为各维度自己的 gameTime
        // 而让界面上的冷却剩余时间跳变——这是必须避免的"多个时钟"缺陷。
        long now = com.evolvingblade.combat.StarEaterAbilities.clock(player);

        List<Long> finalPoints = new ArrayList<>(3);
        List<Long> finalBosses = new ArrayList<>(3);
        List<Long> modePoints = new ArrayList<>(GrowthMode.values().length * StateSnapshot.STAGES);
        List<Long> modeBosses = new ArrayList<>(GrowthMode.values().length * StateSnapshot.STAGES);
        for (GrowthMode candidate : GrowthMode.values()) {
            GrowthThresholds modeThresholds = settings.thresholds(candidate);
            finalPoints.add(modeThresholds.pointAt(GrowthMode.STAGE_COUNT - 1));
            finalBosses.add(modeThresholds.bossAt(GrowthMode.STAGE_COUNT - 1));
            // 三选一界面要显示**每个候选模式**的完整八阶段门槛（服务器配置推导，客户端不硬编码）。
            for (int s = 0; s < StateSnapshot.STAGES; s++) {
                modePoints.add(modeThresholds.pointAt(s));
                modeBosses.add(modeThresholds.bossAt(s));
            }
        }
        Abilities abilities = settings.abilities();
        boolean inheritAttack = abilities.abilityDamageFromAttack();
        // 先把持有者属性协调到当前阶段，再读"有效攻击"：这样下发给 UI 的数字与
        // 真实结算用的属性值在同一个 tick 内一致（不会出现"面板比实际晚一拍"）。
        com.evolvingblade.combat.StageAttributes.reconcile(player);
        boolean holdingSword = com.evolvingblade.item.StarEaterItem.isStarEater(player.getMainHandItem());
        double effectiveAttack = holdingSword ? GrowthService.effectiveAttackDamage(player) : Double.NaN;
        // 能力的真实数值（伤害/射程/冷却/比例/上限），界面只显示这些服务器值。
        // 技能伤害口径与真实结算完全一致：默认取 min(系数×阶段基础攻击, 系数×有效攻击)。
        List<Double> ability = new ArrayList<>(StateSnapshot.ABILITY_COUNT);
        ability.add(CombatMath.waveDamage(settings, stage, effectiveAttack, inheritAttack));
        ability.add(abilities.waveRange());
        ability.add((double) abilities.waveCooldownFor(stage));
        ability.add((double) abilities.waveFinalCooldownTicks());
        ability.add((double) CombatMath.waveMaxTargets(settings, stage));
        ability.add(abilities.lifestealFraction());
        ability.add(abilities.lifestealCap());
        ability.add(abilities.lifestealHighFraction());
        ability.add(abilities.lifestealHighCap());
        ability.add(CombatMath.burstDamage(settings, stage, effectiveAttack, inheritAttack));
        ability.add((double) abilities.burstCooldownFor(stage));
        ability.add((double) abilities.burstFinalCooldownTicks());
        ability.add(abilities.burstFinalRadius());
        ability.add((double) abilities.burstMaxTargets());
        ability.add(abilities.bossBonusFraction());
        ability.add(abilities.bossBonusCap());
        ability.add(abilities.bossBonusFinalFraction());
        ability.add(abilities.bossBonusFinalCap());
        return new StateSnapshot(
                data.mode() == null ? "" : data.mode().id(),
                stage,
                data.giftState().name(),
                data.growthPoints(),
                data.hostileKills(),
                data.bossKills(),
                com.evolvingblade.combat.AbilityState.remaining(now, data.waveReadyAt()),
                com.evolvingblade.combat.AbilityState.remaining(now, data.burstReadyAt()),
                settings.stageAttackDamage(stage),
                settings.stageAttackSpeed(stage),
                com.evolvingblade.growth.StageCalculator.pointsForNextStage(data.growthPoints(), data.bossKills(), thresholds),
                com.evolvingblade.growth.StageCalculator.bossesForNextStage(data.growthPoints(), data.bossKills(), thresholds),
                thresholds.points(),
                thresholds.bosses(),
                finalPoints,
                finalBosses,
                abilities.waveUnlockStage(),
                abilities.burstUnlockStage(),
                abilities.lifestealUnlockStage(),
                abilities.bossBonusUnlockStage(),
                abilities.killHeal(),
                abilities.waveRange(),
                abilities.waveDamageMultiplier(),
                abilities.burstRadiusFor(stage),
                abilities.burstDamageMultiplier(),
                giftDelivered,
                modePoints,
                modeBosses,
                ability,
                settings.stageAttackDamage(stage),
                holdingSword ? effectiveAttack : Double.NaN,
                holdingSword,
                inheritAttack);
    }

    /** 下发权威快照（登录/重生/换维度/选择/管理员修改/升级时调用）。 */
    public static void sendState(ServerPlayer player, boolean giftDelivered) {
        if (player == null || player.connection == null) {
            return;
        }
        // 只有真正协商过本频道的连接才发送：GameTest 的模拟玩家、以及任何尚未完成
        // NeoForge 握手/未安装本模组的连接都没有这个频道，盲发会抛
        // UnsupportedOperationException("Payload ... may not be sent to the client!")。
        if (!player.connection.hasChannel(StateSnapshot.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshotFor(player, giftDelivered));
    }

    /** 注册处理器（在 mod 事件总线上调用）。客户端处理器只在真正收到包时才会触达客户端类。 */
    public static void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        // 协议版本：1.1.0 给 StateSnapshot 增加了 4 个字段（stageBaseAttack / effectiveHeldAttack /
        // holdingStarEater / abilityDamageFromAttack），并新增了 evolvingblade:ability_effect 载荷。
        // 版本号必须一起提升，否则 1.0.0 客户端与服务端通过握手后会按旧布局解码而抛异常。
        // 存档兼容 ≠ 网络协议兼容：双方必须是同一版本。
        net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(ChooseMode.TYPE, ChooseMode.CODEC, EBPayloads::handleChooseOnServer);
        registrar.playToClient(StateSnapshot.TYPE, StateSnapshot.CODEC, EBPayloads::handleStateOnClient);
        // 有界施法视觉事件：与数值无关，客户端只在真正收到包时才会触达客户端类。
        registrar.playToClient(AbilityEffectPayload.TYPE, AbilityEffectPayload.CODEC,
                EBPayloads::handleAbilityEffectOnClient);
    }

    /**
     * S2C 处理器：把施法视觉事件交给客户端特效层（专用服不会走到这里）。
     *
     * <p>客户端类引用被严格包在 {@code FMLEnvironment.dist.isClient()} 分支里，
     * 因此专用服务器加载本类时不会解析到任何客户端类型。
     */
    static void handleAbilityEffectOnClient(AbilityEffectPayload payload, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            context.enqueueWork(() -> com.evolvingblade.client.EBAbilityClient.accept(payload));
        }
    }

    /** S2C 处理器：只更新客户端缓存并交给客户端 UI（专用服不会走到这里）。 */
    static void handleStateOnClient(StateSnapshot snapshot, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            // 放到客户端主线程再写入缓存：登录包可能早于世界/玩家创建。
            context.enqueueWork(() -> com.evolvingblade.client.EBClientState.accept(snapshot));
        }
    }

    /** C2S 处理器：校验 + 主线程原子执行。 */
    static void handleChooseOnServer(ChooseMode payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GrowthService.chooseMode(player, payload.mode());
            }
        });
    }
}
