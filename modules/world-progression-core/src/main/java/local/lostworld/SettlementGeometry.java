package local.lostworld;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Pure coordinate/grade rules, shared by the builder and bounded JVM checks. */
public final class SettlementGeometry {
    public record Point(int x,int y,int z) {}
    public record Rect(int x,int z,int width,int depth) {
        public boolean contains(int px,int pz) { return px>=x && px<x+width && pz>=z && pz<z+depth; }
        public boolean overlaps(Rect other,int gap) {
            return x<other.x+other.width+gap && x+width+gap>other.x
                    && z<other.z+other.depth+gap && z+depth+gap>other.z;
        }
    }
    private SettlementGeometry() {}
    public static Point legacyAnchor(Point bed) { return new Point(bed.x-6,bed.y-1,bed.z-32); }

    public static OptionalInt terraceFloor(int[] ground,int maxCut,int maxFill) {
        if (ground.length==0 || maxCut<0 || maxFill<0) throw new IllegalArgumentException("Invalid terrace input");
        int low=Integer.MIN_VALUE,high=Integer.MAX_VALUE;
        long sum=0;
        for (int height:ground) {
            low=Math.max(low,height-maxCut); high=Math.min(high,height+maxFill); sum+=height;
        }
        if (low>high) return OptionalInt.empty();
        return OptionalInt.of(Math.max(low,Math.min(high,(int)Math.round((double)sum/ground.length))));
    }

    /** Grade an existing horizontal route; endpoints exact, every step at most one block. */
    public static int[] gradeRoad(int[] surface,int startFloor,int endFloor,int maxCut,int maxFill) {
        int[] pins=new int[surface.length]; java.util.Arrays.fill(pins,Integer.MIN_VALUE);
        return gradeRoad(surface,startFloor,endFloor,maxCut,maxFill,pins);
    }
    public static int[] gradeRoad(int[] surface,int startFloor,int endFloor,int maxCut,int maxFill,int[] pins) {
        if (surface.length<2 || maxCut<0 || maxFill<0) throw new IllegalArgumentException("Invalid road input");
        if (pins.length!=surface.length) throw new IllegalArgumentException("Pin count must match route");
        int n=surface.length;
        int[] low=new int[n],high=new int[n],result=new int[n];
        for (int i=0;i<n;i++) { low[i]=surface[i]-maxCut; high[i]=surface[i]+maxFill; }
        for (int i=0;i<n;i++) if (pins[i]!=Integer.MIN_VALUE) {
            if (pins[i]<low[i] || pins[i]>high[i]) return null;
            low[i]=high[i]=pins[i];
        }
        if (pins[0]!=Integer.MIN_VALUE && pins[0]!=startFloor || pins[n-1]!=Integer.MIN_VALUE && pins[n-1]!=endFloor) return null;
        if (startFloor<low[0] || startFloor>high[0] || endFloor<low[n-1] || endFloor>high[n-1]) return null;
        low[0]=high[0]=startFloor; low[n-1]=high[n-1]=endFloor;
        for (int i=n-2;i>=0;i--) {
            low[i]=Math.max(low[i],low[i+1]-1); high[i]=Math.min(high[i],high[i+1]+1);
            if (low[i]>high[i]) return null;
        }
        result[0]=startFloor;
        for (int i=1;i<n;i++) {
            int min=Math.max(low[i],result[i-1]-1),max=Math.min(high[i],result[i-1]+1);
            if (min>max) return null;
            result[i]=Math.max(min,Math.min(max,surface[i]));
        }
        return result;
    }

    public static List<Point> interleavedRoute(Point start,Point end) {
        int dx=Math.abs(end.x-start.x),dz=Math.abs(end.z-start.z),total=dx+dz;
        List<Point> points=new ArrayList<>(); points.add(new Point(start.x,0,start.z));
        for (int i=1;i<=total;i++) {
            int movedX=(int)Math.round((double)i*dx/total),movedZ=i-movedX;
            points.add(new Point(start.x+Integer.signum(end.x-start.x)*movedX,0,
                    start.z+Integer.signum(end.z-start.z)*movedZ));
        }
        return points;
    }

    public static List<Point> route(Point... corners) {
        if (corners.length<2) throw new IllegalArgumentException("A route needs endpoints");
        List<Point> result=new ArrayList<>();
        Point first=corners[0]; result.add(new Point(first.x,0,first.z));
        for (int i=1;i<corners.length;i++) {
            Point a=corners[i-1],b=corners[i];
            if (a.x!=b.x && a.z!=b.z) throw new IllegalArgumentException("Route segments must be axis-aligned");
            int x=a.x,z=a.z;
            while (x!=b.x || z!=b.z) {
                x+=Integer.compare(b.x,x); z+=Integer.compare(b.z,z); result.add(new Point(x,0,z));
            }
        }
        return result;
    }
}
