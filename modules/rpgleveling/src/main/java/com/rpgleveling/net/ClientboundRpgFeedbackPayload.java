package com.rpgleveling.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** S2C rejection reason. Sent only when an action failed. */
public record ClientboundRpgFeedbackPayload(int code) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundRpgFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "rpg_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRpgFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ClientboundRpgFeedbackPayload::code,
                    ClientboundRpgFeedbackPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
