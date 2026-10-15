package local.lostworld;

import java.util.HashSet;
import java.util.List;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

final class SettlementLayoutCheck {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        var plots=List.of(new Rect(0,0,15,17),new Rect(-14,-6,11,13),new Rect(18,-3,11,13),
                new Rect(4,-15,7,7),new Rect(-14,-23,13,15),new Rect(17,-19,11,9));
        Rect area=SettlementLayout.bounds(plots,2);
        check(area.equals(new Rect(-16,-25,47,44)),"Whole settlement retains the authored footprint and two-block border");
        var entrances=List.of(new Point(8,91,-1),new Point(-9,91,-7),new Point(23,91,-4),
                new Point(7,91,-16),new Point(-8,91,-24),new Point(22,91,-20));
        for (Point entrance:entrances) {
            var route=SettlementLayout.path(area,plots,entrances.getFirst(),entrance);
            check(!route.isEmpty() && route.getFirst().equals(entrances.getFirst()) && route.getLast().equals(entrance),
                    "Every authored doorway connects without relocating a building");
            for (int i=0;i<route.size();i++) {
                Point point=route.get(i);
                check(point.y()==91 && area.contains(point.x(),point.z()),"Road stays inside the fixed, level village site");
                check(plots.stream().noneMatch(plot->plot.contains(point.x(),point.z())),"Road never cuts through a building footprint");
                if (i>0) check(Math.abs(point.x()-route.get(i-1).x())+Math.abs(point.z()-route.get(i-1).z())==1,
                        "Every road step has a traversable four-neighbor connection");
            }
        }
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
        System.out.println("PASS: fixed six-building footprint, all doorway roads, bounded BFS and 49 whole-village site offsets");
    }
}
