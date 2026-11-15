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
        System.out.println("PASS: workshop consumed ingredients, persistent totals and distinct exploration categories");
    }
}
