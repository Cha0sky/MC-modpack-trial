package com.evolvingblade.growth;

import java.util.ArrayList;
import java.util.List;

/**
 * 一种成长模式的累计门槛：成长点与 Boss 击杀数，两者必须<b>同时</b>满足才能到达某阶段。
 *
 * <p>纯 Java（零 Minecraft 依赖），可直接单元测试。
 */
public record GrowthThresholds(List<Long> points, List<Long> bosses) {

    public GrowthThresholds {
        points = List.copyOf(points);
        bosses = List.copyOf(bosses);
    }

    public static GrowthThresholds of(List<Long> points, List<Long> bosses) {
        return new GrowthThresholds(points, bosses);
    }

    public int size() {
        return Math.min(this.points.size(), this.bosses.size());
    }

    /** 第 {@code stage} 阶段的成长点门槛；越界时钳制到合法下标。 */
    public long pointAt(int stage) {
        return this.points.get(clampIndex(stage));
    }

    /** 第 {@code stage} 阶段的 Boss 击杀门槛；越界时钳制到合法下标。 */
    public long bossAt(int stage) {
        return this.bosses.get(clampIndex(stage));
    }

    private int clampIndex(int stage) {
        if (this.points.isEmpty() || this.bosses.isEmpty()) {
            throw new IllegalStateException("Empty growth thresholds");
        }
        return Math.max(0, Math.min(GrowthMode.STAGE_COUNT - 1, Math.min(stage, size() - 1)));
    }

    /**
     * 校验门槛是否可用。
     *
     * <p>规则：两个列表长度都必须恰好为 {@value GrowthMode#STAGE_COUNT}；
     * 所有数值非负；且必须单调不减（门槛不能回落）。
     *
     * @return 问题描述列表；为空表示合法
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (this.points.size() != GrowthMode.STAGE_COUNT) {
            problems.add("points length must be " + GrowthMode.STAGE_COUNT + " but was " + this.points.size());
        }
        if (this.bosses.size() != GrowthMode.STAGE_COUNT) {
            problems.add("bosses length must be " + GrowthMode.STAGE_COUNT + " but was " + this.bosses.size());
        }
        checkSeries("points", this.points, problems);
        checkSeries("bosses", this.bosses, problems);
        return List.copyOf(problems);
    }

    public boolean isValid() {
        return validate().isEmpty();
    }

    /**
     * 若 {@code candidate} 合法则返回它，否则返回 {@code fallback}。
     * 配置校验用它实现"非法设置回落到默认值，而不是让主逻辑崩溃"。
     */
    public static GrowthThresholds sanitized(GrowthThresholds candidate, GrowthThresholds fallback) {
        if (candidate != null && candidate.isValid()) {
            return candidate;
        }
        return fallback;
    }

    private static void checkSeries(String name, List<Long> series, List<String> problems) {
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < series.size(); i++) {
            Long value = series.get(i);
            if (value == null) {
                problems.add(name + "[" + i + "] must not be null");
                continue;
            }
            if (value < 0L) {
                problems.add(name + "[" + i + "] must be >= 0 but was " + value);
            }
            if (value < previous) {
                problems.add(name + "[" + i + "] must be sorted ascending (" + value + " < " + previous + ")");
            }
            previous = value;
        }
    }
}
