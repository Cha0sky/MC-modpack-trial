package com.evolvingblade.registry;

import com.evolvingblade.EvolvingBlade;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造模式物品栏注册。
 */
public final class EBCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EvolvingBlade.MODID);

    /** {@code evolvingblade:main} —— 噬星之刃专属页签，排在战斗页签之后。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.evolvingblade"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> EBItems.STAR_EATER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(EBItems.STAR_EATER.get()))
                    .build());

    private EBCreativeTabs() {}
}
