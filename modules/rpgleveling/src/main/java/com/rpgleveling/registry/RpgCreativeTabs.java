package com.rpgleveling.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One creative tab holding the three mod items. */
public final class RpgCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgAttributes.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rpgleveling"))
                    .icon(() -> new ItemStack(RpgItems.SKILL_SCROLL.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(RpgItems.RESPEC_TOKEN.get());
                        output.accept(RpgItems.SKILL_SCROLL.get());
                        output.accept(RpgItems.EXPERIENCE_CRYSTAL.get());
                    })
                    .build());

    private RpgCreativeTabs() {}
}
