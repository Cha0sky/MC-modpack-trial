package local.lostworld;

import java.util.Set;

/** Route-completion rules shared by runtime observations and lightweight JVM checks. */
public final class HeritageProgress {
    public static final int ENGINEERING=1,ARCANE=2,EXPEDITION=4;
    private static final Set<String> EXPEDITION_STRUCTURES=Set.of(
            "mowziesmobs:wrought_chamber","confluence:enchanted_sword_shrine",
            "cataclysm:desert_site","cataclysm:ancient_factory");
    private static final Set<String> CHALLENGES=Set.of(
            "mowziesmobs:ferrous_wroughtnaut","mowziesmobs:frostmaw",
            "terra_entity:eye_of_cthulhu","terra_entity:eater_of_worlds","terra_entity:brain_of_cthulhu",
            "goety:skull_lord");

    private HeritageProgress() {}

    public static int contribute(int routes,int bit) {
        if (bit!=ENGINEERING && bit!=ARCANE && bit!=EXPEDITION)
            throw new IllegalArgumentException("One recognized heritage route is required");
        return routes|bit;
    }

    public static boolean ready(int routes) { return Integer.bitCount(routes&7)>=2; }

    public static boolean arcaneComplete(boolean ironFoundation,boolean ironCast,
                                         boolean goetyFoundation,boolean goetySummon,boolean goetyKill) {
        return ironFoundation && ironCast || goetyFoundation && goetySummon && goetyKill;
    }

    public static boolean expeditionStructure(String id) { return EXPEDITION_STRUCTURES.contains(id); }
    public static boolean acceptedChallenge(String id) { return CHALLENGES.contains(id); }

    public static String surveyQuest(String structureId) {
        return switch (structureId) {
            case "irons_spellbooks:catacombs" -> "catacombs";
            case "minecraft:mansion" -> "mansion";
            default -> "";
        };
    }

    static boolean inDepotRange(int x, int z, int siteX, int siteZ) {
        long dx = (long) x - siteX;
        long dz = (long) z - siteZ;
        return dx * dx + dz * dz <= 128 * 128;
    }

    public static String bossContract(String entityId) {
        return switch (entityId) {
            case "irons_spellbooks:dead_king" -> "dead_king";
            case "goety:vizier" -> "vizier";
            default -> "";
        };
    }

    public static String siteKind(String id) {
        if (expeditionStructure(id) || !surveyQuest(id).isEmpty()) return id;
        String explorify=WorkshopProgress.explorationKind(id);
        return explorify.isEmpty()?"":"explorify:"+explorify;
    }
}
