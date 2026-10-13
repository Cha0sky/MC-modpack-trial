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
import net.minecraft.tags.BiomeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Places our vanilla blueprint; automatic callers must be inside CreateSpawnPosition. */
public final class SettlementBuilder {
    public record Result(BlockPos origin, BlockPos home, BlockPos bed, int blocks) {}
    private SettlementBuilder() {}

    public static Result createForFreshWorld(ServerLevel level) {
        ChapterData existing = ChapterData.find(level);
        if (existing != null && existing.hasSettlement()) return null;
        ChunkPos spawn = new ChunkPos(level.getChunkSource().randomState().sampler().findSpawnPosition());
        java.util.Map<String,Integer> rejected=new java.util.TreeMap<>();
        int terrainSites=0;
        // Spiral-like rings near the game's own climate-selected spawn, bounded search.
        search: for (int ring = 0; ring <= 32; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = spawn.getMinBlockX() + dx * 96;
                    int z = spawn.getMinBlockZ() + dz * 96;
                    // Climate/noise query does not generate a chunk. Skip oceans before terrain work.
                    var biome=level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                            (x+22)>>2,(level.getSeaLevel()+8)>>2,(z+19)>>2,level.getChunkSource().randomState().sampler());
                    if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(EvilBoundary.LOCKED)) continue;
                    if (terrainSites++>=32) break search;
                    BlockPos origin = suitableSite(level, x, z,rejected);
                    if (origin != null) {
                        Result result = place(level, origin);
                        ChapterData.create(level).settlementBuilt(result.origin(), result.home());
                        LostWorld.LOG.info("启程聚落已生成：{}, 玩家之家：{}, 实际选址检查 {} 次", result.origin(), result.home(),terrainSites);
                        return result;
                    }
                }
            }
        }
        LostWorld.LOG.error("附近未找到安全的聚落地块：{}；保留原生出生点。",rejected);
        return null;
    }

    private static BlockPos reject(java.util.Map<String,Integer> reasons,String reason) {
        reasons.merge(reason,1,Integer::sum); return null;
    }
    private static BlockPos suitableSite(ServerLevel level, int x, int z,java.util.Map<String,Integer> reasons) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, sum = 0, count = 0;
        for (int dx = 0; dx <= 44; dx += 4) {
            for (int dz = 0; dz <= 38; dz += 2) {
                int y = groundHeight(level,x+dx,z+dz);
                BlockPos surface = new BlockPos(x+dx, y, z+dz);
                BlockState ground = level.getBlockState(surface);
                if (y < level.getSeaLevel()-1) return reject(reasons,"low-ground");
                if (y > level.getMaxBuildHeight()-20) return reject(reasons,"high-ground");
                if (!ground.getFluidState().isEmpty()) return reject(reasons,"water");
                if (!ground.isSolidRender(level,surface)) return reject(reasons,"non-solid:"+ground.getBlock());
                if (EvilBoundary.evil(level,surface)) return reject(reasons,"evil");
                min = Math.min(min,y); max = Math.max(max,y); sum += y; count++;
                if (max-min > 8) return reject(reasons,"slope");
            }
        }
        int floor = sum/count;
        // Reject any intersecting native structure references, including mod structures.
        for (int cx = x>>4; cx <= (x+44)>>4; cx++) {
            for (int cz = z>>4; cz <= (z+38)>>4; cz++) {
                for (var start : level.structureManager().startsForStructure(new ChunkPos(cx,cz),s->true)) {
                    var bounds=start.getBoundingBox();
                    if (bounds.intersects(x,z,x+44,z+38) && bounds.maxY()>=floor-6 && bounds.minY()<=floor+32) return reject(reasons,"structure");
                }
            }
        }
        for (int dx = 0; dx < 45; dx++) {
            for (int dz = 0; dz < 39; dz++) {
                int surfaceY = groundHeight(level,x+dx,z+dz);
                BlockPos surface = new BlockPos(x+dx,surfaceY,z+dz);
                if (surfaceY<level.getSeaLevel()-1 || Math.abs(surfaceY-floor)>6 || EvilBoundary.evil(level,surface)
                        || !level.getFluidState(surface).isEmpty()) return reject(reasons,"full-footprint-ground");
                int top=Math.max(floor+12,surfaceHeight(level,x+dx,z+dz));
                if (top>floor+32) return reject(reasons,"tall-tree");
                for (int y = Math.min(surfaceY,floor)-3; y <= top; y++) {
                    if (level.getBlockState(new BlockPos(x+dx,y,z+dz)).hasBlockEntity()) return reject(reasons,"block-entity");
                }
            }
        }
        return new BlockPos(x,floor,z);
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
                for (int y=Math.max(-6,surface-origin.getY());y<0;y++)
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
