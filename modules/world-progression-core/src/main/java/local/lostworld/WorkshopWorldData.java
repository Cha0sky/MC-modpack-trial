package local.lostworld;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** The first restored workshop is a shared world milestone; each console is recorded once. */
public final class WorkshopWorldData extends SavedData {
    public static final String KEY="lostworld_workshops";
    public static final Factory<WorkshopWorldData> FACTORY=new Factory<>(WorkshopWorldData::new,WorkshopWorldData::load);
    private final Set<Long> consoles=new HashSet<>();

    public static WorkshopWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY,KEY);
    }

    public boolean restored() { return !consoles.isEmpty(); }

    public boolean restore(BlockPos console) {
        if (!consoles.add(console.asLong())) return false;
        setDirty();
        return true;
    }

    public static WorkshopWorldData load(CompoundTag tag,HolderLookup.Provider provider) {
        var data=new WorkshopWorldData();
        for (long position:tag.getLongArray("restored_consoles")) data.consoles.add(position);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        tag.putInt("schema_version",1);
        tag.putLongArray("restored_consoles",consoles.stream().mapToLong(Long::longValue).sorted().toArray());
        return tag;
    }
}
