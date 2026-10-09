package com.infinite.core.client;

import com.infinite.core.InfiniteCore;
import com.infinite.core.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client entry point only: dedicated servers do not load GUI types. */
@Mod(value = InfiniteCore.MODID, dist = Dist.CLIENT)
public final class InfiniteCoreClient {
    public InfiniteCoreClient(IEventBus bus) { bus.addListener(InfiniteCoreClient::registerScreens); }
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.GENERATOR_MENU.get(), GeneratorScreen::new);
    }
}
