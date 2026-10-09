package com.rpgleveling.net;

import com.rpgleveling.client.ClientRpgData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C handlers.
 *
 * <p>This class lives outside any {@code client} package and imports nothing from
 * {@code net.minecraft.client.*}, so loading it on a dedicated server is harmless -
 * the handlers simply never run there.</p>
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    public static void onData(ClientboundRpgDataPayload payload, IPayloadContext context) {
        ClientRpgData.accept(payload);
    }

    public static void onFeedback(ClientboundRpgFeedbackPayload payload, IPayloadContext context) {
        ClientRpgData.setFeedback(payload.code());
    }
}
