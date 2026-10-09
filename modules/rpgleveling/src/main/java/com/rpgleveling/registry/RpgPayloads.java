package com.rpgleveling.registry;

import com.rpgleveling.net.ClientPayloadHandler;
import com.rpgleveling.net.ClientboundRpgDataPayload;
import com.rpgleveling.net.ClientboundRpgFeedbackPayload;
import com.rpgleveling.net.ServerPayloadHandler;
import com.rpgleveling.net.ServerboundRequestDataPayload;
import com.rpgleveling.net.ServerboundRespecPayload;
import com.rpgleveling.net.ServerboundSpendPointPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Mod-bus payload registration. Handlers run on the main thread by default. */
public final class RpgPayloads {

    public static final String PROTOCOL_VERSION = "1";

    private RpgPayloads() {}

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ClientboundRpgDataPayload.TYPE, ClientboundRpgDataPayload.STREAM_CODEC,
                ClientPayloadHandler::onData);
        registrar.playToClient(ClientboundRpgFeedbackPayload.TYPE, ClientboundRpgFeedbackPayload.STREAM_CODEC,
                ClientPayloadHandler::onFeedback);
        registrar.playToServer(ServerboundSpendPointPayload.TYPE, ServerboundSpendPointPayload.STREAM_CODEC,
                ServerPayloadHandler::onSpend);
        registrar.playToServer(ServerboundRespecPayload.TYPE, ServerboundRespecPayload.STREAM_CODEC,
                ServerPayloadHandler::onRespec);
        registrar.playToServer(ServerboundRequestDataPayload.TYPE, ServerboundRequestDataPayload.STREAM_CODEC,
                ServerPayloadHandler::onRequest);
    }
}
