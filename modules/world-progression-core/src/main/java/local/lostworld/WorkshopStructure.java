package local.lostworld;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorkshopStructure extends Structure {
    private static final MapCodec<WorkshopStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(settingsCodec(instance), StructureTemplatePool.CODEC.fieldOf("start_pool")
                    .forGetter(structure -> structure.pool)).apply(instance, WorkshopStructure::new));
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, LostWorld.ID);
    private static final Supplier<StructureType<WorkshopStructure>> TYPE =
            TYPES.register("workshop", () -> () -> CODEC);
    // The masonry hall, machine wing and exterior stair in abandoned_workshop.nbt.
    private static final int[][] PAD = {{2,3}, {12,3}, {22,3}, {2,10}, {12,10}, {22,10}, {2,17}, {12,17}, {22,16}};
    private final Holder<StructureTemplatePool> pool;

    private WorkshopStructure(StructureSettings settings, Holder<StructureTemplatePool> pool) {
        super(settings);
        this.pool = pool;
    }

    static void register(IEventBus bus) {
        TYPES.register(bus);
        WorkshopFooting.register(bus);
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var template = pool.value().getRandomTemplate(context.random());
        if (template == EmptyPoolElement.INSTANCE) return Optional.empty();
        var manager = context.structureTemplateManager();
        BlockPos center = context.chunkPos().getMiddleBlockPosition(0);
        Map<BlockPos, Integer> ground = new HashMap<>();
        for (Rotation rotation : Rotation.getShuffled(context.random())) {
            BoundingBox bounds = template.getBoundingBox(manager, BlockPos.ZERO, rotation);
            BlockPos origin = center.offset(-bounds.getCenter().getX(), 0, -bounds.getCenter().getZ());
            int front = sample(context, ground, origin, rotation, 8, -1);
            int side = sample(context, ground, origin, rotation, -1, 11);
            if (front == WorkshopTerrain.UNSUITABLE || side == WorkshopTerrain.UNSUITABLE || Math.abs(front - side) > 2) continue;
            int[] heights = new int[PAD.length];
            for (int i = 0; i < PAD.length; i++) {
                heights[i] = sample(context, ground, origin, rotation, PAD[i][0], PAD[i][1]);
            }
            int floor = WorkshopTerrain.floor(heights, front, side);
            if (floor == WorkshopTerrain.UNSUITABLE || floor + bounds.maxY() >= context.heightAccessor().getMaxBuildHeight()) continue;
            BlockPos position = origin.above(floor);
            var piece = new PoolElementStructurePiece(manager, template, position, template.getGroundLevelDelta(), rotation,
                    bounds.moved(position.getX(), position.getY(), position.getZ()), LiquidSettings.IGNORE_WATERLOGGING);
            return Optional.of(new GenerationStub(center.above(floor + 1), builder -> builder.addPiece(piece)));
        }
        return Optional.empty();
    }

    private static int sample(GenerationContext context, Map<BlockPos, Integer> cache, BlockPos origin, Rotation rotation,
                              int localX, int localZ) {
        BlockPos column = origin.offset(new BlockPos(localX, 0, localZ).rotate(rotation));
        return cache.computeIfAbsent(column, pos -> {
            var generator = context.chunkGenerator();
            int surface = generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            int floor = generator.getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            return floor == surface && floor > context.heightAccessor().getMinBuildHeight() ? floor - 1 : WorkshopTerrain.UNSUITABLE;
        });
    }

    @Override public StructureType<?> type() { return TYPE.get(); }
}
