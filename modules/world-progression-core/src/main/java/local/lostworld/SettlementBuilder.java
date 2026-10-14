package local.lostworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Places our blueprint only in a marked new world, after vanilla spawn preparation. */
public final class SettlementBuilder {
    public record Result(BlockPos origin, BlockPos home, BlockPos bed, int blocks) {}
    private SettlementBuilder() {}

    public static Result createForMarkedWorld(ServerLevel level) {
        return ModularSettlementBuilder.create(level);
    }

    private static int groundHeight(ServerLevel level,int x,int z) {
        // Heightmaps include tree trunks: compare actual ground rather than forest crowns.
        int y=surfaceHeight(level,x,z)-1;
        while (y>level.getMinBuildHeight()+8) {
            BlockState state=level.getBlockState(new BlockPos(x,y,z));
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                    || state.getFluidState().isEmpty() && !state.isSolidRender(level,new BlockPos(x,y,z))) y--;
            else break;
        }
        return y;
    }
    private static int surfaceHeight(ServerLevel level,int x,int z) {
        // During initial spawn, generated chunks may not yet be in Level's visible chunk map.
        return level.getChunk(x>>4,z>>4).getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1;
    }

    /** Test hook places only at an explicit isolated position; no saved-data activation. */
    public static Result place(ServerLevel level, BlockPos origin) {
        try (var stream = SettlementBuilder.class.getResourceAsStream(
                "/data/world_progression_core/settlement/starter.json")) {
            if (stream == null) throw new IllegalStateException("Missing starter settlement blueprint");
            JsonObject blueprint = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            List<BlockState> palette = new ArrayList<>();
            for (var entry : blueprint.getAsJsonArray("palette")) {
                palette.add(BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK),entry.getAsString(),false).blockState());
            }
            // A shallow natural foundation and air clearance; no loot-table containers.
            for (int x=0;x<45;x++) for (int z=0;z<39;z++) {
                int surface = groundHeight(level,origin.getX()+x,origin.getZ()+z);
                for (int y=Math.max(-8,surface-origin.getY());y<0;y++)
                    level.setBlock(origin.offset(x,y,z),Blocks.DIRT.defaultBlockState(),2);
                int clearTop=Math.min(32,Math.max(12,surfaceHeight(level,origin.getX()+x,origin.getZ()+z)-origin.getY()));
                for (int y=1;y<=clearTop;y++) level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for (var entry : blueprint.getAsJsonArray("blocks")) {
                JsonArray block = entry.getAsJsonArray();
                level.setBlock(origin.offset(block.get(0).getAsInt(),block.get(1).getAsInt(),block.get(2).getAsInt()),
                        palette.get(block.get(3).getAsInt()),2);
            }
            for (var entry : blueprint.getAsJsonArray("villagers")) {
                BlockPos pos = offset(origin,entry.getAsJsonArray());
                Villager villager = EntityType.VILLAGER.create(level);
                if (villager != null) {
                    villager.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
                    villager.setPersistenceRequired();
                    level.addFreshEntity(villager);
                }
            }
            return new Result(origin,offset(origin,blueprint.getAsJsonArray("home")),
                    offset(origin,blueprint.getAsJsonArray("bed")),blueprint.getAsJsonArray("blocks").size());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build starter settlement",exception);
        }
    }
    private static BlockPos offset(BlockPos origin, JsonArray pos) {
        return origin.offset(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt());
    }
}
