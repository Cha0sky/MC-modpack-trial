package com.evolvingblade.boss;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.growth.HostileRules;
import com.evolvingblade.registry.EBTags;
import com.evolvingblade.util.BoundedLog;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;

/**
 * 服务器端的 Boss / 敌对判定入口：把 Minecraft 世界里的实体"翻译"成
 * {@link BossClassifierCore} 需要的纯事实，然后交给纯函数内核裁决。
 *
 * <p>本类<b>没有</b>任何对第三方 Boss 类的硬引用：只依赖原版 {@link ServerBossEvent}
 * 与类名/标签，因此不会因为某个模组缺失而在类加载期崩溃。
 */
public final class BossClassifier {

    /** 反射诊断日志上限：失败信息最多 16 条，之后静默计数，避免刷屏。 */
    private static final BoundedLog REFLECTION_LOG =
            new BoundedLog(16, message -> EvolvingBlade.LOGGER.warn("[EvolvingBlade] boss probe: {}", message));

    private BossClassifier() {}

    /** 实体注册 ID，例如 {@code minecraft:wither}。 */
    public static String entityId(Entity entity) {
        if (entity == null) {
            return "unknown";
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? "unknown" : key.toString();
    }

    /** 实体是否命中 Boss 标签（含社区 {@code c:bosses}）。 */
    public static boolean inBossTag(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return entity.getType().is(EBTags.BOSSES) || entity.getType().is(EBTags.C_BOSSES);
        } catch (Throwable throwable) {
            // 标签未绑定（例如注册表尚未冻结）时静默降级为"不在标签里"。
            return false;
        }
    }

    /** 实体是否命中本模组的普通敌对标签。 */
    public static boolean inHostileTag(Entity entity) {
        if (entity == null) {
            return false;
        }
        try {
            return entity.getType().is(EBTags.HOSTILE);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * 对实体做完整 Boss 判定，返回结论与获胜理由。
     */
    public static BossClassification classify(Entity entity, BossRules rules) {
        if (entity == null) {
            return BossClassification.of("unknown", false, BossReason.NOT_LIVING, "");
        }
        String id = entityId(entity);
        if (!(entity instanceof LivingEntity living)) {
            return BossClassifierCore.decide(id, false, false, 0.0D, false, false, rules);
        }
        double maxHealth = living.getMaxHealth();
        boolean bossEventField = BossEventProbe.hasNonNullFieldOfType(
                living, ServerBossEvent.class, BossEventProbe.DEFAULT_MAX_DEPTH, REFLECTION_LOG);
        boolean classNameHasBoss = BossEventProbe.classChainNameContains(
                living.getClass(), "boss", BossEventProbe.DEFAULT_MAX_DEPTH);
        return BossClassifierCore.decide(id, true, inBossTag(living), maxHealth,
                bossEventField, classNameHasBoss, rules);
    }

    /**
     * 普通敌对判定：黑名单优先，其次白名单、本模组标签，
     * 最后是原版分类（{@link Enemy} 或 {@link MobCategory#MONSTER}）。
     *
     * <p>Boss 判定与敌对判定相互独立：Boss 不要求属于敌对分类。
     */
    public static boolean isHostile(LivingEntity entity, HostileRules rules) {
        if (entity == null) {
            return false;
        }
        String id = entityId(entity);
        if (rules.isExcluded(id)) {
            return false;
        }
        if (rules.isIncluded(id)) {
            return true;
        }
        if (inHostileTag(entity)) {
            return true;
        }
        if (entity instanceof Enemy) {
            return true;
        }
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }
}
