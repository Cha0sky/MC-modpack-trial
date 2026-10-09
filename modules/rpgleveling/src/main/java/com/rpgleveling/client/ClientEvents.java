package com.rpgleveling.client;

import com.rpgleveling.client.screen.RpgScreen;
import com.rpgleveling.net.ServerboundRequestDataPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opening the screen, and degrading gracefully when the link drops. */
@EventBusSubscriber(modid = "rpgleveling", value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (KeyBindings.OPEN_RPG.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                if (!ClientRpgData.has()) {
                    PacketDistributor.sendToServer(ServerboundRequestDataPayload.INSTANCE);
                }
                minecraft.setScreen(new RpgScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRpgData.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RpgScreen) {
            minecraft.setScreen(null);
        }
    }
}
