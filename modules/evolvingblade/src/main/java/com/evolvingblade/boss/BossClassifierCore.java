package com.evolvingblade.boss;

/**
 * Boss 判定的<b>纯函数内核</b>：不接触任何 Minecraft 类型，全部输入都是已采集好的事实。
 *
 * <p>优先级严格按 SPEC：排除列表 &gt; 包含列表 &gt; Boss 标签 &gt; 自动探测兜底。
 * 自动探测要求"最大生命 ≥ 阈值"<b>并且</b>（实体字段持有非空 ServerBossEvent 或继承链类名含 Boss）；
 * <b>绝不</b>只凭生命值判定——这正是典狱长（Warden，500 血）必须留在普通敌对类别的原因。
 */
public final class BossClassifierCore {

    private BossClassifierCore() {}

    public static BossClassification decide(
            String entityId,
            boolean livingEntity,
            boolean inBossTag,
            double maxHealth,
            boolean hasServerBossEventField,
            boolean classNameContainsBoss,
            BossRules rules) {
        if (!livingEntity) {
            return BossClassification.of(entityId, false, BossReason.NOT_LIVING, "");
        }
        if (rules.isExcluded(entityId)) {
            return BossClassification.of(entityId, false, BossReason.EXCLUDED_BY_CONFIG, "id=" + entityId);
        }
        if (rules.isIncluded(entityId)) {
            return BossClassification.of(entityId, true, BossReason.INCLUDED_BY_CONFIG, "id=" + entityId);
        }
        if (inBossTag) {
            return BossClassification.of(entityId, true, BossReason.BOSS_TAG, "boss tag");
        }
        if (!rules.autoDetect()) {
            return BossClassification.of(entityId, false, BossReason.AUTO_DETECT_DISABLED, "");
        }
        if (!Double.isFinite(maxHealth) || maxHealth < rules.autoDetectHealthThreshold()) {
            return BossClassification.of(entityId, false, BossReason.HEALTH_BELOW_THRESHOLD,
                    "maxHealth=" + maxHealth + " < " + rules.autoDetectHealthThreshold());
        }
        if (hasServerBossEventField) {
            return BossClassification.of(entityId, true, BossReason.SERVER_BOSS_EVENT_FIELD,
                    "maxHealth=" + maxHealth);
        }
        if (classNameContainsBoss) {
            return BossClassification.of(entityId, true, BossReason.CLASS_NAME_CONTAINS_BOSS,
                    "maxHealth=" + maxHealth);
        }
        return BossClassification.of(entityId, false, BossReason.NO_RULE_MATCHED, "maxHealth=" + maxHealth);
    }
}
