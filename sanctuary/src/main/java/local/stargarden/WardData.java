package local.stargarden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;

/** Each dimension persists its own anchors; no chunk force-loading. */
public final class WardData extends SavedData {
    private static final Factory<WardData> FACTORY = new Factory<>(WardData::new, WardData::load, null);
    private final Set<Long> anchors = new HashSet<>();
    public static WardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "stargarden_sanctuary");
    }
    private static WardData load(CompoundTag tag, HolderLookup.Provider lookup) {
        WardData data = new WardData();
        for (long position : tag.getLongArray("anchors")) data.anchors.add(position);
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLongArray("anchors", anchors.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
    public void add(BlockPos pos) { if (anchors.add(pos.asLong())) setDirty(); }
    public void remove(BlockPos pos) { if (anchors.remove(pos.asLong())) setDirty(); }
    public boolean isEmpty() { return anchors.isEmpty(); }
    public boolean covers(double x, double z) {
        double radius = StarGardenSanctuary.RADIUS.get();
        for (long packed : anchors) {
            BlockPos pos = BlockPos.of(packed);
            double dx = x - (pos.getX() + 0.5), dz = z - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= radius * radius) return true;
        }
        return false;
    }
    public void pruneLoadedAnchors(ServerLevel level) {
        boolean changed = anchors.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            return level.hasChunkAt(pos) && !level.getBlockState(pos).is(StarGardenSanctuary.WARD.get());
        });
        if (changed) setDirty();
    }
}
