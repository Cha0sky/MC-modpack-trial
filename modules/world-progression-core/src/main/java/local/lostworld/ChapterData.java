package local.lostworld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChapterData extends SavedData {
    public static final String KEY = "lostworld_chapter1";
    public static final Factory<ChapterData> FACTORY = new Factory<>(ChapterData::new, ChapterData::load);
    private ChapterState state = new ChapterState(false, false);
    private BlockPos settlement;
    private BlockPos home;
    private BlockPos bed;
    private boolean settlementPending;
    private final Set<UUID> arrivals = new HashSet<>();

    public static ChapterData find(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().get(FACTORY, KEY);
    }
    public static ChapterData create(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }
    public ChapterState state() { return state; }
    public BlockPos settlement() { return settlement; }
    public BlockPos home() { return home; }
    public BlockPos bed() { return bed!=null ? bed : settlement==null ? null : settlement.offset(6,1,32); }
    public boolean hasSettlement() { return settlement != null && home != null; }
    public boolean settlementPending() { return settlementPending; }
    public void requestSettlement() { if (!hasSettlement()) { settlementPending=true; setDirty(); } }
    public boolean arrived(UUID player) { return arrivals.contains(player); }
    public void markArrival(UUID player) { if (arrivals.add(player)) setDirty(); }
    public void settlementBuilt(BlockPos origin, BlockPos spawn) {
        settlementBuilt(origin,spawn,origin.offset(6,1,32));
    }
    public void settlementBuilt(BlockPos origin,BlockPos spawn,BlockPos actualBed) {
        // Preserve the old bed-offset contract as well as the explicit schema-3 bed.
        var anchor=SettlementGeometry.legacyAnchor(new SettlementGeometry.Point(actualBed.getX(),actualBed.getY(),actualBed.getZ()));
        settlement = new BlockPos(anchor.x(),anchor.y(),anchor.z()); home = spawn.immutable(); bed=actualBed.immutable();
        settlementPending=false; state.activate(); setDirty();
    }
    public boolean recordBoss(String id) { boolean changed = state.defeat(id); if (changed) setDirty(); return changed; }
    public void setDefeated(boolean value) { state.setDefeated(value); setDirty(); }
    public static ChapterData load(CompoundTag tag, HolderLookup.Provider provider) {
        ChapterData data = new ChapterData();
        data.state = new ChapterState(tag.getBoolean("active"), tag.getBoolean("king_slime_defeated"));
        data.settlementPending = tag.getBoolean("settlement_pending");
        if (tag.contains("settlement", Tag.TAG_LONG)) data.settlement = BlockPos.of(tag.getLong("settlement"));
        if (tag.contains("home", Tag.TAG_LONG)) data.home = BlockPos.of(tag.getLong("home"));
        if (tag.contains("bed", Tag.TAG_LONG)) data.bed = BlockPos.of(tag.getLong("bed"));
        ListTag players = tag.getList("arrivals", Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) {
            try { data.arrivals.add(UUID.fromString(players.getString(i))); }
            catch (IllegalArgumentException ignored) { LostWorld.LOG.warn("忽略损坏的聚落到达记录"); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("schema_version", 3);
        tag.putBoolean("settlement_pending", settlementPending);
        tag.putBoolean("active", state.active()); tag.putBoolean("king_slime_defeated", state.defeated());
        if (settlement != null) tag.putLong("settlement", settlement.asLong());
        if (home != null) tag.putLong("home", home.asLong());
        if (bed != null) tag.putLong("bed", bed.asLong());
        ListTag players = new ListTag();
        arrivals.stream().map(UUID::toString).sorted().forEach(id -> players.add(StringTag.valueOf(id)));
        tag.put("arrivals", players); return tag;
    }
}
