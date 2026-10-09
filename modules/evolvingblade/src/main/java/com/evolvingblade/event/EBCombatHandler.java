package com.evolvingblade.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.combat.AbilityState;
import com.evolvingblade.combat.CombatMath;
import com.evolvingblade.combat.StageAttributes;
import com.evolvingblade.combat.StarEaterAbilities;
import com.evolvingblade.combat.SwordHit;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.item.StarEaterItem;
import com.evolvingblade.registry.EBAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 战斗事件处理：阶段属性协调、满蓄力采样、吸血、剑气/爆发触发、来源标记。
 *
 * <p>关键时序（一手源码核实）：{@code Player.attack(Entity)} 在 1223 行先调用
 * {@code CommonHooks.onPlayerAttackTarget}（即 {@link AttackEntityEvent}），
 * <b>之后</b>才在 1226 读取 {@code ATTACK_DAMAGE}、在 1230 采样
 * {@code getAttackStrengthScale(0.5F)}；NeoForge 还把 {@code resetAttackStrengthTicker()}
 * 移到攻击事件之后（Player.java:1414）。因此 {@link AttackEntityEvent} 是
 * "协调持有者属性 + 采样满蓄力"的唯一正确时机——不能假设周期性 tick 已经跑过。
 */
@EventBusSubscriber(modid = EvolvingBlade.MODID)
public final class EBCombatHandler {

    /** 一次主手近战的上下文：目标、是否满蓄力、时间戳。 */
    private record PrimaryMelee(UUID target, boolean charged, long gameTime) {}

    private static final Map<UUID, PrimaryMelee> PRIMARY = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> HEALTH_BEFORE = new ConcurrentHashMap<>();

    /** 临时状态的最长保留时间，超时即清理，保证有界。 */
    private static final long STATE_TTL_TICKS = 40L;

    private EBCombatHandler() {}

    /** 主手攻击前：协调属性 + 采样满蓄力（必须在原版读取属性/重置计时机之前）。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int stage = StageAttributes.reconcile(player);
        if (stage <= 0 && !StarEaterItem.isStarEater(player.getMainHandItem())) {
            PRIMARY.remove(player.getUUID());
            return;
        }
        float charge = player.getAttackStrengthScale(0.5F);
        // 多部位实体（原版末影龙等）：攻击目标可能是 part 实体，而原版的受伤事件发生在**父实体**上。
        // 必须把身份归一化到父实体，否则主手上下文对不上，吸血/斩 Boss 加成/剑来源全部失效。
        // 只使用 NeoForge 的通用 PartEntity#getParent()，不硬编码任何具体 Boss 类型。
        PrimaryMelee melee = new PrimaryMelee(attackIdentity(event.getTarget()).getUUID(),
                CombatMath.isFullyCharged(charge), StarEaterAbilities.clock(player));
        PRIMARY.put(player.getUUID(), melee);
    }

    /** 把"被攻击的实体"归一化成真正承受伤害的实体（part → 父实体）。供测试与诊断读取。 */
    public static Entity attackIdentity(Entity target) {
        if (target instanceof net.neoforged.neoforge.entity.PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent != null) {
                return parent;
            }
        }
        return target;
    }

    /**
     * 记录命中前的生命值（用于精确计算"实际损失"），对<b>所有</b>受害者生效，
     * 而不仅是主手近战目标：任何被接受的伤害都要能算出真实生命损失。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (HEALTH_BEFORE.size() > 512) {
            HEALTH_BEFORE.clear();
        }
        HEALTH_BEFORE.put(victim.getUUID(), victim.getHealth());
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PrimaryMelee melee = PRIMARY.get(player.getUUID());
        if (melee == null || !melee.target().equals(victim.getUUID())) {
            return;
        }
        // 上下文必须属于"当前这一 tick 的攻击"：被取消的攻击/技能不能沿用上一 tick 的记录。
        if (StarEaterAbilities.clock(player) != melee.gameTime()) {
            PRIMARY.remove(player.getUUID());
        }
    }

    /**
     * 伤害结算完成之后：<b>每个被接受的伤害</b>都会更新"来源事实"，然后才处理吸血与斩 Boss 加成。
     *
     * <p>来源事实规则（SPEC + 独立复核要求）：
     * <ul>
     *   <li>本剑造成（满蓄力无关：只要伤害来自本剑的近战直击或本模组技能）→ 写入
     *       {@link SwordHit}（玩家 + 当前 tick）；</li>
     *   <li>其它<b>任何</b>被接受的伤害（弓/弩/三叉戟/宠物/环境/别的玩家的攻击）→ 写入
     *       {@link SwordHit#NONE}，即<b>清除</b>旧的剑来源。否则"先用剑砍一下、再用弓射死"
     *       会在 20 tick 窗口内白拿击杀回血；</li>
     *   <li>零伤害/取消的合成 Post 不改变任何来源事实。</li>
     * </ul>
     *
     * <p>主手近战上下文（{@link #PRIMARY}）要求：同 tick、来源类型是
     * {@code minecraft:is_player_attack}、直击实体就是玩家本人、并且该玩家用本剑命中：
     * 这样被取消的攻击、上一 tick 的攻击、以及同一 tick 内的无关伤害都无法复用满蓄力/吸血上下文。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (event.getNewDamage() <= 0.0F) {
            // 没有实际伤害的合成事件：不写入、也不清除来源事实。
            return;
        }
        boolean ability = StarEaterAbilities.isAbilitySource(event.getSource());
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        long tick = player != null ? StarEaterAbilities.clock(player)
                : victim.level().getGameTime();
        PrimaryMelee melee = player == null ? null : PRIMARY.get(player.getUUID());
        boolean primary = player != null
                && event.getSource().getDirectEntity() == player
                && event.getSource().is(DamageTypeTags.IS_PLAYER_ATTACK)
                && StarEaterItem.isStarEater(player.getMainHandItem())
                && melee != null
                && melee.target().equals(victim.getUUID())
                && melee.gameTime() == tick;
        boolean charged = primary && melee.charged();

        // 1) 来源事实：本剑（技能或近战直击）写入；其它一切被接受的伤害清除。
        if (ability) {
            if (player != null) {
                victim.setData(EBAttachments.SWORD_HIT, SwordHit.of(player.getUUID(), tick));
            }
        } else {
            victim.setData(EBAttachments.SWORD_HIT,
                    primary ? SwordHit.of(player.getUUID(), tick) : SwordHit.NONE);
        }

        if (ability) {
            // 技能伤害：不吸血、不递归加成，也不消耗/复用主手上下文。
            return;
        }
        if (player == null) {
            return;
        }
        PRIMARY.remove(player.getUUID());
        Float before = HEALTH_BEFORE.remove(victim.getUUID());
        if (charged && before != null) {
            GrowthSettings settings = EBConfig.settings();
            int stage = GrowthService.stage(player);
            if (com.evolvingblade.growth.Abilities.unlocked(stage, settings.abilities().lifestealUnlockStage())) {
                double heal = CombatMath.lifestealAmount(settings, stage, before, victim.getHealth());
                if (heal > 0.0D) {
                    player.heal((float) heal);
                    // 视觉：少量从目标向施法者运动的点（客户端铺开，单次一个包）。
                    if (victim.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        com.evolvingblade.combat.AbilityVisuals.lifesteal(serverLevel, player,
                                victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), heal, stage);
                    }
                }
            }
            // 2) 斩 Boss 加成：只在"满蓄力主手近战直击"之后施加，防递归。
            StarEaterAbilities.applyBossBonus(player, victim);
        }
    }

    /** 该玩家是否在<b>当前这一 tick</b>对这一目标留下了主手攻击上下文。 */
    private static boolean isCurrentPrimary(ServerPlayer player, UUID target, long tick) {
        PrimaryMelee melee = PRIMARY.get(player.getUUID());
        return melee != null && melee.target().equals(target) && melee.gameTime() == tick;
    }

    /** 右键：Shift+右键释放爆发，否则释放剑气（都在服务器侧判定）。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) {
            return;
        }
        if (!StarEaterItem.isStarEater(event.getItemStack())) {
            return;
        }
        if (player.isShiftKeyDown()) {
            StarEaterAbilities.tryBurst(player);
        } else {
            StarEaterAbilities.tryWave(player);
        }
    }

    /** 装备变化：立刻重新协调属性（换剑/换手）。 */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StageAttributes.reconcile(player);
        }
    }

    /** 周期性兜底：属性协调（例如通过命令改阶段）+ 清理过期状态。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        StageAttributes.reconcile(player);
        // 有界的冷却同步（约 1 秒一次）：只在"手持本剑或仍有冷却"时发包。
        GrowthData data = GrowthService.data(player);
        long now = StarEaterAbilities.clock(player);
        boolean cooldownActive = com.evolvingblade.combat.AbilityState.remaining(now, data.waveReadyAt()) > 0
                || com.evolvingblade.combat.AbilityState.remaining(now, data.burstReadyAt()) > 0;
        if (cooldownActive || GrowthService.holdsStarEater(player)) {
            GrowthService.sendSnapshot(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        PRIMARY.entrySet().removeIf(entry -> now - entry.getValue().gameTime() > STATE_TTL_TICKS);
        if (HEALTH_BEFORE.size() > 256) {
            HEALTH_BEFORE.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PRIMARY.clear();
        HEALTH_BEFORE.clear();
        StageAttributes.clearInjectionRegistry();
    }

    /** 供测试与诊断：某玩家当前是否处于满蓄力主手近战记录中。 */
    public static boolean hasPrimaryMelee(ServerPlayer player) {
        PrimaryMelee melee = PRIMARY.get(player.getUUID());
        return melee != null && melee.charged();
    }

    /** 供测试与诊断：清理某玩家的临时状态。 */
    public static void clearTransient(ServerPlayer player) {
        PRIMARY.remove(player.getUUID());
    }

    /** 供测试与诊断：当前速率限制表大小（有界性检查）。 */
    public static int rateTableSize(ServerPlayer player) {
        return player.getData(EBAttachments.BOSS_RATE).size();
    }

    /** 供测试与诊断：主动清理某玩家的速率限制表（时钟与运行时统一：主世界 gameTime）。 */
    public static int pruneRateTable(ServerPlayer player) {
        return AbilityState.pruneExpired(player.getData(EBAttachments.BOSS_RATE),
                StarEaterAbilities.clock(player), AbilityState.DEFAULT_MAX_RATE_ENTRIES);
    }

    static {
        EvolvingBlade.LOGGER.debug("[EvolvingBlade] combat handler ready");
    }
}
