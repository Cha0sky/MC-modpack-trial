package com.evolvingblade.client;

import java.util.ArrayDeque;
import java.util.Deque;

import com.evolvingblade.network.AbilityEffectPayload;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 客户端技能特效：<b>连续几何</b>渲染（弯月刀光 / 三维球壳 / 星轨 / 斩痕 / 吸血轨迹）。
 *
 * <h2>为什么不再用"撒原版粒子"</h2>
 * 旧实现每 tick 只生成 5–12 个 DUST 点，全效果预算 64，约第 6 tick 就耗尽，
 * 球壳还没扩到 6/8 半径动画就停了；而且点云拼不出连续形状。
 * 现在改为在 {@code RenderLevelStageEvent.AFTER_PARTICLES} 里直接画<b>有界三角形带</b>：
 * 形状连续、半径与寿命严格按脚本走，原版点粒子只留极少量做辅效。
 *
 * <h2>硬上限（都是本类的常量，不是"通常不会超"）</h2>
 * <ul>
 *   <li>同屏活动效果 ≤ {@value #MAX_ACTIVE_EFFECTS}（超出丢最新）；</li>
 *   <li><b>每帧顶点硬预算</b> {@value #MAX_VERTICES_PER_FRAME}：累计到预算就停止提交，
 *       单个效果也不会写爆；</li>
 *   <li>寿命上限：剑气 {@value #WAVE_LIFETIME_TICKS} tick、爆发/命中/斩痕
 *       {@value #BURST_LIFETIME_TICKS} tick、吸血 {@value #LIFESTEAL_LIFETIME_TICKS} tick；</li>
 *   <li>每 tick 原版点粒子 ≤ {@value #MAX_PARTICLES_PER_TICK}（只做辅效）；</li>
 *   <li>换维度 / 死亡 / 断线立刻清空。</li>
 * </ul>
 *
 * <h2>画质降级</h2>
 * 读原版"粒子效果"选项：{@code MINIMAL} 时只保留主弧与低分段（但<b>半径与时间仍然完整</b>），
 * {@code DECREASED} 时减半分段。降级只影响观感，不影响任何判定。
 *
 * <p>本类<b>只在客户端加载</b>（{@code @Mod(dist = CLIENT)} 的事件总线上注册）。
 */
public final class EBAbilityClient {

    /** 同屏活动效果硬上限。 */
    public static final int MAX_ACTIVE_EFFECTS = 12;
    /** 每帧顶点硬预算（累计，所有效果共享）。 */
    public static final int MAX_VERTICES_PER_FRAME = 8192;
    /** 每帧原版点粒子硬上限（只做辅效）。 */
    public static final int MAX_ACCENT_PARTICLES_PER_FRAME = 8;
    /** 剑气寿命（tick）。 */
    public static final int WAVE_LIFETIME_TICKS = 10;
    /** 爆发 / 命中 / 斩痕寿命（tick）：一次约 14 tick 的稀疏三维脉冲。 */
    public static final int BURST_LIFETIME_TICKS = 14;
    /** 吸血寿命（tick）。 */
    public static final int LIFESTEAL_LIFETIME_TICKS = 8;

    /** 球壳扩张占寿命的比例（与 {@link com.evolvingblade.combat.AbilityGeometry} 保持一致）。 */
    private static final double BURST_EXPAND_FRACTION = com.evolvingblade.combat.AbilityGeometry.BURST_EXPAND_FRACTION;
    /** 全画质下球壳环的分段数。 */
    private static final int FULL_RING_SEGMENTS = 28;
    /** 球壳的倾斜环组数（终阶 3 组，非终阶 2 组；DESIGN 要求三组不同倾角）。 */
    private static final int MAX_TILT_GROUPS = 3;
    /** 每个四边形（两三角形）的顶点数。 */
    private static final int VERTICES_PER_QUAD = com.evolvingblade.combat.AbilityGeometry.VERTICES_PER_QUAD;
    /** 环带相对半径的厚度。 */
    private static final double RING_THICKNESS_RATIO = 0.055D;

    /** 冷青主色（低饱和、亮度克制）。 */
    private static final float[] CORE_COLOR = {0.45F, 0.85F, 0.95F};
    /** 暗紫辅色（星砂 / 星轨）。 */
    private static final float[] STAR_COLOR = {0.46F, 0.32F, 0.86F};
    /** 紫金斩痕色（只用一点点）。 */
    private static final float[] SLAYER_COLOR = {0.95F, 0.78F, 0.35F};

    private static final DustParticleOptions CORE_DUST = new DustParticleOptions(
            new Vector3f(CORE_COLOR[0], CORE_COLOR[1], CORE_COLOR[2]), 1.2F);
    private static final DustParticleOptions STAR_DUST = new DustParticleOptions(
            new Vector3f(STAR_COLOR[0], STAR_COLOR[1], STAR_COLOR[2]), 0.9F);

    private static final Deque<Effect> EFFECTS = new ArrayDeque<>();

    private EBAbilityClient() {}

    /** 收到服务器施法事件：登记一个寿命有界的活动效果。 */
    public static void accept(AbilityEffectPayload payload) {
        if (EFFECTS.size() >= MAX_ACTIVE_EFFECTS) {
            // 丢最新的，保证"活动效果数"永远不会超过硬上限。
            return;
        }
        ResourceKey<Level> dimension = Minecraft.getInstance().level == null
                ? null
                : Minecraft.getInstance().level.dimension();
        EFFECTS.addLast(new Effect(payload.kind(),
                new Vec3(payload.x(), payload.y(), payload.z()),
                new Vec3(payload.dirX(), payload.dirY(), payload.dirZ()),
                payload.magnitude(), payload.tier(), dimension, lifetimeOf(payload.kind())));
    }

    /** 切换维度/死亡/断线时清空。 */
    public static void reset() {
        EFFECTS.clear();
    }

    /** 当前活动效果数（诊断/测试用）。 */
    public static int activeEffects() {
        return EFFECTS.size();
    }

    /** 当前客户端的原版点粒子预算（随"粒子效果"选项降级）。 */
    public static int particleBudget() {
        return switch (quality()) {
            case MINIMAL -> 1;
            case DECREASED -> 3;
            case ALL -> MAX_ACCENT_PARTICLES_PER_FRAME;
        };
    }

    /**
     * 每客户端 tick 推进一次：淘汰过期/换维度的效果。
     *
     * <p>几何渲染与辅效点粒子都在 {@link #render} 里按<b>帧</b>做，
     * 因为绘制缓冲是帧级的；tick 只负责"记账 + 淘汰"。
     */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            EFFECTS.clear();
            return;
        }
        var iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (!effect.isSameDimension(level.dimension()) || effect.age >= effect.lifetime) {
                iterator.remove();
                continue;
            }
            effect.age++;
        }
    }

    // ------------------------------------------------------------------
    // 几何渲染（RenderLevelStageEvent.AFTER_PARTICLES）
    // ------------------------------------------------------------------

    /**
     * 把当前所有活动效果画成连续几何。由 {@code EBClient} 在
     * {@code RenderLevelStageEvent.AFTER_PARTICLES} 上调用。
     *
     * @param pose        相机相对世界的变换矩阵（事件提供的 PoseStack 最后一层）
     * @param cameraPos   相机世界坐标（保留参数：当前阶段 pose 是单位矩阵，顶点用世界坐标）
     * @param partialTick 当前帧的插值系数（0..1）：用 {@code age + partialTick} 计算进度，
     *                    球壳/刀光因此是<b>平滑连续</b>扩张，而不是每 20 tick 跳一档
     */
    public static void render(Matrix4f pose, Vec3 cameraPos, float partialTick) {
        if (EFFECTS.isEmpty() || pose == null || cameraPos == null) {
            return;
        }
        float tickFraction = Math.max(0.0F, Math.min(1.0F, partialTick));
        BufferBuilder buffer = null;
        int written = 0;
        try {
            buffer = beginGeometry();
            if (buffer == null) {
                return;
            }
            int budget = MAX_VERTICES_PER_FRAME;
            int accentLeft = particleBudget();
            Level level = Minecraft.getInstance().level;
            for (Effect effect : EFFECTS) {
                if (budget <= 0) {
                    break;
                }
                double progress = effect.progress(tickFraction);
                int used = switch (effect.kind) {
                    case AbilityEffectPayload.KIND_WAVE ->
                            buildWave(effect, buffer, pose, cameraPos, progress, budget);
                    case AbilityEffectPayload.KIND_BURST ->
                            buildBurst(effect, buffer, pose, cameraPos, progress, budget);
                    case AbilityEffectPayload.KIND_HIT ->
                            buildHit(effect, buffer, pose, cameraPos, progress, budget);
                    case AbilityEffectPayload.KIND_SLAYER ->
                            buildSlayer(effect, buffer, pose, cameraPos, progress, budget);
                    case AbilityEffectPayload.KIND_LIFESTEAL ->
                            buildLifesteal(effect, buffer, pose, cameraPos, progress, budget);
                    default -> 0;
                };
                budget -= used;
                written += used;
                if (level != null && accentLeft > 0 && effect.accentAge != effect.age) {
                    effect.accentAge = effect.age;
                    emitAccentParticles(effect, level, accentLeft);
                    accentLeft -= Math.min(accentLeft,
                            effect.kind == AbilityEffectPayload.KIND_BURST ? 2 : 1);
                }
            }
            if (written > 0) {
                MeshData mesh = buffer.build();
                if (mesh != null) {
                    com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
                }
            }
        } finally {
            // 无论成功、零顶点还是抛异常，都必须恢复全局渲染状态，绝不污染其它模组的渲染。
            endGeometry();
        }
    }

    private static BufferBuilder beginGeometry() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void endGeometry() {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------
    // 各效果的几何脚本
    // ------------------------------------------------------------------

    /**
     * 爆发：以<b>真实范围中心</b>（躯干）发出一次约 {@value #BURST_LIFETIME_TICKS} tick 的
     * 静默稀疏三维脉冲——三条<b>不同平面、不同方位</b>的分段弧表达球体，半径严格等于判定的 6 / 8。
     *
     * <p>三条弧的相位由 {@code AbilityGeometry.burstArcPhase} 给出：每条都<b>跨过 y 的零点</b>，
     * 因此画面上下都有弧线段（旧实现固定从 0° 起走 150°，三条全在上半球）。
     * 三条弧的倾角与方位角都不同，不是"同一个轴转三圈"。
     *
     * <p>刻意不做的事：不随 age 旋转（一次安静的脉冲）、不画不透明球、不画地面圆圈、不做粗管子
     * （厚度固定在 0.04…0.09 格）。
     */
    private static int buildBurst(Effect effect, BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            double progress, int budget) {
        double radius = com.evolvingblade.combat.AbilityGeometry.burstRadius(progress, effect.magnitude);
        if (radius <= 0.05D) {
            return 0;
        }
        // 后半段淡出：alpha 从 1 降到 0。
        float fade = (float) Math.max(0.0D, Math.min(1.0D, (1.0D - progress) / 0.35D));
        int segments = segmentsFor(FULL_RING_SEGMENTS);
        double thickness = com.evolvingblade.combat.AbilityGeometry.burstArcThickness(radius);
        double inner = Math.max(0.0D, radius - thickness);
        double outer = radius + thickness;
        int written = 0;
        for (int group = 0; group < MAX_TILT_GROUPS; group++) {
            double[] phase = com.evolvingblade.combat.AbilityGeometry.burstArcPhase(group);
            double startAngle = phase[0];
            double span = Math.toRadians(phase[1]);
            double tilt = phase[2];
            double azimuth = phase[3];
            float[] color = group == 0 ? CORE_COLOR : STAR_COLOR;
            float alpha = (group == 0 ? 0.70F : 0.40F) * fade;
            for (int i = 0; i < segments; i++) {
                double a0 = startAngle + span * (i / (double) segments);
                double a1 = startAngle + span * ((i + 1) / (double) segments);
                Vec3 p0 = burstPoint(effect.origin, a0, inner, tilt, azimuth);
                Vec3 p1 = burstPoint(effect.origin, a1, inner, tilt, azimuth);
                Vec3 p2 = burstPoint(effect.origin, a1, outer, tilt, azimuth);
                Vec3 p3 = burstPoint(effect.origin, a0, outer, tilt, azimuth);
                double taper = 0.55D + 0.45D * Math.sin(Math.PI * (i + 0.5D) / segments);
                written += quad(buffer, pose, camera, p0, p1, p2, p3, color,
                        (float) (alpha * taper), budget - written);
                if (written >= budget) {
                    break;
                }
            }
            if (written >= budget) {
                break;
            }
        }
        return written;
    }

    /** 爆发弧上的一点（世界坐标），委托纯几何层以便单测相位覆盖。 */
    private static Vec3 burstPoint(Vec3 center, double angle, double radius, double tilt, double azimuth) {
        double[] point = com.evolvingblade.combat.AbilityGeometry.burstArcPoint(
                center.x, center.y, center.z, angle, radius, tilt, azimuth);
        return new Vec3(point[0], point[1], point[2]);
    }

    /**
     * 剑气：<b>横切射线的短弧刃</b> + 极短稀疏尾迹。
     *
     * <p>关键修正：旧实现把弧刃画在"射线方向 × 侧向"的平面里，第一人称沿射线看过去几乎 edge-on，
     * 基本看不见。现在弧刃画在<b>垂直于射线</b>的平面（{@code side} × {@code up}）上，
     * 半径约 {@value com.evolvingblade.combat.AbilityGeometry#WAVE_ARC_RADIUS} 格（直径 ≈ 1.4 格）、
     * 厚度约 {@value com.evolvingblade.combat.AbilityGeometry#WAVE_ARC_THICKNESS} 格，
     * 整条弧沿射线从 {@value com.evolvingblade.combat.AbilityGeometry#WAVE_START_ALONG} 格
     * 移动到真实（被墙截断的）reach 末端；两个面都渲染（已 disableCull）。
     *
     * <p>结算仍然是<b>服务器即时射线</b>：伤害在施法那一刻按射线结算，
     * 这里画的只是"看起来在飞"的弧刃，不承诺"飞到才结算"。
     */
    private static int buildWave(Effect effect, BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            double progress, int budget) {
        // 服务器下发的是<b>被墙截断后的真实非负距离</b>。这里绝不做 Math.max(0.5, reach)：
        // 墙在 0.1 格时那样会把弧刃画到墙后 0.5 格去。reach 为 0 / 极小就不画。
        double reach = effect.magnitude;
        if (!Double.isFinite(reach) || reach <= 0.05D) {
            return 0;
        }
        Vec3 direction = effect.direction.normalize();
        double along = com.evolvingblade.combat.AbilityGeometry.waveAlong(progress, reach);
        Vec3 side = perpendicular(direction);
        Vec3 up = direction.cross(side).normalize();
        double[] dirArray = {direction.x, direction.y, direction.z};
        double[] sideArray = {side.x, side.y, side.z};
        double[] upArray = {up.x, up.y, up.z};
        double radius = com.evolvingblade.combat.AbilityGeometry.WAVE_ARC_RADIUS;
        double thickness = com.evolvingblade.combat.AbilityGeometry.WAVE_ARC_THICKNESS;
        double span = Math.toRadians(com.evolvingblade.combat.AbilityGeometry.WAVE_ARC_SPAN_DEGREES);
        float fade = (float) Math.max(0.0D, 1.0D - progress * 0.85D);
        int steps = Math.max(4, segmentsFor(WAVE_ARC_SEGMENTS));
        int written = 0;
        double inner = Math.max(0.05D, radius - thickness * 0.5D);
        double outer = radius + thickness * 0.5D;
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps;
            double t1 = (i + 1) / (double) steps;
            double theta0 = -span * 0.5D + span * t0;
            double theta1 = -span * 0.5D + span * t1;
            Vec3 p0 = arcPoint(effect.origin, dirArray, sideArray, upArray, along, theta0, inner);
            Vec3 p1 = arcPoint(effect.origin, dirArray, sideArray, upArray, along, theta1, inner);
            Vec3 p2 = arcPoint(effect.origin, dirArray, sideArray, upArray, along, theta1, outer);
            Vec3 p3 = arcPoint(effect.origin, dirArray, sideArray, upArray, along, theta0, outer);
            // 两端稍淡，读起来像一记弧形挥砍。
            double taper = 0.5D + 0.5D * Math.sin(Math.PI * (i + 0.5D) / steps);
            written += quad(buffer, pose, camera, p0, p1, p2, p3, CORE_COLOR,
                    (float) (0.85D * fade * taper), budget - written);
            if (written >= budget) {
                break;
            }
        }
        // 尾迹：沿射线在弧刃后面留 2 个极短小段，不连成条。
        int tail = segmentsFor(WAVE_TAIL_DOTS);
        for (int i = 0; i < tail && written < budget; i++) {
            double back = along - 0.30D * (i + 1);
            if (back <= 0.05D) {
                break;
            }
            double half = 0.05D;
            Vec3 a = arcPoint(effect.origin, dirArray, sideArray, upArray, back, 0.0D, -half);
            Vec3 b = arcPoint(effect.origin, dirArray, sideArray, upArray, back, 0.0D, half);
            Vec3 c = b.add(direction.scale(0.12D));
            Vec3 d = a.add(direction.scale(0.12D));
            written += quad(buffer, pose, camera, a, b, c, d, STAR_COLOR,
                    0.30F * fade * (1.0F - i * 0.3F), budget - written);
        }
        return written;
    }

    /** 弧刃分段上限。 */
    private static final int WAVE_ARC_SEGMENTS = 10;
    /** 尾迹点数上限。 */
    private static final int WAVE_TAIL_DOTS = 2;

    /** 弧刃上的一点（世界坐标），委托纯几何层以便单测。 */
    private static Vec3 arcPoint(Vec3 origin, double[] direction, double[] side, double[] up,
            double along, double theta, double radius) {
        double[] point = com.evolvingblade.combat.AbilityGeometry.waveArcPoint(
                origin.x, origin.y, origin.z, direction, side, up, along, theta, radius);
        return new Vec3(point[0], point[1], point[2]);
    }

    /** 剑气弧刃半宽（委托纯几何层，便于无客户端运行时也能单测形状）。 */
    /** 四邻域倾斜环上的点（辅效点粒子用；委托纯几何层）。 */
    static Vec3 ringPoint(Vec3 center, double angle, double radius, double tilt) {
        double[] point = com.evolvingblade.combat.AbilityGeometry.ringPoint(
                center.x, center.y, center.z, angle, radius, tilt);
        return new Vec3(point[0], point[1], point[2]);
    }

    /** 命中：两片交叉的短带（紧凑星芒），快速淡出。 */
    private static int buildHit(Effect effect, BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            double progress, int budget) {
        float fade = (float) Math.max(0.0D, 1.0D - progress);
        double length = 0.45D * (1.0D - progress * 0.4D);
        Vec3 dir = effect.direction.normalize();
        Vec3 side = perpendicular(dir);
        int written = 0;
        for (int i = 0; i < 2; i++) {
            double angle = i * Math.PI * 0.5D;
            Vec3 axis = side.scale(Math.cos(angle)).add(dir.cross(side).normalize().scale(Math.sin(angle)));
            Vec3 a = effect.origin.subtract(axis.scale(length));
            Vec3 b = effect.origin.add(axis.scale(length));
            Vec3 offset = dir.cross(axis).normalize().scale(0.05D);
            written += quad(buffer, pose, camera, a.add(offset), b.add(offset), b.subtract(offset), a.subtract(offset),
                    CORE_COLOR, 0.9F * fade, budget - written);
        }
        return written;
    }

    /** 弑王：目标附近的小型紫金斩痕（斜十字）。 */
    private static int buildSlayer(Effect effect, BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            double progress, int budget) {
        float fade = (float) Math.max(0.0D, 1.0D - progress);
        double length = 0.6D * (1.0D - progress * 0.35D);
        Vec3 dir = effect.direction.normalize();
        Vec3 side = perpendicular(dir);
        int written = 0;
        for (int i = 0; i < 2; i++) {
            double sign = i == 0 ? 1.0D : -1.0D;
            Vec3 a = effect.origin.subtract(side.scale(length)).add(0.0D, -sign * length * 0.7D, 0.0D);
            Vec3 b = effect.origin.add(side.scale(length)).add(0.0D, sign * length * 0.7D, 0.0D);
            Vec3 offset = dir.scale(0.05D);
            written += quad(buffer, pose, camera, a.add(offset), b.add(offset), b.subtract(offset), a.subtract(offset),
                    SLAYER_COLOR, 0.85F * fade, budget - written);
        }
        return written;
    }

    /**
     * 吸血：<b>两三颗短流星</b>从受害者飞向施法者真实位置。
     *
     * <p>刻意不做的事：不做密集红雾、不铺满画面。每颗流星是一小段细带（长度约 0.35 格），
     * 彼此错开一点相位，总共不超过 {@value #LIFESTEAL_METEORS} 颗，颜色用低饱和暗紫。
     *
     * <p>{@code direction} 保留的是"目标 → 施法者"的<b>真实向量（不归一化）</b>，
     * 所以长度就是真实距离；终点在寿命末尾精确落在施法者身上。
     */
    private static int buildLifesteal(Effect effect, BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            double progress, int budget) {
        Vec3 toCaster = effect.direction;
        double distance = toCaster.length();
        if (distance < 0.05D) {
            return 0;
        }
        Vec3 unit = toCaster.scale(1.0D / distance);
        Vec3 side = perpendicular(unit);
        float fade = (float) Math.max(0.0D, 1.0D - progress * 0.35D);
        int meteors = Math.max(1, Math.min(LIFESTEAL_METEORS, segmentsFor(LIFESTEAL_METEORS)));
        int written = 0;
        for (int i = 0; i < meteors; i++) {
            // 每颗流星错开一点相位，形成"回收"的连缀感而不是一整条带。
            double offset = i * 0.14D;
            double head = distance * Math.min(1.0D, Math.max(0.0D, progress - offset));
            double tail = Math.max(0.0D, head - LIFESTEAL_METEOR_LENGTH);
            if (head <= 0.0D) {
                continue;
            }
            Vec3 headPoint = effect.origin.add(unit.scale(head));
            Vec3 tailPoint = effect.origin.add(unit.scale(tail));
            double half = 0.035D;
            written += quad(buffer, pose, camera,
                    tailPoint.add(side.scale(half)), headPoint.add(side.scale(half)),
                    headPoint.subtract(side.scale(half)), tailPoint.subtract(side.scale(half)),
                    STAR_COLOR, 0.55F * fade, budget - written);
            if (written >= budget) {
                break;
            }
        }
        return written;
    }

    /** 吸血流星数量上限。 */
    private static final int LIFESTEAL_METEORS = 3;
    /** 单颗流星长度（格）。 */
    private static final double LIFESTEAL_METEOR_LENGTH = 0.35D;

    /** 四边形提交：把全局预算夹紧后才写顶点。 */
    private static int quad(BufferBuilder buffer, Matrix4f pose, Vec3 camera,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] color, float alpha, int budget) {
        int allowed = com.evolvingblade.combat.AbilityGeometry.accumulateVertices(0, budget);
        if (allowed < VERTICES_PER_QUAD) {
            return 0;
        }
        vertex(buffer, pose, camera, a, color, alpha);
        vertex(buffer, pose, camera, b, color, alpha);
        vertex(buffer, pose, camera, c, color, alpha);
        vertex(buffer, pose, camera, a, color, alpha);
        vertex(buffer, pose, camera, c, color, alpha);
        vertex(buffer, pose, camera, d, color, alpha);
        return VERTICES_PER_QUAD;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 camera, Vec3 world, float[] color, float alpha) {
        // 一手源码核实（neoforge-21.1.248-sources）：
        //   GameRenderer: frustumMatrix = new Matrix4f().rotation(camera.rotation().conjugate()) —— 只有旋转；
        //   LevelRenderer: modelViewStack.mul(frustumMatrix) + applyModelViewMatrix()，
        //                  AFTER_PARTICLES 仍在这个 modelView 下，posestack 是单位矩阵。
        // 因此顶点必须先在 CPU 侧减去相机位置，再由 modelView 做那一次旋转；
        // 提交世界坐标会让特效飘到世界原点附近。这里绝不追加第二次相机旋转。
        double[] relative = com.evolvingblade.combat.AbilityGeometry.cameraRelative(
                world.x, world.y, world.z, camera.x, camera.y, camera.z);
        buffer.addVertex(pose, (float) relative[0], (float) relative[1], (float) relative[2])
                .setColor(color[0], color[1], color[2], Math.max(0.0F, Math.min(1.0F, alpha)));
    }

    // ------------------------------------------------------------------
    // 辅效点粒子（极少）
    // ------------------------------------------------------------------

    private static void emitAccentParticles(Effect effect, Level level, int budget) {
        if (budget <= 0) {
            return;
        }
        int count = Math.min(budget, effect.kind == AbilityEffectPayload.KIND_BURST ? 2 : 1);
        for (int i = 0; i < count; i++) {
            double progress = (double) effect.age / Math.max(1, effect.lifetime);
            if (effect.kind == AbilityEffectPayload.KIND_BURST) {
                double radius = com.evolvingblade.combat.AbilityGeometry.burstRadius(progress, effect.magnitude);
                Vec3 point = ringPoint(effect.origin, level.random.nextDouble() * Math.PI * 2.0D, radius,
                        (i + 1) * 0.42D);
                level.addParticle(i == 0 ? CORE_DUST : STAR_DUST, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            } else if (effect.kind == AbilityEffectPayload.KIND_WAVE) {
                // 与弧刃共用同一条行进函数与同一个真实（非负）reach，避免粒子跑到墙后或与弧刃脱节。
                double reach = effect.magnitude;
                if (!Double.isFinite(reach) || reach <= 0.05D) {
                    continue;
                }
                double along = com.evolvingblade.combat.AbilityGeometry.waveAlong(progress, reach);
                Vec3 direction = effect.direction.normalize();
                Vec3 point = effect.origin.add(direction.scale(along));
                level.addParticle(CORE_DUST, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 画质 / 工具
    // ------------------------------------------------------------------

    private static ParticleStatus quality() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return ParticleStatus.ALL;
        }
        return minecraft.options.particles().get();
    }

    /** 按画质取分段数（最少档只保留主弧与低分段，但半径与时间完整）。 */
    static int segmentsFor(int full) {
        return switch (quality()) {
            case MINIMAL -> Math.max(6, full / 4);
            case DECREASED -> Math.max(8, full / 2);
            case ALL -> full;
        };
    }

    private static int lifetimeOf(int kind) {
        return switch (kind) {
            case AbilityEffectPayload.KIND_WAVE -> WAVE_LIFETIME_TICKS;
            case AbilityEffectPayload.KIND_LIFESTEAL -> LIFESTEAL_LIFETIME_TICKS;
            default -> BURST_LIFETIME_TICKS;
        };
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 up = Math.abs(direction.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        return direction.cross(up).normalize();
    }

    /** 一个活动效果：进度、寿命、所属维度。{@code direction} 的语义随 kind 而定（见各 builder）。 */
    private static final class Effect {
        final int kind;
        final Vec3 origin;
        final Vec3 direction;
        final double magnitude;
        final int tier;
        final ResourceKey<Level> dimension;
        final int lifetime;
        int age;
        /** 上次发射辅效点粒子时的 age（渲染是帧级、粒子只应按 tick 发射）。 */
        int accentAge = -1;

        /** 带 partialTick 的插值进度（0..1）：让扩张/淡出是平滑连续的，而不是每 tick 跳一档。 */
        double progress(float tickFraction) {
            return Math.max(0.0D,
                    Math.min(1.0D, (this.age + tickFraction) / Math.max(1, this.lifetime)));
        }

        Effect(int kind, Vec3 origin, Vec3 direction, double magnitude, int tier,
                ResourceKey<Level> dimension, int lifetime) {
            this.kind = kind;
            this.origin = origin;
            this.direction = direction.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : direction;
            this.magnitude = magnitude;
            this.tier = tier;
            this.dimension = dimension;
            this.lifetime = lifetime;
        }

        boolean isSameDimension(ResourceKey<Level> current) {
            return this.dimension != null && this.dimension.equals(current);
        }
    }
}
