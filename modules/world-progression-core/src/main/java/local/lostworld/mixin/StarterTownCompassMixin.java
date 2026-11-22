package local.lostworld.mixin;

import java.util.List;
import local.lostworld.ChapterData;
import local.lostworld.StarterTownLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The town is built once after spawn preparation, outside the chunk generator. */
@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.worker.StructureSearchWorker", remap = false)
abstract class StarterTownCompassMixin {
    @Shadow protected ServerLevel level;
    @Shadow protected List<Structure> structureSet;
    @Shadow protected BlockPos currentPos;
    @Shadow protected List<BlockPos> prevPos;
    @Shadow protected boolean finished;
    @Shadow protected int samples;
    @Shadow public abstract boolean hasWork();
    @Shadow protected abstract void succeed(BlockPos pos, Structure structure);
    @Shadow protected abstract void fail();

    @Inject(method = "start()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/WorldWorkerManager;addWorker(Lnet/neoforged/neoforge/common/WorldWorkerManager$IWorker;)V"),
            cancellable = true, require = 1)
    private void lostworld$locateTown(CallbackInfo callback) {
        if (structureSet.size() != 1) return;
        Structure structure = structureSet.getFirst();
        if (!StarterTownLocator.isTown(level, structure)) return;

        callback.cancel();
        ChapterData chapter = level.dimension() == Level.OVERWORLD ? ChapterData.find(level) : null;
        BlockPos home = StarterTownLocator.nextHome(chapter, prevPos);
        if (home != null) {
            currentPos = home;
            // A completed town exists even in a world with vanilla structures disabled.
            // hasWork still applies the compass radius, sample and next-search limits.
            finished = false;
            if (hasWork()) {
                samples++;
                prevPos.add(currentPos);
                succeed(currentPos, structure);
                return;
            }
        }
        fail();
    }
}
