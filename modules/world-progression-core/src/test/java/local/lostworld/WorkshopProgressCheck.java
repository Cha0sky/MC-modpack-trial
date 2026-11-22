package local.lostworld;

final class WorkshopProgressCheck {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        check(WorkshopProgress.observed(7,2,5)==7,"Making a whisk retains the five consumed iron sheets without double counting");
        check(WorkshopProgress.observed(5,2,5)==7,"New sheets after the whisk count toward the seven-sheet milestone");
        check(WorkshopProgress.observed(4,0,4)==4,"A tuner and three calibrations preserve the four-binder milestone");
        check(WorkshopProgress.observed(7,7,0)==7 && WorkshopProgress.observed(7,0,0)==7,
                "Repeated observations and moving an item out of inventory never erase or multiply progress");
        check(WorkshopProgress.explorationKind("explorify:watchtower/plains").equals(
                WorkshopProgress.explorationKind("explorify:watchtower/taiga")),"Watchtower biome variants count as one type");
        check(WorkshopProgress.explorationKind("explorify:supply_cache/forest").equals("supply_cache"),"Supply-cache variants share one type");
        check(WorkshopProgress.explorationKind("explorify:campsite").equals("campsite")
                && WorkshopProgress.explorationKind("explorify:ruins").equals("ruins")
                && WorkshopProgress.explorationKind("explorify:farmstead").equals("farmstead"),"Three genuine structure types stay distinct");
        check(WorkshopProgress.explorationKind("minecraft:village_plains").isEmpty()
                && WorkshopProgress.explorationKind("explorify:end_shipwreck").isEmpty(),"Unrelated or late-dimension structures do not satisfy the early survey");
        check(WorkshopTerrain.floor(new int[]{67,68,69,70,70,70,71,71,72},70,69)==70,
                "A gentle slope keeps both workshop landings within one step of the floor");
        check(WorkshopTerrain.floor(new int[]{64,65,66,66,66,67,67,68,69},67,68)==67,
                "The entrance and cut limits can shift the floor away from the median");
        check(WorkshopTerrain.floor(new int[]{66,67,68,69,70,71,72,73,74},70,70)==WorkshopTerrain.UNSUITABLE,
                "A single rigid workshop must not straddle a cliff");
        check(WorkshopTerrain.floor(new int[]{70,70,70},67,72)==WorkshopTerrain.UNSUITABLE,
                "Level ground in the middle does not compensate for inaccessible approaches");
        check(WorkshopTerrain.floor(new int[]{70,WorkshopTerrain.UNSUITABLE,70},70,70)==WorkshopTerrain.UNSUITABLE
                        && WorkshopTerrain.floor(new int[]{70,70,70},70,WorkshopTerrain.UNSUITABLE)==WorkshopTerrain.UNSUITABLE,
                "A wet footprint or flooded side approach rules out that orientation");
        System.out.println("PASS: workshop foundation limits, dry footprint and both approaches");
        System.out.println("PASS: workshop consumed ingredients, persistent totals and distinct exploration categories");
    }
}
