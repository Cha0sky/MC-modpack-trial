package com.infinite.core.network;

import com.infinite.core.InfiniteCore;
import com.infinite.core.block.GeneratorBlockEntity;
import com.infinite.core.block.IoMode;
import com.infinite.core.menu.GeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Retains payload IDs; protocol 2 requires matching rebuilt client and server. */
public final class IoPackets {
    private IoPackets() {}
    public record Update(BlockPos pos, int face, IoMode mode) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InfiniteCore.MODID, "update_io_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeVarInt(packet.face); buf.writeByte(packet.mode.id()); },
                buf -> new Update(buf.readBlockPos(), buf.readVarInt(), IoMode.fromId(buf.readUnsignedByte())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(BlockPos pos, byte[] modes) implements CustomPacketPayload {
        public Sync { pos = pos.immutable(); modes = modes.clone(); }
        @Override public byte[] modes() { return modes.clone(); }
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(InfiniteCore.MODID, "sync_io_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeBlockPos(packet.pos); buf.writeByteArray(packet.modes); },
                buf -> new Sync(buf.readBlockPos(), buf.readByteArray(6)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Update.TYPE, Update.CODEC, (packet, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.containerMenu instanceof GeneratorMenu menu) || !menu.pos().equals(packet.pos)
                    || !menu.stillValid(player) || packet.face < 0 || packet.face >= 6) return;
            GeneratorBlockEntity generator = menu.generator();
            if (generator == null) return;
            generator.setIoMode(packet.face, packet.mode);
            menu.broadcastChanges();
            PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) player, new Sync(packet.pos, generator.ioModes()));
        }));
        registrar.playToClient(Sync.TYPE, Sync.CODEC, (packet, context) -> context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().isClientSide && player.level().getBlockEntity(packet.pos) instanceof GeneratorBlockEntity generator) {
                generator.applyIoModes(packet.modes);
            }
            // GUI reads authoritative ContainerData, so first open and other viewers also synchronize.
        }));
    }
}
