package com.evolvingblade.combat;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.network.AbilityEffectPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 服务器侧的<b>施法视觉广播</b>唯一入口。
 *
 * <p>服务器在这里只做三件事：<b>算参数、夹范围、发一个有界的包</b>。
 * 粒子本身由观察者客户端铺开（见 {@code client.EBAbilityClient}），因此：
 * <ul>
 *   <li>每次施法对每个观察者最多 1 个包，与粒子数量无关（不会数以千计）；</li>
 *   <li>只有同维度、距离 ≤ {@link AbilityEffectPayload#MAX_VIEW_DISTANCE} 的玩家收得到；</li>
 *   <li>所有幅度都由真实判定值夹紧（射程被墙截断多少就发多少），
 *       客户端因此不可能画出与判定不一致的刀光。</li>
 * </ul>
 *
 * <p>本类不改变任何游戏状态：不发爆炸、不破坏方块、不改实体字段。
 */
public final class AbilityVisuals {

    /** 终阶阶段序号（用于"仅增强层次、不暴增粒子"的档位）。 */
    private static final int FINAL_STAGE = GrowthMode.STAGE_COUNT - 1;

    private AbilityVisuals() {}

    /** 命中点（生命值真的下降过的目标中心）：交叉星芒。 */
    public static void hit(ServerLevel level, ServerPlayer caster, Vec3 point, int stage) {
        if (level == null || point == null) {
            return;
        }
        Vec3 direction = caster == null ? Vec3.ZERO : caster.getLookAngle();
        AbilityEffectPayload.broadcast(level, point,
                AbilityEffectPayload.of(AbilityEffectPayload.KIND_HIT, point, direction, 1.0D, stage));
    }

    /**
     * 剑气刀光：沿<b>真实被墙截断后的</b>射线前进。
     *
     * <p>{@code origin} 必须是<b>眼睛位置本身</b>，{@code reach} 必须是截断后的真实距离——
     * 客户端画的是 {@code origin + dir × reach}，可见终点与判定终点严格一致
     * （既不穿墙，也不会比判定短一截）。
     *
     * @param eye   施法者眼睛位置（起点，不做任何后退偏移）
     * @param reach 实际可达距离（已被方块截断）
     */
    public static void wave(ServerLevel level, ServerPlayer caster, Vec3 eye, Vec3 direction, double reach,
            int stage) {
        if (level == null || eye == null || caster == null) {
            return;
        }
        AbilityEffectPayload.broadcast(level, eye,
                AbilityEffectPayload.of(AbilityEffectPayload.KIND_WAVE, eye, direction, reach, stage));
    }

    /**
     * 三维球壳爆发：球心＝玩家躯干中心。
     *
     * @param center 球心（躯干中心，不是脚底）
     * @param radius 真实判定半径（6 / 终阶 8）
     */
    public static void burst(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, int stage) {
        if (level == null || center == null || caster == null) {
            return;
        }
        AbilityEffectPayload.broadcast(level, center,
                AbilityEffectPayload.of(AbilityEffectPayload.KIND_BURST, center, caster.getLookAngle(),
                        radius, stage));
    }

    /** 弑王：目标附近的小型紫金斩痕。 */
    public static void slayer(ServerLevel level, ServerPlayer caster, Vec3 targetCenter, int stage) {
        if (level == null || targetCenter == null) {
            return;
        }
        Vec3 direction = caster == null ? Vec3.ZERO : caster.getLookAngle();
        AbilityEffectPayload.broadcast(level, targetCenter,
                AbilityEffectPayload.of(AbilityEffectPayload.KIND_SLAYER, targetCenter, direction, 1.0D,
                        Math.max(stage, FINAL_STAGE - 2)));
    }

    /**
     * 吸血：从受害者向施法者运动的少量点。
     *
     * <p>下发的是<b>"目标 → 施法者"的真实向量（不归一化）</b>，其长度就是真实距离；
     * 客户端按进度沿它前进，终点精确落在施法者眼睛位置。
     *
     * @param amount 实际回复的生命值，用于把"强度"夹进视觉幅度（不改变任何结算）
     */
    public static void lifesteal(ServerLevel level, ServerPlayer caster, Vec3 from, double amount, int stage) {
        if (level == null || caster == null || from == null) {
            return;
        }
        Vec3 toCaster = caster.getEyePosition().subtract(from);
        if (toCaster.lengthSqr() < 1.0E-8D) {
            return;
        }
        AbilityEffectPayload.broadcast(level, from,
                AbilityEffectPayload.of(AbilityEffectPayload.KIND_LIFESTEAL, from, toCaster, amount, stage, false));
    }

    /** 玩家躯干中心（三维球心）：脚底 + 身高的一半。 */
    public static Vec3 torsoCenter(ServerPlayer player) {
        return new Vec3(player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ());
    }
}
