package com.evolvingblade.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.registry.EBAttachments;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * 剑的技能实现：剑气（stage2+）、爆发（stage4+）、斩 Boss 加成（stage5+）与击杀回血（stage1+）。
 *
 * <p>权威性要点：
 * <ul>
 *   <li>全部判定在服务器主线程执行；冷却来自玩家 attachment 的<b>绝对到期 tick</b>，
 *       因此换剑/重连/死亡都无法绕过；</li>
 *   <li>剑气用逐段射线做方块碰撞与目标检索，<b>严格不穿墙</b>；</li>
 *   <li>爆发按距离取最近的有限目标（默认 32），并做视线检查，不破坏地形；</li>
 *   <li>斩 Boss 加成使用自定义伤害类型：写进 {@code bypasses_armor} 与 {@code bypasses_cooldown} 标签
 *       （只绕过护甲与受击冷却），<b>不</b>绕过无敌/抗性/模组事件取消，也不直接 setHealth/kill；
 *       也绝不修改实体的 {@code invulnerableTime} 等可观察状态；</li>
 *   <li>技能伤害不触发吸血，也不触发斩 Boss 加成（防递归）。</li>
 * </ul>
 */
public final class StarEaterAbilities {

    /** 剑气伤害类型（数据包定义）。 */
    public static final ResourceKey<DamageType> SWORD_ABILITY = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "sword_ability"));
    /** 斩 Boss 加成伤害类型（在 {@code minecraft:bypasses_armor} 标签里）。 */
    public static final ResourceKey<DamageType> BOSS_SLAYER = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "boss_slayer"));

    /** 剑气采样步长（方块）：越小越精确，0.5 已足够避免穿墙。 */
    private static final double WAVE_STEP = 0.5D;
    /** 剑气命中判定半径（方块）。 */
    private static final double WAVE_HIT_RADIUS = 1.6D;
    /**
     * 爆发的粗筛余量（方块）：包围盒先按"球心 ± (半径 + 余量)"取候选，
     * 保证"包围盒与球相交"的大型实体（原版末影龙级别，宽度可达 16）也被纳入精确判定，
     * 不会在粗筛阶段就被丢掉。精确判定仍是三轴最近点 ≤ r²。
     */
    private static final double BURST_BROAD_PHASE_MARGIN = 16.0D;

    private StarEaterAbilities() {}

    /**
     * 全模组统一时钟：一律使用主世界 {@code gameTime}。
     *
     * <p>各维度有各自的 {@code gameTime}，混用会让"换维度"变相缩短或延长冷却，
     * 也会让清理逻辑与创建逻辑对不上。
     */
    public static long clock(ServerPlayer player) {
        return player.getServer() == null
                ? player.level().getGameTime()
                : player.getServer().overworld().getGameTime();
    }

    private static DamageSource abilitySource(ServerLevel level, ServerPlayer player, ResourceKey<DamageType> key) {
        return level.damageSources().source(key, player, player);
    }

    /**
     * 目标是否可以被技能命中：排除玩家、友军，以及<b>一切被驯服或有主人</b>的生物
     * （包括别人的宠物，即使它们被配置强制标成 Boss）。
     *
     * <p>两道判定都要有：{@link net.minecraft.world.entity.TamableAnimal#isTame()} 覆盖"已驯服但 owner 为空"
     * 的异常状态，owner 非空覆盖"实现了 owner 语义但没走驯服标记"的其它实体。
     */
    public static boolean isValidTarget(ServerPlayer player, LivingEntity target) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (target instanceof Player) {
            return false;
        }
        if (target instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        if (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return false;
        }
        if (player.isAlliedTo(target) || target.isAlliedTo(player)) {
            return false;
        }
        GrowthSettings settings = EBConfig.settings();
        return BossClassifier.isHostile(target, settings.hostileRules())
                || BossClassifier.classify(target, settings.bossRules()).boss();
    }

    private static boolean hasLineOfSight(ServerPlayer player, LivingEntity target) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------
    // 剑气（stage2+，右键）
    // ------------------------------------------------------------------

    /**
     * 释放剑气。
     *
     * <p>伤害口径（1.1.0）：默认按<b>持有者服务端有效 ATTACK_DAMAGE</b>取值，
     * 应用原技能系数（默认 1.0），因此第三方对 {@code minecraft:attack_damage} 的
     * flat / 百分比加伤会体现在剑气上；上限仍是"系数 × 阶段基础攻击"，
     * 第三方倍率只由原版伤害管线在 {@code hurt()} 里执行<b>一次</b>。
     * 配置 {@code abilities.ability_damage_from_attack = false} 可退回 1.0.0 的行为。
     *
     * @return 是否真的释放（冷却中/未解锁/非本剑返回 false）
     */
    /**
     * 施法前的<b>统一准备</b>：确认持剑 → <b>先把服务端属性协调到当前阶段</b> → 校验解锁与冷却。
     *
     * <p>为什么要在这里再协调一次：技能伤害按"服务端有效持剑 ATTACK_DAMAGE"取值，
     * 而该属性的协调可能还没在本 tick 跑过（原版装备 tick 与本模组周期协调都可能在之后）。
     * 少了这一步，技能会读到裸 base（实测 burst 1.5 / wave 1.0）。
     * 协调只写本模组自己的两个 id，不伪造近战事件、不绕过取消/无敌/墙壁/友军、不触发递归。
     *
     * @return 已就绪的施法上下文；不满足条件返回 {@code null}
     */
    private static Cast ready(ServerPlayer player, int unlockStage,
            java.util.function.ToLongFunction<GrowthData> cooldownDeadline) {
        if (!GrowthService.holdsStarEater(player)) {
            return null;
        }
        long now = clock(player);
        GrowthData data = GrowthService.data(player);
        int stage = data.stage(EBConfig.settings());
        // 冷却与解锁先判：被拒绝的施法绝不能改动任何属性状态（含基线补偿）。
        if (!com.evolvingblade.growth.Abilities.unlocked(stage, unlockStage)) {
            return null;
        }
        if (!AbilityState.isReady(now, cooldownDeadline.applyAsLong(data))) {
            return null;
        }
        // 关键：通过全部门槛之后，先按当前阶段与装备状态协调持有者属性，
        // 再读它作为技能伤害来源（否则会读到裸 base：实测 burst 1.5 / wave 1.0）。
        StageAttributes.reconcile(player);
        return new Cast(EBConfig.settings(), data, stage, now, (ServerLevel) player.level());
    }

    /** 施法上下文（ready() 的产物）。 */
    private record Cast(GrowthSettings settings, GrowthData data, int stage, long now, ServerLevel level) {}

    /**
     * 释放剑气。
     *
     * <p>伤害口径（1.1.0）：默认按<b>持有者服务端有效 ATTACK_DAMAGE</b>取值再乘原比例（默认 1.0），
     * 因此第三方对 {@code minecraft:attack_damage} 的 flat / 百分比加伤会体现在剑气上；
     * 比例不是阶段天花板。配置 {@code abilities.ability_damage_from_attack = false}
     * 可退回 1.0.0 的"只看阶段配置"行为。
     *
     * @return 是否真的释放（冷却中/未解锁/非本剑返回 false）
     */
    public static boolean tryWave(ServerPlayer player) {
        Cast cast = ready(player, EBConfig.settings().abilities().waveUnlockStage(),
                GrowthData::waveReadyAt);
        if (cast == null) {
            return false;
        }
        GrowthSettings settings = cast.settings();
        GrowthData data = cast.data();
        int stage = cast.stage();
        ServerLevel level = cast.level();
        long now = cast.now();
        // 占用冷却（持久化在玩家 attachment 上，换剑/重连都无法绕过）。
        GrowthService.set(player, data.withCooldowns(
                AbilityState.readyAt(now, CombatMath.waveCooldownTicks(settings, stage)), data.burstReadyAt()));
        // 立刻下发服务器权威快照：仪表盘会立刻显示真实冷却，而不是等周期性同步。
        GrowthService.sendSnapshot(player);

        double range = settings.abilities().waveRange();
        boolean inherit = settings.abilities().abilityDamageFromAttack();
        double damage = CombatMath.waveDamage(settings, stage, effectiveAttackDamage(player), inherit);
        int maxTargets = CombatMath.waveMaxTargets(settings, stage);
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        DamageSource source = abilitySource(level, player, SWORD_ABILITY);

        // 1) 先求出射线被墙截断的位置：剑气绝不穿墙。
        HitResult obstacle = level.clip(new ClipContext(eye, eye.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = range;
        Vec3 end = eye.add(direction.scale(range));
        if (obstacle.getType() != HitResult.Type.MISS) {
            reach = Math.max(0.0D, obstacle.getLocation().distanceTo(eye));
            end = obstacle.getLocation();
        }
        // 视觉：把"被墙截断后的真实 reach"交给客户端，观察到玩家自己铺刀光。
        AbilityVisuals.wave(level, player, eye, direction, reach, stage);

        // 2) 一次性收集整条（被截断的）射线上的合法候选，按"沿射线的投影距离"稳定排序。
        //    这样近处目标一定先于远处目标命中，且**前向投影 < 0（玩家背后）的目标永远不会被命中**——
        //    它们即使落在某个采样球里也不再抢占命中位。
        AABB sweep = player.getBoundingBox().expandTowards(direction.scale(reach)).inflate(WAVE_HIT_RADIUS + 1.0D);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, sweep,
                entity -> isValidTarget(player, entity));
        record WaveHit(LivingEntity target, double along) {}
        List<WaveHit> ordered = new java.util.ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (LivingEntity target : candidates) {
            if (!seen.add(target.getUUID())) {
                continue;
            }
            Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye);
            double along = toTarget.dot(direction);
            if (along < 0.0D || along > reach) {
                // 背后目标或超出（被墙截断的）射程：绝不命中。
                continue;
            }
            double perpendicular = toTarget.subtract(direction.scale(along)).length();
            // 保留原采样球的"横向容差"语义（含实体自身尺寸），但前向投影必须为正且不超过被墙截断的射程。
            double tolerance = WAVE_HIT_RADIUS
                    + Math.max(target.getBbWidth(), target.getBbHeight()) * 0.5D;
            if (perpendicular > tolerance) {
                continue;
            }
            if (!hasLineOfSight(player, target)) {
                continue;
            }
            ordered.add(new WaveHit(target, along));
        }
        ordered.sort(java.util.Comparator.comparingDouble(WaveHit::along));

        int hits = 0;
        Vec3 firstHit = null;
        for (WaveHit hit : ordered) {
            // 不触碰 invulnerableTime：普通技能伤害必须遵守原版受击冷却。
            float before = hit.target().getHealth();
            hit.target().hurt(source, (float) damage);
            if (hit.target().getHealth() < before) {
                hits++;
                if (firstHit == null) {
                    firstHit = hit.target().position().add(0.0D, hit.target().getBbHeight() * 0.5D, 0.0D);
                }
            }
            if (hits >= maxTargets) {
                break;
            }
        }
        if (firstHit != null) {
            AbilityVisuals.hit(level, player, firstHit, stage);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, hits > 0 ? 1.4F : 1.2F);
        return true;
    }

    // ------------------------------------------------------------------
    // 爆发（stage4+，Shift+右键）
    // ------------------------------------------------------------------

    /**
     * 释放爆发（<b>三维球</b>）。
     *
     * <p>判定语义（1.1.0 明确化）：
     * <ul>
     *   <li>球心＝施法者<b>躯干中心</b>（脚底 + 身高的一半），不是脚底；</li>
     *   <li>半径＝配置的 6（终阶 8），<b>三轴</b>都参与；</li>
     *   <li>命中判据＝目标<b>包围盒</b>到球心的最近点距离平方 <b>严格 ≤ r²</b>
     *       （{@link EffectiveDamage#sphereIntersectsAabb}），
     *       因此正上方/正下方、斜上方、贴边的大型实体都能命中；
     *       不是 X/Z 圆柱，也不是"只算脚底到球心的距离"。</li>
     * </ul>
     *
     * <p>伤害口径与剑气一致：默认按持有者服务端有效 ATTACK_DAMAGE 取值，应用原系数 1.5。
     */
    public static boolean tryBurst(ServerPlayer player) {
        Cast cast = ready(player, EBConfig.settings().abilities().burstUnlockStage(),
                GrowthData::burstReadyAt);
        if (cast == null) {
            return false;
        }
        GrowthSettings settings = cast.settings();
        GrowthData data = cast.data();
        int stage = cast.stage();
        ServerLevel level = cast.level();
        long now = cast.now();
        GrowthService.set(player, data.withCooldowns(data.waveReadyAt(),
                AbilityState.readyAt(now, CombatMath.burstCooldownTicks(settings, stage))));
        // 立刻下发服务器权威快照（客户端从不自己算冷却）。
        GrowthService.sendSnapshot(player);

        double radius = settings.abilities().burstRadiusFor(stage);
        boolean inherit = settings.abilities().abilityDamageFromAttack();
        // ready() 已经协调过持有者属性，这里读到的是服务端权威有效攻击。
        double damage = CombatMath.burstDamage(settings, stage, effectiveAttackDamage(player), inherit);
        int cap = settings.abilities().burstMaxTargets();
        Vec3 center = AbilityVisuals.torsoCenter(player);
        // 粗筛包围盒：球心 ± 半径，再向外扩一个"最大实体半宽"的余量，
        // 保证"包围盒与球相交"的大型实体不会被提前筛掉（贴边命中必须可达）。
        AABB box = new AABB(center, center).inflate(radius + BURST_BROAD_PHASE_MARGIN);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> isValidTarget(player, entity) && hasLineOfSight(player, entity)
                        && EffectiveDamage.sphereIntersectsAabb(center.x, center.y, center.z, radius,
                                entity.getBoundingBox().minX, entity.getBoundingBox().minY,
                                entity.getBoundingBox().minZ, entity.getBoundingBox().maxX,
                                entity.getBoundingBox().maxY, entity.getBoundingBox().maxZ));

        double[] distances = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            // 排序键＝最近点距离（与命中判据同一口径），保证"最近优先"与"贴边可命中"一致。
            AABB targetBox = candidates.get(i).getBoundingBox();
            distances[i] = EffectiveDamage.distanceSqrToAabb(center.x, center.y, center.z,
                    targetBox.minX, targetBox.minY, targetBox.minZ, targetBox.maxX, targetBox.maxY,
                    targetBox.maxZ);
        }
        int[] chosen = CombatMath.nearestWithinLimit(distances, radius * radius, cap);
        DamageSource source = abilitySource(level, player, SWORD_ABILITY);
        Vec3 firstHit = null;
        for (int index : chosen) {
            LivingEntity target = candidates.get(index);
            // 不触碰 invulnerableTime：爆发是普通技能伤害，必须遵守原版受击冷却。
            float before = target.getHealth();
            target.hurt(source, (float) damage);
            if (firstHit == null && target.getHealth() < before) {
                firstHit = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            }
        }
        // 视觉：三维球壳 + 星轨（客户端铺开，单次施法每观察者最多 1 个包）。
        AbilityVisuals.burst(level, player, center, radius, stage);
        if (firstHit != null) {
            AbilityVisuals.hit(level, player, firstHit, stage);
        }
        // 音效保持克制：不调用爆炸（不改方块、不产生爆炸伤害）。
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
        return true;
    }

    /**
     * 持有者当前服务端有效 ATTACK_DAMAGE（含标准属性 base、物品基线、第三方 ADD 与乘法，
     * 并已由原版 {@code RangedAttribute} 钳到 2048）。
     *
     * <p>这是<b>服务器权威</b>的取值：客户端只从快照读取显示值，从不自行计算。
     */
    public static double effectiveAttackDamage(ServerPlayer player) {
        if (player == null) {
            return Double.NaN;
        }
        return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    }

    // ------------------------------------------------------------------
    // 斩 Boss 加成（stage5+，满蓄力主手近战）
    // ------------------------------------------------------------------

    /**
     * 施加斩 Boss 额外伤害（由战斗处理器在主手命中结算之后调用）。
     *
     * @return 是否真的施加了加成
     */
    public static boolean applyBossBonus(ServerPlayer player, LivingEntity target) {
        GrowthSettings settings = EBConfig.settings();
        GrowthData data = GrowthService.data(player);
        int stage = data.stage(settings);
        if (!com.evolvingblade.growth.Abilities.unlocked(stage, settings.abilities().bossBonusUnlockStage())) {
            return false;
        }
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (!BossClassifier.classify(target, settings.bossRules()).boss()) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = clock(player);
        java.util.Map<UUID, Long> rate = player.getData(EBAttachments.BOSS_RATE);
        if (!AbilityState.tryConsumeRate(rate, target.getUUID(), now,
                settings.abilities().bossBonusCooldownTicks(), AbilityState.DEFAULT_MAX_RATE_ENTRIES)) {
            return false;
        }
        double bonus = CombatMath.bossBonusDamage(settings, stage, target.getMaxHealth());
        if (bonus <= 0.0D) {
            return false;
        }
        // 只绕过"护甲"和"受击冷却"，且都通过数据包标签实现
        // （minecraft:bypasses_armor 与 minecraft:bypasses_cooldown），
        // 绝不修改实体字段、不绕过无敌/抗性/模组事件取消。
        target.hurt(abilitySource(level, player, BOSS_SLAYER), (float) bonus);
        // 视觉：目标附近的小型紫金斩痕（客户端铺开一个有界脚本，不刷屏、不炸屏）。
        AbilityVisuals.slayer(level, player,
                target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D), stage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    /** 判断一个伤害来源是否来自本模组的剑类技能（用于排除吸血/加成的递归）。 */
    public static boolean isAbilitySource(DamageSource source) {
        return source != null && (source.is(SWORD_ABILITY) || source.is(BOSS_SLAYER));
    }
}
