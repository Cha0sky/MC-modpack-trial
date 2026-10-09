package com.evolvingblade.growth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 普通敌对生物判定规则（服务器配置）。纯 Java，可单元测试。
 *
 * <p>基础判定：实体实现 {@code net.minecraft.world.entity.monster.Enemy}
 * 或所属 {@code MobCategory} 为 {@code MONSTER}，或命中 {@code evolvingblade:hostile} 标签。
 * 白名单可强制纳入，黑名单优先级最高。
 */
public record HostileRules(Set<String> include, Set<String> exclude) {

    public HostileRules {
        include = Set.copyOf(new LinkedHashSet<>(include));
        exclude = Set.copyOf(new LinkedHashSet<>(exclude));
    }

    public static HostileRules defaults() {
        return new HostileRules(Set.of(), Set.of());
    }

    public boolean isIncluded(String entityId) {
        return this.include.contains(entityId);
    }

    public boolean isExcluded(String entityId) {
        return this.exclude.contains(entityId);
    }

    public List<String> validate() {
        return List.of();
    }
}
