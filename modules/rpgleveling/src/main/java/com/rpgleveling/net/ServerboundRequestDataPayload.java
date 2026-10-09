package com.rpgleveling.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S "send me my data" - sent when the screen opens before the first sync arrived. */
public record ServerboundRequestDataPayload() implements CustomPacketPayload {

    public static final ServerboundRequestDataPayload INSTANCE = new ServerboundRequestDataPayload();

    public static final CustomPacketPayload.Type<ServerboundRequestDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "request_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRequestDataPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
