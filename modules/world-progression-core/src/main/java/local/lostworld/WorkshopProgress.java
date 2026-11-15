package local.lostworld;

/** Small shared rules for inventory progress and exploration categories. */
public final class WorkshopProgress {
    private WorkshopProgress() {}

    public static int observed(int previous,int inventory,int spent) {
        return Math.max(previous,inventory+spent);
    }

    public static String explorationKind(String structureId) {
        if (!structureId.startsWith("explorify:")) return "";
        String path=structureId.substring("explorify:".length());
        if (path.startsWith("watchtower/")) return "watchtower";
        if (path.startsWith("supply_cache/")) return "supply_cache";
        if (path.startsWith("guide_post_")) return "guide_post";
        return switch (path) {
            case "campsite","farmstead","ruins","tavern","mausoleum","desert_shrine",
                    "mangrove_hut","dark_forest_settlement","badlands_pyramid" -> path;
            default -> "";
        };
    }
}
