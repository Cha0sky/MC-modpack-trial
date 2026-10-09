package com.infinite.core;

import com.infinite.core.network.IoPackets;
import com.infinite.core.registry.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** New maintainable implementation, not recovered original source. */
@Mod(InfiniteCore.MODID)
public final class InfiniteCore {
    public static final String MODID = "infinitecore";

    public InfiniteCore(IEventBus bus) {
        ModContent.register(bus);
        bus.addListener(IoPackets::register);
    }
}
