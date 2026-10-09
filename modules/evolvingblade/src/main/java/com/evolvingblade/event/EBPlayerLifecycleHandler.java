package com.evolvingblade.event;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.growth.GiftState;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 玩家生命周期：死亡重生 / 末地返回的数据延续，以及待发放赠剑的重试。
 *
 * <p><b>关于"末地返回不重复复制/累加"：</b>
 * 1.21.1 的 {@code AttachmentInternals.onPlayerClone} 对
 * {@code PlayerEvent.Clone} 统一调用 {@code copyAttachmentsFrom(original, event.isWasDeath())}，
 * 目标永远是<b>全新的玩家实例</b>，写入方式是 {@code put}（覆盖）而不是累加；
 * 死亡时只复制声明了 {@code copyOnDeath()} 的类型，末地返回时复制所有可序列化类型。
 * 两种路径都只会执行一次复制，因此本模组<b>不需要</b>（也绝不应该）在 Clone 里再加一次计数。
 */
@EventBusSubscriber(modid = EvolvingBlade.MODID)
public final class EBPlayerLifecycleHandler {

    /** 待发放赠剑的背包重试间隔（tick）。 */
    private static final int GIFT_RETRY_INTERVAL = 100;

    private EBPlayerLifecycleHandler() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        Player original = event.getOriginal();
        // 只做校验与记录，绝不在这里修改数值——任何加法都会造成"末地返回翻倍"。
        GrowthData newData = GrowthService.data(newPlayer);
        GrowthData oldData = GrowthService.data(original);
        if (newData != oldData && newData.hostileKills() > oldData.hostileKills()) {
            EvolvingBlade.LOGGER.warn("[EvolvingBlade] 检测到重生后计数异常增长（旧 {} → 新 {}），已忽略；请上报此日志。",
                    oldData.hostileKills(), newData.hostileKills());
        }
        EvolvingBlade.LOGGER.debug("[EvolvingBlade] 玩家克隆完成(wasDeath={})：保留 敌对击杀={} Boss击杀={} 成长点={} 模式={} 赠剑={}",
                event.isWasDeath(), newData.hostileKills(), newData.bossKills(), newData.growthPoints(),
                newData.mode(), newData.giftState());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GrowthData data = GrowthService.data(player);
        EvolvingBlade.LOGGER.debug("[EvolvingBlade] 玩家 {} 登录：阶段={} 模式={} 敌对击杀={} Boss击杀={} 成长点={} 赠剑={}",
                player.getGameProfile().getName(), data.stage(com.evolvingblade.config.EBConfig.settings()),
                data.mode(), data.hostileKills(), data.bossKills(), data.growthPoints(), data.giftState());
        if (data.giftState() == GiftState.PENDING) {
            GrowthService.tryDeliverGift(player);
        }
        // 登录后立刻下发权威快照（客户端若尚未就绪，会把"首次选择"提示排队到世界加载完成）。
        GrowthService.sendSnapshot(player);
    }

    /** 重生 / 末地返回：属性与界面状态都用服务器数据重建。 */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendLate(player);
        }
    }

    /** 换维度：客户端会重建本地状态，必须补发快照。 */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendLate(player);
        }
    }

    /** 延迟一 tick 补发，避免与重生/传送过程中的客户端重建竞态。 */
    private static void sendLate(final ServerPlayer player) {
        final net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            if (!player.hasDisconnected()) {
                GrowthService.sendSnapshot(player);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % GIFT_RETRY_INTERVAL != 0) {
            return;
        }
        // 只对"待发放"的玩家做一次廉价检查，绝大多数玩家直接返回。
        if (GrowthService.data(player).giftState() == GiftState.PENDING) {
            GrowthService.tryDeliverGift(player);
        }
    }
}
