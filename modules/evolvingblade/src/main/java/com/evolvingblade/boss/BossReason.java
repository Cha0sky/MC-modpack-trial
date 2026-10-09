package com.evolvingblade.boss;

/**
 * Boss 判定的获胜理由。管理员 {@code bosscheck} 会把它翻译成中文回显。
 */
public enum BossReason {
    EXCLUDED_BY_CONFIG("配置排除列表", "excluded by config exclude list"),
    INCLUDED_BY_CONFIG("配置包含列表", "included by config include list"),
    BOSS_TAG("Boss 标签", "matched evolvingblade:bosses or c:bosses tag"),
    SERVER_BOSS_EVENT_FIELD("实体持有 ServerBossEvent 字段", "entity field holds a non-null ServerBossEvent"),
    CLASS_NAME_CONTAINS_BOSS("继承链类名包含 Boss", "a class simple name in the inheritance chain contains 'Boss'"),
    AUTO_DETECT_DISABLED("自动探测已关闭", "auto detection disabled and no list/tag matched"),
    HEALTH_BELOW_THRESHOLD("最大生命低于阈值", "max health below auto detect threshold"),
    NOT_LIVING("不是生物实体", "not a living entity"),
    NO_RULE_MATCHED("无规则命中", "no rule matched");

    private final String chinese;
    private final String english;

    BossReason(String chinese, String english) {
        this.chinese = chinese;
        this.english = english;
    }

    public String chinese() {
        return this.chinese;
    }

    public String english() {
        return this.english;
    }

    /** 语言文件键：{@code commands.evolvingblade.boss_reason.<id>}。 */
    public String translationKey() {
        return "commands.evolvingblade.boss_reason." + this.name().toLowerCase(java.util.Locale.ROOT);
    }
}
