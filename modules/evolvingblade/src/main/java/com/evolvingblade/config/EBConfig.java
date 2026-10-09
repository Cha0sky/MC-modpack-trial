package com.evolvingblade.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossRules;
import com.evolvingblade.growth.Abilities;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.growth.GrowthThresholds;
import com.evolvingblade.growth.HostileRules;
import com.evolvingblade.util.BoundedLog;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 服务器配置（{@code serverconfig/evolvingblade-server.toml}）。
 *
 * <p>设计：配置文件"可以写错"，但 {@link #settings()} 返回的快照永远自洽。
 * 原始值全部经过 {@link GrowthSettings} 的净化器二次校验；任何非法项回落到默认值并<b>有界地</b>记录问题，
 * 从而保证畸形配置不会让 tick 逻辑崩溃，也不会产生 NaN / 无穷伤害。
 *
 * <p>所有数值都走配置：三种模式的点/Boss 门槛、Boss 点数权重、8 阶段攻击与攻速、
 * 以及全部能力旋钮（回血、剑气、吸血、爆发、斩 Boss 加成）与 Boss/敌对名单。
 *
 * <p>配置事件（{@code ModConfigEvent.Loading/Reloading}）由主类显式注册到 mod 事件总线，
 * 而不是用 {@code @EventBusSubscriber}——后者在 FML 4.0.43 中的 {@code bus()} 已标记为待删除，
 * 显式注册更稳且不产生弃用警告。
 */
public final class EBConfig {

    private static final int MAX_PROBLEM_LOGS = 32;
    private static final BoundedLog PROBLEM_LOG =
            new BoundedLog(MAX_PROBLEM_LOGS, message -> EvolvingBlade.LOGGER.warn("[EvolvingBlade] config: {}", message));

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ------------------------------------------------------------------
    // 成长
    // ------------------------------------------------------------------
    private static final ModConfigSpec.LongValue HOSTILE_POINT_WEIGHT;
    private static final ModConfigSpec.LongValue BOSS_POINT_WEIGHT;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> QUICK_POINTS;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> QUICK_BOSSES;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> STANDARD_POINTS;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> STANDARD_BOSSES;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> LONG_POINTS;
    private static final ModConfigSpec.ConfigValue<List<? extends Long>> LONG_BOSSES;

    // ------------------------------------------------------------------
    // 阶段数值
    // ------------------------------------------------------------------
    private static final ModConfigSpec.ConfigValue<List<? extends Double>> STAGE_ATTACK_DAMAGE;
    private static final ModConfigSpec.ConfigValue<List<? extends Double>> STAGE_ATTACK_SPEED;

    // ------------------------------------------------------------------
    // 能力旋钮（服务器权威：战斗逻辑与界面显示都只读取这里的值）
    // ------------------------------------------------------------------
    private static final ModConfigSpec.DoubleValue KILL_HEAL;
    private static final ModConfigSpec.IntValue WAVE_UNLOCK_STAGE;
    private static final ModConfigSpec.DoubleValue WAVE_RANGE;
    private static final ModConfigSpec.DoubleValue WAVE_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue WAVE_COOLDOWN;
    private static final ModConfigSpec.IntValue WAVE_FINAL_COOLDOWN;
    private static final ModConfigSpec.IntValue WAVE_FINAL_PIERCE;
    private static final ModConfigSpec.IntValue WAVE_LIFETIME;
    private static final ModConfigSpec.IntValue LIFESTEAL_UNLOCK_STAGE;
    private static final ModConfigSpec.DoubleValue LIFESTEAL_FRACTION;
    private static final ModConfigSpec.DoubleValue LIFESTEAL_CAP;
    private static final ModConfigSpec.IntValue LIFESTEAL_HIGH_UNLOCK_STAGE;
    private static final ModConfigSpec.DoubleValue LIFESTEAL_HIGH_FRACTION;
    private static final ModConfigSpec.DoubleValue LIFESTEAL_HIGH_CAP;
    private static final ModConfigSpec.IntValue BURST_UNLOCK_STAGE;
    private static final ModConfigSpec.DoubleValue BURST_RADIUS;
    private static final ModConfigSpec.DoubleValue BURST_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue BURST_COOLDOWN;
    private static final ModConfigSpec.DoubleValue BURST_FINAL_RADIUS;
    private static final ModConfigSpec.IntValue BURST_FINAL_COOLDOWN;
    private static final ModConfigSpec.IntValue BURST_MAX_TARGETS;
    private static final ModConfigSpec.IntValue BOSS_BONUS_UNLOCK_STAGE;
    private static final ModConfigSpec.DoubleValue BOSS_BONUS_FRACTION;
    private static final ModConfigSpec.DoubleValue BOSS_BONUS_CAP;
    private static final ModConfigSpec.DoubleValue BOSS_BONUS_FINAL_FRACTION;
    private static final ModConfigSpec.DoubleValue BOSS_BONUS_FINAL_CAP;
    private static final ModConfigSpec.IntValue BOSS_BONUS_COOLDOWN;
    /** 1.1.0：技能伤害是否继承持有者服务端有效 ATTACK_DAMAGE（默认 true）。 */
    private static final ModConfigSpec.BooleanValue ABILITY_DAMAGE_FROM_ATTACK;

    // ------------------------------------------------------------------
    // Boss / 敌对名单
    // ------------------------------------------------------------------
    private static final ModConfigSpec.BooleanValue BOSS_AUTO_DETECT;
    private static final ModConfigSpec.DoubleValue BOSS_AUTO_DETECT_THRESHOLD;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_INCLUDE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_EXCLUDE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HOSTILE_INCLUDE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HOSTILE_EXCLUDE;

    static {
        Abilities defaults = Abilities.defaults();
        BossRules bossDefaults = BossRules.defaults();

        BUILDER.comment("成长门槛：三种模式只在门槛上不同；阶段要求成长点与 Boss 击杀数同时达标。").push("growth");
        HOSTILE_POINT_WEIGHT = BUILDER
                .comment("普通敌对生物击杀的成长点权重（可改，只影响之后的击杀；已累计的点不会重算）。")
                .defineInRange("hostile_point_weight", 1L, 0L, 1_000_000L);
        BOSS_POINT_WEIGHT = BUILDER
                .comment("Boss 击杀的成长点权重（SPEC 默认 100）。")
                .defineInRange("boss_point_weight", 100L, 0L, 1_000_000L);
        QUICK_POINTS = defineThresholdList("quick_points", GrowthMode.QUICK.defaultPoints(),
                "快速模式：各阶段累计成长点门槛（必须恰好 8 个、非负、单调不减）。");
        QUICK_BOSSES = defineThresholdList("quick_bosses", GrowthMode.QUICK.defaultBosses(),
                "快速模式：各阶段累计 Boss 击杀门槛（必须恰好 8 个、非负、单调不减）。");
        STANDARD_POINTS = defineThresholdList("standard_points", GrowthMode.STANDARD.defaultPoints(),
                "标准模式：各阶段累计成长点门槛。");
        STANDARD_BOSSES = defineThresholdList("standard_bosses", GrowthMode.STANDARD.defaultBosses(),
                "标准模式：各阶段累计 Boss 击杀门槛。");
        LONG_POINTS = defineThresholdList("long_points", GrowthMode.LONG.defaultPoints(),
                "长期模式：各阶段累计成长点门槛。");
        LONG_BOSSES = defineThresholdList("long_bosses", GrowthMode.LONG.defaultBosses(),
                "长期模式：各阶段累计 Boss 击杀门槛。");
        BUILDER.pop();

        BUILDER.comment("阶段数值：8 个阶段的总基础攻击与总攻击速度（已包含玩家自身基础攻击 1.0）。").push("stages");
        STAGE_ATTACK_DAMAGE = BUILDER
                .comment("各阶段总基础攻击 [7, 12, 24, 48, 120, 300, 750, 1500]（必须恰好 8 项）。")
                .defineList("attack_damage", GrowthSettings.defaults().stageAttackDamage(),
                        () -> 1.0D, EBConfig::isPositiveFiniteNumber);
        STAGE_ATTACK_SPEED = BUILDER
                .comment("各阶段总攻击速度（每秒次数）[1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.4]（必须恰好 8 项）。")
                .defineList("attack_speed", GrowthSettings.defaults().stageAttackSpeed(),
                        () -> 1.0D, EBConfig::isPositiveFiniteNumber);
        BUILDER.pop();

        BUILDER.comment("能力旋钮（当前版本只做定义与校验；战斗能力将在后续版本启用）。").push("abilities");
        KILL_HEAL = BUILDER.comment("阶段1+：用本剑完成有效击杀后回复的生命值。")
                .defineInRange("kill_heal", defaults.killHeal(), 0.0D, 1000.0D);
        WAVE_UNLOCK_STAGE = BUILDER.comment("解锁剑气的阶段。")
                .defineInRange("wave_unlock_stage", defaults.waveUnlockStage(), 0, GrowthMode.STAGE_COUNT - 1);
        WAVE_RANGE = BUILDER.comment("剑气射程（方块）。")
                .defineInRange("wave_range", defaults.waveRange(), 1.0D, Abilities.MAX_RANGE);
        WAVE_DAMAGE_MULTIPLIER = BUILDER.comment("剑气伤害 = 该系数 × 阶段总基础攻击。")
                .defineInRange("wave_damage_multiplier", defaults.waveDamageMultiplier(), 0.0D, Abilities.MAX_FRACTION);
        WAVE_COOLDOWN = BUILDER.comment("剑气冷却（tick）。")
                .defineInRange("wave_cooldown_ticks", defaults.waveCooldownTicks(), 0, Abilities.MAX_COOLDOWN_TICKS);
        WAVE_FINAL_COOLDOWN = BUILDER.comment("终阶剑气冷却（tick）。")
                .defineInRange("wave_final_cooldown_ticks", defaults.waveFinalCooldownTicks(), 0, Abilities.MAX_COOLDOWN_TICKS);
        WAVE_FINAL_PIERCE = BUILDER.comment("终阶剑气最多穿透的父实体数。")
                .defineInRange("wave_final_pierce", defaults.waveFinalPierce(), 0, 64);
        WAVE_LIFETIME = BUILDER.comment("剑气射线/弹射物最长存活（tick），保证寿命有限。")
                .defineInRange("wave_lifetime_ticks", defaults.waveLifetimeTicks(), 1, Abilities.MAX_COOLDOWN_TICKS);
        LIFESTEAL_UNLOCK_STAGE = BUILDER.comment("解锁吸血的阶段。")
                .defineInRange("lifesteal_unlock_stage", defaults.lifestealUnlockStage(), 0, GrowthMode.STAGE_COUNT - 1);
        LIFESTEAL_FRACTION = BUILDER.comment("满蓄力主手近战吸血比例（占实际损失生命）。")
                .defineInRange("lifesteal_fraction", defaults.lifestealFraction(), 0.0D, Abilities.MAX_FRACTION);
        LIFESTEAL_CAP = BUILDER.comment("吸血单次上限（生命）。")
                .defineInRange("lifesteal_cap", defaults.lifestealCap(), 0.0D, 100_000.0D);
        LIFESTEAL_HIGH_UNLOCK_STAGE = BUILDER.comment("吸血强化档解锁阶段。")
                .defineInRange("lifesteal_high_unlock_stage", defaults.lifestealHighUnlockStage(), 0, GrowthMode.STAGE_COUNT - 1);
        LIFESTEAL_HIGH_FRACTION = BUILDER.comment("强化后吸血比例。")
                .defineInRange("lifesteal_high_fraction", defaults.lifestealHighFraction(), 0.0D, Abilities.MAX_FRACTION);
        LIFESTEAL_HIGH_CAP = BUILDER.comment("强化后吸血单次上限（生命）。")
                .defineInRange("lifesteal_high_cap", defaults.lifestealHighCap(), 0.0D, 100_000.0D);
        BURST_UNLOCK_STAGE = BUILDER.comment("解锁爆发（Shift+右键）的阶段。")
                .defineInRange("burst_unlock_stage", defaults.burstUnlockStage(), 0, GrowthMode.STAGE_COUNT - 1);
        BURST_RADIUS = BUILDER.comment("爆发半径（方块）。")
                .defineInRange("burst_radius", defaults.burstRadius(), 1.0D, Abilities.MAX_RANGE);
        BURST_DAMAGE_MULTIPLIER = BUILDER.comment("爆发伤害 = 该系数 × 阶段总基础攻击。")
                .defineInRange("burst_damage_multiplier", defaults.burstDamageMultiplier(), 0.0D, Abilities.MAX_FRACTION);
        BURST_COOLDOWN = BUILDER.comment("爆发冷却（tick）。")
                .defineInRange("burst_cooldown_ticks", defaults.burstCooldownTicks(), 0, Abilities.MAX_COOLDOWN_TICKS);
        BURST_FINAL_RADIUS = BUILDER.comment("终阶爆发半径（方块）。")
                .defineInRange("burst_final_radius", defaults.burstFinalRadius(), 1.0D, Abilities.MAX_RANGE);
        BURST_FINAL_COOLDOWN = BUILDER.comment("终阶爆发冷却（tick）。")
                .defineInRange("burst_final_cooldown_ticks", defaults.burstFinalCooldownTicks(), 0, Abilities.MAX_COOLDOWN_TICKS);
        BURST_MAX_TARGETS = BUILDER.comment("爆发最多命中的有效目标数（默认 32）。")
                .defineInRange("burst_max_targets", defaults.burstMaxTargets(), 0, 256);
        BOSS_BONUS_UNLOCK_STAGE = BUILDER.comment("解锁斩 Boss 额外伤害的阶段。")
                .defineInRange("boss_bonus_unlock_stage", defaults.bossBonusUnlockStage(), 0, GrowthMode.STAGE_COUNT - 1);
        BOSS_BONUS_FRACTION = BUILDER.comment("额外伤害 = 该比例 × Boss 最大生命。")
                .defineInRange("boss_bonus_fraction", defaults.bossBonusFraction(), 0.0D, Abilities.MAX_FRACTION);
        BOSS_BONUS_CAP = BUILDER.comment("额外伤害上限。")
                .defineInRange("boss_bonus_cap", defaults.bossBonusCap(), 0.0D, 1_000_000.0D);
        BOSS_BONUS_FINAL_FRACTION = BUILDER.comment("终阶额外伤害比例。")
                .defineInRange("boss_bonus_final_fraction", defaults.bossBonusFinalFraction(), 0.0D, Abilities.MAX_FRACTION);
        BOSS_BONUS_FINAL_CAP = BUILDER.comment("终阶额外伤害上限。")
                .defineInRange("boss_bonus_final_cap", defaults.bossBonusFinalCap(), 0.0D, 1_000_000.0D);
        BOSS_BONUS_COOLDOWN = BUILDER.comment("同一玩家对同一 Boss 的最小间隔（tick，默认 20 = 1 秒）。")
                .defineInRange("boss_bonus_cooldown_ticks", defaults.bossBonusCooldownTicks(), 1, Abilities.MAX_COOLDOWN_TICKS);
        ABILITY_DAMAGE_FROM_ATTACK = BUILDER.comment(
                        "技能伤害是否继承「当前持剑有效攻击」（默认 true，1.1.0 新兼容行为）。",
                        "true  = 技能伤害 = min(系数 × 阶段基础攻击, 系数 × 持有者服务端有效 ATTACK_DAMAGE)。",
                        "        第三方模组对 minecraft:attack_damage 的 flat / 百分比加伤会体现在剑气与爆发上，",
                        "        但不会超过技能系数对应的上限（阶段配置值）。",
                        "false = 回到 1.0.0 的行为：技能伤害只看阶段配置，第三方加伤不影响技能。")
                .define("ability_damage_from_attack", defaults.abilityDamageFromAttack());
        BUILDER.pop();

        BUILDER.comment("Boss 判定。排除列表优先级最高；自动探测绝不只看生命值。").push("boss");
        BOSS_AUTO_DETECT = BUILDER.comment("是否启用自动探测兜底（默认开）。")
                .define("auto_detect", bossDefaults.autoDetect());
        BOSS_AUTO_DETECT_THRESHOLD = BUILDER
                .comment("自动探测要求的最低最大生命值（默认 200）。")
                .defineInRange("auto_detect_health_threshold", bossDefaults.autoDetectHealthThreshold(), 0.0D, 1_000_000.0D);
        BOSS_INCLUDE = BUILDER.comment("强制判定为 Boss 的实体 ID 列表（默认含末影龙与凋灵）。")
                .defineListAllowEmpty("include", List.copyOf(bossDefaults.include()), () -> "minecraft:wither",
                        EBConfig::isEntityId);
        BOSS_EXCLUDE = BUILDER.comment("强制判定为非 Boss 的实体 ID 列表（最高优先级，默认含典狱长）。")
                .defineListAllowEmpty("exclude", List.copyOf(bossDefaults.exclude()), () -> "minecraft:warden",
                        EBConfig::isEntityId);
        BUILDER.pop();

        BUILDER.comment("普通敌对生物判定。黑名单优先于白名单与标签。").push("hostile");
        HOSTILE_INCLUDE = BUILDER.comment("额外视为普通敌对的实体 ID 列表。")
                .defineListAllowEmpty("include", List.of(), () -> "minecraft:zombie", EBConfig::isEntityId);
        HOSTILE_EXCLUDE = BUILDER.comment("永不视为普通敌对的实体 ID 列表。")
                .defineListAllowEmpty("exclude", List.of(), () -> "minecraft:pig", EBConfig::isEntityId);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** 当前生效的、已校验的配置快照。配置尚未加载时返回出厂默认值。 */
    private static volatile GrowthSettings cached = GrowthSettings.defaults();

    private EBConfig() {}

    /** 服务器逻辑读取的唯一入口。 */
    public static GrowthSettings settings() {
        return cached;
    }

    /**
     * <b>仅测试用</b>：临时替换阶段攻击/攻速曲线，用于验证"服务器把 stage0 从 7 改成 12"
     * 这类配置改动是否真的让八阶段全部生效。
     *
     * <p>调用方<b>必须</b>在 {@code finally} 里调用 {@link #restoreAfterTesting(GrowthSettings)}，
     * 否则会污染并行运行的其它 GameTest。这里只改内存快照，不写配置文件。
     */
    public static void overrideForTesting(GrowthSettings base, List<Double> stageAttackDamage,
            List<Double> stageAttackSpeed) {
        cached = new GrowthSettings(base.hostilePointWeight(), base.bossPointWeight(),
                base.thresholdsByMode(), stageAttackDamage, stageAttackSpeed, base.abilities(),
                base.bossRules(), base.hostileRules());
    }

    /** <b>仅测试用</b>：恢复测试前的配置快照。 */
    public static void restoreAfterTesting(GrowthSettings original) {
        cached = original == null ? GrowthSettings.defaults() : original;
    }

    /** 配置加载完成后重建快照（由主类显式注册到 mod 事件总线，不使用注解扫描）。 */
    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refresh();
        }
    }

    /** 配置重载后重建快照。 */
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refresh();
        }
    }

    /**
     * 从原始配置构建并缓存快照。包可见，方便未来做配置重载测试。
     */
    static synchronized void refresh() {
        List<String> problems = new ArrayList<>();
        GrowthSettings defaults = GrowthSettings.defaults();

        long hostileWeight = GrowthSettings.sanitizeWeight("growth.hostile_point_weight",
                HOSTILE_POINT_WEIGHT.get(), defaults.hostilePointWeight(), 1_000_000L, problems);
        long bossWeight = GrowthSettings.sanitizeWeight("growth.boss_point_weight",
                BOSS_POINT_WEIGHT.get(), defaults.bossPointWeight(), 1_000_000L, problems);

        Map<GrowthMode, GrowthThresholds> thresholds = new EnumMap<>(GrowthMode.class);
        for (GrowthMode mode : GrowthMode.values()) {
            GrowthThresholds fallback = mode.defaultThresholds();
            List<Long> points = sanitizeLongList(mode.id() + "_points", rawPoints(mode), fallback.points(), problems);
            List<Long> bosses = sanitizeLongList(mode.id() + "_bosses", rawBosses(mode), fallback.bosses(), problems);
            thresholds.put(mode, GrowthThresholds.sanitized(new GrowthThresholds(points, bosses), fallback));
        }

        List<Double> attackDamage = GrowthSettings.sanitizeStageValues("stages.attack_damage",
                toDoubleList(STAGE_ATTACK_DAMAGE.get()), defaults.stageAttackDamage(), true, problems);
        List<Double> attackSpeed = GrowthSettings.sanitizeStageValues("stages.attack_speed",
                toDoubleList(STAGE_ATTACK_SPEED.get()), defaults.stageAttackSpeed(), true, problems);

        Abilities abilities = readAbilities(problems);

        BossRules bossRules = new BossRules(booleanOr(BOSS_AUTO_DETECT, defaults.bossRules().autoDetect()),
                finiteOr(BOSS_AUTO_DETECT_THRESHOLD, defaults.bossRules().autoDetectHealthThreshold()),
                entityIdSet(BOSS_INCLUDE), entityIdSet(BOSS_EXCLUDE));
        HostileRules hostileRules = new HostileRules(entityIdSet(HOSTILE_INCLUDE), entityIdSet(HOSTILE_EXCLUDE));

        GrowthSettings candidate = new GrowthSettings(hostileWeight, bossWeight, thresholds,
                attackDamage, attackSpeed, abilities, bossRules, hostileRules);
        List<String> validation = candidate.validate();
        for (String problem : validation) {
            problems.add("post-validation: " + problem);
        }
        if (!validation.isEmpty()) {
            // 走到这里说明净化逻辑有缺口：退回默认快照，绝不让非法值进入游戏逻辑。
            cached = defaults;
        } else {
            cached = candidate;
        }
        for (String problem : problems) {
            PROBLEM_LOG.log(problem);
        }
        if (!problems.isEmpty()) {
            EvolvingBlade.LOGGER.warn("[EvolvingBlade] 服务器配置有 {} 项被修正为安全值（最多显示 {} 条）。",
                    problems.size(), MAX_PROBLEM_LOGS);
        }
        EvolvingBlade.LOGGER.info("[EvolvingBlade] 配置已加载：hostile_weight={}, boss_weight={}, 自动探测={}, 阈值={}",
                cached.hostilePointWeight(), cached.bossPointWeight(), cached.bossRules().autoDetect(),
                cached.bossRules().autoDetectHealthThreshold());
    }

    private static Abilities readAbilities(List<String> problems) {
        Abilities d = Abilities.defaults();
        return new Abilities(
                GrowthSettings.sanitizeDouble("abilities.kill_heal", KILL_HEAL.get(), 0.0D, 1000.0D, d.killHeal(), problems),
                GrowthSettings.sanitizeInt("abilities.wave_unlock_stage", WAVE_UNLOCK_STAGE.get(), 0, 7, d.waveUnlockStage(), problems),
                GrowthSettings.sanitizeDouble("abilities.wave_range", WAVE_RANGE.get(), 1.0D, Abilities.MAX_RANGE, d.waveRange(), problems),
                GrowthSettings.sanitizeDouble("abilities.wave_damage_multiplier", WAVE_DAMAGE_MULTIPLIER.get(), 0.0D, Abilities.MAX_FRACTION, d.waveDamageMultiplier(), problems),
                GrowthSettings.sanitizeInt("abilities.wave_cooldown_ticks", WAVE_COOLDOWN.get(), 0, Abilities.MAX_COOLDOWN_TICKS, d.waveCooldownTicks(), problems),
                GrowthSettings.sanitizeInt("abilities.wave_final_cooldown_ticks", WAVE_FINAL_COOLDOWN.get(), 0, Abilities.MAX_COOLDOWN_TICKS, d.waveFinalCooldownTicks(), problems),
                GrowthSettings.sanitizeInt("abilities.wave_final_pierce", WAVE_FINAL_PIERCE.get(), 0, 64, d.waveFinalPierce(), problems),
                GrowthSettings.sanitizeInt("abilities.wave_lifetime_ticks", WAVE_LIFETIME.get(), 1, Abilities.MAX_COOLDOWN_TICKS, d.waveLifetimeTicks(), problems),
                GrowthSettings.sanitizeInt("abilities.lifesteal_unlock_stage", LIFESTEAL_UNLOCK_STAGE.get(), 0, 7, d.lifestealUnlockStage(), problems),
                GrowthSettings.sanitizeDouble("abilities.lifesteal_fraction", LIFESTEAL_FRACTION.get(), 0.0D, Abilities.MAX_FRACTION, d.lifestealFraction(), problems),
                GrowthSettings.sanitizeDouble("abilities.lifesteal_cap", LIFESTEAL_CAP.get(), 0.0D, 100_000.0D, d.lifestealCap(), problems),
                GrowthSettings.sanitizeInt("abilities.lifesteal_high_unlock_stage", LIFESTEAL_HIGH_UNLOCK_STAGE.get(), 0, 7, d.lifestealHighUnlockStage(), problems),
                GrowthSettings.sanitizeDouble("abilities.lifesteal_high_fraction", LIFESTEAL_HIGH_FRACTION.get(), 0.0D, Abilities.MAX_FRACTION, d.lifestealHighFraction(), problems),
                GrowthSettings.sanitizeDouble("abilities.lifesteal_high_cap", LIFESTEAL_HIGH_CAP.get(), 0.0D, 100_000.0D, d.lifestealHighCap(), problems),
                GrowthSettings.sanitizeInt("abilities.burst_unlock_stage", BURST_UNLOCK_STAGE.get(), 0, 7, d.burstUnlockStage(), problems),
                GrowthSettings.sanitizeDouble("abilities.burst_radius", BURST_RADIUS.get(), 1.0D, Abilities.MAX_RANGE, d.burstRadius(), problems),
                GrowthSettings.sanitizeDouble("abilities.burst_damage_multiplier", BURST_DAMAGE_MULTIPLIER.get(), 0.0D, Abilities.MAX_FRACTION, d.burstDamageMultiplier(), problems),
                GrowthSettings.sanitizeInt("abilities.burst_cooldown_ticks", BURST_COOLDOWN.get(), 0, Abilities.MAX_COOLDOWN_TICKS, d.burstCooldownTicks(), problems),
                GrowthSettings.sanitizeDouble("abilities.burst_final_radius", BURST_FINAL_RADIUS.get(), 1.0D, Abilities.MAX_RANGE, d.burstFinalRadius(), problems),
                GrowthSettings.sanitizeInt("abilities.burst_final_cooldown_ticks", BURST_FINAL_COOLDOWN.get(), 0, Abilities.MAX_COOLDOWN_TICKS, d.burstFinalCooldownTicks(), problems),
                GrowthSettings.sanitizeInt("abilities.burst_max_targets", BURST_MAX_TARGETS.get(), 0, 256, d.burstMaxTargets(), problems),
                GrowthSettings.sanitizeInt("abilities.boss_bonus_unlock_stage", BOSS_BONUS_UNLOCK_STAGE.get(), 0, 7, d.bossBonusUnlockStage(), problems),
                GrowthSettings.sanitizeDouble("abilities.boss_bonus_fraction", BOSS_BONUS_FRACTION.get(), 0.0D, Abilities.MAX_FRACTION, d.bossBonusFraction(), problems),
                GrowthSettings.sanitizeDouble("abilities.boss_bonus_cap", BOSS_BONUS_CAP.get(), 0.0D, 1_000_000.0D, d.bossBonusCap(), problems),
                GrowthSettings.sanitizeDouble("abilities.boss_bonus_final_fraction", BOSS_BONUS_FINAL_FRACTION.get(), 0.0D, Abilities.MAX_FRACTION, d.bossBonusFinalFraction(), problems),
                GrowthSettings.sanitizeDouble("abilities.boss_bonus_final_cap", BOSS_BONUS_FINAL_CAP.get(), 0.0D, 1_000_000.0D, d.bossBonusFinalCap(), problems),
                GrowthSettings.sanitizeInt("abilities.boss_bonus_cooldown_ticks", BOSS_BONUS_COOLDOWN.get(), 1, Abilities.MAX_COOLDOWN_TICKS, d.bossBonusCooldownTicks(), problems),
                ABILITY_DAMAGE_FROM_ATTACK.get() == null
                        ? d.abilityDamageFromAttack()
                        : ABILITY_DAMAGE_FROM_ATTACK.get().booleanValue());
    }

    private static ModConfigSpec.ConfigValue<List<? extends Long>> defineThresholdList(String path, List<Long> defaults, String comment) {
        // 注意：1.21.1 的 ModConfigSpec 只在 List<String> 路径重载上提供 sizeRange，
        // 因此这里不设 sizeRange，长度必须为 8 的约束由 sanitizeLongList 强制（非法即回落默认值）。
        return BUILDER.comment(comment)
                .defineList(path, defaults, () -> 0L, EBConfig::isNonNegativeNumber);
    }

    private static List<? extends Long> rawPoints(GrowthMode mode) {
        return switch (mode) {
            case QUICK -> QUICK_POINTS.get();
            case STANDARD -> STANDARD_POINTS.get();
            case LONG -> LONG_POINTS.get();
        };
    }

    private static List<? extends Long> rawBosses(GrowthMode mode) {
        return switch (mode) {
            case QUICK -> QUICK_BOSSES.get();
            case STANDARD -> STANDARD_BOSSES.get();
            case LONG -> LONG_BOSSES.get();
        };
    }

    private static List<Long> sanitizeLongList(String name, List<?> raw, List<Long> fallback, List<String> problems) {
        if (raw == null || raw.size() != GrowthMode.STAGE_COUNT) {
            problems.add(name + " 必须恰好有 " + GrowthMode.STAGE_COUNT + " 项，已回落到默认值");
            return fallback;
        }
        List<Long> values = new ArrayList<>(raw.size());
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (!(element instanceof Number number)) {
                problems.add(name + "[" + i + "] 不是数字，已回落到默认值");
                return fallback;
            }
            long value = number.longValue();
            if (value < 0L || value < previous) {
                problems.add(name + "[" + i + "] 必须非负且单调不减，已回落到默认值");
                return fallback;
            }
            previous = value;
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static List<Double> toDoubleList(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<Double> values = new ArrayList<>(raw.size());
        for (Object element : raw) {
            values.add(element instanceof Number number ? number.doubleValue() : Double.NaN);
        }
        return List.copyOf(values);
    }

    private static Set<String> entityIdSet(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        Set<String> ids = new LinkedHashSet<>();
        List<? extends String> raw = value.get();
        if (raw == null) {
            return Set.of();
        }
        for (String element : raw) {
            if (element != null) {
                ids.add(element.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(ids);
    }

    private static boolean booleanOr(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double finiteOr(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            double raw = value.get();
            return Double.isFinite(raw) && raw >= 0.0D ? raw : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static boolean isNonNegativeNumber(Object element) {
        return element instanceof Number number && number.doubleValue() >= 0.0D
                && number.doubleValue() == Math.floor(number.doubleValue());
    }

    private static boolean isPositiveFiniteNumber(Object element) {
        return element instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() > 0.0D;
    }

    private static boolean isEntityId(Object element) {
        return element instanceof String string && ResourceLocation.tryParse(string) != null;
    }
}
