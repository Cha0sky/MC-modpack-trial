package local.lostworld;

import net.minecraft.resources.ResourceLocation;

final class BossBalanceCheck {
    private static BossBalance.Profile profile(String name) {
        return BossBalance.PROFILES.get(ResourceLocation.parse(name));
    }

    private static void close(double a, double b) {
        if (Math.abs(a - b) > 0.001) throw new AssertionError(a + " != " + b);
    }

    static void run() {
        for (String id : new String[]{BossSummonOrigin.KING, BossSummonOrigin.EYE, BossSummonOrigin.BROOD}) {
            var p = profile(id);
            if (p.tier() != 1 || p.incoming(10000) > 10 || p.finalLimit(10000) > 10)
                throw new AssertionError("First-tier hit limit: " + id);
            close(p.incoming(1), p.damageScale());
            close(p.finalLimit(0), 0);
        }
        var king = profile(BossSummonOrigin.KING);
        close(728 * 0.66F * king.healthMultiplier(true, 0), 460);
        close(2 * 728 * 0.66F * king.healthMultiplier(true, 0), 920);
        close(profile("terra_entity:eater_of_worlds").soloHp() +
                60 * profile("terra_entity:eater_of_worlds_segment").soloHp(), 1234);
        close(profile("terra_entity:brain_of_cthulhu").soloHp() +
                20 * profile("terra_entity:visual_neuron").soloHp(), 600);
        var ignis = profile("cataclysm:ignis");
        close(450 * ignis.healthMultiplier(false, 0), 3150);
        close(450 * 4 * ignis.healthMultiplier(false, 3), 3150);
        close(450 * 5 * ignis.healthMultiplier(false, 4), 3937.5);
        close(profile("irons_spellbooks:dead_king").healthMultiplier(false, 0) * 1000, 3200);
        close(profile("mowziesmobs:ferrous_wroughtnaut").healthMultiplier(false, 0), 1);
        close(profile("terra_entity:hill_of_flesh").incoming(40), 21);
        if (!profile("touhou_little_maid_spell:stellar_witch").nativeHealthConfig())
            throw new AssertionError("The duel owns its health bar");
        System.out.println("PASS: encounter budgets, opening hit limits, Classic scaling, multipart and native variants");
    }
}
