package com.evolvingblade.network;

import com.evolvingblade.EvolvingBlade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * S2C：<b>有界的施法视觉事件</b>。
 *
 * <p>为什么要有这个包，而不是直接用 {@code ServerLevel.sendParticles}：
 * <ul>
 *   <li>原版 {@code sendParticles} 是"每个粒子一次广播"，本模组的三维球壳/刀光需要
 *       几十个<b>有结构</b>的采样点，逐点广播会产生几十个包；</li>
 *   <li>这里改成"<b>一个施法事件一个包</b>"：服务器只发送事件参数（原点、方向、半径、阶段档），
 *       观察者客户端在自己的 {@code ClientTickEvent} 里把粒子脚本铺开。
 *       单次施法对每个观察者最多 1 个包，绝不产生数千个独立粒子包。</li>
 * </ul>
 *
 * <p><b>范围有界</b>：只发给"同一维度且距离 ≤ {@link #MAX_VIEW_DISTANCE}"的玩家，
 * 距离以外的玩家完全收不到包（不是收到后再丢弃）。
 *
 * <p>该包只携带视觉参数，<b>不含任何权威数值</b>；客户端不会用它做任何判定。
 */
public record AbilityEffectPayload(
        int kind,
        double x,
        double y,
        double z,
        float dirX,
        float dirY,
        float dirZ,
        double magnitude,
        int tier) implements CustomPacketPayload {

    /** 剑气（细长弯月刀光沿视线前进）。 */
    public static final int KIND_WAVE = 0;
    /** 三维球壳爆发。 */
    public static final int KIND_BURST = 1;
    /** 命中交叉星芒。 */
    public static final int KIND_HIT = 2;
    /** 弑王紫金斩痕。 */
    public static final int KIND_SLAYER = 3;
    /** 吸血点流向施法者。 */
    public static final int KIND_LIFESTEAL = 4;

    /** 观察者可见距离上限（方块）；超出者收不到包，事件本身也因此有界。 */
    public static final double MAX_VIEW_DISTANCE = 64.0D;
    /** 单个事件允许的最大幅度（半径/长度），由服务器按真实判定值夹紧后下发。 */
    public static final double MAX_MAGNITUDE = 64.0D;
    /** 阶段档上限（用于视觉层次，越界一律夹到该值）。 */
    public static final int MAX_TIER = 7;

    public static final CustomPacketPayload.Type<AbilityEffectPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "ability_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityEffectPayload> CODEC = StreamCodec.of(
            AbilityEffectPayload::write, AbilityEffectPayload::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, AbilityEffectPayload payload) {
        buffer.writeVarInt(payload.kind);
        buffer.writeDouble(payload.x);
        buffer.writeDouble(payload.y);
        buffer.writeDouble(payload.z);
        buffer.writeFloat(payload.dirX);
        buffer.writeFloat(payload.dirY);
        buffer.writeFloat(payload.dirZ);
        buffer.writeDouble(payload.magnitude);
        buffer.writeVarInt(payload.tier);
    }

    private static AbilityEffectPayload read(RegistryFriendlyByteBuf buffer) {
        return new AbilityEffectPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readDouble(), buffer.readVarInt());
    }

    /**
     * 构造一个已在服务器侧夹紧的事件。
     *
     * <p><b>方向向量的语义随 kind 而定</b>，这里刻意保持"归一化"与"保留长度"两种模式：
     * <ul>
     *   <li>{@code normalizeDirection = true}：刀光/爆发/命中/斩痕只需要方向与幅度，
     *       幅度单独由 {@code magnitude} 携带（剑气就是被墙截断后的 reach）；</li>
     *   <li>{@code normalizeDirection = false}：吸血需要<b>真实距离</b>（目标 → 施法者），
     *       归一化会把距离抹成 1，导致轨迹只飞 1 格。</li>
     * </ul>
     */
    public static AbilityEffectPayload of(int kind, Vec3 origin, Vec3 direction, double magnitude, int tier,
            boolean normalizeDirection) {
        Vec3 safeOrigin = origin == null ? Vec3.ZERO : origin;
        Vec3 safeDirection;
        if (direction == null || direction.lengthSqr() < 1.0E-8D) {
            safeDirection = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            safeDirection = normalizeDirection ? direction.normalize() : direction;
        }
        double clampedMagnitude = Double.isFinite(magnitude)
                ? Math.max(0.0D, Math.min(MAX_MAGNITUDE, magnitude))
                : 0.0D;
        return new AbilityEffectPayload(Math.max(0, Math.min(KIND_LIFESTEAL, kind)),
                safeOrigin.x, safeOrigin.y, safeOrigin.z,
                (float) safeDirection.x, (float) safeDirection.y, (float) safeDirection.z,
                clampedMagnitude, Math.max(0, Math.min(MAX_TIER, tier)));
    }

    /** 默认按"方向归一化"构造（刀光 / 爆发 / 命中 / 斩痕）。 */
    public static AbilityEffectPayload of(int kind, Vec3 origin, Vec3 direction, double magnitude, int tier) {
        return of(kind, origin, direction, magnitude, tier, true);
    }

    /**
     * 把事件广播给附近的观察玩家（含施法者自己）。
     *
     * <p>“附近”＝同维度且 {@code distanceSqr <= MAX_VIEW_DISTANCE²}；
     * 距离以外的玩家完全不会被发送。
     */
    public static int broadcast(ServerLevel level, Vec3 origin, AbilityEffectPayload payload) {
        if (level == null || payload == null || origin == null) {
            return 0;
        }
        double limitSqr = MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE;
        int sent = 0;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.level() != level) {
                continue;
            }
            // 只发"已经协商并打开了本 payload 频道"的连接：GameTest 的模拟玩家、离线/未完成握手的
            // 连接没有这个频道，直接发送会让 NeoForge 抛 UnsupportedOperationException，
            // 而那个异常会打断施法流程、连带影响近战回血与弑王加成。
            // 这里只跳过纯视觉网络包，伤害/回血/计数的代码路径完全不受影响。
            if (viewer.connection == null || !viewer.connection.hasChannel(TYPE)) {
                continue;
            }
            if (viewer.position().distanceToSqr(origin) > limitSqr) {
                continue;
            }
            PacketDistributor.sendToPlayer(viewer, payload);
            sent++;
        }
        return sent;
    }
}
