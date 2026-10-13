package local.lostworld;

import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Explicit opt-in standalone smoke test in an isolated normal-terrain world. Excluded from release. */
@EventBusSubscriber(modid=LostWorld.ID)
public final class WorldGenerationSmoke {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("lostworld.smokeWorldGeneration")) return;
        var level=event.getServer().overworld();
        var data=ChapterData.find(level);
        if (data==null || !data.hasSettlement() || !data.state().active())
            throw new AssertionError("Fresh-world settlement did not generate");
        if (!level.getBlockState(data.settlement().offset(6,1,32)).is(Blocks.RED_BED)
                || !level.getSharedSpawnPos().equals(data.home()) || EvilBoundary.evil(level,data.home()))
            throw new AssertionError("Invalid generated player home/spawn");
        LostWorld.LOG.info("CHAPTER_TEST_NORMAL_WORLD_GENERATION_PASS origin={} home={}",data.settlement(),data.home());
        event.getServer().halt(false);
    }
}
