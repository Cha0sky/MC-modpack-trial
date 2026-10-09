package com.evolvingblade;

import org.slf4j.Logger;

import com.evolvingblade.config.EBConfig;
import com.evolvingblade.network.EBPayloads;
import com.evolvingblade.registry.EBAttachments;
import com.evolvingblade.registry.EBCreativeTabs;
import com.evolvingblade.registry.EBDataComponents;
import com.evolvingblade.registry.EBItems;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * 噬星之刃 / Evolving Blade 主入口。
 *
 * <p>注册表（物品 / 创造页签 / 玩家数据 attachment / 显示用数据组件）、服务器配置、
 * 网络包处理器、纯 JVM 成长计算、服务器权威的击杀入账服务、Boss 分类器与管理员命令，
 * 以及战斗能力（剑气 / 吸血 / 爆发 / 斩 Boss / 击杀回血）的事件接线都在这里汇合。
 */
@Mod(EvolvingBlade.MODID)
public final class EvolvingBlade {

    /** 模组 ID，必须与 {@code META-INF/neoforge.mods.toml} 和资源目录名一致。 */
    public static final String MODID = "evolvingblade";

    /** 全局日志器。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public EvolvingBlade(IEventBus modEventBus, ModContainer modContainer) {
        // 注册表
        EBItems.ITEMS.register(modEventBus);
        EBCreativeTabs.TABS.register(modEventBus);
        EBAttachments.ATTACHMENTS.register(modEventBus);
        EBDataComponents.COMPONENTS.register(modEventBus);

        // 网络：C2S 只有"选择模式"这一条枚举意图，S2C 是服务器权威状态快照。
        modEventBus.addListener(EBPayloads::registerPayloads);

        // 服务器配置：所有门槛、数值与能力旋钮的唯一来源。
        modContainer.registerConfig(ModConfig.Type.SERVER, EBConfig.SPEC);
        // 配置加载/重载后重建"已校验快照"。显式注册，避免使用已弃用的 EventBusSubscriber.bus()。
        modEventBus.addListener(EBConfig::onLoading);
        modEventBus.addListener(EBConfig::onReloading);

        LOGGER.info("[EvolvingBlade] 初始化完成：物品/创造页签/玩家成长数据/数据组件/网络/服务器配置已注册");
    }
}
