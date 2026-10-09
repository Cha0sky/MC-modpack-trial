package com.evolvingblade.client;

import java.util.List;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBDataComponents;
import com.evolvingblade.registry.EBItems;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 仅客户端入口：键位、物品模型属性、界面调度。
 *
 * <p>专用服务器永远不会加载这个类（{@code @Mod(dist = CLIENT)}），因此其中的
 * Minecraft 客户端类型不会污染服务端。
 */
@Mod(value = EvolvingBlade.MODID, dist = Dist.CLIENT)
public final class EBClient {

    /** G 键：打开成长仪表盘（未选择模式时打开三选一界面）。可自由改键。 */
    private static final KeyMapping OPEN_DASHBOARD = new KeyMapping(
            "key.evolvingblade.dashboard",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            "key.categories.evolvingblade");

    public EBClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(EBClient::onRegisterKeyMappings);
        modEventBus.addListener(EBClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(EBClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(EBClient::onGatherTooltip);
        NeoForge.EVENT_BUS.addListener(EBClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(EBClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(EBClient::onRenderLevelStage);
    }

    /**
     * 在 {@code AFTER_PARTICLES} 阶段绘制技能特效的<b>连续几何</b>
     * （弯月刀光 / 三维球壳星轨 / 命中星芒 / 斩痕 / 吸血轨迹）。
     *
     * <p>事件给出的 {@code PoseStack} 最后一层已经是"相机相对世界"的变换，
     * 所以顶点用 {@code world − camera} 提交即可与地形/实体正确对齐；
     * 顶点数由 {@link EBAbilityClient#MAX_VERTICES_PER_FRAME} 硬性封顶。
     */
    private static void onRenderLevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        var poseStack = event.getPoseStack();
        if (poseStack == null) {
            return;
        }
        EBAbilityClient.render(poseStack.last().pose(), event.getCamera().getPosition(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /**
     * 物品 tooltip 的<b>权威数值</b>：只读服务器下发的快照（当前登录玩家的真实阶段与数值），
     * 从不读物品上的 {@code evolvingblade:stage} 组件。
     *
     * <p>1.1.0 起把两件事<b>分开显示</b>，避免"把基础配置当成全部伤害"的误导：
     * <ul>
     *   <li>「阶段基础攻击」＝服务器配置里该阶段的总攻击，是技能系数对应的口径；</li>
     *   <li>「当前持剑有效攻击」＝服务器读到的 {@code ATTACK_DAMAGE} 最终值，
     *       只在<b>真的主手持剑</b>时显示（背包里预览别的剑不会借用手上的武器加成）。</li>
     * </ul>
     * 并且明确写出"实际伤害还受附魔、目标护甲与事件影响"，不把属性值说成最终伤害。
     */
    private static void onGatherTooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
        if (!com.evolvingblade.item.StarEaterItem.isStarEater(event.getItemStack())) {
            return;
        }
        List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText,
                net.minecraft.world.inventory.tooltip.TooltipComponent>> elements =
                        event.getTooltipElements();
        EBPayloads.StateSnapshot snapshot = EBClientState.snapshot();
        if (snapshot == null || !snapshot.hasMode()) {
            elements.add(com.mojang.datafixers.util.Either.left(
                    Component.translatable("tooltip.evolvingblade.star_eater.awaiting")));
            return;
        }
        elements.add(com.mojang.datafixers.util.Either.left(Component.translatable(
                "tooltip.evolvingblade.star_eater.stage_authoritative",
                Component.translatable("evolvingblade.stage." + snapshot.stage()))));
        elements.add(com.mojang.datafixers.util.Either.left(Component.translatable(
                "tooltip.evolvingblade.star_eater.stage_base_attack",
                format(snapshot.stageBaseAttack()))));
        if (snapshot.holdingStarEater() && Double.isFinite(snapshot.effectiveHeldAttack())) {
            elements.add(com.mojang.datafixers.util.Either.left(Component.translatable(
                    "tooltip.evolvingblade.star_eater.effective_attack",
                    format(snapshot.effectiveHeldAttack()))));
            elements.add(com.mojang.datafixers.util.Either.left(Component.translatable(
                    "tooltip.evolvingblade.star_eater.effective_note")));
        } else {
            // 未持剑：绝不显示"有效攻击"，否则就是在借用别的武器的加成。
            elements.add(com.mojang.datafixers.util.Either.left(
                    Component.translatable("tooltip.evolvingblade.star_eater.not_held")));
        }
        elements.add(com.mojang.datafixers.util.Either.left(Component.translatable(
                "tooltip.evolvingblade.star_eater.speed", format(snapshot.stageAttackSpeed()))));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DASHBOARD);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // 模型属性只读物品上的阶段快照组件（显示用，绝不参与结算）。
        net.minecraft.client.renderer.item.ItemProperties.register(EBItems.starEater(),
                ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "stage"),
                (stack, level, entity, seed) -> stack.getOrDefault(EBDataComponents.STAGE.get(), 0));
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        EBClientState.resetOnLogin();
        EBAbilityClient.reset();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        EBClientState.resetOnLogin();
        EBAbilityClient.reset();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 先推进技能特效脚本（纯客户端本地粒子，不产生网络流量）。
        EBAbilityClient.tick();
        while (OPEN_DASHBOARD.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null) {
                continue;
            }
            EBClientState.allowManualOpen();
            EBPayloads.StateSnapshot snapshot = EBClientState.snapshot();
            minecraft.setScreen(snapshot != null && snapshot.hasMode()
                    ? new DashboardScreen()
                    : new ChoiceScreen());
        }
        // 首次登录自动弹出一次：必须等世界/玩家就绪，且当前没有别的界面（例如原版加载界面）。
        if (minecraft.player != null && minecraft.level != null && minecraft.screen == null
                && EBClientState.consumePendingOpen()) {
            minecraft.setScreen(new ChoiceScreen());
        }
    }
}
