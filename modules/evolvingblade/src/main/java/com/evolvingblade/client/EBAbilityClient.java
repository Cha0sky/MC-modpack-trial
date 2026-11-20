package com.evolvingblade.client;

import java.util.ArrayDeque;
import java.util.Deque;

import com.evolvingblade.network.AbilityEffectPayload;
import com.evolvingblade.combat.AbilityGeometry;
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

/** Client-side, camera-relative geometry for the Star Eater and its evolution. */
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

    private static final int VERTICES_PER_QUAD = AbilityGeometry.VERTICES_PER_QUAD;
    private static final float[] CORE_COLOR = {0.60F,0.94F,0.92F};
    private static final float[] STAR_COLOR = {0.57F,0.43F,0.82F};
    private static final float[] SLAYER_COLOR = {1.0F,0.74F,0.37F};
    private static final float[] PALE = {1.0F,0.94F,0.76F};
    private static final float[][] TINT = {
        {0.66F,0.72F,0.73F}, {0.83F,0.77F,0.47F}, CORE_COLOR,
        {0.98F,0.43F,0.53F}, {0.59F,0.76F,1F}, SLAYER_COLOR, STAR_COLOR, PALE
    };

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
                    case AbilityEffectPayload.KIND_EVOLUTION -> buildEvolution(effect,buffer,pose,cameraPos,progress,budget);
                    default -> 0;
                };
                budget -= used;
                if (level != null && accentLeft > 0 && effect.accentAge != effect.age) {
                    effect.accentAge = effect.age;
                    emitAccentParticles(effect, level, accentLeft);
                    accentLeft -= Math.min(accentLeft,
                            effect.kind == AbilityEffectPayload.KIND_BURST ? 2 : 1);
                }
            }
            MeshData mesh = buffer.build();
            if (mesh != null) com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
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

    /** Three broken orbits expand to the server-reported radius. At their tips a glint marks the rim. */
    private static int buildBurst(Effect fx, BufferBuilder b, Matrix4f pose, Vec3 camera, double t, int budget) {
        double radius = AbilityGeometry.burstRadius(t, fx.magnitude);
        if (radius < 0.05) return 0;
        int used = 0, steps = segmentsFor(24);
        float fade = (float)Math.min(1, (1-t)*3.2);
        for (int ring=0;ring<3;ring++) {
            double[] phase=AbilityGeometry.burstArcPhase(ring);
            double start=phase[0]+0.30*t;
            double span=Math.toRadians(phase[1]);
            for (int i=0;i<steps;i++) {
                if (i==steps/2) continue;
                double u=(double)i/steps;
                double thickness=AbilityGeometry.burstArcThickness(radius)*(0.5+1.3*Math.sin(Math.PI*u));
                double a0=start+span*u, a1=start+span*(i+1)/steps;
                used+=quad(b,pose,camera,orbit(fx.origin,a0,radius,phase),orbit(fx.origin,a1,radius,phase),
                        orbit(fx.origin,a1,radius-thickness,phase),orbit(fx.origin,a0,radius-thickness,phase),
                        ring==0 ? PALE : tint(fx),fade*(ring==0 ? .84F : .58F),budget-used);
            }
            Vec3 tip=orbit(fx.origin,start+span,radius,phase);
            used+=glint(b,pose,camera,tip,Math.min(.25,radius*.08),PALE,fade,budget-used);
        }
        return used;
    }

    private static Vec3 orbit(Vec3 origin,double a,double r,double[] plane) {
        double[] p=AbilityGeometry.burstArcPoint(origin.x,origin.y,origin.z,a,r,plane[2],plane[3]);
        return new Vec3(p[0],p[1],p[2]);
    }

    /** Two tapered crescent blades cross the aim line with a short, delayed copper after-image. */
    private static int buildWave(Effect fx,BufferBuilder b,Matrix4f pose,Vec3 camera,double t,int budget) {
        double reach=fx.magnitude;
        if (reach<=.05) return 0;
        Vec3 direction=fx.direction.normalize();
        Vec3 side=perpendicular(direction), up=direction.cross(side).normalize();
        int used=0, steps=segmentsFor(18);
        int layers=quality()==ParticleStatus.MINIMAL ? 1 : 2;
        for (int layer=0;layer<layers;layer++) {
            double along=AbilityGeometry.waveAlong(Math.max(0,t-.10*layer),reach);
            Vec3 center=fx.origin.add(direction.scale(along));
            double tilt=(layer==0 ? -.34 : .52) + t*.35;
            Vec3 width=side.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
            Vec3 height=direction.cross(width).normalize();
            double radius=AbilityGeometry.WAVE_ARC_RADIUS*(layer==0 ? 1 : .72);
            for (int i=0;i<steps;i++) {
                double u=(double)i/steps, v=(double)(i+1)/steps;
                Vec3 p=wing(center,width,height,radius,u), q=wing(center,width,height,radius,v);
                double inset=.09*(layer==0 ? 1 : .65);
                used+=quad(b,pose,camera,p,q,q.subtract(height.scale(inset*Math.sin(Math.PI*v))),
                        p.subtract(height.scale(inset*Math.sin(Math.PI*u))),layer==0?tint(fx):PALE,
                        (float)((1-.76*t)*(layer==0 ? .91 : .49)),budget-used);
            }
        }
        return used;
    }

    private static Vec3 wing(Vec3 center,Vec3 side,Vec3 up,double r,double u) {
        double angle=(u-.5)*Math.toRadians(156);
        return center.add(side.scale(Math.sin(angle)*r)).add(up.scale((Math.cos(angle)-.62)*r));
    }

    /** One camera-facing cut star. Its four tips fade before the bright central lozenge. */
    private static int buildHit(Effect fx,BufferBuilder b,Matrix4f pose,Vec3 camera,double t,int budget) {
        double r=(.13+.23*Math.sin(Math.PI*t))*(1+.045*fx.tier);
        float fade=(float)(1-t);
        int used=glint(b,pose,camera,fx.origin,r*1.35,tint(fx),fade,budget);
        return used+glint(b,pose,camera,fx.origin,r*.48,PALE,fade,budget-used);
    }

    /** The three prongs of a broken crown close down on the target. */
    private static int buildSlayer(Effect fx,BufferBuilder b,Matrix4f pose,Vec3 camera,double t,int budget) {
        Vec3 normal=camera.subtract(fx.origin).normalize(), side=perpendicular(normal), up=normal.cross(side).normalize();
        if (up.y<0) up=up.reverse();
        int used=0;
        for (int i=-1;i<=1;i++) {
            double apex=i==0 ? .62 : .39;
            Vec3 start=fx.origin.add(side.scale(i*(.47-.18*t))).add(up.scale(apex+.18*(1-t)));
            Vec3 end=fx.origin.add(side.scale(.12*i)).subtract(up.scale(.30*t));
            Vec3 mid=start.lerp(end,.35), offset=side.scale(.075);
            used+=quad(b,pose,camera,start,mid.add(offset),end,mid.subtract(offset),i==0?PALE:SLAYER_COLOR,
                    (float)(1-t),budget-used);
        }
        return used;
    }

    /** Two glass-red motes spiral together on the real path back to the caster. */
    private static int buildLifesteal(Effect fx,BufferBuilder b,Matrix4f pose,Vec3 camera,double t,int budget) {
        Vec3 axis=fx.direction.normalize(), side=perpendicular(axis), up=axis.cross(side).normalize();
        int used=0, strands=quality()==ParticleStatus.MINIMAL ? 1 : 2;
        for (int strand=0;strand<strands;strand++) {
            for (int i=0;i<4;i++) {
                double end=Math.max(0,t-.035*i), start=Math.max(0,end-.035);
                Vec3 p=blood(fx,side,up,start,strand), q=blood(fx,side,up,end,strand);
                double w=.032*(1-.13*i);
                Vec3 offset=side.scale(w);
                used+=quad(b,pose,camera,p.subtract(offset),q.subtract(offset),q.add(offset),p.add(offset),
                        strand==0?TINT[3]:SLAYER_COLOR,(float)(.78*(1-.17*i)*(1-.45*t)),budget-used);
            }
            used+=glint(b,pose,camera,blood(fx,side,up,t,strand),.075,PALE,(float)(1-.7*t),budget-used);
        }
        return used;
    }

    private static Vec3 blood(Effect fx,Vec3 side,Vec3 up,double t,int strand) {
        double a=t*Math.PI*2+strand*Math.PI;
        double r=Math.sin(Math.PI*t)*.22;
        return fx.origin.add(fx.direction.scale(t)).add(side.scale(r*Math.cos(a))).add(up.scale(r*Math.sin(a)));
    }

    /** Faceted sparks rise from the feet and converge above the wielder in one gold star. */
    private static int buildEvolution(Effect fx,BufferBuilder b,Matrix4f pose,Vec3 camera,double t,int budget) {
        int count=quality()==ParticleStatus.MINIMAL ? 4 : 7;
        double radius=.13+1.15*Math.sin(Math.PI*t);
        double y=-.65+2*t;
        int used=0;
        for (int i=0;i<count;i++) {
            double a=Math.PI*2*i/count + t*1.7;
            Vec3 p=fx.origin.add(Math.cos(a)*radius,y,Math.sin(a)*radius);
            used+=glint(b,pose,camera,p,.10+.10*Math.sin(Math.PI*t),tint(fx),(float)Math.sin(Math.PI*t),budget-used);
        }
        if (t>.64) {
            double flare=Math.sin(Math.PI*(t-.64)/.36);
            used+=glint(b,pose,camera,fx.origin.add(0,1.38,0),.45*flare,PALE,(float)flare,budget-used);
        }
        return used;
    }

    private static float[] tint(Effect fx) {
        return TINT[Math.clamp(fx.tier,0,TINT.length-1)];
    }

    private static int glint(BufferBuilder b,Matrix4f pose,Vec3 camera,Vec3 p,double r,float[] color,float alpha,int budget) {
        Vec3 normal=camera.subtract(p).normalize(), x=perpendicular(normal), y=normal.cross(x).normalize();
        int used=quad(b,pose,camera,p.add(y.scale(r)),p.add(x.scale(r*.18)),p.subtract(y.scale(r)),
                p.subtract(x.scale(r*.18)),color,alpha,budget);
        return used+quad(b,pose,camera,p.add(x.scale(r*.75)),p.add(y.scale(r*.12)),p.subtract(x.scale(r*.75)),
                p.subtract(y.scale(r*.12)),color,alpha,budget-used);
    }

    static Vec3 ringPoint(Vec3 center, double angle, double radius, double tilt) {
        double[] point=AbilityGeometry.ringPoint(center.x,center.y,center.z,angle,radius,tilt);
        return new Vec3(point[0],point[1],point[2]);
    }

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
            case AbilityEffectPayload.KIND_EVOLUTION -> 26;
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
