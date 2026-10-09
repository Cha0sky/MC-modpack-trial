package com.rpgleveling.net;

import com.rpgleveling.service.RpgService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S handlers. Every single one delegates to {@code RpgService}, which re-validates
 * from scratch; the player identity always comes from the connection, never from the
 * payload body.
 */
public final class ServerPayloadHandler {

    private ServerPayloadHandler() {}

    public static void onSpend(ServerboundSpendPointPayload payload, IPayloadContext context) {
        // Identity from the connection only - there is no UUID field to forge.
        if (!(context.player() instanceof ServerPlayer player)) return;
        RpgService.spend(player, payload.lineIndex(), payload.amount());
    }

    public static void onRespec(ServerboundRespecPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        RpgService.respec(player);
    }

    public static void onRequest(ServerboundRequestDataPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        RpgService.syncOnly(player);
    }
}
