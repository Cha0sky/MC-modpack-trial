package local.lostworld;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Each player's real route completion contributes one shared fact; any two routes converge. */
public final class HeritageEvents {
    private static final String IRON_ADV="irons_spellbooks:irons_spellbooks/";
    private static final String GOETY_ADV="goety:goety/";
    private static final String ARCHIVE=LostWorld.ID+":heritage_archive";
    private static final List<LivingDeathEvent> PENDING_DEATHS=new ArrayList<>();

    private HeritageEvents() {}

    public static boolean enabled(ServerPlayer player) {
        return WorkshopWorldData.get(player.serverLevel()).restored();
    }

    public static CompoundTag data(ServerPlayer player) {
        CompoundTag workshop=WorkshopEvents.data(player);
        if (!workshop.contains("heritage",Tag.TAG_COMPOUND)) workshop.put("heritage",new CompoundTag());
        return workshop.getCompound("heritage");
    }

    public static void mark(ServerPlayer player,String name) {
        if (!enabled(player)) return;
        data(player).putBoolean("done_"+name,true);
        ChapterEvents.award(player,"heritage/"+name);
    }

    public static void sync(ServerPlayer player) {
        if (!enabled(player)) return;
        HeritageWorldData world=HeritageWorldData.get(player.serverLevel());
        int routes=world.routes();
        mark(player,"welcome");
        if ((routes&HeritageProgress.ENGINEERING)!=0) {
            mark(player,"engineering_heat");
            mark(player,"engineering_assembly");
            mark(player,"engineering_record");
        }
        if ((routes&HeritageProgress.ARCANE)!=0) {
            mark(player,"arcane_foundation");
            mark(player,"arcane_practice");
            mark(player,"arcane_record");
        }
        if ((routes&HeritageProgress.EXPEDITION)!=0) {
            mark(player,"expedition_sites");
            mark(player,"expedition_challenge");
            mark(player,"expedition_record");
        }
        if (world.ready()) {
            mark(player,"convergence");
            if (data(player).getBoolean("archive_owned")) mark(player,"archive_craft");
        }
        if ((routes&7)==7) mark(player,"all_three");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void spellCast(SpellOnCastEvent event) {
        // This non-cancellable event is posted inside the actual castSpell execution path.
        // It records releasing a spell, not whether its projectile subsequently hits a target.
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()
                && event.getCastSource()==CastSource.SPELLBOOK) data(player).putBoolean("iron_spell_cast",true);
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).toString().equals(ARCHIVE)) {
            data(player).putBoolean("archive_owned",true);
            sync(player);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString().equals(ARCHIVE)) {
            data(player).putBoolean("archive_owned",true);
            sync(player);
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount%20!=0) return;
        ChapterData chapter=ChapterData.find(player.serverLevel());
        if ((chapter==null || !chapter.state().active()) && !enabled(player)) return;
        CompoundTag progress=data(player);
        int brass=0,precision=0;
        for (int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            ItemStack stack=player.getInventory().getItem(slot);
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (id.equals("create:brass_ingot")) brass+=stack.getCount();
            if (id.equals("create:precision_mechanism")) precision+=stack.getCount();
            if (id.equals(ARCHIVE) && !stack.isEmpty()) progress.putBoolean("archive_owned",true);
        }
        progress.putInt("brass_max",Math.max(brass,progress.getInt("brass_max")));
        if (nativeDone(player,"create:precision_mechanism")) precision=Math.max(precision,1);
        progress.putInt("precision_max",Math.max(precision,progress.getInt("precision_max")));
        observeStructures(player);
        if (nativeDone(player,LostWorld.ID+":workshop/wroughtnaut")
                || nativeDone(player,"goety:goety/kill_a_skull_lord")) progress.putBoolean("challenge",true);
        if (!enabled(player)) return;

        boolean heat=nativeDone(player,"create:burner") && progress.getInt("brass_max")>=8;
        boolean assembly=nativeDone(player,"create:deployer") && nativeDone(player,"create:spout")
                && progress.getInt("precision_max")>=1;
        if (heat) mark(player,"engineering_heat");
        if (assembly) mark(player,"engineering_assembly");

        boolean ironFoundation=nativeDone(player,IRON_ADV+"make_inscription_table")
                && nativeDone(player,IRON_ADV+"spell_book_iron") && nativeDone(player,IRON_ADV+"spell_book_equip");
        boolean ironCast=progress.getBoolean("iron_spell_cast");
        boolean goetyFoundation=nativeDone(player,GOETY_ADV+"craft_dark_wand");
        boolean goetySummon=nativeDone(player,GOETY_ADV+"summon_zombie");
        boolean goetyKill=nativeDone(player,GOETY_ADV+"servant_kill_mob");
        boolean arcane=HeritageProgress.arcaneComplete(ironFoundation,ironCast,goetyFoundation,goetySummon,goetyKill);
        if (ironFoundation || goetyFoundation) mark(player,"arcane_foundation");
        if (arcane) mark(player,"arcane_practice");

        boolean sites=strings(progress,"expedition_sites").size()>=2;
        boolean challenge=progress.getBoolean("challenge");
        if (sites) mark(player,"expedition_sites");
        if (challenge) mark(player,"expedition_challenge");

        HeritageWorldData world=HeritageWorldData.get(player.serverLevel());
        boolean changed=false;
        if (heat && assembly) changed|=world.contribute(HeritageProgress.ENGINEERING);
        if (arcane) changed|=world.contribute(HeritageProgress.ARCANE);
        if (sites && challenge) changed|=world.contribute(HeritageProgress.EXPEDITION);
        if (changed) for (ServerPlayer online:player.server.getPlayerList().getPlayers()) sync(online);
        else sync(player);
    }

    private static boolean nativeDone(ServerPlayer player,String id) {
        var advancement=player.server.getAdvancements().get(ResourceLocation.parse(id));
        return advancement!=null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static void observeStructures(ServerPlayer player) {
        ServerLevel level=player.serverLevel();
        BlockPos pos=player.blockPosition();
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if (chunk==null) return;
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry:chunk.getAllReferences().entrySet()) {
            var key=registry.getKey(entry.getKey());
            if (key==null || HeritageProgress.siteKind(key.toString()).isEmpty()) continue;
            String id=key.toString();
            for (long reference:entry.getValue()) {
                ChunkPos startPos=new ChunkPos(reference);
                var startChunk=level.getChunkSource().getChunkNow(startPos.x,startPos.z);
                if (startChunk==null) continue;
                var start=startChunk.getStartForStructure(entry.getKey());
                if (start==null || !start.isValid() || !start.getBoundingBox().isInside(pos)) continue;
                HeritageWorldData.get(level).discover(id,level.dimension().location().toString(),
                        start.getBoundingBox().getCenter());
                if (HeritageProgress.expeditionStructure(id)) addString(data(player),"expedition_sites",id);
                String survey = HeritageProgress.surveyQuest(id);
                if (!survey.isEmpty()) ChapterEvents.award(player, "relic_expeditions/" + survey);
                break;
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel)) return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        if (HeritageProgress.acceptedChallenge(id) || !HeritageProgress.bossContract(id).isEmpty())
            PENDING_DEATHS.add(event);
    }

    @SubscribeEvent
    public static void finishDeaths(ServerTickEvent.Post event) {
        for (LivingDeathEvent death:List.copyOf(PENDING_DEATHS)) {
            var boss=death.getEntity();
            if (death.isCanceled() || !boss.isDeadOrDying() || !(boss.level() instanceof ServerLevel level)) continue;
            Set<ServerPlayer> participants=new HashSet<>();
            for (ServerPlayer player:level.players())
                if (!player.isSpectator() && player.distanceToSqr(boss)<=64*64) participants.add(player);
            if (death.getSource().getEntity() instanceof ServerPlayer player) participants.add(player);
            if (boss.getKillCredit() instanceof ServerPlayer player) participants.add(player);
            String contract = HeritageProgress.bossContract(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString());
            for (ServerPlayer player:participants) {
                if (contract.isEmpty()) data(player).putBoolean("challenge",true);
                else ChapterEvents.award(player, "relic_expeditions/" + contract);
            }
        }
        PENDING_DEATHS.clear();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { PENDING_DEATHS.clear(); }

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
