package com.rpgleveling.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C snapshot of everything the point-buy screen renders.
 *
 * <p>Eight fields, so {@code StreamCodec.composite} (six at most) cannot express it and
 * the codec is written by hand.</p>
 */
public record ClientboundRpgDataPayload(int level,
                                        long totalXp,
                                        long xpIntoLevel,
                                        long xpForNextLevel,
                                        long earnedPoints,
                                        long spentPoints,
                                        long unspentPoints,
                                        int[] points) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundRpgDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rpgleveling", "rpg_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRpgDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.level());
                        buf.writeVarLong(p.totalXp());
                        buf.writeVarLong(p.xpIntoLevel());
                        buf.writeVarLong(p.xpForNextLevel());
                        buf.writeVarLong(p.earnedPoints());
                        buf.writeVarLong(p.spentPoints());
                        buf.writeVarLong(p.unspentPoints());
                        buf.writeVarIntArray(p.points());
                    },
                    buf -> new ClientboundRpgDataPayload(
                            buf.readVarInt(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarIntArray()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
