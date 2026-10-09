package com.evolvingblade.registry;

import com.evolvingblade.EvolvingBlade;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的数据组件。
 *
 * <p>{@code stage} 只用于<b>物品模型与 tooltip 显示</b>：它由服务器在"当前持有者"变化时写入，
 * 客户端据此选择 8 个模型之一。它<b>永远不是</b>战斗数值的权威来源——伤害/攻速一律由服务器按
 * 玩家真实阶段施加的属性修饰符决定，复制/伪造该组件的物品不会得到任何强度。
 */
public final class EBDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE,
                    EvolvingBlade.MODID);

    /** {@code evolvingblade:stage} —— 当前持有者阶段快照（显示用）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STAGE =
            COMPONENTS.registerComponentType("stage", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .cacheEncoding());

    private EBDataComponents() {}
}
