package local.lostworld;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ChapterEvents {
    private static final java.util.List<LivingDeathEvent> PENDING_DEATHS = new java.util.ArrayList<>();
    private ChapterEvents() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void newWorld(LevelEvent.CreateSpawnPosition event) {
        if (event.isCanceled() || Boolean.getBoolean("lostworld.disableAutomaticSettlement")
                || !(event.getLevel() instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD) return;
        // No terrain generation here: the vanilla progress listener is already counting chunks.
        ChapterData.create(level).requestSettlement();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void started(ServerStartedEvent event) {
        ServerLevel level=event.getServer().overworld();
        ChapterData data=ChapterData.find(level);
        if (data==null || !data.settlementPending() || data.hasSettlement()) return;
        LostWorld.LOG.info("原版出生区已准备完成，开始有限范围的启程聚落生成");
        SettlementBuilder.Result result = SettlementBuilder.createForMarkedWorld(level);
        if (result != null) {
            level.setDefaultSpawnPos(result.home(),180);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        // Lock the chapter's default before EvolvingBlade's NORMAL-priority login snapshot.
        if (event.getEntity() instanceof ServerPlayer player) arrive(player);
    }
    public static void arrive(ServerPlayer player) {
        ChapterData data = ChapterData.find(player.serverLevel());
        if (data == null || !(data.settlementPending() || data.hasSettlement())) return;
        // Upgrade older failed-generation worlds without touching unmarked existing worlds.
        if (data.settlementPending()) data.requestSettlement();
        // The sword and chapter gates must not depend on whether a house could be placed.
        if (!data.arrived(player.getUUID())) gift(player);
        if (!data.hasSettlement()) { EvilBoundary.enforce(player); return; }
        if (!data.arrived(player.getUUID())) {
            var home = data.home();
            player.teleportTo(player.server.overworld(),home.getX()+0.5,home.getY(),home.getZ()+0.5,180,0);
            player.setRespawnPosition(Level.OVERWORLD,data.bed(),180,false,false);
            data.markArrival(player.getUUID());
            player.sendSystemMessage(Component.literal("你在一间小屋里醒来。村民说，找到你时，你身边只有这把剑。这里可以成为你的第一个家。"));
        }
        award(player,"arrive");
        if (data.state().defeated()) award(player,"unlock_evil");
        EvilBoundary.enforce(player);
    }
    public static void gift(ServerPlayer player) {
        if (!GrowthService.data(player).hasMode()) GrowthService.chooseMode(player,GrowthMode.STANDARD);
        else GrowthService.requestGift(player);
        // Put the delivered sword in the selected hand without inserting another stack.
        for (int slot=0;slot<9;slot++) {
            if (player.getInventory().getItem(slot).is(EBItems.starEater())) {
                player.getInventory().selected=slot;
                break;
            }
        }
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean blocked = EvilBoundary.enforce(player);
        if (blocked || player.tickCount%20!=0 || player.isSpectator()) return;
        ChapterData data = ChapterData.find(player.serverLevel());
        if (data == null || !data.hasSettlement()) return;
        if (player.serverLevel().dimension()==Level.OVERWORLD
                && player.blockPosition().distSqr(data.home())>96*96) award(player,"explore");
        // Sync a world-wide milestone for people joining after the initial boss kill.
        if (data.state().defeated()) award(player,"unlock_evil");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.isCanceled()) return;
        if (EvilBoundary.blocksTarget(player,event.getPos())) { event.setCanceled(true); return; }
        ChapterData data = ChapterData.find(player.serverLevel());
        if (data != null && data.hasSettlement() && !player.isCreative() && !player.isSpectator()
                && player.serverLevel().dimension()==Level.OVERWORLD
                && event.getPos().getY()<=data.settlement().getY()-12
                && event.getState().is(BlockTags.MINEABLE_WITH_PICKAXE)
                && player.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) award(player,"first_mine");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        if (!ChapterState.KING_SLIME.equals(id)) return;
        // End-of-tick commit: later listeners may still cancel the death event.
        PENDING_DEATHS.add(event);
    }
    @SubscribeEvent
    public static void finishDeaths(ServerTickEvent.Post event) {
        for (LivingDeathEvent death : java.util.List.copyOf(PENDING_DEATHS)) {
            var boss = death.getEntity();
            if (death.isCanceled() || !boss.isDeadOrDying() || !(boss.level() instanceof ServerLevel level)) continue;
            ChapterData data = ChapterData.find(level);
            if (data != null && data.recordBoss(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString())) {
                for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) award(player,"unlock_evil");
                level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                        "史莱姆王已被击败。通往猩红与腐化区域的阻隔消散了。"),false);
                LostWorld.LOG.info("第一章里程碑：史莱姆王已击败，邪恶地形已开放");
            }
        }
        PENDING_DEATHS.clear();
    }
    @SubscribeEvent
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && EvilBoundary.blocksTarget(p,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && (EvilBoundary.blocksTarget(p,event.getPos())
                || event.getFace()!=null && EvilBoundary.blocksTarget(p,event.getPos().relative(event.getFace())))) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent
    public static void entityClick(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p && EvilBoundary.blocksTarget(p,event.getTarget().blockPosition())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
        }
    }
    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && EvilBoundary.blocksTarget(p,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) EvilBoundary.enforce(p);
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { EvilBoundary.clearPlayer(event.getEntity().getUUID()); }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { EvilBoundary.clear(); PENDING_DEATHS.clear(); }
    public static void award(ServerPlayer player,String name) {
        var advancement = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(LostWorld.ID,name));
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone())
            player.getAdvancements().award(advancement,"done");
    }
}
