package com.evolvingblade.event;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.combat.SwordHit;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.Abilities;
import com.evolvingblade.growth.DeathDecision;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.growth.PendingDeathQueue;
import com.evolvingblade.registry.EBAttachments;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 击杀入账：服务器权威，且<b>只有一名真实玩家</b>能拿到成长。
 *
 * <h3>两阶段提交</h3>
 * <ol>
 *   <li><b>LOWEST 优先级捕获候选</b>（{@link #onLivingDeath}）：其它模组的处理器都跑完之后，
 *       把这次死亡记为候选，并<b>保存原始 {@link LivingDeathEvent} 引用</b>与归属事实
 *       （伤害来源解析出的玩家、宠物/弹射物主人链、Boss 分类）。</li>
 *   <li><b>本 tick 末核实后入账</b>（{@link #onServerTick}，同样 LOWEST）：重新读取
 *       {@code event.isCanceled()}，并按 {@link DeathDecision} 的决策表决定入账 / 保留 / 丢弃。</li>
 * </ol>
 *
 * <h3>为什么不能只看血量，也不能只要求原版死亡姿态</h3>
 * 1.21.1 的 {@code LivingEntity.die()} 先调用 {@code CommonHooks.onLivingDeath(...)}，
 * 被取消时立即返回——此时<b>血量已经是 0</b>（伤害早于 die 结算）。
 * 所以"血量 0"不等于"死亡成立"：必须重新检查事件最终的取消状态。
 * 反过来，模组 Boss 可以完全不走原版 {@code Pose.DYING} 姿态，因此也<b>不能</b>把姿态当作必要条件。
 *
 * <h3>延迟死亡阶段（原版末影龙）</h3>
 * {@code EnderDragon.hurt(...)} 在飞行/非坐姿阶段受到致命玩家伤害时，会先把血量恢复成 1
 * 并转入 {@code DYING} 阶段；稍后 {@code DragonDeathPhase} 才把血量归零，
 * 最后 {@code tickDeath()} 在 200 tick 后以 {@code KILLED} 移除实体。
 * 因此候选会被<b>保留</b>（{@link DeathDecision#KEEP_WAITING}），既不会在血量 1 时提前入账，
 * 也不会被丢弃——我们只观察，绝不修改 Boss 的血量或阶段。
 *
 * <h3>去重与时钟</h3>
 * 入账标记写在实体自身的 {@link EBAttachments#KILL_CREDITED}（非持久、随实体生命周期结束而消失），
 * 因此不依赖时间窗口或全局容量上限，留存尸体在任意长时间之后都不会二次入账。
 * 所有时间戳统一使用服务器<b>主世界</b>的 {@code gameTime}；服务器停止时清空状态。
 *
 * <p>支持的归属链：本剑直接攻击、玩家的弹射物、其他武器与玩家技能、原版 {@code getKillCredit()}
 * 归属、以及可明确识别的宠物主人。拒绝：FakePlayer、PvP 受害者、无归属的环境死亡、被取消的死亡、
 * 重复的死亡通知。末影龙的多部位伤害会重定向到父实体，因此只为父实体入账一次。
 */
@EventBusSubscriber(modid = EvolvingBlade.MODID)
public final class EBKillCreditHandler {

    /** 归属解析的最大链长（玩家 → 弹射物 → 宠物 → 主人…）。 */
    private static final int MAX_OWNER_CHAIN = 4;

    /** 每个服务器一份状态，服务器停止时移除。 */
    private static final Map<MinecraftServer, ServerState> STATES = new ConcurrentHashMap<>();

    private EBKillCreditHandler() {}

    /**
     * 候选死亡：事件阶段固化的全部事实，包括原始事件引用、死亡时刻的剑来源事实与死亡发生的 tick。
     *
     * <p>{@code swordHit} 必须在<b>死亡事件阶段</b>快照下来，而不是在结算时再读实体上的 attachment：
     * 末影龙等"延迟死亡"的 Boss 会在收到致命伤后继续存活 200 tick，期间可能被别的伤害打到，
     * 实体上的来源事实会被后续命中覆盖甚至清除；只有死亡那一刻的事实才能回答"这具尸体是不是被剑打死的"。
     * {@code deathGameTime} 同理，用于把"命中时间"与"死亡时间"做窗口比较，而不受结算延迟影响。
     */
    private record PendingDeath(LivingEntity victim, LivingDeathEvent event, ServerPlayer credited,
            BossClassification classification, SwordHit swordHit, long deathGameTime) {}

    private static final class ServerState {
        final PendingDeathQueue<PendingDeath> pending = new PendingDeathQueue<>();
    }

    private static ServerState stateFor(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    /** 服务器停止时清空，避免状态跨服务器残留。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    /** 第一阶段：在所有其它处理器之后捕获候选死亡。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim instanceof Player) {
            // PvP 不提供成长。
            return;
        }
        if (!(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerState state = stateFor(serverLevel.getServer());
        UUID victimId = victim.getUUID();
        if (isAlreadyCredited(victim)) {
            // 这条命已经入账：留存尸体的重复通知在这里就被挡住。
            return;
        }
        // 归属事实在事件阶段解析：弹射物/宠物之后可能已被移除。
        ServerPlayer credited = resolveCreditPlayer(event.getSource(), victim);
        if (credited == null) {
            // 本次死亡没有归属（环境死亡 / 被杀死的宠物但主人不明…）。
            // 关键：必须作废任何既有的陈旧候选，否则"对活体合成的死亡事件"会认领这次无归属的死亡。
            state.pending.remove(victimId);
            return;
        }
        GrowthSettings settings = EBConfig.settings();
        BossClassification classification = BossClassifier.classify(victim, settings.bossRules());
        long gameTime = serverLevel.getServer().overworld().getGameTime();
        // 死亡时刻的来源事实快照（延迟死亡的 Boss 可能在这之后被别的伤害改写）。
        SwordHit swordHit = victim.getData(EBAttachments.SWORD_HIT);
        state.pending.put(victimId,
                new PendingDeath(victim, event, credited, classification, swordHit, gameTime), gameTime);
    }

    /** 第二阶段：本 tick 末核实后才入账。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerState state = STATES.get(server);
        if (state == null || state.pending.size() == 0) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        for (UUID victimId : state.pending.snapshotIds()) {
            PendingDeathQueue.Entry<PendingDeath> entry = state.pending.get(victimId);
            if (entry == null) {
                continue;
            }
            PendingDeath death = entry.payload();
            LivingEntity victim = death.victim();
            boolean removed = victim == null || victim.isRemoved();
            DeathDecision decision = DeathDecision.decide(
                    death.event().isCanceled(),
                    victim != null && isAlreadyCredited(victim),
                    removed,
                    victim != null && victim.getRemovalReason() == Entity.RemovalReason.KILLED,
                    victim != null && victim.isDeadOrDying(),
                    gameTime - entry.acceptedGameTime() > PendingDeathQueue.DEFAULT_RETENTION_TICKS);
            if (decision.credits()) {
                markCredited(victim);
                boolean creditedKill = false;
                if (death.classification().boss()) {
                    GrowthService.creditBossKill(death.credited());
                    creditedKill = true;
                } else if (BossClassifier.isHostile(victim, EBConfig.settings().hostileRules())) {
                    GrowthService.creditHostileKill(death.credited());
                    creditedKill = true;
                }
                if (creditedKill) {
                    applySwordKillHeal(death.credited(), death.swordHit(), death.deathGameTime());
                }
                // 既非 Boss 也非普通敌对（例如合成事件打在被改分类的实体上）：确认死亡但不给成长点。
            }
            if (decision.removesCandidate()) {
                state.pending.remove(victimId);
            }
        }
    }

    /**
     * 阶段1+ 的击杀回血：<b>只有这次击杀的最后一次有效剑命中来自该玩家</b>时才回复。
     *
     * <p>传入的是<b>死亡事件阶段快照</b>的来源事实与死亡 tick（见 {@link PendingDeath}），
     * 因此延迟死亡（末影龙）在结算时也用的是"被打死那一刻"的事实，不受之后命中改写影响。
     * 来源窗口按"命中 → 死亡"的间隔判定：只有 20 tick 内的最后一次剑命中才算这次击杀的来源。
     */
    static void applySwordKillHeal(ServerPlayer player, SwordHit hit, long deathGameTime) {
        if (player == null || hit == null || !hit.isPresent()) {
            return;
        }
        if (!player.getUUID().equals(hit.player())
                || !hit.isStillValidFor(deathGameTime, SwordHit.VALID_WINDOW_TICKS)) {
            return;
        }
        GrowthSettings settings = EBConfig.settings();
        int stage = GrowthService.stage(player);
        if (!Abilities.unlocked(stage, 1)) {
            return;
        }
        double heal = settings.abilities().killHeal();
        if (heal > 0.0D) {
            player.heal((float) heal);
        }
    }

    /**
     * 这条命是否已经入账（写在实体自身上的非持久标记）。
     *
     * <p>比较的是<b>值</b>而不是"是否存在"：{@code IAttachmentHolder.getData} 在缺省时会把默认值
     * 写进 holder，因此仅仅读取一次默认值就会让 {@code hasData(...)} 变成 true。
     * 若按存在性判定，一次无关的读取就会永久压制该实体的正常击杀入账。
     */
    static boolean isAlreadyCredited(LivingEntity victim) {
        return victim != null && Boolean.TRUE.equals(victim.getData(EBAttachments.KILL_CREDITED));
    }

    /** 写入"这条命已入账"的永久标记。 */
    static void markCredited(LivingEntity victim) {
        if (victim != null) {
            victim.setData(EBAttachments.KILL_CREDITED, Boolean.TRUE);
        }
    }

    /**
     * 解析这次死亡应该记给谁；无法明确归属时返回 {@code null}。
     */
    static ServerPlayer resolveCreditPlayer(DamageSource source, LivingEntity victim) {
        if (source != null) {
            ServerPlayer direct = asCreditedPlayer(source.getEntity());
            if (direct != null) {
                return direct;
            }
        }
        // 原版归属：lastHurtByPlayer / lastHurtByMob。
        return asCreditedPlayer(victim.getKillCredit());
    }

    /**
     * 沿着"直接来源 → 弹射物主人 → 可拥有实体的主人"链寻找真实玩家。
     * FakePlayer 会被拒绝。
     */
    static ServerPlayer asCreditedPlayer(Entity entity) {
        Entity current = entity;
        for (int depth = 0; depth < MAX_OWNER_CHAIN && current != null; depth++) {
            if (current instanceof ServerPlayer player) {
                return player instanceof FakePlayer ? null : player;
            }
            Entity next = null;
            if (current instanceof Projectile projectile && projectile.getOwner() != null) {
                next = projectile.getOwner();
            } else if (current instanceof OwnableEntity ownable && ownable.getOwner() != null) {
                next = ownable.getOwner();
            }
            if (next == null || next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    /** 仅供测试与诊断：某个服务器当前待核实的候选数量。 */
    public static int pendingCount(MinecraftServer server) {
        ServerState state = STATES.get(server);
        return state == null ? 0 : state.pending.size();
    }

    /** 仅供测试与诊断：指定实体是否还有待核实的候选。 */
    public static boolean hasPendingCandidate(MinecraftServer server, UUID victimId) {
        ServerState state = STATES.get(server);
        return state != null && state.pending.contains(victimId);
    }

    /** 仅供测试与诊断：指定实体这条命是否已经入账。 */
    public static boolean isCreditedForLife(LivingEntity victim) {
        return isAlreadyCredited(victim);
    }
}
