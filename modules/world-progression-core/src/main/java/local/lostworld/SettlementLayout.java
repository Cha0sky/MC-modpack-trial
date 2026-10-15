package local.lostworld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import local.lostworld.SettlementGeometry.Point;
import local.lostworld.SettlementGeometry.Rect;

/** Pure fixed-village footprint and bounded connector planning; never loads chunks. */
public final class SettlementLayout {
    private static final int[][] DIRECTIONS={{0,-1},{1,0},{0,1},{-1,0}};

    private SettlementLayout() {}

    public static boolean complete(List<String> parts) {
        return parts.size()==6 && new java.util.HashSet<>(parts).equals(java.util.Set.of(
                "player_home","villager_west","villager_east","well","farm","expansion_plot"));
    }

    public static Rect bounds(List<Rect> plots,int margin) {
        if (plots.isEmpty() || margin<0) throw new IllegalArgumentException("Bounds need plots and a nonnegative margin");
        long minX=Long.MAX_VALUE,minZ=Long.MAX_VALUE,maxX=Long.MIN_VALUE,maxZ=Long.MIN_VALUE;
        for (Rect plot:plots) {
            if (plot.width()<=0 || plot.depth()<=0) throw new IllegalArgumentException("Plot dimensions must be positive");
            minX=Math.min(minX,plot.x()); minZ=Math.min(minZ,plot.z());
            maxX=Math.max(maxX,(long)plot.x()+plot.width()); maxZ=Math.max(maxZ,(long)plot.z()+plot.depth());
        }
        return new Rect(Math.toIntExact(minX-margin),Math.toIntExact(minZ-margin),
                Math.toIntExact(maxX-minX+2L*margin),Math.toIntExact(maxZ-minZ+2L*margin));
    }

    /** Four-neighbor shortest path outside all building rectangles, including both endpoints. */
    public static List<Point> path(Rect area,List<Rect> obstacles,Point start,Point end) {
        if (area.width()<=0 || area.depth()<=0) throw new IllegalArgumentException("Path area must be positive");
        if (!area.contains(start.x(),start.z()) || !area.contains(end.x(),end.z())) return List.of();
        int count=Math.multiplyExact(area.width(),area.depth());
        int[] previous=new int[count]; Arrays.fill(previous,-1);
        boolean[] blocked=new boolean[count];
        for (int z=0;z<area.depth();z++) for (int x=0;x<area.width();x++) {
            int px=area.x()+x,pz=area.z()+z;
            blocked[z*area.width()+x]=obstacles.stream().anyMatch(plot->plot.contains(px,pz));
        }
        int first=(start.z()-area.z())*area.width()+start.x()-area.x();
        int last=(end.z()-area.z())*area.width()+end.x()-area.x();
        if (blocked[first] || blocked[last]) return List.of();
        var queue=new ArrayDeque<Integer>(); queue.add(first); previous[first]=first;
        while (!queue.isEmpty() && previous[last]<0) {
            int current=queue.removeFirst(),x=current%area.width(),z=current/area.width();
            for (int[] direction:DIRECTIONS) {
                int nx=x+direction[0],nz=z+direction[1];
                if (nx<0 || nx>=area.width() || nz<0 || nz>=area.depth()) continue;
                int next=nz*area.width()+nx;
                if (previous[next]>=0 || blocked[next]) continue;
                previous[next]=current; queue.addLast(next);
            }
        }
        if (previous[last]<0) return List.of();
        var result=new ArrayList<Point>();
        for (int cell=last;;cell=previous[cell]) {
            result.add(new Point(area.x()+cell%area.width(),start.y(),area.z()+cell/area.width()));
            if (cell==first) break;
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    /** Origin and six fixed eight-bearing rings; diagonal coordinates use the full radius. */
    public static List<Point> siteOffsets() {
        var result=new ArrayList<Point>(); result.add(new Point(0,0,0));
        for (int radius:new int[]{64,128,192,256,384,512})
            for (int[] bearing:new int[][]{{0,-1},{1,0},{0,1},{-1,0},{-1,-1},{1,-1},{1,1},{-1,1}})
                result.add(new Point(bearing[0]*radius,0,bearing[1]*radius));
        return List.copyOf(result);
    }
}
