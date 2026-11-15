package local.lostworld;

import java.util.Arrays;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

final class SettlementGeometryCheck {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    static void run() {
        int[] flat={126,126,126,126};
        check(SettlementGeometry.terraceFloor(flat,6,18).orElseThrow()==126,"Flat building remains at ground level");
        int[] hillside={110,115,126,132};
        int floor=SettlementGeometry.terraceFloor(hillside,6,18).orElseThrow();
        for (int y:hillside) check(y-floor<=6 && floor-y<=18,"Terrace stays within excavation/foundation limits");
        check(SettlementGeometry.terraceFloor(new int[]{110,170},6,18).isEmpty(),"One large steep site remains rejected");
        check(SettlementGeometry.terraceFloor(new int[]{110,122},6,18).isPresent()
                && SettlementGeometry.terraceFloor(new int[]{150,162},6,18).isPresent(),"Independent buildings fit different hillside levels");
        Rect a=new Rect(-12,-9,15,15),b=new Rect(5,-9,11,12);
        check(!a.overlaps(b,2) && a.overlaps(new Rect(4,-9,11,12),2),"Plot spacing includes eaves and a two-block gap");
        check(a.contains(-12,-9) && !a.contains(3,6),"Negative-coordinate bounds are half-open");
        Rect home=new Rect(0,0,15,15);
        Point authored=new Point(-18,0,-19);
        var candidates=SettlementGeometry.partCandidates(home,11,12,authored.x(),authored.z());
        check(candidates.size()<=33 && new java.util.HashSet<>(candidates).size()==candidates.size()
                && candidates.getFirst().equals(authored),"Authored position remains first in a bounded, unique search");
        for (Point point:candidates) {
            int dx=point.x()-authored.x(),dz=point.z()-authored.z();
            check(Math.abs(dx)<=24 && Math.abs(dz)<=24,"Alternatives stay near their own authored plot");
            check(candidates.contains(new Point(authored.x()-dz,0,authored.z()+dx)),
                    "Each alternative has its quarter-turn counterpart instead of a one-sided diagonal fallback");
        }
        var shifted=SettlementGeometry.partCandidates(home,11,12,authored.x()+80,authored.z()-65);
        for (int i=0;i<candidates.size();i++) check(shifted.get(i).equals(
                new Point(candidates.get(i).x()+80,0,candidates.get(i).z()-65)),
                "Different authored plots do not fall back to shared positions around the player home");
        Rect occupied=new Rect(authored.x(),authored.z(),11,12);
        var available=candidates.stream().filter(p->!occupied.overlaps(new Rect(p.x(),p.z(),11,12),2)).toList();
        check(!available.isEmpty() && available.size()<candidates.size(),
                "Geometry can discard occupied candidates before terrain probes while retaining local alternatives");
        Point actualBed=new Point(-24,132,19),legacy=SettlementGeometry.legacyAnchor(actualBed);
        check(legacy.x()+6==actualBed.x() && legacy.y()+1==actualBed.y() && legacy.z()+32==actualBed.z(),
                "The old schema bed-offset contract remains valid after rollback");
        var route=SettlementGeometry.interleavedRoute(new Point(-5,130,-7),new Point(3,136,-1));
        check(route.size()==15 && route.get(0).x()==-5 && route.get(14).x()==3 && route.get(14).z()==-1,"Route retains exact horizontal endpoints");
        for (int i=1;i<route.size();i++) check(Math.abs(route.get(i).x()-route.get(i-1).x())
                +Math.abs(route.get(i).z()-route.get(i-1).z())==1,"Every route step is axis-adjacent");
        int[] surfaces={130,130,131,132,132,133,133,134,134,135,135,136,136,136,136};
        int[] grades=SettlementGeometry.gradeRoad(surfaces,130,136,6,18);
        verifyRoad(surfaces,grades,130,136);
        int[] downhill=SettlementGeometry.gradeRoad(surfaces,136,130,6,18);
        verifyRoad(surfaces,downhill,136,130);
        check(SettlementGeometry.gradeRoad(new int[]{100,100,100},100,110,6,18)==null,"A short route cannot become a vertical wall");
        int[] pins=new int[surfaces.length];Arrays.fill(pins,Integer.MIN_VALUE);pins[6]=133;
        int[] shared=SettlementGeometry.gradeRoad(surfaces,130,136,6,18,pins);
        check(shared!=null && shared[6]==133,"Joining roads retain shared floor height");
        pins[0]=131;
        check(SettlementGeometry.gradeRoad(surfaces,130,136,6,18,pins)==null,"Conflicting endpoint cannot overwrite an earlier road");
        System.out.println("PASS: independent terrace levels, plot spacing, exact road endpoints, stairs and shared junctions");
    }
    private static void verifyRoad(int[] ground,int[] result,int start,int end) {
        check(result!=null && result[0]==start && result[result.length-1]==end,"Road endpoints stay on the building entrances");
        for (int i=0;i<result.length;i++) {
            check(ground[i]-result[i]<=6 && result[i]-ground[i]<=18,"Road avoids unbounded cutting/filling");
            if (i>0) check(Math.abs(result[i]-result[i-1])<=1,"Stair height is traversable in both directions");
        }
    }
}
