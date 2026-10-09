package com.evolvingblade.boss;

/**
 * 一次 Boss 判定的结果：命中的实体 ID、结论、获胜理由，以及用于诊断的补充信息。
 * 纯 Java（零 Minecraft 依赖），可单元测试。
 */
public record BossClassification(String entityId, boolean boss, BossReason reason, String detail) {

    public static BossClassification of(String entityId, boolean boss, BossReason reason, String detail) {
        return new BossClassification(entityId == null ? "unknown" : entityId, boss, reason,
                detail == null ? "" : detail);
    }

    /** 供 {@code bosscheck} 回显的一行中文说明。 */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.boss ? "Boss" : "非 Boss")
                .append(" | 实体=").append(this.entityId)
                .append(" | 理由=").append(this.reason.chinese())
                .append(" (").append(this.reason.english()).append(')');
        if (!this.detail.isEmpty()) {
            builder.append(" | 细节=").append(this.detail);
        }
        return builder.toString();
    }
}
