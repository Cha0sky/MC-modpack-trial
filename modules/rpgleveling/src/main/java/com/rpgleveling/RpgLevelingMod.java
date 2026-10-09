package com.rpgleveling;

import com.rpgleveling.event.ConfigEvents;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.registry.RpgCreativeTabs;
import com.rpgleveling.registry.RpgItems;
import com.rpgleveling.registry.RpgAttributes;
import com.rpgleveling.registry.RpgPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Mod entry point.
 *
 * <p>Only common, dist-neutral registrations happen here. Client-only code lives in
 * {@code com.rpgleveling.client} and is filtered by {@code @EventBusSubscriber(value = Dist.CLIENT)},
 * so nothing in this class can ever pull a client class onto a dedicated server.</p>
 */
@Mod(RpgLevelingMod.MODID)
public final class RpgLevelingMod {

    public static final String MODID = "rpgleveling";

    public RpgLevelingMod(IEventBus modBus, ModContainer container) {
        RpgAttributes.ATTRIBUTES.register(modBus);
        RpgAttachments.ATTACHMENT_TYPES.register(modBus);
        RpgItems.ITEMS.register(modBus);
        RpgCreativeTabs.TABS.register(modBus);
        modBus.addListener(RpgPayloads::onRegisterPayloads);
        modBus.addListener(RpgAttributes::onEntityAttributeModification);
        ConfigEvents.init(modBus);

        container.registerConfig(ModConfig.Type.COMMON, RpgConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, RpgConfig.CLIENT_SPEC);

        // Build a snapshot from the defaults right away; ModConfigEvent.Loading replaces
        // it with the real file contents a moment later.
        RpgConfig.refresh();
    }
}