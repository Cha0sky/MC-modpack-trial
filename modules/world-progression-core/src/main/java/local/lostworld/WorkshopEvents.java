package local.lostworld;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded server-side observations; never launches a client or probes unloaded terrain. */
public final class WorkshopEvents {
    private static final String PLAYER_KEY="lostworld_workshop";
    private static final String EBONY="confluence:ebony_log",SHADOW="confluence:shadow_log";
    private static final String IRON="create:iron_sheet",COPPER="create:copper_sheet";
    private static final String FIBER=LostWorld.ID+":tainted_fiber",BINDER=LostWorld.ID+":resonant_binder";
    private static final String TUNER=LostWorld.ID+":rough_tuner";
    private static final Set<String> TRACKED=Set.of(EBONY,SHADOW,IRON,COPPER,FIBER,BINDER,TUNER);
    private static final Set<String> MACHINES=Set.of("create:mechanical_press","create:millstone","create:mechanical_mixer");
    private static final List<LivingDeathEvent> PENDING_DEATHS=new ArrayList<>();

    private WorkshopEvents() {}

    public static boolean enabled(ServerPlayer player) {
        ChapterData chapter=ChapterData.find(player.serverLevel());
        return chapter!=null && chapter.state().defeated();
    }

    public static CompoundTag data(ServerPlayer player) {
        CompoundTag persistent=player.getPersistentData();
        if (!persistent.contains(PLAYER_KEY,Tag.TAG_COMPOUND)) persistent.put(PLAYER_KEY,new CompoundTag());
        return persistent.getCompound(PLAYER_KEY);
    }

    public static void mark(ServerPlayer player,String shortName) {
        if (!enabled(player)) return;
        data(player).putBoolean("done_"+shortName,true);
        ChapterEvents.award(player,"workshop/"+shortName);
    }

    public static void addSpent(ServerPlayer player,String itemId,int count) {
        if (count<=0) return;
        CompoundTag spent=child(data(player),"spent");
        spent.putInt(itemId,spent.getInt(itemId)+count);
    }

    public static void syncRestored(ServerLevel level) {
        if (!WorkshopWorldData.get(level).restored()) return;
        for (ServerPlayer player:level.getServer().getPlayerList().getPlayers()) syncRestored(player);
    }

    private static void syncRestored(ServerPlayer player) {
        if (!enabled(player) || !WorkshopWorldData.get(player.serverLevel()).restored()) return;
        mark(player,"arrival");
        mark(player,"calibrate");
        mark(player,"restore");
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        CompoundTag original=event.getOriginal().getPersistentData();
        if (original.contains(PLAYER_KEY,Tag.TAG_COMPOUND))
            event.getEntity().getPersistentData().put(PLAYER_KEY,original.getCompound(PLAYER_KEY).copy());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && enabled(player)) {
            mark(player,"commission");
            syncRestored(player);
        }
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChapterData chapter=ChapterData.find(player.serverLevel());
        if (chapter==null || !chapter.state().active()) return;
        String id=BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).toString();
        int count=event.getCrafting().getCount();
        if (id.equals("create:whisk")) addSpent(player,IRON,5*count);
        if (id.equals(TUNER)) {
            addSpent(player,IRON,2*count);
            addSpent(player,COPPER,2*count);
            addSpent(player,BINDER,count);
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount%20!=0) return;
        ChapterData chapter=ChapterData.find(player.serverLevel());
        if (chapter==null || !chapter.state().active()) return;
        CompoundTag progress=data(player);
        Map<String,Integer> inventory=new HashMap<>();
        for (int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            ItemStack stack=player.getInventory().getItem(slot);
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (TRACKED.contains(id)) inventory.merge(id,stack.getCount(),Integer::sum);
            if (!progress.getBoolean("done_photography")) observePhotograph(player,stack);
        }
        CompoundTag totals=child(progress,"observed"),spent=child(progress,"spent");
        for (String id:TRACKED) totals.putInt(id,WorkshopProgress.observed(
                totals.getInt(id),inventory.getOrDefault(id,0),spent.getInt(id)));
        // A deterministic downstream product proves its consumed intermediate ingredients.
        // This lets a real automated line feed its basin without requiring extra pickup chores.
        int fiber=Math.max(totals.getInt(FIBER),totals.getInt(BINDER)*2);
        totals.putInt(FIBER,fiber);
        int samples=Math.max(totals.getInt(EBONY)+totals.getInt(SHADOW),(fiber+1)/2);
        player.serverLevel().getBiome(player.blockPosition()).unwrapKey().ifPresent(key->{
            String id=key.location().toString();
            if (id.startsWith("confluence:the_corruption")) progress.putBoolean("visited_corruption",true);
            if (id.startsWith("confluence:the_crimson")) progress.putBoolean("visited_crimson",true);
        });
        if (!progress.getBoolean("done_arrival") || strings(progress,"exploration_types").size()<3)
            observeStructures(player);
        if (!enabled(player)) return;
        mark(player,"commission");
        if (samples>=4 && (progress.getBoolean("visited_corruption") || progress.getBoolean("visited_crimson"))) mark(player,"survey");
        if (totals.getInt(EBONY)>=4 && totals.getInt(SHADOW)>=4
                && progress.getBoolean("visited_corruption") && progress.getBoolean("visited_crimson")) mark(player,"both_regions");
        if (totals.getInt(IRON)>=7 && totals.getInt(COPPER)>=2) mark(player,"plates");
        if (fiber>=8) mark(player,"fiber");
        if (totals.getInt(BINDER)>=4) mark(player,"binder");
        if (totals.getInt(TUNER)>=1) mark(player,"tuner");
        if (totals.getInt(COPPER)>=16) mark(player,"batch_copper");
        if (strings(progress,"exploration_types").size()>=3) mark(player,"field_notes");
        if (strings(progress,"photographs").size()>=3) mark(player,"photography");
        if (!progress.getBoolean("done_power") && rotatingMachineNearby(player)) {
            int seconds=progress.getInt("powered_seconds")+1;
            progress.putInt("powered_seconds",seconds);
            if (seconds>=10) mark(player,"power");
        }
        syncRestored(player);
    }

    private static boolean rotatingMachineNearby(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        BlockPos centre=player.blockPosition();
        for (BlockPos pos:BlockPos.betweenClosed(centre.offset(-6,-6,-6),centre.offset(6,6,6))) {
            if (pos.distSqr(centre)>36 || !level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) || kinetic.getSpeed()==0) continue;
            if (MACHINES.contains(BuiltInRegistries.BLOCK.getKey(kinetic.getBlockState().getBlock()).toString())) return true;
        }
        return false;
    }

    private static void observeStructures(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        BlockPos pos=player.blockPosition();
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if (chunk==null) return;
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry:chunk.getAllReferences().entrySet()) {
            var key=registry.getKey(entry.getKey());
            if (key==null) continue;
            String id=key.toString(),kind=WorkshopProgress.explorationKind(id);
            boolean workshop=id.equals(LostWorld.ID+":abandoned_workshop");
            if (!workshop && kind.isEmpty()) continue;
            for (long reference:entry.getValue()) {
                ChunkPos startPos=new ChunkPos(reference);
                var startChunk=level.getChunkSource().getChunkNow(startPos.x,startPos.z);
                if (startChunk==null) continue;
                var start=startChunk.getStartForStructure(entry.getKey());
                if (start==null || !start.isValid() || !start.getBoundingBox().isInside(pos)) continue;
                if (workshop) mark(player,"arrival");
                else if (strings(data(player),"exploration_types").size()<3) addString(data(player),"exploration_types",kind);
                break;
            }
        }
    }

    private static void observePhotograph(ServerPlayer player,ItemStack stack) {
        if (stack.getItem() instanceof PhotographItem photograph) {
            var frame=photograph.getFrame(stack);
            if (!frame.identifier().isEmpty() && frame.isTakenBy(player)
                    && strings(data(player),"photographs").size()<3)
                addString(data(player),"photographs",frame.identifier().toValueString());
        }
        var bundle=stack.get(Exposure.DataComponents.STACKED_PHOTOGRAPHS);
        if (bundle!=null) for (ItemStack photo:bundle.photographs()) observePhotograph(player,photo);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity().level() instanceof ServerLevel
                && BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString().equals("mowziesmobs:ferrous_wroughtnaut"))
            PENDING_DEATHS.add(event);
    }

    @SubscribeEvent
    public static void finishDeaths(ServerTickEvent.Post event) {
        for (LivingDeathEvent death:List.copyOf(PENDING_DEATHS)) {
            var boss=death.getEntity();
            if (death.isCanceled() || !boss.isDeadOrDying() || !(boss.level() instanceof ServerLevel level)) continue;
            var participants=new HashSet<ServerPlayer>();
            for (ServerPlayer player:level.players())
                if (!player.isSpectator() && player.distanceToSqr(boss)<=64*64) participants.add(player);
            if (death.getSource().getEntity() instanceof ServerPlayer player) participants.add(player);
            if (boss.getKillCredit() instanceof ServerPlayer player) participants.add(player);
            for (ServerPlayer player:participants) mark(player,"wroughtnaut");
        }
        PENDING_DEATHS.clear();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { PENDING_DEATHS.clear(); }

    private static CompoundTag child(CompoundTag parent,String key) {
        if (!parent.contains(key,Tag.TAG_COMPOUND)) parent.put(key,new CompoundTag());
        return parent.getCompound(key);
    }

    private static Set<String> strings(CompoundTag parent,String key) {
        ListTag list=parent.getList(key,Tag.TAG_STRING);
        Set<String> values=new HashSet<>();
        for (int i=0;i<list.size();i++) values.add(list.getString(i));
        return values;
    }

    private static void addString(CompoundTag parent,String key,String value) {
        Set<String> values=strings(parent,key);
        if (!values.add(value)) return;
        ListTag list=new ListTag();
        values.stream().sorted().forEach(v->list.add(StringTag.valueOf(v)));
        parent.put(key,list);
    }
}
