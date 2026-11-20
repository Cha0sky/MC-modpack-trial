package local.lostworld;

public final class ChapterLogicCheck {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        WorkshopProgressCheck.run();
        HeritageProgressCheck.run();
        check(HeritageProgress.inDepotRange(-138, 30, -10, 30), "A camp can sit on the 128-block boundary");
        check(!HeritageProgress.inDepotRange(-138, 31, -10, 30), "Use a horizontal radius around the archived site");
        check(new WorldTurnEvents.Status(true,true,WorldTurnEvents.Conversion.IN_PROGRESS).reachedWorldTurn(),
                "A defeated flesh boss and active hardmode count while terrain conversion is underway");
        check(!new WorldTurnEvents.Status(false,true,WorldTurnEvents.Conversion.COMPLETED).reachedWorldTurn(),
                "Native hardmode conversion by itself is not a flesh-boss victory");
        check(!new WorldTurnEvents.Status(true,false,WorldTurnEvents.Conversion.NOT_STARTED).reachedWorldTurn(),
                "A recorded fight alone does not imply that native hardmode is enabled");
        System.out.println("PASS: world turn gates, conversion status independent of the boss and mode flags");
        SettlementGeometryCheck.run();
        SettlementLayoutCheck.run();
        ChapterState old = new ChapterState(false,false);
        check(!old.blocksEvil(true,false),"Old worlds remain passive");
        check(!old.defeat(ChapterState.KING_SLIME),"Old-world deaths do not activate a chapter");
        ChapterState fresh = new ChapterState(true,false);
        check(fresh.blocksEvil(true,false),"Evil region initially locked");
        check(!fresh.blocksEvil(false,false),"Normal biomes accessible");
        check(!fresh.blocksEvil(true,true),"Spectator inspection allowed");
        check(!fresh.defeat("minecraft:slime"),"Normal slime cannot unlock");
        check(!fresh.defeat("terra_entity:king_slime_minion"),"Minions cannot unlock");
        check(fresh.defeat(ChapterState.KING_SLIME),"Actual King unlocks");
        check(!fresh.defeat(ChapterState.KING_SLIME),"Repeated death is idempotent");
        check(!fresh.blocksEvil(true,false),"Unlocked biome accessible");
        ChapterState reload = new ChapterState(fresh.active(),fresh.defeated());
        check(!reload.blocksEvil(true,false),"Persisted world remains unlocked");
        check(SettlementTerrainPreview.dryAndWithinSlope(new int[]{70,70,70,70,70,70,70,70,70},63),"Dry inland footprint allowed");
        check(!SettlementTerrainPreview.dryAndWithinSlope(new int[]{62,70,70,70,70,70,70,70,70},63),"Wet corner rejected even with a dry centre");
        check(!SettlementTerrainPreview.dryAndWithinSlope(new int[]{70,70,70,70,63,70,70,70,70},63),"Sea-level centre rejected");
        check(SettlementTerrainPreview.dryAndWithinSlope(new int[]{64,70,76,70,70,70,70,70,70},63),"Twelve-block rise remains allowed");
        check(!SettlementTerrainPreview.dryAndWithinSlope(new int[]{64,70,77,70,70,70,70,70,70},63),"Thirteen-block rise rejected before chunk loading");
        check(!SettlementTerrainPreview.dryAndWithinSlope(new int[]{58,58,58,58,58,58,58,58,58},63),"Ocean bottom is not land");
        System.out.println("PASS: inland preview, water at edges, sea-level and slope boundaries");
        System.out.println("PASS: chapter state, root boss identity, idempotency, restore and old-world passivity");
    }
}
