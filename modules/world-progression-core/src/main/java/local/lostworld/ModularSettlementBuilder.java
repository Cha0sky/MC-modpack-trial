package local.lostworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

/** Bounded, per-building terraces. All protection/route planning precedes block placement. */
public final class ModularSettlementBuilder {
    private static final int CUT=6,FILL=18;
    private static final TagKey<Block> NATURAL=TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"replaceable_terrain"));
    private static final int[][] HOUSE_OFFSETS={{0,0},{-16,0},{0,-16},{16,0},{0,16},{-16,-16},{16,-16},{-16,16}};
    private static final int[][] PART_OFFSETS={{0,0},{-8,0},{0,-8},{8,0},{0,8},{-8,-8}};
    private record Voxel(int x,int y,int z,BlockState state) {}
    private record Template(String id,int width,int height,int depth,Point entry,Point desired,
                            Point home,Point bed,List<Point> villagers,List<Voxel> blocks) {}
    private record Plot(Template template,Rect rect,int floor) {
        BlockPos origin() { return new BlockPos(rect.x(),floor,rect.z()); }
        Point entry() { return new Point(rect.x()+template.entry.x(),floor,rect.z()+template.entry.z()); }
    }
    private record Road(List<Point> points,int[] heights) {}
    private record Home(Plot plot,Road exit) {}
    private final ServerLevel level;
    private final Map<Long,Integer> groundCache=new HashMap<>();
    private final Map<Long,List<BoundingBox>> structureCache=new HashMap<>();
    private final Map<Long,Integer> roadFloors=new HashMap<>();
    private final HashSet<Long> placedRoads=new HashSet<>();
    private final Map<String,Integer> rejects=new LinkedHashMap<>();
    private final List<Plot> plots=new ArrayList<>();
    private final List<Road> roads=new ArrayList<>();
    private int checkedPlots;

    private ModularSettlementBuilder(ServerLevel level) { this.level=level; }
    public static SettlementBuilder.Result create(ServerLevel level) {
        ChapterData data=ChapterData.find(level);
        if (data==null || !data.settlementPending() || data.hasSettlement()) return null;
        return new ModularSettlementBuilder(level).build(data);
    }

    private SettlementBuilder.Result build(ChapterData data) {
        Map<String,Template> templates=loadTemplates();
        BlockPos spawn=level.getSharedSpawnPos();
        Home home=null;
        for (String id:List.of("player_home","player_compact")) {
            Template template=templates.get(id);
            for (int[] offset:HOUSE_OFFSETS) {
                Plot candidate=planPlot(template,spawn.getX()-template.width/2+offset[0],
                        spawn.getZ()-template.depth/2+offset[1]);
                if (candidate==null) continue;
                Road exit=planExit(candidate);
                if (exit!=null) { home=new Home(candidate,exit); break; }
                rejected("home-exit");
            }
            if (home!=null) break;
        }
        if (home==null) {
            LostWorld.LOG.error("分栋聚落暂未找到安全玩家房屋地块：检查 {} 处，{}",checkedPlots,rejects);
            return null;
        }
        plots.add(home.plot); addRoad(home.exit);
        for (String id:List.of("villager_west","villager_east","well","farm","expansion_plot")) {
            Template template=templates.get(id);
            int desiredX=home.plot.rect.x()+template.desired.x(),desiredZ=home.plot.rect.z()+template.desired.z();
            boolean accepted=false;
            for (int[] offset:PART_OFFSETS) {
                Plot candidate=planPlot(template,desiredX+offset[0],desiredZ+offset[1]);
                if (candidate==null) continue;
                List<Plot> obstacles=new ArrayList<>(plots); obstacles.add(candidate);
                Road road=planRoad(home.plot.entry(),candidate.entry(),obstacles);
                if (road==null) { rejected("part-road"); continue; }
                plots.add(candidate); addRoad(road); accepted=true; break;
            }
            if (!accepted) LostWorld.LOG.warn("玩家房屋可建，本次跳过尚无安全地块/连接路的部件：{}",id);
        }
        int blocks=0;
        for (Plot plot:plots) blocks+=placePlot(plot);
        for (Road road:roads) placeRoad(road);
        BlockPos origin=home.plot.origin();
        BlockPos arrival=offset(origin,home.plot.template.home),bed=offset(origin,home.plot.template.bed);
        // Validate the primary promise before enabling the chapter and recording completion.
        if (!level.getBlockState(bed).is(Blocks.RED_BED)
                || !level.getBlockState(arrival).isAir() || !level.getBlockState(arrival.above()).isAir())
            throw new IllegalStateException("Player house bed or arrival clearance missing");
        data.settlementBuilt(origin,arrival,bed);
        LostWorld.LOG.info("分栋启程聚落已生成：玩家房屋 {}，出生点 {}，床 {}，部件 {}/6，道路 {}，检查地块 {} 处",
                home.plot.template.id,arrival,bed,plots.size(),roads.size(),checkedPlots);
        return new SettlementBuilder.Result(data.settlement(),arrival,bed,blocks);
    }

    private Plot planPlot(Template template,int x,int z) {
        checkedPlots++;
        Rect rect=new Rect(x,z,template.width,template.depth);
        for (Plot plot:plots) if (rect.overlaps(plot.rect,2)) return fail("overlap");
        int[] terrain=new int[template.width*template.depth+1];
        int index=0;
        for (int dx=0;dx<template.width;dx++) for (int dz=0;dz<template.depth;dz++) {
            if (roadFloors.containsKey(key(x+dx,z+dz))) return fail("road-overlap");
            int y=ground(x+dx,z+dz);
            BlockPos pos=new BlockPos(x+dx,y,z+dz);
            BlockState state=level.getBlockState(pos);
            if (!level.getWorldBorder().isWithinBounds(pos) || y<level.getSeaLevel()-1
                    || !state.getFluidState().isEmpty() || !state.isSolidRender(level,pos)
                    || EvilBoundary.evil(level,pos)) return fail("ground");
            terrain[index++]=y;
        }
        terrain[index]=ground(x+template.entry.x(),z+template.entry.z());
        var floor=SettlementGeometry.terraceFloor(terrain,CUT,FILL);
        if (floor.isEmpty()) return fail("local-slope");
        int y=floor.getAsInt();
        if (y+template.height+2>=level.getMaxBuildHeight()) return fail("height");
        for (int dx=0;dx<template.width;dx++) for (int dz=0;dz<template.depth;dz++) {
            if (!columnSafe(x+dx,z+dz,y,template.height+2,true)) return fail("protected-column");
        }
        if (!columnSafe(x+template.entry.x(),z+template.entry.z(),y,3,false)) return fail("entrance");
        return new Plot(template,rect,y);
    }

    private Road planExit(Plot home) {
        Point start=home.entry();
        int[][] exits={{0,-24},{-24,-8},{24,-8},{0,-40},{-32,-24},{32,-24}};
        for (int[] offset:exits) {
            int x=start.x()+offset[0],z=start.z()+offset[1],y=ground(x,z);
            BlockPos pos=new BlockPos(x,y,z);
            if (!level.getFluidState(pos).isEmpty() || EvilBoundary.evil(level,pos)) continue;
            Road road=planRoad(start,new Point(x,y,z),List.of(home));
            if (road!=null) return road;
        }
        return null;
    }

    private Road planRoad(Point start,Point end,List<Plot> obstacles) {
        int left=Math.min(start.x(),end.x()),right=Math.max(start.x(),end.x());
        int north=Math.min(start.z(),end.z()),south=Math.max(start.z(),end.z());
        for (Plot plot:obstacles) {
            left=Math.min(left,plot.rect.x()); right=Math.max(right,plot.rect.x()+plot.rect.width());
            north=Math.min(north,plot.rect.z()); south=Math.max(south,plot.rect.z()+plot.rect.depth());
        }
        int extra=Math.max(4,(Math.abs(end.y()-start.y())+1)/2);
        List<List<Point>> options=new ArrayList<>();
        options.add(SettlementGeometry.interleavedRoute(start,end));
        options.add(SettlementGeometry.route(start,new Point(end.x(),0,start.z()),end));
        options.add(SettlementGeometry.route(start,new Point(start.x(),0,end.z()),end));
        for (int x:new int[]{left-extra,right+extra})
            options.add(SettlementGeometry.route(start,new Point(x,0,start.z()),new Point(x,0,end.z()),end));
        for (int z:new int[]{north-extra,south+extra})
            options.add(SettlementGeometry.route(start,new Point(start.x(),0,z),new Point(end.x(),0,z),end));
        for (List<Point> option:options) {
            if (option.size()<2 || option.size()>192) continue;
            var visited=new HashSet<Long>();
            boolean blocked=false;
            int[] surfaces=new int[option.size()],pins=new int[option.size()];
            java.util.Arrays.fill(pins,Integer.MIN_VALUE);
            for (int i=0;i<option.size();i++) {
                Point point=option.get(i); long key=key(point.x(),point.z());
                if (!visited.add(key)) { blocked=true; break; }
                for (Plot plot:obstacles) if (plot.rect.contains(point.x(),point.z())) { blocked=true; break; }
                if (blocked) break;
                surfaces[i]=ground(point.x(),point.z());
                pins[i]=roadFloors.getOrDefault(key,Integer.MIN_VALUE);
                // A new branch leaves an existing road on a level landing, not a differently oriented stair.
                if (pins[i]==Integer.MIN_VALUE) {
                    for (int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        Integer adjacent=roadFloors.get(key(point.x()+direction[0],point.z()+direction[1]));
                        if (adjacent==null) continue;
                        if (pins[i]!=Integer.MIN_VALUE && pins[i]!=adjacent) { blocked=true; break; }
                        pins[i]=adjacent;
                    }
                    if (blocked) break;
                }
            }
            if (blocked) continue;
            int[] profile=SettlementGeometry.gradeRoad(surfaces,start.y(),end.y(),CUT,FILL,pins);
            if (profile==null) continue;
            for (int i=0;i<option.size();i++) {
                Point point=option.get(i);
                if (!columnSafe(point.x(),point.z(),profile[i],3,false)) { blocked=true; break; }
            }
            if (!blocked) return new Road(option,profile);
        }
        return null;
    }

    private void addRoad(Road road) {
        roads.add(road);
        for (int i=0;i<road.points.size();i++) {
            Point point=road.points.get(i); roadFloors.put(key(point.x(),point.z()),road.heights[i]);
        }
    }

    private boolean columnSafe(int x,int z,int floor,int clearance,boolean clearTree) {
        int raw=ground(x,z);
        if (raw-floor>CUT || floor-raw>FILL || floor<=level.getMinBuildHeight()) return false;
        int top=clearTree ? Math.max(floor+clearance,surface(x,z)) : Math.max(floor+clearance,raw);
        if (top>floor+48 || top>=level.getMaxBuildHeight()) return false;
        BlockPos pos=new BlockPos(x,raw,z);
        if (!level.getWorldBorder().isWithinBounds(pos) || EvilBoundary.evil(level,pos)
                || EvilBoundary.evil(level,new BlockPos(x,floor+1,z))) return false;
        for (BoundingBox box:structures(x,z))
            if (x>=box.minX() && x<=box.maxX() && z>=box.minZ() && z<=box.maxZ()
                    && box.maxY()>=Math.min(raw+1,floor) && box.minY()<=top) return false;
        for (int y=Math.min(raw+1,floor);y<=top;y++) {
            BlockState state=level.getBlockState(new BlockPos(x,y,z));
            if (state.hasBlockEntity() || !state.isAir() && !state.is(NATURAL)) return false;
        }
        return true;
    }

    private List<BoundingBox> structures(int x,int z) {
        long chunkKey=new ChunkPos(x>>4,z>>4).toLong();
        return structureCache.computeIfAbsent(chunkKey,k->{
            List<BoundingBox> boxes=new ArrayList<>();
            for (var start:level.structureManager().startsForStructure(new ChunkPos(x>>4,z>>4),s->true))
                for (var piece:start.getPieces()) boxes.add(piece.getBoundingBox());
            return boxes;
        });
    }

    private int ground(int x,int z) {
        return groundCache.computeIfAbsent(key(x,z),k->{
            int y=surface(x,z)-1;
            while (y>level.getMinBuildHeight()+8) {
                BlockPos pos=new BlockPos(x,y,z); BlockState state=level.getBlockState(pos);
                if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MUSHROOM_STEM)
                        || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                        || state.getFluidState().isEmpty() && !state.isSolidRender(level,pos)) y--;
                else break;
            }
            return y;
        });
    }
    private int surface(int x,int z) {
        return level.getChunk(x>>4,z>>4).getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1;
    }

    private int placePlot(Plot plot) {
        Template template=plot.template;
        for (int x=0;x<template.width;x++) for (int z=0;z<template.depth;z++) {
            int wx=plot.rect.x()+x,wz=plot.rect.z()+z;
            int raw=ground(wx,wz),top=Math.max(plot.floor+template.height+2,surface(wx,wz));
            fillFoundation(wx,wz,plot.floor,raw);
            for (int y=plot.floor+1;y<=top;y++) set(new BlockPos(wx,y,wz),Blocks.AIR.defaultBlockState());
        }
        for (Voxel voxel:template.blocks) set(plot.origin().offset(voxel.x,voxel.y,voxel.z),voxel.state);
        placeColumn(plot.entry().x(),plot.entry().z(),plot.floor,Blocks.COBBLESTONE.defaultBlockState());
        for (Point point:template.villagers) {
            BlockPos pos=offset(plot.origin(),point);
            var villager=EntityType.VILLAGER.create(level);
            if (villager!=null) {
                villager.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
                villager.setPersistenceRequired(); level.addFreshEntity(villager);
            }
        }
        return template.blocks.size();
    }

    private void placeRoad(Road road) {
        for (int i=0;i<road.points.size();i++) {
            Point point=road.points.get(i);
            if (!placedRoads.add(key(point.x(),point.z()))) continue;
            BlockState state=Blocks.COBBLESTONE.defaultBlockState();
            boolean previousLower=i>0 && road.heights[i-1]<road.heights[i];
            boolean nextLower=i+1<road.points.size() && road.heights[i+1]<road.heights[i];
            if (previousLower && nextLower) state=Blocks.COBBLESTONE_SLAB.defaultBlockState();
            else if (previousLower || nextLower) {
                Point lower=road.points.get(previousLower?i-1:i+1);
                state=Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                        Direction.fromDelta(point.x()-lower.x(),0,point.z()-lower.z()));
            }
            placeColumn(point.x(),point.z(),road.heights[i],state);
        }
    }
    private void placeColumn(int x,int z,int floor,BlockState surface) {
        int raw=ground(x,z);
        fillFoundation(x,z,floor,raw);
        for (int y=floor+1;y<=Math.max(raw,floor+3);y++) set(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        set(new BlockPos(x,floor,z),surface);
    }
    private void fillFoundation(int x,int z,int floor,int raw) {
        for (int y=raw+1;y<floor;y++) set(new BlockPos(x,y,z),Blocks.COBBLESTONE.defaultBlockState());
    }
    private void set(BlockPos pos,BlockState state) {
        if (!level.getBlockState(pos).equals(state) && !level.setBlock(pos,state,2))
            throw new IllegalStateException("Settlement block placement refused at "+pos);
    }
    private Plot fail(String reason) { rejected(reason); return null; }
    private void rejected(String reason) { rejects.merge(reason,1,Integer::sum); }
    private static long key(int x,int z) { return BlockPos.asLong(x,0,z); }
    private static BlockPos offset(BlockPos origin,Point point) { return origin.offset(point.x(),point.y(),point.z()); }

    private Map<String,Template> loadTemplates() {
        try (var stream=getClass().getResourceAsStream("/data/world_progression_core/settlement/modules.json")) {
            if (stream==null) throw new IllegalStateException("Missing modular settlement templates");
            JsonObject root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,Template> templates=new LinkedHashMap<>();
            for (var value:root.getAsJsonArray("modules")) {
                JsonObject module=value.getAsJsonObject(); JsonArray size=module.getAsJsonArray("size");
                List<BlockState> palette=new ArrayList<>();
                for (var entry:module.getAsJsonArray("palette"))
                    palette.add(BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK),entry.getAsString(),false).blockState());
                List<Voxel> voxels=new ArrayList<>();
                for (var entry:module.getAsJsonArray("blocks")) {
                    JsonArray b=entry.getAsJsonArray();
                    voxels.add(new Voxel(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsInt(),palette.get(b.get(3).getAsInt())));
                }
                List<Point> villagers=new ArrayList<>();
                for (var entry:module.getAsJsonArray("villagers")) villagers.add(point(entry.getAsJsonArray()));
                JsonArray desired=module.getAsJsonArray("desired");
                Template template=new Template(module.get("id").getAsString(),size.get(0).getAsInt(),size.get(1).getAsInt(),size.get(2).getAsInt(),
                        point(module.getAsJsonArray("entry")),new Point(desired.get(0).getAsInt(),0,desired.get(1).getAsInt()),
                        module.has("home")?point(module.getAsJsonArray("home")):null,module.has("bed")?point(module.getAsJsonArray("bed")):null,
                        villagers,voxels);
                templates.put(template.id,template);
            }
            return templates;
        } catch (Exception error) { throw new IllegalStateException("Cannot load modular settlement",error); }
    }
    private static Point point(JsonArray a) { return new Point(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()); }
}
