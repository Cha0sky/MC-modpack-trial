package com.rpgleveling.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S "respec me". Carries nothing at all - the server knows who is asking. */
public record ServerboundRespecPayload() implements CustomPacketPayload {

    public static final ServerboundRespecPayload INSTANCE = new ServerboundRespecPayload();

    public static final CustomPacketPayload.Type<ServerboundRespecPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "respec"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRespecPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
