package local.lostworld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-shared route facts and the first visited site of each category in each dimension. */
public final class HeritageWorldData extends SavedData {
    public static final String KEY="lostworld_heritage";
    public static final Factory<HeritageWorldData> FACTORY=new Factory<>(HeritageWorldData::new,HeritageWorldData::load);
    public record Site(String structure,String dimension,BlockPos pos) {}
    private int routes;
    private final List<Site> sites=new ArrayList<>();
    private final Set<String> engineeringEvidence=new HashSet<>();

    public static HeritageWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY,KEY);
    }

    public int routes() { return routes; }
    public boolean ready() { return HeritageProgress.ready(routes); }
    public List<Site> sites() { return List.copyOf(sites); }

    Set<String> engineeringEvidence() { return Set.copyOf(engineeringEvidence); }

    boolean recordEngineering(String advancement) {
        if (!EngineeringCommission.SOURCES.contains(advancement) || !engineeringEvidence.add(advancement)) return false;
        setDirty();
        return true;
    }

    public boolean contribute(int bit) {
        if (bit==HeritageProgress.ENGINEERING && (routes & bit)==0
                && !EngineeringCommission.ready(engineeringEvidence)) return false;
        int next=HeritageProgress.contribute(routes,bit);
        if (next==routes) return false;
        routes=next;
        setDirty();
        return true;
    }

    public boolean discover(String structure,String dimension,BlockPos pos) {
        String kind=HeritageProgress.siteKind(structure);
        if (kind.isEmpty() || sites.stream().anyMatch(site->site.dimension.equals(dimension)
                && HeritageProgress.siteKind(site.structure).equals(kind))) return false;
        sites.add(new Site(structure,dimension,pos.immutable()));
        setDirty();
        return true;
    }

    public static HeritageWorldData load(CompoundTag tag,HolderLookup.Provider provider) {
        HeritageWorldData data=new HeritageWorldData();
        data.routes=tag.getInt("routes")&7;
        ListTag evidence=tag.getList("engineering_evidence",Tag.TAG_STRING);
        for (int i=0;i<evidence.size();i++) data.recordEngineering(evidence.getString(i));
        ListTag sites=tag.getList("sites",Tag.TAG_COMPOUND);
        for (int i=0;i<sites.size();i++) {
            CompoundTag site=sites.getCompound(i);
            data.discover(site.getString("structure"),site.getString("dimension"),BlockPos.of(site.getLong("pos")));
        }
        data.setDirty(false);
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        tag.putInt("schema_version",2);
        tag.putInt("routes",routes);
        ListTag evidence=new ListTag();
        engineeringEvidence.stream().sorted().forEach(value->evidence.add(StringTag.valueOf(value)));
        tag.put("engineering_evidence",evidence);
        ListTag locations=new ListTag();
        for (Site site:sites) {
            CompoundTag entry=new CompoundTag();
            entry.putString("structure",site.structure);
            entry.putString("dimension",site.dimension);
            entry.putLong("pos",site.pos.asLong());
            locations.add(entry);
        }
        tag.put("sites",locations);
        return tag;
    }
}
