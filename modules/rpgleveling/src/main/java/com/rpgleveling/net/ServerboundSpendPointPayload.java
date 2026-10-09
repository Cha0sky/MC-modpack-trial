package com.rpgleveling.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S "put {@code amount} points into line {@code lineIndex}".
 *
 * <p>Deliberately carries <b>no</b> level, XP or point total: the client can only ask,
 * the server always recomputes. There is simply no field to forge.</p>
 */
public record ServerboundSpendPointPayload(int lineIndex, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerboundSpendPointPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "spend_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSpendPointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ServerboundSpendPointPayload::lineIndex,
                    ByteBufCodecs.VAR_INT, ServerboundSpendPointPayload::amount,
                    ServerboundSpendPointPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
