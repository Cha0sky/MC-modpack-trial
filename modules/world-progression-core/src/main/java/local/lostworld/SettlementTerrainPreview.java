package local.lostworld;

import java.util.Arrays;
import java.util.List;

/** Ranks the authored town footprints using generator samples, before requesting chunks. */
public final class SettlementTerrainPreview {
    public record Footprint(int median, int relief) {}
    static final int MAX_RELIEF = 12;
    private SettlementTerrainPreview() {}

    static boolean drySurface(int firstFreeFloor, int firstFreeSurface, int minBuildHeight) {
        return firstFreeFloor > minBuildHeight && firstFreeSurface == firstFreeFloor;
    }

    static Footprint footprint(int[] samples) {
        if (samples.length != 9) throw new IllegalArgumentException("Expected a 3 by 3 footprint");
        int[] sorted = samples.clone();
        Arrays.sort(sorted);
        int relief = sorted[8] - sorted[0];
        return relief <= MAX_RELIEF ? new Footprint(sorted[4], relief) : null;
    }

    static int siteCost(List<Footprint> plots, int distance) {
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int relief = 0, displaced = 0;
        for (Footprint plot : plots) {
            if (plot == null) {
                displaced++;
                continue;
            }
            lowest = Math.min(lowest, plot.median());
            highest = Math.max(highest, plot.median());
            relief += plot.relief();
        }
        int present = plots.size() - displaced;
        if (present == 0) return Integer.MAX_VALUE;
        // Prefer small foundations and streets between similar terraces. A plot whose
        // authored position fails can still be shifted by the live planner.
        return displaced * 48 + 4 * relief / present + (highest - lowest) + distance / 16;
    }
}
