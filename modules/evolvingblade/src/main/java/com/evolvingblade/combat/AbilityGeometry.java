package com.evolvingblade.combat;

/**
 * 技能特效的<b>纯几何与预算</b>计算（零 Minecraft 依赖，可纯 JVM 单测）。
 *
 * <p>客户端渲染层（{@code client.EBAbilityClient}）只负责把这些数变成顶点，
 * 所有"形状 / 时间 / 坐标 / 预算"的事实都在这里，因此可以在没有客户端运行时的环境里回归：
 * <ul>
 *   <li>{@link #burstRadius}：脉冲半径在寿命内的扩张曲线
 *       （前 {@value #BURST_EXPAND_FRACTION} 扩张到真实半径，之后保持不再长大）；</li>
 *   <li>{@link #burstArcPhase} / {@link #burstArcPoint}：爆发的三条分段弧
 *       （每条跨过 y 零点、倾角与方位角都不同、厚度固定在 0.04…0.09 格）；</li>
 *   <li>{@link #waveArcPoint} / {@link #waveAlong}：剑气弧刃
 *       （画在<b>垂直于射线</b>的平面，从 0.7 格移动到真实截断点）；</li>
 *   <li>{@link #cameraRelative} / {@link #applyRotation3x3}：渲染坐标
 *       （顶点先减相机位置，旋转只由 modelView 应用一次）；</li>
 *   <li>{@link #accumulateVertices}：<b>全局</b>顶点预算累计（每个效果都在同一笔预算里扣）。</li>
 * </ul>
 */
public final class AbilityGeometry {

    /** 球壳扩张占寿命的比例。 */
    public static final double BURST_EXPAND_FRACTION = 0.7D;
    /** 每帧顶点硬预算。 */
    public static final int MAX_VERTICES_PER_FRAME = 8192;
    /** 一个四边形（两个三角形）的顶点数。 */
    public static final int VERTICES_PER_QUAD = 6;
    /**
     * 环上的点（含<b>方位角</b>）：先在 XZ 平面取半径 {@code radius} 的圆，绕 X 轴倾斜 {@code tilt}，
     * 再绕 Y 轴旋转 {@code azimuth}。
     *
     * <p>加方位角是为了让三组弧<b>不在同一组平面上</b>：只改 {@code tilt} 会让三条弧共享同一个
     * 法线方向的家族，视觉上读不出球面。
     *
     * @param angle 弧上的相位（弧度）。{@code sin(angle)} 的正负决定这一点在球心的上方还是下方。
     */
    public static double[] burstArcPoint(double cx, double cy, double cz, double angle, double radius,
            double tilt, double azimuth) {
        double[] ring = ringPoint(cx, cy, cz, angle, radius, tilt);
        double dx = ring[0] - cx;
        double dy = ring[1] - cy;
        double dz = ring[2] - cz;
        double cos = Math.cos(azimuth);
        double sin = Math.sin(azimuth);
        return new double[] {cx + dx * cos - dz * sin, cy + dy, cz + dx * sin + dz * cos};
    }

    /**
     * 剑气弧刃上的点：在<b>垂直于射线</b>的平面（{@code side} × {@code up}）上取弧。
     *
     * <p>为什么必须垂直：沿射线方向铺开的扁带在第一人称下几乎 edge-on，看不见。
     * 垂直平面上的弧刃是"横切视线"的，正对镜头。
     *
     * @param side  与 direction 垂直的单位向量（横向）
     * @param up    与 direction、side 都垂直的单位向量（纵向）
     * @param along 弧刃中心沿射线方向的距离（格）
     * @param theta 弧上的相位（弧度）
     * @param radius 弧半径（格）——决定了弧刃的"宽度"（直径 = 2×radius）
     */
    public static double[] waveArcPoint(double ox, double oy, double oz, double[] direction,
            double[] side, double[] up, double along, double theta, double radius) {
        double cos = Math.cos(theta) * radius;
        double sin = Math.sin(theta) * radius;
        return new double[] {
                ox + direction[0] * along + side[0] * cos + up[0] * sin,
                oy + direction[1] * along + side[1] * cos + up[1] * sin,
                oz + direction[2] * along + side[2] * cos + up[2] * sin};
    }

    /** 弧刃沿射线的行进距离：从 {@value #WAVE_START_ALONG} 格开始，随进度到真实（被墙截断的）reach。 */
    public static double waveAlong(double progress, double reach) {
        if (!Double.isFinite(reach) || reach <= 0.0D) {
            return 0.0D;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        double start = Math.min(WAVE_START_ALONG, reach * 0.5D);
        return start + (reach - start) * clamped;
    }

    /** 弧刃起始距离（格）：近处就要看得见，不能等到 0.3×reach 才出现。 */
    public static final double WAVE_START_ALONG = 0.7D;
    /** 弧刃半径（格）：直径 ≈ 1.4 格，落在要求 1.2…1.6 的区间内。 */
    public static final double WAVE_ARC_RADIUS = 0.7D;
    /** 弧刃厚度（格）：薄，0.06…0.1。 */
    public static final double WAVE_ARC_THICKNESS = 0.08D;
    /** 弧刃张角（度）。 */
    public static final double WAVE_ARC_SPAN_DEGREES = 150.0D;

    // ------------------------------------------------------------------
    // 渲染坐标：相机相对顶点（可测的纯数学）
    // ------------------------------------------------------------------

    /**
     * 把世界坐标转成<b>相机相对</b>坐标。
     *
     * <p>为什么必须做这一步（一手源码核实，neoforge-21.1.248-sources）：
     * <ul>
     *   <li>{@code GameRenderer} 构造的 {@code frustumMatrix} 是
     *       {@code new Matrix4f().rotation(camera.rotation().conjugate())} —— <b>只有旋转，没有平移</b>；</li>
     *   <li>{@code LevelRenderer} 在实体渲染前 {@code modelViewStack.mul(frustumMatrix)} 并
     *       {@code applyModelViewMatrix()}；{@code AFTER_PARTICLES} 仍在这个 modelView 下，
     *       {@code matrix4fstack.popMatrix()} 发生在更后面；</li>
     *   <li>{@code posestack} 在 {@code AFTER_PARTICLES} 时是<b>单位矩阵</b>。
     * </ul>
     * 因此"提交世界坐标"会被 GPU 用"只有旋转"的 modelView 变换，特效会飘到世界原点附近；
     * 正确做法是先在 CPU 侧减去相机位置，再由 modelView 做那一次旋转。
     */
    public static double[] cameraRelative(double wx, double wy, double wz, double cx, double cy, double cz) {
        return new double[] {wx - cx, wy - cy, wz - cz};
    }

    /** 行主序 3×3 旋转矩阵作用于向量（用于单测"旋转只应用一次"）。 */
    public static double[] applyRotation3x3(double[] rowMajor, double[] v) {
        return new double[] {
                rowMajor[0] * v[0] + rowMajor[1] * v[1] + rowMajor[2] * v[2],
                rowMajor[3] * v[0] + rowMajor[4] * v[1] + rowMajor[5] * v[2],
                rowMajor[6] * v[0] + rowMajor[7] * v[1] + rowMajor[8] * v[2]};
    }

    /** 绕 Y 轴旋转 {@code radians} 的行主序 3×3（单测用）。 */
    public static double[] rotationY(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        return new double[] {c, 0.0D, s, 0.0D, 1.0D, 0.0D, -s, 0.0D, c};
    }

    /**
     * 一条弧的相位区间，保证<b>每一条弧自身</b>都同时覆盖球心上方与下方。
     *
     * <p>{@code ringPoint} 的 y 分量是 {@code sin(angle) · sin(tilt) · r}；
     * 只有当相位区间<b>跨过 {@code angle = 0} 或 {@code angle = π}</b> 时，
     * 这条弧的 y 才会同时取到正负。旧实现里第三条弧固定走 195°…345°，
     * {@code sin} 全程为负（整条在下半球），却与注释声称的"上下都有"矛盾。
     *
     * @param index 第几条弧（0..2）
     * @return {@code {startAngle, spanDegrees, tilt, azimuth}}
     */
    public static double[] burstArcPhase(int index) {
        return switch (Math.floorMod(index, 3)) {
            // 竖直平面，-75°..75°：sin 由负到正，y 覆盖 -0.97r … +0.97r。
            case 0 -> new double[] {Math.toRadians(-75.0D), 150.0D, Math.PI / 2.0D, 0.0D};
            // 倾斜平面 + 方位角 60°，105°..255°：跨过 π（sin=0），y 覆盖 +0.79r … -0.79r。
            case 1 -> new double[] {Math.toRadians(105.0D), 150.0D, 0.95D, Math.toRadians(60.0D)};
            // 更平的平面 + 方位角 120°，15°..225°：跨过 180°（sin=0），y 覆盖 +0.46r … -0.34r。
            // 注意<b>不能</b>用 195°..345°：那一段 sin 全程为负，整条弧都在下半球。
            default -> new double[] {Math.toRadians(15.0D), 210.0D, 0.50D, Math.toRadians(120.0D)};
        };
    }

    /**
     * 弧带厚度（格）：固定窄区间 {@value #BURST_ARC_MIN_THICKNESS}…{@value #BURST_ARC_MAX_THICKNESS}。
     *
     * <p>旧实现用 {@code 0.055 × radius}，半径 8 时厚度 0.44 格，看起来像粗管子。
     */
    public static double burstArcThickness(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return BURST_ARC_MIN_THICKNESS;
        }
        return Math.max(BURST_ARC_MIN_THICKNESS, Math.min(BURST_ARC_MAX_THICKNESS, radius * 0.02D));
    }

    /** 弧带最小厚度（格）。 */
    public static final double BURST_ARC_MIN_THICKNESS = 0.04D;
    /** 弧带最大厚度（格）。 */
    public static final double BURST_ARC_MAX_THICKNESS = 0.09D;

    private AbilityGeometry() {}

    /**
     * 球壳在给定进度下的半径。
     *
     * @param progress 0..1 的寿命进度
     * @param radius   真实判定半径（6 / 终阶 8）
     * @return 前 {@value #BURST_EXPAND_FRACTION} 线性扩张到 {@code radius}，之后保持 {@code radius}
     */
    public static double burstRadius(double progress, double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0.0D;
        }
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        double grown = Math.min(1.0D, clamped / BURST_EXPAND_FRACTION);
        return radius * grown;
    }

    /**
     * 倾斜环上的点：先在 XZ 平面取半径 {@code radius} 的圆，再绕 X 轴旋转 {@code tilt} 弧度。
     *
     * <p>倾角不同 → 环所在平面不同：{@code tilt = 0} 时点落在 XZ 平面（y = center.y），
     * {@code tilt = π/2} 时落在 XY 平面（z = center.z）。
     *
     * @return 世界坐标下的点
     */
    public static double[] ringPoint(double cx, double cy, double cz, double angle, double radius, double tilt) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        double y = sin * Math.sin(tilt);
        double z = sin * Math.cos(tilt);
        return new double[] {cx + cos, cy + y, cz + z};
    }

    /** 环上点到中心的距离（必须恒等于 {@code radius}，用于单测自检）。 */
    public static double ringDistance(double cx, double cy, double cz, double angle, double radius, double tilt) {
        double[] point = ringPoint(cx, cy, cz, angle, radius, tilt);
        double dx = point[0] - cx;
        double dy = point[1] - cy;
        double dz = point[2] - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 全局顶点预算累计：把一次提交请求夹进剩余预算。
     *
     * @param used   本帧已用顶点
     * @param wanted 本次想提交的顶点
     * @return 实际可提交的顶点数（保证 {@code used + 返回值 <= MAX_VERTICES_PER_FRAME}）
     */
    public static int accumulateVertices(int used, int wanted) {
        if (used < 0 || wanted <= 0 || used >= MAX_VERTICES_PER_FRAME) {
            return 0;
        }
        return Math.min(wanted, MAX_VERTICES_PER_FRAME - used);
    }

    /**
     * 一个效果在寿命末（{@code progress → 1}）是否仍然会渲染。
     *
     * <p>用于回归"球壳不能在扩张完成前就画不出来"：半径必须在
     * {@code progress = BURST_EXPAND_FRACTION} 时达到真实半径，且在此之前始终为正。
     */
    public static boolean rendersAtEndOfLife(double radius) {
        double atExpand = burstRadius(BURST_EXPAND_FRACTION, radius);
        double atEnd = burstRadius(1.0D, radius);
        return atExpand > 0.0D && Math.abs(atExpand - radius) < 1.0E-9D
                && Math.abs(atEnd - radius) < 1.0E-9D;
    }
}
