package local.lostworld;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Test sources/resources are excluded from the distributable mod. Desert is a test-only evil biome. */
@GameTestHolder(LostWorld.ID)
@PrefixGameTestTemplate(false)
public final class ChapterGameTests {
    @GameTest(template="chapter_arena",timeoutTicks=200)
    @SuppressWarnings("removal")
    public static void starterLifecycleAndBoundary(GameTestHelper h) {
        var level=h.getLevel();
        var result=SettlementBuilder.place(level,h.absolutePos(new BlockPos(1,2,1)));
        var data=ChapterData.create(level);
        data.settlementBuilt(result.origin(),result.home());
        h.assertTrue(level.getBlockState(result.bed()).is(Blocks.RED_BED),"Player bed exists");
        h.assertTrue(level.getBlockState(result.origin().offset(10,1,22)).is(Blocks.IRON_DOOR),"Private home entrance");
        h.assertTrue(level.noCollision(new net.minecraft.world.phys.AABB(result.home())),"Spawn space clear");
        var chest=(ChestBlockEntity)level.getBlockEntity(result.origin().offset(14,1,32));
        h.assertTrue(chest!=null && chest.isEmpty(),"Starter storage has no free loot");
        ServerPlayer p=survivalPlayer(h);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ChapterEvents.arrive(p);
        h.assertTrue(GrowthService.data(p).mode()==GrowthMode.STANDARD,"Standard route selected");
        h.assertTrue(p.getMainHandItem().is(EBItems.starEater()),"Wake holding sword");
        h.assertTrue(p.getRespawnPosition().equals(result.bed()),"Respawn set to real bed");
        p.teleportTo(level,result.home().getX()+1.5,result.home().getY(),result.home().getZ()+0.5,0,0);
        ChapterEvents.arrive(p);
        h.assertTrue(p.getX()==result.home().getX()+1.5,"Repeated login does not teleport home again");
        h.assertTrue(p.getInventory().items.stream().filter(s->s.is(EBItems.starEater())).mapToInt(ItemStack::getCount).sum()==1,
                "No duplicate gift on rejoin");
        ServerPlayer chosen=h.makeMockServerPlayerInLevel();
        // makeMockServerPlayerInLevel fires login immediately; stage an existing chosen route.
        GrowthService.forceMode(chosen,GrowthMode.QUICK);
        ChapterEvents.gift(chosen);
        h.assertTrue(GrowthService.data(chosen).mode()==GrowthMode.QUICK,"Existing selected route preserved");
        ChapterData restored=ChapterData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        h.assertTrue(restored.hasSettlement() && restored.arrived(p.getUUID()) && !restored.state().defeated(),"NBT round-trip retains settlement and arrival");

        // Give the boundary a known collision-free last position, then a true biome-backed target.
        EvilBoundary.enforce(p);
        BlockPos target=result.origin().offset(24,1,33);
        String command="fillbiome "+coords(target.offset(-4,-4,-4))+" "+coords(target.offset(4,4,4))+" minecraft:desert";
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack()
                .withLevel(level).withPermission(4).withSuppressedOutput(),command);
        h.assertTrue(EvilBoundary.evil(level,target),"Real biome tag queried");
        h.assertTrue(EvilBoundary.blocksTarget(p,target),"Remote block interaction refused while locked");
        p.teleportTo(level,target.getX()+0.5,target.getY(),target.getZ()+0.5,0,0);
        h.assertTrue(EvilBoundary.enforce(p),"Entry into evil biome corrected");
        h.assertFalse(EvilBoundary.evil(level,p.blockPosition()),"Returned to safe biome");
        var denied=new BlockEvent.BreakEvent(level,target,Blocks.STONE.defaultBlockState(),p);
        NeoForge.EVENT_BUS.post(denied);
        h.assertTrue(denied.isCanceled(),"Break-event guard works across boundary");
        p.getInventory().setItem(p.getInventory().selected,new ItemStack(Items.STONE_PICKAXE));
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,result.origin().below(12),Blocks.STONE.defaultBlockState(),p));
        h.assertTrue(p.getAdvancements().getOrStartProgress(level.getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"first_mine"))).isDone(),"Mining event credits actual underground pickaxe use");
        // With the installed Confluence jar, exercise its REAL boss death listener.
        var type=BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(ChapterState.KING_SLIME));
        if (type.isPresent()) {
            LivingEntity king=(LivingEntity)type.get().create(level);
            h.assertTrue(king!=null,"Real King Slime creates");
            king.moveTo(result.origin().getX()+22.5,result.origin().getY()+1,result.origin().getZ()+14.5,0,0);
            level.addFreshEntity(king);
            king.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
            h.runAfterDelay(5,()-> {
                h.assertTrue(data.state().defeated(),"Real King Slime death unlocks shared world");
                verifyUnlock(h,p,target,data);
                LostWorld.LOG.info("CHAPTER_TEST_REAL_CONFLUENCE_KING_PASS");
                h.succeed();
            });
        } else {
            h.assertTrue(data.recordBoss(ChapterState.KING_SLIME),"Standalone state transition");
            verifyUnlock(h,p,target,data);
            LostWorld.LOG.info("CHAPTER_TEST_STANDALONE_PASS (Confluence boss not loaded)");
            h.succeed();
        }
    }
    private static void verifyUnlock(GameTestHelper h,ServerPlayer p,BlockPos target,ChapterData data) {
        h.assertFalse(EvilBoundary.blocksTarget(p,target),"Block access unlocks");
        ChapterData reload=ChapterData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(reload.state().defeated() && !reload.recordBoss(ChapterState.KING_SLIME),"Saved unlock is permanent and idempotent");
        ChapterEvents.arrive(p);
        h.assertTrue(p.getAdvancements().getOrStartProgress(h.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"unlock_evil"))).isDone(),"Late login receives world milestone");
    }
    private static String coords(BlockPos p) { return p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        // Vanilla's deprecated helper overrides isCreative() to ALWAYS return true.
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"chapter-test-player"),false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        return player;
    }
}
