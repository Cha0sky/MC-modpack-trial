package com.evolvingblade.boss;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Boss 判定规则（全部来自服务器配置）。纯 Java，可单元测试。
 *
 * <p>优先级（SPEC）：排除列表 &gt; 包含列表 &gt; {@code evolvingblade:bosses} 与 {@code c:bosses} 标签 &gt; 自动探测兜底。
 *
 * @param autoDetect               是否启用自动探测兜底
 * @param autoDetectHealthThreshold 自动探测的最低最大生命值（含）
 * @param include                  强制判定为 Boss 的实体 ID
 * @param exclude                  强制判定为非 Boss 的实体 ID（最高优先级）
 */
public record BossRules(boolean autoDetect, double autoDetectHealthThreshold, Set<String> include, Set<String> exclude) {

    /** 规范 ID 集合是大小写敏感的（命名空间与路径都应为小写）。 */
    public BossRules {
        include = Set.copyOf(new LinkedHashSet<>(include));
        exclude = Set.copyOf(new LinkedHashSet<>(exclude));
        if (!Double.isFinite(autoDetectHealthThreshold) || autoDetectHealthThreshold < 0.0D) {
            autoDetectHealthThreshold = DEFAULT_HEALTH_THRESHOLD;
        }
    }

    public static final double DEFAULT_HEALTH_THRESHOLD = 200.0D;

    public static BossRules defaults() {
        return new BossRules(
                true,
                DEFAULT_HEALTH_THRESHOLD,
                Set.of("minecraft:ender_dragon", "minecraft:wither"),
                Set.of("minecraft:warden"));
    }

    public boolean isIncluded(String entityId) {
        return this.include.contains(entityId);
    }

    public boolean isExcluded(String entityId) {
        return this.exclude.contains(entityId);
    }

    /** 规则本身的自洽性检查；空列表表示合法。 */
    public List<String> validate() {
        if (!Double.isFinite(this.autoDetectHealthThreshold) || this.autoDetectHealthThreshold < 0.0D) {
            return List.of("auto_detect_health_threshold must be finite and >= 0");
        }
        return List.of();
    }
}
