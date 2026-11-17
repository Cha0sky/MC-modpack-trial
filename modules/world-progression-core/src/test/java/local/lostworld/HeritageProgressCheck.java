package local.lostworld;

final class HeritageProgressCheck {
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }

    static void run() {
        for (int one:new int[]{0,1,2,4}) check(!HeritageProgress.ready(one),"A single route cannot open convergence");
        for (int two:new int[]{3,5,6,7}) check(HeritageProgress.ready(two),"Every pair of distinct routes opens convergence");
        check(HeritageProgress.contribute(1,1)==1,"Repeating one route never counts as a second route");
        check(HeritageProgress.contribute(1,2)==3 && HeritageProgress.contribute(3,4)==7,
                "Independent route contributions retain previous world facts");
        check(HeritageProgress.arcaneComplete(true,true,false,false,false),"The complete Iron route suffices alone");
        check(HeritageProgress.arcaneComplete(false,false,true,true,true),"The complete Goety route suffices alone");
        check(!HeritageProgress.arcaneComplete(true,false,false,true,true)
                && !HeritageProgress.arcaneComplete(false,true,true,false,false),
                "Foundations and practice from different schools cannot be mixed into a false completion");
        check(HeritageProgress.expeditionStructure("confluence:enchanted_sword_shrine")
                && !HeritageProgress.expeditionStructure("explorify:ruins"),
                "Explorify sightseeing does not replace the four substantial expedition targets");
        check(HeritageProgress.siteKind("explorify:watchtower/plains").equals(
                HeritageProgress.siteKind("explorify:watchtower/taiga")),
                "Archive sites group biome variants while retaining their actual structure IDs");
        check(HeritageProgress.acceptedChallenge("terra_entity:eater_of_worlds")
                && HeritageProgress.acceptedChallenge("goety:skull_lord")
                && !HeritageProgress.acceptedChallenge("terra_entity:eater_of_worlds_segment")
                && !HeritageProgress.acceptedChallenge("minecraft:slime")
                && !HeritageProgress.acceptedChallenge("cataclysm:the_harbinger")
                && !HeritageProgress.acceptedChallenge("cataclysm:ignis")
                && !HeritageProgress.acceptedChallenge("goety:apostle"),
                "Only a recognized root boss contributes an expedition challenge");
        System.out.println("PASS: heritage any-two convergence, same-school arcane completion, site categories and root-boss identity");
    }
}
