package local.lostworld;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

final class SettlementLayoutCheck {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        int[][] localTerrain={{64,65,66},{78,79,80},{91,92,93},{61,62,63},{50,51,52},{96,97,98}};
        for (int[] ground:localTerrain) check(SettlementGeometry.terraceFloor(ground,6,18).isPresent(),
                "Each building can independently fit local terrain");
        check(SettlementGeometry.terraceFloor(new int[]{50,66,80,93,98},6,18).isEmpty(),
                "Different valid building terraces need not fit a single village-wide floor");
        verifyAuthoredTown();
        var detour=SettlementLayout.path(new Rect(0,0,5,5),List.of(new Rect(2,0,1,4)),
                new Point(0,70,0),new Point(4,999,0));
        check(detour.size()==13 && detour.getLast().equals(new Point(4,70,0)),
                "BFS finds the shortest obstacle detour while preserving the starting floor");
        check(SettlementLayout.path(new Rect(0,0,5,5),List.of(new Rect(2,0,1,5)),
                new Point(0,70,0),new Point(4,70,0)).isEmpty(),"A sealed connection rejects the whole plan instead of crossing buildings");
        var offsets=SettlementLayout.siteOffsets();
        check(offsets.size()==49 && new HashSet<>(offsets).size()==49 && offsets.getFirst().equals(new Point(0,0,0)),
                "Site search has exactly 49 unique candidates and begins at the requested origin");
        check(offsets.stream().allMatch(p->p.y()==0 && Math.abs(p.x())<=512 && Math.abs(p.z())<=512)
                && offsets.contains(new Point(512,0,512)) && offsets.contains(new Point(-512,0,-512)),
                "Site probes stay bounded and diagonal bearings retain the unnormalized radius");
        System.out.println("PASS: 13 authored town plots, connected doorways, independent terraces and bounded site search");
    }

    private static void verifyAuthoredTown() {
        var plots=new ArrayList<Rect>();
        var entrances=new ArrayList<Point>();
        var ids=new ArrayList<String>();
        Point square=null;
        try (var stream=SettlementLayoutCheck.class.getResourceAsStream(
                "/data/world_progression_core/settlement/modules.json")) {
            check(stream!=null,"Packaged town layout must exist");
            var modules=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("modules");
            for (var value:modules) {
                var module=value.getAsJsonObject();
                String id=module.get("id").getAsString();
                if (id.endsWith("_compact")) continue;
                var pos=module.getAsJsonArray("desired");
                var size=module.getAsJsonArray("size");
                var entry=module.getAsJsonArray("entry");
                Rect plot=new Rect(pos.get(0).getAsInt(),pos.get(1).getAsInt(),size.get(0).getAsInt(),size.get(2).getAsInt());
                check(plots.stream().noneMatch(old->old.overlaps(plot,2)),"Authored plots leave room for streets: "+id);
                Point doorway=new Point(plot.x()+entry.get(0).getAsInt(),91,plot.z()+entry.get(2).getAsInt());
                if (id.equals("well")) square=doorway;
                ids.add(id); plots.add(plot); entrances.add(doorway);
            }
        } catch (IOException ex) {
            throw new AssertionError("Could not read packaged town layout",ex);
        }
        check(ids.size()==13 && SettlementLayout.complete(ids),"All 13 authored plots are required");
        var selected=new ArrayList<Rect>();
        Rect home=plots.get(ids.indexOf("player_home"));
        selected.add(home);
        for (String id:SettlementLayout.TOWN_PARTS.subList(1,SettlementLayout.TOWN_PARTS.size())) {
            Rect authored=plots.get(ids.indexOf(id));
            Rect planned=SettlementGeometry.partCandidates(home,authored.width(),authored.depth(),authored.x(),authored.z())
                    .stream().map(p->new Rect(p.x(),p.z(),authored.width(),authored.depth()))
                    .filter(candidate->selected.stream().noneMatch(old->old.overlaps(candidate,2)))
                    .findFirst().orElseThrow(()->new AssertionError("No non-overlapping candidate for "+id));
            check(planned.equals(authored),"The runtime plot order retains every available authored position: "+id);
            selected.add(planned);
        }
        check(selected.size()==13,"Local candidate search preserves all 13 authored plots after overlap filtering");
        var missingFarm=new ArrayList<>(ids); missingFarm.remove("farm");
        check(!SettlementLayout.complete(missingFarm),"Missing farm cannot be committed as a complete town");
        var oldHome=new ArrayList<>(ids); oldHome.set(oldHome.indexOf("player_home"),"player_compact");
        check(!SettlementLayout.complete(oldHome),"Old hut cannot stand in for the authored player home");
        check(square!=null,"The central square is a road hub");
        Rect area=SettlementLayout.bounds(plots,2);
        check(area.width()==84 && area.depth()==69,"The authored town has an 80×65 block footprint");
        for (int i=0;i<entrances.size();i++) {
            check(!SettlementLayout.path(area,plots,square,entrances.get(i)).isEmpty(),
                    "Every authored doorway connects to the square without crossing a building: "+ids.get(i));
        }
    }
}
