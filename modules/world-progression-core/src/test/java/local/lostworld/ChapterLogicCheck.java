package local.lostworld;

public final class ChapterLogicCheck {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        WorkshopProgressCheck.run();
        BossBalanceCheck.run();
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
        StructureCompassCheck.run();
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
        check(SettlementTerrainPreview.drySurface(-60,-60,-64),"Dry superflat ground below sea level is usable");
        check(!SettlementTerrainPreview.drySurface(58,63,-64),"Submerged ground is rejected");
        check(!SettlementTerrainPreview.drySurface(-64,-64,-64),"An empty void has no footing");
        var slope=SettlementTerrainPreview.footprint(new int[]{64,70,76,70,70,70,70,70,70});
        check(slope!=null && slope.median()==70 && slope.relief()==12,"Twelve-block footprint relief is allowed");
        check(SettlementTerrainPreview.footprint(new int[]{64,70,77,70,70,70,70,70,70})==null,"Steep footprint rejected");
        var flat=new SettlementTerrainPreview.Footprint(70,0);
        var ledge=new SettlementTerrainPreview.Footprint(93,0);
        int gentle=SettlementTerrainPreview.siteCost(java.util.List.of(flat,flat,flat),32);
        check(gentle<SettlementTerrainPreview.siteCost(java.util.List.of(flat,flat,ledge),0),
                "A nearby level street outranks a spawn house overlooking a cliff");
        check(gentle<SettlementTerrainPreview.siteCost(java.util.List.of(flat,slope,slope),0),
                "Terrain under the other buildings affects the rank of a flat home");
        check(SettlementTerrainPreview.siteCost(java.util.Arrays.asList(flat,null,flat),0)>gentle,
                "Plots that need to move cost more but still leave an alternative");
        System.out.println("PASS: whole-town terrace ranking, dry lowlands, drowned sites and void");
        System.out.println("PASS: chapter state, root boss identity, idempotency, restore and old-world passivity");
    }
}
