package com.rpgleveling.gametest;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.event.DropRateHelper;
import com.rpgleveling.service.PlayerCache;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.service.StatOverrides;
import com.rpgleveling.stat.DerivedStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T9: the <b>gameplay effect layer</b> of the six custom dimensions.
 *
 * <p>The other suites prove that a point investment reaches the attribute; this one proves
 * that the value is then actually consumed by the combat / drop / regeneration logic.
 * Every assertion here observes a real game quantity (health lost, health healed, item
 * count, entity count, crit outcome), never an intermediate value.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class CombatEffectSuite {

    private CombatEffectSuite() {}

    /** Points that put damage reduction at exactly SAT(200, 60, 80) = 42.857%. */
    private static final int DR_POINTS = 200;
    /** Points that put max health at 20 + 200 = 220. */
    private static final int HEALTH_POINTS = 200;
    /**
     * lifesteal.percent @80 is exactly 10%: LINEAR 1.25/pt -> 100 percentage points of the
     * line, and the DESIGN_BASELINE contribution is B_ref 10 * 100/100 = 10.0. The percent
     * line is used (not the flat line) because 1.25 * 80 and 10 * 100/100 are both exact in
     * binary floating point, so the whole-point healing thresholds below stay exact.
     */
    private static final int LIFESTEAL_PCT_POINTS = 80;

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ServerPlayer toughPlayer(GameTestHelper helper, int extraHealthPoints) {
        ServerPlayer player = GameTestSupport.join(helper);
        RpgService.setPoints(player, StatLine.MAX_HEALTH_FLAT, extraHealthPoints);
        return player;
    }

    /**
     * Damage from a mob attack: {@code minecraft:generic} is tagged {@code bypasses_armor},
     * so it can never demonstrate the armour pipeline - a mob attack is not.
     */
    private static float hitByMob(ServerPlayer player, LivingEntity attacker, float amount) {
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(player.damageSources().mobAttack(attacker), amount);
        return before - player.getHealth();
    }

    /** Damage with no attacker and no armour interaction, bypassing the hurt cooldown. */
    private static float hit(ServerPlayer player, float amount) {
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(player.damageSources().generic(), amount);
        return before - player.getHealth();
    }

    private static float hitWith(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    // ------------------------------------------------------------------
    // 1. damage reduction, and its composition with the armour pipeline
    // ------------------------------------------------------------------

    /**
     * A freshly placed {@link ServerPlayer} carries {@code spawnInvulnerableTime = 60} and
     * {@code ServerPlayer#hurt} refuses every non-bypassing source until it reaches zero,
     * so any damage-based assertion has to wait it out first.
     */
    private static final int SPAWN_INVULNERABILITY_TICKS = 65;

    private static void afterSpawnProtection(GameTestHelper helper, Runnable body) {
        helper.runAfterDelay(SPAWN_INVULNERABILITY_TICKS, body);
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "effects")
    public static void damageReductionScalesTheDamageThatArrives(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);

        afterSpawnProtection(helper, () -> {
            double reduction = DerivedStats.damageReductionPercent(player);
            GameTestSupport.evidence("dr.percent", String.format(Locale.ROOT, "%.6f", reduction));
            helper.assertTrue(reduction > 1.0, "damage reduction is not active: " + reduction);

            player.setHealth(player.getMaxHealth());
            float lost = hit(player, 100.0F);
            GameTestSupport.evidence("dr.lostFor100", String.format(Locale.ROOT, "%.6f", lost));

            helper.assertTrue(Math.abs(lost - 100.0F * (1.0 - reduction / 100.0)) < 1.0E-3,
                    "100 damage with " + reduction + "% reduction removed " + lost
                            + " hp, expected " + (100.0 * (1.0 - reduction / 100.0)));
            helper.assertTrue(player.getHealth() > 0.0F, "the player should have survived 100 reduced damage");
            helper.assertTrue(lost > 0.0F, "damage reduction made the player immune");
            helper.succeed();
        });
    }

    /** The reduction must sit on top of the armour pipeline, not inside it (no double count). */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "effects")
    public static void reductionComposesWithArmourWithoutDoubleCounting(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);

        afterSpawnProtection(helper, () -> {
            GameTestSupport.Report report = new GameTestSupport.Report();
            LivingEntity attacker = helper.spawn(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(6, 2, 6));
            attacker.getAttribute(Attributes.ARMOR).setBaseValue(0.0);

            // (a) armour only
            RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, 0);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            applyWorn(player);
            GameTestSupport.evidence("compose.armorAttribute", player.getAttribute(Attributes.ARMOR).getValue());
            player.setHealth(player.getMaxHealth());
            float armourOnly = hitByMob(player, attacker, 100.0F);

            // (b) armour + reduction
            RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);
            double reduction = DerivedStats.damageReductionPercent(player);
            player.setHealth(player.getMaxHealth());
            float both = hitByMob(player, attacker, 100.0F);

            // (c) reduction only: strip the armour again so the three regimes are distinct
            RpgService.respec(player);
            for (EquipmentSlot slot : new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            stripWornModifiers(player);
            RpgService.setPoints(player, StatLine.MAX_HEALTH_FLAT, HEALTH_POINTS);
            RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);
            player.setHealth(player.getMaxHealth());
            float reductionOnly = hitByMob(player, attacker, 100.0F);
            GameTestSupport.evidence("compose.armorAfterStrip", player.getAttribute(Attributes.ARMOR).getValue());

            GameTestSupport.evidence("compose.armourOnly", armourOnly);
            GameTestSupport.evidence("compose.reductionOnly", reductionOnly);
            GameTestSupport.evidence("compose.both", both);
            GameTestSupport.evidence("compose.drPercent", reduction);

            // Both layers must be live, otherwise the composition assertion is vacuous.
            report.check(armourOnly < 100.0F - 1.0E-3, "iron armour reduced nothing: " + armourOnly);
            report.check(reductionOnly < 100.0F - 1.0E-3, "reduction reduced nothing: " + reductionOnly);

            // Serial composition: the reduction multiplies what survived the armour pipeline.
            float predicted = (float) (armourOnly * (1.0 - reduction / 100.0));
            report.near("armour+reduction", predicted, both, 1.0E-2);
            report.assertAllPassed(helper, "reductionComposesWithArmourWithoutDoubleCounting");
            helper.succeed();
        });
    }

    /** Removes the equipment-sourced modifiers again, so the naked regime is really naked. */
    private static void stripWornModifiers(ServerPlayer player) {
        for (net.minecraft.resources.ResourceLocation id : new net.minecraft.resources.ResourceLocation[] {
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("armor.helmet"),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("armor.chestplate"),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("armor.leggings"),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("armor.boots") }) {
            var armor = player.getAttribute(Attributes.ARMOR);
            if (armor != null) armor.removeModifier(id);
            var toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
            if (toughness != null) toughness.removeModifier(id);
        }
    }

    private static void applyWorn(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack stack = player.getItemBySlot(slot);
            stack.forEachModifier(slot, (attribute, modifier) -> {
                var instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // 2. hard bypass: /kill and the void must always work
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 600, batch = "effects")
    public static void killCommandStillKillsThroughMaxReduction(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);
        RpgService.setPoints(player, StatLine.ARMOR_FLAT, 200);
        RpgService.setPoints(player, StatLine.ARMOR_TOUGHNESS_FLAT, 200);

        afterSpawnProtection(helper, () -> {
            double reduction = DerivedStats.damageReductionPercent(player);
            float maxHealth = player.getMaxHealth();

            // 300 raw damage is more than the whole health bar; every layer is maxed, so the
            // only reason it can be survived is the reduction (and armour) doing their job.
            // With an unbounded amount both the reduced and the bypassed path would kill,
            // which is what makes this test non-vacuous.
            player.setHealth(maxHealth);
            float lostReduced = hit(player, 300.0F);
            GameTestSupport.evidence("bypass.lostReduced", lostReduced);
            helper.assertTrue(player.isAlive(),
                    "300 damage with " + reduction + "% reduction and maxed armour killed the player");
            helper.assertTrue(lostReduced < 300.0F * 0.75F,
                    "the reduction did not bite: " + lostReduced + " of 300 got through");
            helper.assertTrue(lostReduced > 0.0F, "the player took no damage at all");

            // /kill goes through generic_kill, which is hard-wired to bypass the reduction.
            player.setHealth(maxHealth);
            hitWith(player, player.damageSources().genericKill(), 300.0F);
            helper.assertTrue(!player.isAlive(),
                    "generic_kill (used by /kill) was reduced and the player survived - it must never be blocked");

            // The void is the second hard-wired bypass.
            ServerPlayer second = toughPlayer(helper, HEALTH_POINTS);
            RpgService.setPoints(second, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);
            second.setHealth(second.getMaxHealth());
            hitWith(second, second.damageSources().fellOutOfWorld(), 300.0F);
            helper.assertTrue(!second.isAlive(), "out_of_world damage was reduced and the player survived");

            // And the literal /kill call the acceptance criteria name.
            ServerPlayer third = toughPlayer(helper, HEALTH_POINTS);
            RpgService.setPoints(third, StatLine.DAMAGE_REDUCTION_FLAT, DR_POINTS);
            third.kill();
            helper.assertTrue(!third.isAlive(), "player.kill() did not kill a fully invested player");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------
    // 3. critical hits
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void critChanceAndMultiplierAreHonoured(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        // 60 * 0.833333 = 49.99998%: a non-degenerate probability, so the 5-sigma binomial
        // check below is still a real check. (Investment above 100% is pinned by
        // CurvePolicySuite.functionalCeilingsAreFixedAndBite, not here.)
        RpgService.setPoints(player, StatLine.CRIT_CHANCE_FLAT, 60);
        RpgService.setPoints(player, StatLine.CRIT_DAMAGE_FLAT, 100);
        helper.assertTrue(Math.abs(DerivedStats.critChancePercent(player) - 49.99998) < 1.0E-4,
                "crit chance should be 60 * 0.833333 = 49.99998% but is "
                        + DerivedStats.critChancePercent(player));
        double chance = DerivedStats.critChancePercent(player);
        double bonus = DerivedStats.critDamagePercent(player);
        GameTestSupport.evidence("crit.chance", String.format(Locale.ROOT, "%.6f", chance));
        GameTestSupport.evidence("crit.bonus", String.format(Locale.ROOT, "%.6f", bonus));

        int trials = 4000;
        int crits = 0;
        float multiplierSeen = -1.0F;
        for (int i = 0; i < trials; i++) {
            CriticalHitEvent event = new CriticalHitEvent(player, player, 1.0F, false);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCriticalHit()) {
                crits++;
                multiplierSeen = event.getDamageMultiplier();
            }
        }
        double observed = 100.0 * crits / trials;
        GameTestSupport.evidence("crit.observedPercent", String.format(Locale.ROOT, "%.3f", observed));
        GameTestSupport.evidence("crit.multiplier", multiplierSeen);

        // 5 sigma on a binomial with n=4000: +-145 hits, flake probability < 1e-6.
        double sigma = Math.sqrt(trials * (chance / 100.0) * (1.0 - chance / 100.0));
        helper.assertTrue(Math.abs(crits - trials * chance / 100.0) <= 5.0 * sigma + 1.0,
                "crit rate " + observed + "% deviates from the expected " + chance + "% by more than 5 sigma");

        float expectedMultiplier = (float) (1.5 + bonus / 100.0);
        helper.assertTrue(Math.abs(multiplierSeen - expectedMultiplier) < 1.0E-4,
                "crit multiplier " + multiplierSeen + " != 1.5 + crit_damage/100 = " + expectedMultiplier);

        // forcecrit is the debug switch verifier's crit_damage workflow depends on.
        StatOverrides.setForceCrit(player.getUUID(), true);
        try {
            CriticalHitEvent forced = new CriticalHitEvent(player, player, 1.0F, false);
            NeoForge.EVENT_BUS.post(forced);
            helper.assertTrue(forced.isCriticalHit(), "forcecrit did not force a critical hit");
        } finally {
            StatOverrides.setForceCrit(player.getUUID(), false);
        }

        // With no crit chance invested the vanilla outcome must be left alone.
        RpgService.respec(player);
        CriticalHitEvent untouched = new CriticalHitEvent(player, player, 1.0F, false);
        NeoForge.EVENT_BUS.post(untouched);
        helper.assertTrue(!untouched.isCriticalHit(), "a zero crit chance invented a critical hit");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 4. life steal
    // ------------------------------------------------------------------

    private static LivingEntity victim(GameTestHelper helper, float maxHealth) {
        LivingEntity entity = helper.spawn(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(2, 2, 2));
        var armor = entity.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(0.0);
        armor = entity.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor != null) armor.setBaseValue(0.0);
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        entity.setHealth(maxHealth);
        entity.invulnerableTime = 0;
        return entity;
    }

    private static void strike(ServerPlayer player, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), amount);
    }

    /** Raising the per-second cap is the only way to observe the amount behind the throttle. */
    private static double withUnthrottledLifesteal(java.util.function.Supplier<Double> body) {
        double previous = RpgConfig.LIFESTEAL_HPS_CAP.get();
        try {
            RpgConfig.LIFESTEAL_HPS_CAP.set(1.0E7);
            return body.get();
        } finally {
            RpgConfig.LIFESTEAL_HPS_CAP.set(previous);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void lifestealHealsProportionalToHealthActuallyLost(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.LIFESTEAL_PCT, LIFESTEAL_PCT_POINTS);
        double percent = DerivedStats.lifestealPercent(player);
        GameTestSupport.evidence("lifesteal.percent", String.format(Locale.ROOT, "%.6f", percent));
        helper.assertTrue(Math.abs(percent - 10.0) < 1.0E-9,
                "lifesteal should be exactly 10% (80 points * 1.25 -> B_ref 10 * 1.0) but is " + percent);

        double healed = withUnthrottledLifesteal(() -> {
            LivingEntity target = victim(helper, 1000.0F);
            player.setHealth(player.getMaxHealth() - 100.0F);
            PlayerCache.of(player.getUUID()).lifestealAccumulator = 0.0;
            double before = player.getHealth();
            strike(player, target, 100.0F);
            return player.getHealth() - before;
        });
        GameTestSupport.evidence("lifesteal.healedFrom100", healed);
        helper.assertTrue(Math.abs(healed - 10.0) < 1.0E-3,
                "100 damage at 10% lifesteal healed " + healed + " instead of 10");

        // Overkill must not pay: a 20 hp target hit for 100 can only lose 20.
        double overkill = withUnthrottledLifesteal(() -> {
            LivingEntity target = victim(helper, 20.0F);
            player.setHealth(player.getMaxHealth() - 100.0F);
            PlayerCache.of(player.getUUID()).lifestealAccumulator = 0.0;
            double before = player.getHealth();
            strike(player, target, 100.0F);
            return player.getHealth() - before;
        });
        GameTestSupport.evidence("lifesteal.healedFromOverkill", overkill);
        helper.assertTrue(Math.abs(overkill - 2.0) < 1.0E-3,
                "overkill leaked: 100 damage on a 20 hp target healed " + overkill
                        + " instead of 20 * 10% = 2 (design 1.10: steal the health actually lost)");

        // A one-heart target is the case the design names explicitly: 2 hp * 10% = 0.2, which
        // is below one hit point, so it must land in the accumulator rather than in the bar.
        double oneHeart = withUnthrottledLifesteal(() -> {
            LivingEntity target = victim(helper, 2.0F);
            player.setHealth(player.getMaxHealth() - 100.0F);
            PlayerCache.of(player.getUUID()).lifestealAccumulator = 0.0;
            double before = player.getHealth();
            strike(player, target, 100.0F);
            GameTestSupport.evidence("lifesteal.accumulatorAfterOneHeart",
                    PlayerCache.of(player.getUUID()).lifestealAccumulator);
            return player.getHealth() - before;
        });
        GameTestSupport.evidence("lifesteal.healedFromOneHeart", oneHeart);
        double accumulator = PlayerCache.of(player.getUUID()).lifestealAccumulator;
        helper.assertTrue(Math.abs(accumulator - 0.2) < 1.0E-3,
                "one-heart overkill banked " + accumulator + " instead of 2 * 10% = 0.2");
        helper.assertTrue(Math.abs(oneHeart) < 1.0E-6,
                "a sub-hit-point steal must not change the health bar, but it moved " + oneHeart);

        // Five of them add up to a whole hit point: the accumulator must pay out exactly once.
        double fiveHearts = withUnthrottledLifesteal(() -> {
            PlayerCache.of(player.getUUID()).lifestealAccumulator = 0.0;
            player.setHealth(player.getMaxHealth() - 100.0F);
            double before = player.getHealth();
            for (int i = 0; i < 5; i++) {
                strike(player, victim(helper, 2.0F), 100.0F);
            }
            return player.getHealth() - before;
        });
        GameTestSupport.evidence("lifesteal.healedFromFiveOneHeartTargets", fiveHearts);
        helper.assertTrue(Math.abs(fiveHearts - 1.0) < 1.0E-3,
                "five one-heart steals (5 x 0.2) healed " + fiveHearts + " instead of exactly 1");
        helper.succeed();
    }

    /** The hps cap is a real throttle: many hits inside one tick cannot refill the bar. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void lifestealIsThrottledPerTick(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.LIFESTEAL_PCT, LIFESTEAL_PCT_POINTS);
        double cap = RpgConfig.lifestealHpsCap();
        LivingEntity target = victim(helper, 10000.0F);
        PlayerCache.of(player.getUUID()).lifestealAccumulator = 0.0;
        player.setHealth(player.getMaxHealth() - 100.0F);
        double before = player.getHealth();
        for (int i = 0; i < 20; i++) {
            target.setHealth(10000.0F);
            strike(player, target, 100.0F);
        }
        double healed = player.getHealth() - before;
        GameTestSupport.evidence("lifesteal.throttledHeal", healed);
        helper.assertTrue(healed <= cap / 20.0 + 1.0,
                "20 hits in one tick healed " + healed + " hp, above the per-tick budget " + (cap / 20.0));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 5. drop rate
    // ------------------------------------------------------------------

    private static List<ItemEntity> drop(ServerLevel level, int entities, int countPerEntity) {
        List<ItemEntity> out = new ArrayList<>();
        for (int i = 0; i < entities; i++) {
            ItemEntity entity = new ItemEntity(level, 8.0 + i, 2.0, 8.0, new ItemStack(Items.STONE, countPerEntity));
            level.addFreshEntity(entity);
            out.add(entity);
        }
        return out;
    }

    /** Item total over the whole level: splitting creates entities outside the input list. */
    private static int countItems(ServerLevel level) {
        int total = 0;
        for (net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
            if (entity instanceof ItemEntity item && item.isAlive()) {
                total += item.getItem().getCount();
            }
        }
        return total;
    }

    private static int maxStack(ServerLevel level) {
        int max = 0;
        for (net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
            if (entity instanceof ItemEntity item && item.isAlive()) {
                max = Math.max(max, item.getItem().getCount());
            }
        }
        return max;
    }

    private static int countItemEntities(ServerLevel level) {
        int n = 0;
        for (net.minecraft.world.entity.Entity entity : level.getEntities().getAll()) {
            if (entity instanceof ItemEntity) n++;
        }
        return n;
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void dropMultiplierScalesAndSplitsAboveOneStack(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        // drop_rate.percent @60 -> LINEAR 5.0/pt = 300 -> B_ref 50 * 3.0 = +150% -> x2.5
        RpgService.setPoints(player, StatLine.DROP_RATE_PCT, 60);
        double multiplier = DerivedStats.dropMultiplier(player);
        GameTestSupport.evidence("drop.multiplier", multiplier);
        helper.assertTrue(Math.abs(multiplier - 2.5) < 1.0E-9, "drop multiplier should be 2.5 but is " + multiplier);

        ServerLevel level = helper.getLevel();

        // 10 stone at x2.5 -> 25, comfortably inside one stack.
        int itemsBefore = countItems(level);
        List<ItemEntity> small = drop(level, 1, 10);
        DropRateHelper.applyDropMultiplier(player, level, small);
        int smallDelta = countItems(level) - itemsBefore;
        helper.assertTrue(smallDelta == 25,
                "10 stone at x2.5 produced " + smallDelta + " items instead of 25");

        // 64 stone at x2.5 -> 160, which must become real extra entities, never a count > 64.
        int itemsBeforeBig = countItems(level);
        int entitiesBeforeBig = countItemEntities(level);
        List<ItemEntity> big = drop(level, 1, 64);
        DropRateHelper.applyDropMultiplier(player, level, big);
        int bigDelta = countItems(level) - itemsBeforeBig;
        int newEntities = countItemEntities(level) - entitiesBeforeBig;
        GameTestSupport.evidence("drop.split.totalItems", bigDelta);
        GameTestSupport.evidence("drop.split.newEntities", newEntities);

        helper.assertTrue(bigDelta == 160, "64 stone at x2.5 produced " + bigDelta + " items instead of 160");
        helper.assertTrue(newEntities >= 1, "an above-one-stack drop was not split into new entities");
        helper.assertTrue(maxStack(level) <= 64, "a stack above 64 was produced: " + maxStack(level));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void dropRateHonoursBothHardBudgets(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        RpgService.setPoints(player, StatLine.DROP_RATE_PCT, 60);
        ServerLevel level = helper.getLevel();

        int maxItems = RpgConfig.dropMaxItemsPerEvent();
        int maxEntities = RpgConfig.dropMaxEntitiesPerEvent();

        List<ItemEntity> drops = drop(level, 40, 64);
        int itemsBeforeApply = countItems(level);
        int entitiesBeforeApply = countItemEntities(level);

        DropRateHelper.applyDropMultiplier(player, level, drops);

        int itemsAdded = countItems(level) - itemsBeforeApply;
        int entitiesAdded = countItemEntities(level) - entitiesBeforeApply;
        int maxStackSeen = maxStackAmong(drops, level);

        GameTestSupport.evidence("drop.budget.itemsAdded", itemsAdded);
        GameTestSupport.evidence("drop.budget.entitiesAdded", entitiesAdded);
        GameTestSupport.evidence("drop.budget.maxStackSeen", maxStackSeen);
        GameTestSupport.evidence("drop.budget.budgetItems", maxItems);
        GameTestSupport.evidence("drop.budget.budgetEntities", maxEntities);

        helper.assertTrue(maxStackSeen <= 64, "an item stack exceeded one stack: " + maxStackSeen);
        helper.assertTrue(entitiesAdded <= maxEntities,
                "the multiplier spawned " + entitiesAdded + " entities, budget is " + maxEntities);
        helper.assertTrue(itemsAdded <= maxItems,
                "the multiplier added " + itemsAdded + " items, the per-event budget is " + maxItems);
        helper.assertTrue(itemsAdded > 0, "the multiplier did nothing, so the budget assertions are vacuous");
        helper.succeed();
    }

    /** Largest stack among the touched entities and everything created near them. */
    private static int maxStackAmong(List<ItemEntity> drops, ServerLevel level) {
        int max = 0;
        for (ItemEntity entity : drops) {
            if (entity.isAlive()) max = Math.max(max, entity.getItem().getCount());
        }
        return Math.max(max, maxStack(level));
    }

    // ------------------------------------------------------------------
    // 6. health regeneration
    // ------------------------------------------------------------------

    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void regenerationMatchesTheAdvertisedRate(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.HEALTH_REGEN_FLAT, 40);   // LINEAR 0.125/pt = 5.0 HP/s
        double hps = DerivedStats.healthRegenPerSecond(player);
        GameTestSupport.evidence("regen.hps", String.format(Locale.ROOT, "%.6f", hps));
        helper.assertTrue(Math.abs(hps - 5.0) < 1.0E-6, "health regen should be 5.0 HP/s but is " + hps);

        PlayerCache.of(player.getUUID()).regenAccumulator = 0.0;
        player.setHealth(5.0F);
        player.hurtTime = 10;               // only_when_hurt is on by default
        int foodBefore = player.getFoodData().getFoodLevel();
        float saturationBefore = player.getFoodData().getSaturationLevel();

        int ticks = 200;
        for (int i = 0; i < ticks; i++) {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        double healed = player.getHealth() - 5.0F;
        GameTestSupport.evidence("regen.healedOver200Ticks", healed);

        helper.assertTrue(Math.abs(healed - hps * ticks / 20.0) < 1.0,
                "200 ticks at " + hps + " HP/s healed " + healed + " instead of " + (hps * ticks / 20.0));
        helper.assertTrue(player.getFoodData().getFoodLevel() == foodBefore,
                "the mod's regeneration consumed hunger, which it must never touch");
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - saturationBefore) < 1.0E-6,
                "the mod's regeneration changed saturation");
        helper.assertTrue(player.getHealth() <= player.getMaxHealth(), "regeneration overflowed max health");
        helper.succeed();
    }

    /** Fractional rates must accumulate rather than being lost or spammed. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "effects")
    public static void fractionalRegenerationAccumulates(GameTestHelper helper) {
        ServerPlayer player = toughPlayer(helper, HEALTH_POINTS);
        RpgService.setPoints(player, StatLine.HEALTH_REGEN_FLAT, 7);    // LINEAR 0.125/pt = 0.875 HP/s
        double hps = DerivedStats.healthRegenPerSecond(player);
        double perTick = hps / 20.0;
        GameTestSupport.evidence("regen.fractionalHps", String.format(Locale.ROOT, "%.6f", hps));
        helper.assertTrue(hps > 0.0 && hps < 1.0, "expected a sub-1 HP/s rate, got " + hps);

        PlayerCache.of(player.getUUID()).regenAccumulator = 0.0;
        player.setHealth(1.0F);
        player.hurtTime = 10;

        // 15 ticks is 0.625 hp: not a single whole hit point, so nothing may be healed.
        // An implementation that called heal(perTick) every tick would already be at 1.625.
        for (int i = 0; i < 15; i++) {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        float after15 = player.getHealth();
        GameTestSupport.evidence("regen.after15Ticks", after15);
        helper.assertTrue(Math.abs(after15 - 1.0F) < 1.0E-4,
                "15 ticks = " + (15 * perTick) + " hp must not heal anything yet, health is " + after15);

        // 30 ticks is 1.25 hp: exactly one whole hit point, with 0.25 carried over.
        for (int i = 0; i < 15; i++) {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        float after30 = player.getHealth();
        GameTestSupport.evidence("regen.after30Ticks", after30);
        helper.assertTrue(Math.abs(after30 - 2.0F) < 1.0E-4,
                "30 ticks = " + (30 * perTick) + " hp should have healed exactly 1 hp, health is " + after30);

        // 270 ticks is 11.25 hp total: ten whole hit points plus the 0.25 still carried.
        for (int i = 0; i < 240; i++) {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        float after270 = player.getHealth();
        GameTestSupport.evidence("regen.after270Ticks", after270);
        helper.assertTrue(Math.abs(after270 - 12.0F) < 0.05F,
                "270 ticks at " + hps + " HP/s should total 11.25 hp, health is " + after270);
        helper.succeed();
    }
}