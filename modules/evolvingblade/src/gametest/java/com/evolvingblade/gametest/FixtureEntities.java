package com.evolvingblade.gametest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.Level;

/**
 * <b>仅开发/GameTest 使用</b>的自定义实体类（位于 gametest 源集，绝不进入发布 jar）。
 *
 * <p>它们都以<b>已注册的原版类型</b> {@code minecraft:husk} 构造（{@code getType()} 合法、可正常生成/存读档），
 * 只是 Java 类不同，用来覆盖 Boss 分类的各个分支：自身 bar 字段、继承的 bar 字段、类名包含 Boss、
 * 无 bar 的高生命（必须仍是普通）。
 */
public final class FixtureEntities {

    private FixtureEntities() {}

    /** 以原版 HUSK 类型构造并加入世界（真实实体，不是伪造对象）。 */
    @SuppressWarnings("unchecked")
    public static <T extends Husk> T spawn(Level level, double x, double y, double z,
            java.util.function.BiFunction<EntityType<? extends Husk>, Level, T> factory) {
        T entity = factory.apply(EntityType.HUSK, level);
        entity.moveTo(x, y, z, 0.0F, 0.0F);
        entity.setNoAi(true);
        entity.setRemainingFireTicks(0);
        level.addFreshEntity(entity);
        return entity;
    }

    /** 以已注册类型生成任意 NoAI 生物（用于狼等非 Husk 夹具）。 */
    public static <T extends net.minecraft.world.entity.Mob> T spawnMob(Level level, double x, double y, double z,
            EntityType<T> type) {
        T entity = type.create(level);
        if (entity == null) {
            throw new IllegalStateException("could not create fixture entity " + type);
        }
        entity.moveTo(x, y, z, 0.0F, 0.0F);
        entity.setNoAi(true);
        entity.setRemainingFireTicks(0);
        level.addFreshEntity(entity);
        return entity;
    }

    /** 自身持有非空 ServerBossEvent 字段。 */
    public static class FixtureBossHusk extends Husk {
        private final ServerBossEvent fixtureBar = new ServerBossEvent(Component.literal("fixture"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

        public FixtureBossHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** bar 字段在父类里（继承字段也必须被识别）。 */
    public static class FixtureInheritedBossHusk extends FixtureBossHusk {
        public FixtureInheritedBossHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** 只有非空 bar，类名不含 Boss。 */
    public static class FixtureBarHusk extends Husk {
        private final ServerBossEvent fixtureBar = new ServerBossEvent(Component.literal("fixture"),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

        public FixtureBarHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** 没有 bar，但类名包含 Boss。 */
    public static class FixtureClassNameBossHusk extends Husk {
        public FixtureClassNameBossHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** 没有 bar、类名不含 Boss。 */
    public static class FixturePlainHusk extends Husk {
        public FixturePlainHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** bar 字段存在但为 <b>null</b>，类名不含 Boss → 必须仍是普通。 */
    public static class FixtureNullBarHusk extends Husk {
        @SuppressWarnings("unused")
        private final ServerBossEvent nullBar = null;

        public FixtureNullBarHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** bar 为 null，但类名包含 Boss → 仍按类名规则判为 Boss。 */
    public static class FixtureNullBarClassNameBossHusk extends Husk {
        @SuppressWarnings("unused")
        private final ServerBossEvent nullBar = null;

        public FixtureNullBarClassNameBossHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** 字段类型是 {@code ServerBossEvent} 的<b>子类</b>：反射探测必须也能识别。 */
    public static class FixtureSubtypeBarHusk extends Husk {
        /** ServerBossEvent 的子类。 */
        public static final class CustomBossBar extends ServerBossEvent {
            public CustomBossBar() {
                super(Component.literal("fixture-subtype"), BossEvent.BossBarColor.GREEN,
                        BossEvent.BossBarOverlay.NOTCHED_10);
            }
        }

        @SuppressWarnings("unused")
        private final CustomBossBar subtypeBar = new CustomBossBar();

        public FixtureSubtypeBarHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /** 类名包含 Boss、没有 bar，且类名来自<b>继承</b>链。 */
    public static class FixtureInheritedClassNameBossHusk extends FixtureClassNameBossHusk {
        public FixtureInheritedClassNameBossHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }
    }

    /**
     * <b>测试用"Boss 狼"</b>：狼本身不是敌对生物，因此"宠物被排除"的断言在普通狼身上毫无意义
     * （即使删掉宠物排除也不会命中）。这个 dev-only 子类持有非空 {@code ServerBossEvent} 字段，
     * 因而**本来就是一个合法技能目标**；于是"驯服/他人宠物/无主但已驯服"能否被排除才真正可验证。
     * 它不进入发布 jar，生产代码里也没有任何对它的硬引用。
     */
    public static class FixtureBossWolf extends Wolf {
        private final ServerBossEvent fixtureBar = new ServerBossEvent(Component.literal("fixture-wolf"),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

        public FixtureBossWolf(EntityType<? extends Wolf> type, Level level) {
            super(type, level);
        }
    }

    /** 以原版 WOLF 类型生成测试用 Boss 狼。 */
    public static FixtureBossWolf spawnBossWolf(Level level, double x, double y, double z) {
        FixtureBossWolf wolf = new FixtureBossWolf(EntityType.WOLF, level);
        wolf.moveTo(x, y, z, 0.0F, 0.0F);
        wolf.setNoAi(true);
        wolf.setRemainingFireTicks(0);
        level.addFreshEntity(wolf);
        return wolf;
    }

    /**
     * <b>超大生命</b>的 dev-only 夹具：模拟"模组给生物加了极大最大生命"的情况。
     *
     * <p>做法严格按 Codex 核实的映射源码：{@code LivingEntity.getMaxHealth()} 是 final，但它委托给
     * {@code getAttributeValue(Holder<Attribute>)}（**不是** final）。本类只在该属性是
     * {@code MAX_HEALTH} 时返回夹具配置值，其余一律走 super。因此：
     * 没有改动全局 RangedAttribute 上限、没有覆写 hurt/setHealth/死亡流程、没有伪造掉血；
     * {@code getMaxHealth()} 与 {@code setHealth(...)} 的钳制都按原版语义使用这个"夹具最大生命"。
     *
     * <p><b>诚实边界</b>：这只验证"本模组在超大最大生命下的加成与上限计算"，
     * 不代表与某个真实的第三方 Boss 模组一起做过集成验证。
     */
    public static class LargeHealthFixtureHusk extends FixtureBossHusk {
        /** 夹具最大生命（可在测试里调整）。 */
        private double fixtureMaxHealth = 100_000.0D;

        public LargeHealthFixtureHusk(EntityType<? extends Husk> type, Level level) {
            super(type, level);
        }

        public LargeHealthFixtureHusk withFixtureMaxHealth(double maxHealth) {
            this.fixtureMaxHealth = maxHealth;
            return this;
        }

        @Override
        public double getAttributeValue(
                net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
            if (attribute.value() == net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH.value()) {
                return this.fixtureMaxHealth;
            }
            return super.getAttributeValue(attribute);
        }
    }
}
