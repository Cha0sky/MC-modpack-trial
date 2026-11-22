package local.lostworld;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class WorkshopFooting extends StructureProcessor {
    private static final MapCodec<WorkshopFooting> CODEC = MapCodec.unit(new WorkshopFooting());
    private static final DeferredRegister<StructureProcessorType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, LostWorld.ID);
    private static final Supplier<StructureProcessorType<WorkshopFooting>> TYPE = TYPES.register("workshop_footing", () -> () -> CODEC);

    static void register(IEventBus bus) { TYPES.register(bus); }

    @Override public StructureBlockInfo process(LevelReader reader, BlockPos origin, BlockPos pivot, StructureBlockInfo local,
            StructureBlockInfo placed, StructurePlaceSettings settings, @Nullable StructureTemplate template) {
        if (local.pos().getY() != 0 || placed.state().isAir() || !(reader instanceof WorldGenLevel world)) return placed;
        var clip = settings.getBoundingBox();
        if (clip != null && !clip.isInside(placed.pos())) return placed;
        BlockState floor = placed.state();
        var support = floor.is(Blocks.GRAVEL) || floor.is(Blocks.COARSE_DIRT) || floor.is(Blocks.MOSS_BLOCK)
                ? Blocks.DIRT : Blocks.COBBLESTONE;
        BlockPos.MutableBlockPos below = placed.pos().mutable();
        for (int depth = 0; depth < WorkshopTerrain.MAX_FILL; depth++) {
            below.move(Direction.DOWN);
            if (world.isOutsideBuildHeight(below)) break;
            BlockState existing = world.getBlockState(below);
            if (!existing.isAir() && !existing.liquid()) break;
            world.setBlock(below, support.defaultBlockState(), 2);
        }
        return placed;
    }

    @Override protected StructureProcessorType<?> getType() { return TYPE.get(); }
}
