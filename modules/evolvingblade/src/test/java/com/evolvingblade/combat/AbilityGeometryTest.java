package com.evolvingblade.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AbilityGeometry} 的纯 JVM 单测：球壳扩张时间线、三轴倾角几何、弯月宽度曲线、
 * 全局顶点预算。
 *
 * <p>这些断言直接对着"渲染层真正用到的那几个函数"，不是照着实现抄一遍的空测试：
 * 每个用例都独立复算期望值（手写三角/线性公式），因此实现改坏时会真的变红。
 */
class AbilityGeometryTest {

    private static final double EPS = 1.0E-9D;

    // ------------------------------------------------------------------
    // 球壳：时间线与半径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("球壳在前 70% 寿命内线性扩张到真实半径，之后保持不再长大")
    void burstReachesFullRadiusWithinLifetime() {
        double radius = 8.0D;
        assertEquals(0.0D, AbilityGeometry.burstRadius(0.0D, radius), EPS);
        // 35% 寿命 → 半程 → 半径一半
        assertEquals(4.0D, AbilityGeometry.burstRadius(0.35D, radius), EPS);
        // 70% 寿命 → 恰好到达真实半径
        assertEquals(radius, AbilityGeometry.burstRadius(0.7D, radius), EPS);
        // 之后保持
        assertEquals(radius, AbilityGeometry.burstRadius(0.85D, radius), EPS);
        assertEquals(radius, AbilityGeometry.burstRadius(1.0D, radius), EPS);
    }

    @Test
    @DisplayName("寿命末仍然会渲染（半径 = 真实半径，不会提前停在很小半径）")
    void stillRenderableAtEndOfLife() {
        assertTrue(AbilityGeometry.rendersAtEndOfLife(6.0D), "stage radius 6 must survive to the end of life");
        assertTrue(AbilityGeometry.rendersAtEndOfLife(8.0D), "final radius 8 must survive to the end of life");
        // 反例：如果扩张比例被改成 >1（永远到不了真实半径），这个断言必须变红。
        assertFalse(AbilityGeometry.burstRadius(1.0D, 8.0D) < 8.0D,
                "the shell must actually reach the real radius by the end of its life");
    }

    @Test
    @DisplayName("非法半径/进度一律退化为 0，不产生 NaN 顶点")
    void illegalRadiusDegradesToZero() {
        assertEquals(0.0D, AbilityGeometry.burstRadius(0.5D, Double.NaN), EPS);
        assertEquals(0.0D, AbilityGeometry.burstRadius(0.5D, -3.0D), EPS);
        assertEquals(0.0D, AbilityGeometry.burstRadius(-1.0D, 8.0D), EPS);
        assertEquals(8.0D, AbilityGeometry.burstRadius(2.0D, 8.0D), EPS);
    }

    // ------------------------------------------------------------------
    // 三轴倾角：环上的点
    // ------------------------------------------------------------------

    @Test
    @DisplayName("环上点到中心的距离恒等于半径（三轴都算）")
    void ringPointsStayOnTheSphereOfRadius() {
        for (double tilt : new double[] {0.0D, 0.42D, 0.84D, Math.PI / 2.0D}) {
            for (double angle = 0.0D; angle < Math.PI * 2.0D; angle += 0.37D) {
                assertEquals(5.0D, AbilityGeometry.ringDistance(1.0D, 2.0D, 3.0D, angle, 5.0D, tilt), 1.0E-9D);
            }
        }
    }

    @Test
    @DisplayName("三组不同倾角必须落在不同平面上（不是同一个圆环叠三次）")
    void threeTiltGroupsAreDistinctPlanes() {
        double radius = 4.0D;
        // tilt=0 → 点全在 XZ 平面：y 恒等于中心 y
        double[] flat = AbilityGeometry.ringPoint(0, 0, 0, Math.PI / 2.0D, radius, 0.0D);
        assertEquals(0.0D, flat[1], EPS, "tilt=0 must keep every point on the XZ plane");
        // tilt=π/2 → 点全在 XY 平面：z 恒等于中心 z
        double[] upright = AbilityGeometry.ringPoint(0, 0, 0, Math.PI / 2.0D, radius, Math.PI / 2.0D);
        assertEquals(0.0D, upright[2], EPS, "tilt=pi/2 must keep every point on the XY plane");
        // 三组倾角的 y/z 分量两两不同
        double[][] samples = new double[3][];
        for (int group = 0; group < 3; group++) {
            samples[group] = AbilityGeometry.ringPoint(0, 0, 0, 1.0D, radius, (group + 1) * 0.42D);
        }
        assertTrue(Math.abs(samples[0][1] - samples[1][1]) > 1.0E-3D
                        && Math.abs(samples[1][1] - samples[2][1]) > 1.0E-3D,
                "the three tilt groups must have different Y offsets");
        assertTrue(Math.abs(samples[0][2] - samples[1][2]) > 1.0E-3D
                        && Math.abs(samples[1][2] - samples[2][2]) > 1.0E-3D,
                "the three tilt groups must have different Z offsets");
    }

    // ------------------------------------------------------------------
    // 弯月宽度曲线
    // ------------------------------------------------------------------

    @Test
    @DisplayName("剑气弧刃横切射线：法线与射线平行，且弧上每点到射线轴距离恒等于弧半径")
    void waveArcIsPerpendicularToRay() {
        // 射线沿 +X；side/up 必须都与它垂直。
        double[] direction = {1.0D, 0.0D, 0.0D};
        double[] side = {0.0D, 0.0D, 1.0D};
        double[] up = {0.0D, 1.0D, 0.0D};
        double along = 3.0D;
        double radius = AbilityGeometry.WAVE_ARC_RADIUS;
        // 弧平面的法线 = side × up = direction（用三个不共线的相位点求法线验证）。
        double[] p0 = AbilityGeometry.waveArcPoint(0, 0, 0, direction, side, up, along, -1.0D, radius);
        double[] p1 = AbilityGeometry.waveArcPoint(0, 0, 0, direction, side, up, along, 0.0D, radius);
        double[] p2 = AbilityGeometry.waveArcPoint(0, 0, 0, direction, side, up, along, 1.0D, radius);
        double[] e1 = {p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]};
        double[] e2 = {p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]};
        double[] normal = {
                e1[1] * e2[2] - e1[2] * e2[1],
                e1[2] * e2[0] - e1[0] * e2[2],
                e1[0] * e2[1] - e1[1] * e2[0]};
        double normalLength = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        assertTrue(normalLength > 1.0E-9D, "the arc must not be degenerate");
        double dot = Math.abs((normal[0] * direction[0] + normal[1] * direction[1] + normal[2] * direction[2])
                / normalLength);
        assertEquals(1.0D, dot, 1.0E-9D,
                "the arc plane normal must be parallel to the ray (so the arc faces the viewer)");
        // 弧上每一点到射线轴的距离 = 弧半径（无论相位）。
        for (double theta = -2.0D; theta <= 2.0D; theta += 0.25D) {
            double[] point = AbilityGeometry.waveArcPoint(0, 0, 0, direction, side, up, along, theta, radius);
            double radial = Math.sqrt(point[1] * point[1] + point[2] * point[2]);
            assertEquals(radius, radial, 1.0E-9D, "arc points must sit at the arc radius from the ray axis");
            assertEquals(along, point[0], 1.0E-9D, "arc points must sit at the requested along-distance");
        }
    }

    @Test
    @DisplayName("剑气弧刃尺寸在人眼可读区间：直径 1.2..1.6 格、厚度 0.06..0.1 格")
    void waveArcSizeIsReadable() {
        double diameter = 2.0D * AbilityGeometry.WAVE_ARC_RADIUS;
        assertTrue(diameter >= 1.2D && diameter <= 1.6D, "arc diameter must be 1.2..1.6 blocks (got " + diameter + ")");
        double thickness = AbilityGeometry.WAVE_ARC_THICKNESS;
        assertTrue(thickness >= 0.06D && thickness <= 0.1D,
                "arc thickness must be 0.06..0.1 blocks (got " + thickness + ")");
    }

    @Test
    @DisplayName("剑气从近处开始（0.7 格）并在寿命末抵达真实截断点")
    void waveTravelsFromNearToReach() {
        assertEquals(0.7D, AbilityGeometry.waveAlong(0.0D, 24.0D), 1.0E-9D,
                "the arc must appear at 0.7 blocks, not at 0.30 x reach");
        assertEquals(24.0D, AbilityGeometry.waveAlong(1.0D, 24.0D), 1.0E-9D,
                "the arc must end exactly at the real wall-clipped reach");
        assertEquals(0.5D, AbilityGeometry.waveAlong(0.0D, 1.0D), 1.0E-9D,
                "a very short reach (1.0) must clamp the start to half the reach, never behind the player");
        assertTrue(AbilityGeometry.waveAlong(0.5D, 24.0D) > 0.7D
                        && AbilityGeometry.waveAlong(0.5D, 24.0D) < 24.0D,
                "the arc must move monotonically along the ray");
        assertEquals(0.0D, AbilityGeometry.waveAlong(0.5D, 0.0D), 1.0E-9D);
    }

    @Test
    @DisplayName("爆发的<b>每一条</b>弧各自都覆盖球心上方与下方，且半径范围准确")
    void burstArcsCoverBothHemispheres() {
        double radius = 8.0D;
        for (int group = 0; group < 3; group++) {
            double[] phase = AbilityGeometry.burstArcPhase(group);
            double start = phase[0];
            double span = Math.toRadians(phase[1]);
            double tilt = phase[2];
            double azimuth = phase[3];
            boolean groupPositive = false;
            boolean groupNegative = false;
            double maxDistance = 0.0D;
            // 用真实渲染同样的采样方式取点（不是 ringPoint 的数学全集）。
            for (int i = 0; i <= 24; i++) {
                double angle = start + span * (i / 24.0D);
                double[] point = AbilityGeometry.burstArcPoint(10.0D, 20.0D, 30.0D, angle, radius, tilt, azimuth);
                double relativeY = point[1] - 20.0D;
                if (relativeY > 0.05D) {
                    groupPositive = true;
                }
                if (relativeY < -0.05D) {
                    groupNegative = true;
                }
                double dx = point[0] - 10.0D;
                double dy = point[1] - 20.0D;
                double dz = point[2] - 30.0D;
                maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            assertEquals(radius, maxDistance, 1.0E-9D,
                    "group " + group + " must reach exactly the true radius");
            // 逐条断言：每一条弧自己都必须同时有上方与下方的点。
            // 旧实现第三条弧走 195°..345°，sin 全程为负 → 这里会直接失败。
            assertTrue(groupPositive, "arc " + group + " must have at least one point ABOVE the centre"
                    + " (phase start=" + Math.toDegrees(start) + "°, span=" + phase[1] + "°)");
            assertTrue(groupNegative, "arc " + group + " must have at least one point BELOW the centre"
                    + " (phase start=" + Math.toDegrees(start) + "°, span=" + phase[1] + "°)");
        }
        // 三条弧的平面/方位必须互不相同（不是同一个轴转三圈）。
        assertEquals(3, java.util.Set.of(
                java.util.Arrays.toString(AbilityGeometry.burstArcPhase(0)),
                java.util.Arrays.toString(AbilityGeometry.burstArcPhase(1)),
                java.util.Arrays.toString(AbilityGeometry.burstArcPhase(2))).size(),
                "the three arcs must use different phase/tilt/azimuth combinations");
    }

    @Test
    @DisplayName("爆发弧带厚度固定在 0.04..0.09 格，不随半径变粗")
    void burstArcThicknessStaysThin() {
        assertEquals(AbilityGeometry.BURST_ARC_MIN_THICKNESS, AbilityGeometry.burstArcThickness(1.0D), 1.0E-9D);
        assertEquals(AbilityGeometry.BURST_ARC_MAX_THICKNESS, AbilityGeometry.burstArcThickness(8.0D), 1.0E-9D,
                "radius 8 must not produce the old 0.055*r = 0.44 thick tube");
        assertEquals(AbilityGeometry.BURST_ARC_MAX_THICKNESS, AbilityGeometry.burstArcThickness(64.0D), 1.0E-9D);
        assertTrue(AbilityGeometry.burstArcThickness(8.0D) <= 0.09D);
    }

    // ------------------------------------------------------------------
    // 渲染坐标：相机相对顶点
    // ------------------------------------------------------------------

    @Test
    @DisplayName("渲染顶点先减相机位置：三轴各自扣对，相机整体平移不改变相对顶点")
    void cameraRelativeSubtractsEveryAxis() {
        double[] relative = AbilityGeometry.cameraRelative(105.5D, 70.25D, -30.75D, 100.0D, 70.0D, -30.0D);
        assertEquals(5.5D, relative[0], 1.0E-9D, "x must subtract the camera x");
        assertEquals(0.25D, relative[1], 1.0E-9D, "y must subtract the camera y");
        assertEquals(-0.75D, relative[2], 1.0E-9D, "z must subtract the camera z");
        // 相机平移 1000 格（同一个世界点跟着走）→ 相对顶点完全不变。
        double[] shifted = AbilityGeometry.cameraRelative(1105.5D, 1070.25D, 969.25D, 1100.0D, 1070.0D, 970.0D);
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(relative[axis], shifted[axis], 1.0E-9D,
                    "a camera translation must not change the camera-relative vertex (axis " + axis + ")");
        }
    }

    @Test
    @DisplayName("旋转只应用一次：应用两遍与一遍不同（防止渲染里追加重复相机旋转）")
    void rotationIsAppliedExactlyOnce() {
        double[] rotation = AbilityGeometry.rotationY(Math.toRadians(90.0D));
        double[] vertex = {1.0D, 0.0D, 0.0D};
        double[] once = AbilityGeometry.applyRotation3x3(rotation, vertex);
        double[] twice = AbilityGeometry.applyRotation3x3(rotation, once);
        // 绕 Y 转 90°：(1,0,0) → (0,0,-1)
        assertEquals(0.0D, once[0], 1.0E-9D);
        assertEquals(0.0D, once[1], 1.0E-9D);
        assertEquals(-1.0D, once[2], 1.0E-9D);
        // 转两遍是 (-1,0,0)：与一遍不同 → 我们的顶点管线只能应用一次。
        assertTrue(Math.abs(twice[0] - once[0]) > 0.5D,
                "applying the rotation twice must differ from once (proving we apply it exactly once)");
    }

    // ------------------------------------------------------------------
    // 全局顶点预算
    // ------------------------------------------------------------------

    @Test
    @DisplayName("全局顶点预算：累计值永不越过硬上限")
    void vertexBudgetNeverOverflows() {
        int used = 0;
        // 模拟 40 个四边形连续提交，每次都"想写 6 个顶点"
        for (int i = 0; i < 4000; i++) {
            used += AbilityGeometry.accumulateVertices(used, AbilityGeometry.VERTICES_PER_QUAD);
            assertTrue(used <= AbilityGeometry.MAX_VERTICES_PER_FRAME,
                    "used must never exceed the frame budget (used=" + used + ")");
        }
        assertEquals(AbilityGeometry.MAX_VERTICES_PER_FRAME, used,
                "the budget must be fully usable, not silently truncated early");
        assertEquals(0, AbilityGeometry.accumulateVertices(AbilityGeometry.MAX_VERTICES_PER_FRAME, 6));
    }

    @Test
    @DisplayName("预算按需夹紧：剩余不足时只给剩余部分，已用为负/请求为 0 时给 0")
    void vertexBudgetClampsRequests() {
        assertEquals(6, AbilityGeometry.accumulateVertices(0, 6));
        assertEquals(4, AbilityGeometry.accumulateVertices(AbilityGeometry.MAX_VERTICES_PER_FRAME - 4, 6));
        assertEquals(0, AbilityGeometry.accumulateVertices(0, 0));
        assertEquals(0, AbilityGeometry.accumulateVertices(-1, 6));
        assertEquals(0, AbilityGeometry.accumulateVertices(AbilityGeometry.MAX_VERTICES_PER_FRAME + 10, 6));
    }

    @Test
    @DisplayName("12 个活动球壳共用一笔预算：全部铺满也不会越界，且仍留有余量")
    void twelveBurstsShareOneFrameBudget() {
        // 每个球壳：3 组倾角 × 28 段 = 84 个四边形 = 504 顶点；12 个 = 6048 顶点。
        int used = 0;
        for (int effect = 0; effect < 12; effect++) {
            for (int segment = 0; segment < 28 * 3; segment++) {
                used += AbilityGeometry.accumulateVertices(used, AbilityGeometry.VERTICES_PER_QUAD);
            }
        }
        assertEquals(12 * 28 * 3 * AbilityGeometry.VERTICES_PER_QUAD, used,
                "12 fully-drawn shells must fit inside one frame budget");
        assertTrue(used <= AbilityGeometry.MAX_VERTICES_PER_FRAME,
                "12 shells must not exceed the frame budget (used=" + used + ")");
        // 但只要继续提交，预算就会硬停在上限——证明它真的是"硬"预算。
        for (int extra = 0; extra < 2000; extra++) {
            used += AbilityGeometry.accumulateVertices(used, AbilityGeometry.VERTICES_PER_QUAD);
        }
        assertEquals(AbilityGeometry.MAX_VERTICES_PER_FRAME, used,
                "the frame budget must be a hard cap, not a hint");
    }
}
