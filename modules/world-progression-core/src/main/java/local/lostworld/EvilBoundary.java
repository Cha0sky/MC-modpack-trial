package local.lostworld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public final class EvilBoundary {
    public static final TagKey<Biome> LOCKED = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID, "locked_evil_biomes"));
    private record SafeSpot(ServerLevel level, Vec3 position, float yaw, float pitch) {}
    private static final Map<UUID, SafeSpot> SAFE = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private EvilBoundary() {}
    public static boolean evil(ServerLevel level, BlockPos position) { return level.getBiome(position).is(LOCKED); }
    public static boolean enforce(ServerPlayer player) {
        ChapterData data = ChapterData.find(player.serverLevel());
        if (data == null || !data.state().active() || data.state().defeated() || player.isSpectator()) return false;
        ServerLevel current = player.serverLevel();
        if (!evil(current, player.blockPosition())) {
            if (player.isAlive() && !player.isInLava() && current.noCollision(player))
                SAFE.put(player.getUUID(), new SafeSpot(current, player.position(), player.getYRot(), player.getXRot()));
            return false;
        }
        SafeSpot spot = SAFE.get(player.getUUID());
        if (spot == null || evil(spot.level(), BlockPos.containing(spot.position()))
                || !spot.level().noCollision(player,player.getBoundingBox().move(spot.position().subtract(player.position())))) {
            BlockPos home = data.home();
            if (home == null) home = player.server.overworld().getSharedSpawnPos();
            spot = new SafeSpot(player.server.overworld(), Vec3.atBottomCenterOf(home), 0, 0);
        }
        player.stopRiding();
        player.teleportTo(spot.level(), spot.position().x, spot.position().y, spot.position().z, spot.yaw(), spot.pitch());
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        long now = player.server.overworld().getGameTime();
        if (now - WARNED.getOrDefault(player.getUUID(), -200L) >= 60) {
            player.displayClientMessage(Component.literal("前方的猩红与腐化区域仍被阻隔。先击败史莱姆王，再继续远征。"), true);
            WARNED.put(player.getUUID(), now);
        }
        return true;
    }
    public static boolean blocksTarget(ServerPlayer player, BlockPos target) {
        ChapterData data = ChapterData.find(player.serverLevel());
        return data != null && data.state().blocksEvil(evil(player.serverLevel(), target), player.isSpectator());
    }
    public static void clearPlayer(UUID id) { SAFE.remove(id); WARNED.remove(id); }
    public static void clear() { SAFE.clear(); WARNED.clear(); }
}
