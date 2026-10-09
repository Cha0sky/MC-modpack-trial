package com.rpgleveling.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-only mod-bus registration.
 *
 * <p>Guarded by {@code value = Dist.CLIENT} on the annotation rather than by an
 * {@code if} inside common code: a dedicated server then never even loads this class.</p>
 *
 * <p>{@code bus()} is deprecated for removal in this NeoForge build but is still honoured
 * by FancyModLoader; it is the only way to subscribe to the mod bus without referencing a
 * client class from common code, which the dist scan forbids.</p>
 */
@EventBusSubscriber(modid = "rpgleveling", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RpgLevelingClient {

    private RpgLevelingClient() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_RPG);
    }
}