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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

/** Plan every authored town plot and connector before any placement. */
public final class ModularSettlementBuilder {
    private static final int CUT=6,FILL=18;
    private static final TagKey<Block> NATURAL=TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"replaceable_terrain"));
    private static final TagKey<Block> PLANTS=TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"replaceable_plants"));
    private static final List<String> REQUIRED=SettlementLayout.TOWN_PARTS;
    private static final int MAX_HOME_SITES=12,MAX_PLOT_CHECKS=384,MAX_PLOTS_PER_SITE=REQUIRED.size()*8;
    private record Voxel(int x,int y,int z,BlockState state) {}
    private record Template(String id,int width,int height,int depth,Point entry,Point desired,
                            Point home,Point bed,List<Point> villagers,List<Voxel> blocks) {}
    private record Plot(Template template,Rect rect,int floor) {
        BlockPos origin() { return new BlockPos(rect.x(),floor,rect.z()); }
        Point entry() { return new Point(rect.x()+template.entry.x(),floor,rect.z()+template.entry.z()); }
    }
    private record Road(List<Point> points,int[] heights) {}
    private record Site(int homeX,int homeZ,int score) {}
    private final ServerLevel level;
    private final Map<Long,Integer> groundCache=new HashMap<>();
    private final Map<Long,List<BoundingBox>> structureCache=new HashMap<>();
    private final Map<Long,Integer> roadFloors=new HashMap<>();
    private final HashSet<Long> placedRoads=new HashSet<>();
    private final Map<String,Integer> rejects=new LinkedHashMap<>();
    private final List<Plot> plots=new ArrayList<>();
    private final List<Road> roads=new ArrayList<>();
    private final List<Road> mainStreets=new ArrayList<>();
    private int checkedPlots;
    private int sitePlotLimit;
    private final HashSet<String> loggedColumnReasons=new HashSet<>();

    private ModularSettlementBuilder(ServerLevel level) { this.level=level; }
    public static SettlementBuilder.Result create(ServerLevel level) {
        ChapterData data=ChapterData.find(level);
        if (data==null || !data.settlementPending() || data.hasSettlement()) return null;
        return new ModularSettlementBuilder(level).build(data);
    }

    private SettlementBuilder.Result build(ChapterData data) {
        Map<String,Template> templates=loadTemplates();
        if (!templates.keySet().containsAll(REQUIRED)) throw new IllegalStateException("Missing required MCP modules");
        Template player=templates.get("player_home");
        BlockPos spawn=level.getSharedSpawnPos();
        List<Point> offsets=new ArrayList<>(List.of(new Point(0,0,0),new Point(-16,0,0),new Point(0,0,-16),
                new Point(16,0,0),new Point(0,0,16),new Point(-16,0,-16),new Point(16,0,-16),new Point(-16,0,16)));
        offsets.addAll(SettlementLayout.siteOffsets());
        List<Site> candidates=new ArrayList<>();HashSet<Long> seen=new HashSet<>();
        for (Point offset:offsets) {
            int x=spawn.getX()-player.width/2+offset.x(),z=spawn.getZ()-player.depth/2+offset.z();
            if (!seen.add(key(x,z))) continue;
            int score=preview(new Rect(x,z,player.width,player.depth));
            if (score<0) continue;
            // Score the entire authored town before spending the bounded terrain budget.
            int penalty=0;
            for (String id:REQUIRED.subList(1,REQUIRED.size())) {
                Template part=templates.get(id);
                if (preview(new Rect(x+part.desired.x(),z+part.desired.z(),part.width,part.depth))<0) penalty+=48;
            }
            candidates.add(new Site(x,z,penalty+score*4+(Math.abs(offset.x())+Math.abs(offset.z()))/8));
        }
        candidates.sort(java.util.Comparator.comparingInt(Site::score));
        Plot home=null;int homeSites=0;
        for (Site site:candidates) {
            if (homeSites>=MAX_HOME_SITES || checkedPlots>=MAX_PLOT_CHECKS) break;
            homeSites++;
            plots.clear();roads.clear();mainStreets.clear();roadFloors.clear();
            sitePlotLimit=Math.min(MAX_PLOT_CHECKS,checkedPlots+MAX_PLOTS_PER_SITE);
            Plot candidate=planPlot(player,site.homeX,site.homeZ);
            if (candidate==null) continue;
            plots.add(candidate);
            boolean complete=true;
            for (int index=1;index<REQUIRED.size();index++) {
                String id=REQUIRED.get(index);
                // Reserve a terrain probe for every later plot, including the town gate.
                int partLimit=sitePlotLimit-(REQUIRED.size()-index-1);
                if (!planPart(templates.get(id),candidate,partLimit)) {
                    rejected("missing-"+id);complete=false;break;
                }
            }
            if (complete && planStreets()) {
                home=candidate;break;
            }
        }
        if (home==null) {
            LostWorld.LOG.error("Terraced settlement pending: {} dry home candidates, {} home sites, {} plot checks; {}. No partial buildings placed; starter mode/gift remains independent.",
                    candidates.size(),homeSites,checkedPlots,rejects);
            return null;
        }
        if (!SettlementLayout.complete(plots.stream().map(p->p.template.id).toList()))
            throw new IllegalStateException("All required town plots must be planned before placement");
        List<Point> shoulders=planStreetShoulders();
        int blocks=0;
        for (Plot plot:plots) blocks+=placePlot(plot);
        for (Road road:roads) placeRoad(road);
        for (Point shoulder:shoulders) placeColumn(shoulder.x(),shoulder.z(),shoulder.y(),paving(shoulder));
        // Complete all support, water, fences and roads before planting. During
        // server startup the light engine may still reflect the cleared terrain;
        // construction-time neighbor shapes must not destroy otherwise valid crops.
        for (Plot plot:plots) {
            for (Voxel voxel:plot.template.blocks)
                if (voxel.state.getBlock() instanceof CropBlock)
                    set(plot.origin().offset(voxel.x,voxel.y,voxel.z),voxel.state,
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        BlockPos arrival=offset(home.origin(),player.home),bed=offset(home.origin(),player.bed);
        if (!level.getBlockState(bed).is(Blocks.RED_BED)
                || !level.getBlockState(arrival).isAir() || !level.getBlockState(arrival.above()).isAir())
            throw new IllegalStateException("MCP player room was not placed");
        for (Plot plot:plots) {
            int crops=0,missingCrops=0;
            for (Voxel voxel:plot.template.blocks) {
                BlockPos pos=plot.origin().offset(voxel.x,voxel.y,voxel.z);
                BlockState actual=level.getBlockState(pos);
                if (voxel.state.getBlock() instanceof CropBlock) {
                    crops++;
                    if (!actual.is(voxel.state.getBlock())) missingCrops++;
                } else if (!actual.is(voxel.state.getBlock())) {
                    throw new IllegalStateException("Missing structural block in "+plot.template.id+" at "+pos
                            +": expected "+voxel.state+", actual "+actual);
                }
            }
            // Harvestable plants are not building structure. A plant update must
            // never turn a complete settlement into a server crash.
            if (crops>0) LostWorld.LOG.info("MCP crop placement {}: {}/{} present",plot.template.id,crops-missingCrops,crops);
            if (missingCrops>0) LostWorld.LOG.warn("MCP crops changed during placement: {} missing {} plants; structural verification passed",
                    plot.template.id,missingCrops);
            LostWorld.LOG.info("Terraced MCP component {} at {}, independent floor {}",plot.template.id,plot.origin(),plot.floor);
        }
        data.settlementBuilt(home.origin(),arrival,bed);
        LostWorld.LOG.info("Terraced starter town {}/{}; player_home {}, {} home sites, {} plot checks.",
                plots.size(),REQUIRED.size(),arrival,homeSites,checkedPlots);
        return new SettlementBuilder.Result(data.settlement(),arrival,bed,blocks);
    }

    private int preview(Rect area) {
        var source=level.getChunkSource();
        var generator=source.getGenerator();
        var random=source.randomState();
        int[] heights=new int[9]; int i=0,min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for (int dx:new int[]{0,area.width()/2,area.width()-1})
            for (int dz:new int[]{0,area.depth()/2,area.depth()-1}) {
                int x=area.x()+dx,z=area.z()+dz;
                int height=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                if (height<=level.getSeaLevel()) return -1;
                if (!level.getWorldBorder().isWithinBounds(new BlockPos(x,height,z))
                        || generator.getBiomeSource().getNoiseBiome(x>>2,height>>2,z>>2,random.sampler()).is(EvilBoundary.LOCKED)) return -1;
                heights[i++]=height; min=Math.min(min,height);max=Math.max(max,height);
                if (max-min>12) return -1;
            }
        return SettlementTerrainPreview.dryAndWithinSlope(heights,level.getSeaLevel())?max-min:-1;
    }

    private boolean planPart(Template template,Plot home,int partLimit) {
        int desiredX=home.rect.x()+template.desired.x(),desiredZ=home.rect.z()+template.desired.z();
        for (Point point:SettlementGeometry.partCandidates(home.rect,template.width,template.depth,desiredX,desiredZ)) {
            if (checkedPlots>=partLimit) { rejected("plot-budget-"+template.id);return false; }
            Plot candidate=planPlot(template,point.x(),point.z());
            if (candidate==null) continue;
            plots.add(candidate);
            return true;
        }
        return false;
    }

    private Plot planPlot(Template template,int x,int z) {
        if (checkedPlots>=sitePlotLimit) return null;
        Rect rect=new Rect(x,z,template.width,template.depth);
        for (Plot plot:plots) if (rect.overlaps(plot.rect,2)) return fail("overlap");
        // A rectangle overlap needs no world access and must not consume a terrain probe.
        checkedPlots++;
        // Reject obvious water/evil corners before touching every column or loading more chunks.
        for (int dx:new int[]{0,template.width/2,template.width-1})
            for (int dz:new int[]{0,template.depth/2,template.depth-1})
                if (!dryGround(x+dx,z+dz)) return fail("ground");
        int[] terrain=new int[template.width*template.depth+1];
        int index=0;
        for (int dx=0;dx<template.width;dx++) for (int dz=0;dz<template.depth;dz++) {
            int y=ground(x+dx,z+dz);
            BlockPos pos=new BlockPos(x+dx,y,z+dz);
            if (!dryGround(pos.getX(),pos.getZ())) return fail("ground");
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

    private boolean dryGround(int x,int z) {
        int y=ground(x,z);
        BlockPos pos=new BlockPos(x,y,z);
        BlockState state=level.getBlockState(pos);
        return level.getWorldBorder().isWithinBounds(pos) && y>=level.getSeaLevel()-1
                && state.getFluidState().isEmpty() && state.isSolidRender(level,pos) && !EvilBoundary.evil(level,pos);
    }

    private boolean planStreets() {
        Plot square=plots.stream().filter(p->p.template.id.equals("well")).findFirst().orElseThrow();
        List<Plot> connected=new ArrayList<>(List.of(square));
        for (Plot part:plots) {
            if (part==square) continue;
            List<Plot> nearest=new ArrayList<>(connected);
            nearest.sort(java.util.Comparator.comparingInt(p->Math.abs(p.entry().x()-part.entry().x())
                    +Math.abs(p.entry().z()-part.entry().z())));
            Road road=null;
            for (Plot other:nearest) {
                road=planRoad(other.entry(),part.entry(),plots);
                if (road!=null) break;
            }
            if (road==null) { rejected("street-"+part.template.id);return false; }
            addRoad(road);
            if (List.of("player_home","town_hall","town_gate").contains(part.template.id)) mainStreets.add(road);
            connected.add(part);
        }
        Plot gate=plots.stream().filter(p->p.template.id.equals("town_gate")).findFirst().orElseThrow();
        Road exit=planExit(gate);
        if (exit==null) { rejected("town-exit");return false; }
        addRoad(exit);
        mainStreets.add(exit);
        return true;
    }

    private Road planExit(Plot gate) {
        // The gate faces north and is open all the way to its south apron.
        Point start=new Point(gate.entry().x(),gate.floor,gate.rect.z()+gate.rect.depth());
        int[][] exits={{0,14},{10,14},{-10,14},{0,24}};
        for (int[] offset:exits) {
            int x=start.x()+offset[0],z=start.z()+offset[1],y=ground(x,z);
            BlockPos pos=new BlockPos(x,y,z);
            if (!level.getFluidState(pos).isEmpty() || EvilBoundary.evil(level,pos)) continue;
            Road road=planRoad(start,new Point(x,y,z),plots);
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
        for (int attempt=0;attempt<=options.size();attempt++) {
            // Four door orientations can require a tighter detour than the simple L-shaped routes.
            List<Point> option=attempt<options.size()?options.get(attempt):SettlementLayout.path(
                    new Rect(left-extra,north-extra,right-left+2*extra+1,south-north+2*extra+1),
                    obstacles.stream().map(Plot::rect).toList(),start,end);
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

    private List<Point> planStreetShoulders() {
        List<Point> shoulders=new ArrayList<>();
        HashSet<Long> used=new HashSet<>(roadFloors.keySet());
        for (Road road:mainStreets) for (int i=1;i<road.points.size()-1;i++) {
            int floor=road.heights[i];
            if (road.heights[i-1]!=floor || road.heights[i+1]!=floor) continue;
            Point before=road.points.get(i-1),point=road.points.get(i),after=road.points.get(i+1);
            int dx=after.x()-before.x(),dz=after.z()-before.z();
            if (dx!=0 && dz!=0) continue;
            for (int side:new int[]{-1,1}) {
                int x=point.x()+side*Integer.signum(dz),z=point.z()+side*Integer.signum(dx);
                if (used.contains(key(x,z)) || plots.stream().anyMatch(p->p.rect.contains(x,z))) continue;
                if (!columnSafe(x,z,floor,3,false)) continue;
                used.add(key(x,z));
                shoulders.add(new Point(x,floor,z));
            }
        }
        return shoulders;
    }

    private boolean columnSafe(int x,int z,int floor,int clearance,boolean clearTree) {
        int raw=ground(x,z);
        if (raw-floor>CUT || floor-raw>FILL || floor<=level.getMinBuildHeight())
            return rejectedColumn("slope",new BlockPos(x,raw,z),null);
        int top=clearTree ? Math.max(floor+clearance,surface(x,z)) : Math.max(floor+clearance,raw);
        if (top>floor+48 || top>=level.getMaxBuildHeight())
            return rejectedColumn("height",new BlockPos(x,top,z),null);
        BlockPos pos=new BlockPos(x,raw,z);
        if (!level.getWorldBorder().isWithinBounds(pos) || EvilBoundary.evil(level,pos)
                || EvilBoundary.evil(level,new BlockPos(x,floor+1,z)))
            return rejectedColumn("border-or-evil",pos,null);
        for (BoundingBox box:structures(x,z))
            if (x>=box.minX() && x<=box.maxX() && z>=box.minZ() && z<=box.maxZ()
                    && box.maxY()>=Math.min(raw+1,floor) && box.minY()<=top)
                return rejectedColumn("structure",pos,null);
        for (int y=Math.min(raw+1,floor);y<=top;y++) {
            BlockState state=level.getBlockState(new BlockPos(x,y,z));
            // Confluence's naturally generated daybloom has an empty BlockEntity.
            // Only explicitly audited plants may bypass the general BlockEntity veto.
            if (state.is(PLANTS)) continue;
            if (state.hasBlockEntity()) return rejectedColumn("block-entity",new BlockPos(x,y,z),state);
            if (!state.isAir() && !state.is(NATURAL)) return rejectedColumn("non-natural-block",new BlockPos(x,y,z),state);
        }
        return true;
    }

    private boolean rejectedColumn(String reason,BlockPos pos,BlockState state) {
        rejected("column-"+reason);
        if (loggedColumnReasons.size()<12 && loggedColumnReasons.add(reason))
            LostWorld.LOG.warn("Settlement column rejected: {} at {}, block {}",reason,pos,state);
        return false;
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
        for (Voxel voxel:template.blocks)
            if (!(voxel.state.getBlock() instanceof CropBlock))
                set(plot.origin().offset(voxel.x,voxel.y,voxel.z),voxel.state);
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
            BlockState state=paving(point);
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

    private static BlockState paving(Point point) {
        return (Math.floorMod(point.x()+point.z(),5)==0?Blocks.COBBLESTONE:Blocks.ANDESITE).defaultBlockState();
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
        set(pos,state,Block.UPDATE_CLIENTS);
    }
    private void set(BlockPos pos,BlockState state,int flags) {
        if (!level.getBlockState(pos).equals(state) && !level.setBlock(pos,state,flags))
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
                JsonObject module=value.getAsJsonObject();
                if (!REQUIRED.contains(module.get("id").getAsString())) continue;
                JsonArray size=module.getAsJsonArray("size");
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
