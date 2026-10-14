package local.lostworld;

/** Evaluates generator height samples before any candidate chunks are requested. */
public final class SettlementTerrainPreview {
    private SettlementTerrainPreview() {}
    public static boolean dryAndWithinSlope(int[] firstFreeHeights,int seaLevel) {
        if (firstFreeHeights.length!=9) throw new IllegalArgumentException("A footprint preview requires nine samples");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for (int height:firstFreeHeights) {
            // OCEAN_FLOOR_WG excludes water: a bottom at/below sea level is not an inland site.
            if (height<=seaLevel) return false;
            min=Math.min(min,height); max=Math.max(max,height);
        }
        return max-min<=12;
    }
}
