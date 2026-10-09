package com.rpgleveling.gametest;

import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.service.XpService;
import com.rpgleveling.service.XpSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 5: experience is booked exactly once per event, the {@code xp_gain}
 * multiplier is applied exactly once (never squared), and the vanilla experience
 * absorption path is untouched (INV-1 / INV-3).
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class XpBookingSuite {

    private XpBookingSuite() {}

    /**
     * {@code xp_gain.percent} is LINEAR now: {@code per_point 5.0 * 40 = 200} percentage
     * points of the line, and the DESIGN_BASELINE contribution is
     * {@code B_ref 50 * 200/100 = 100.0}. Both products are exact in binary floating point,
     * so the attribute really holds 100.0 and the multiplier is exactly 2.0 - which keeps
     * the "booked once, never squared" arithmetic below exact rather than approximate.
     */
    private static final int XP_POINTS = 40;
    private static final double EXPECTED_MULTIPLIER = 2.0;

    private static ServerPlayer withMultiplier(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        com.rpgleveling.service.RpgService.setPoints(player, StatLine.XP_GAIN_PCT, XP_POINTS);
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "xp")
    public static void multiplierIsAppliedExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = withMultiplier(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        GameTestSupport.Report report = new GameTestSupport.Report();

        double predicted = GameTestSupport.lineValue(StatLine.XP_GAIN_PCT, XP_POINTS);
        report.near("xp_gain.percent@" + XP_POINTS, 200.0, predicted, 1.0E-9);

        long before = data.totalXp();
        int granted = XpService.grant(player, XpSource.KILL, 10);
        long delta = data.totalXp() - before;
        report.check(granted == 20, "grant(10) with 2x returned " + granted + ", expected 20");
        report.check(delta == 20L, "totalXp moved by " + delta + ", expected 20 (a squared multiplier would give 40)");
        GameTestSupport.evidence("xp.multiplier", EXPECTED_MULTIPLIER);
        GameTestSupport.evidence("xp.grantedFor10", granted);

        // Booking the same raw amount again books exactly the same amount - never more.
        before = data.totalXp();
        int second = XpService.grant(player, XpSource.KILL, 10);
        report.check(second == granted, "second identical grant returned " + second);
        report.check(data.totalXp() - before == second, "second grant booked the wrong delta");

        // Degenerate inputs must be no-ops.
        report.check(XpService.grant(player, XpSource.KILL, 0) == 0, "grant(0) booked experience");
        report.check(XpService.grant(player, XpSource.KILL, -5) == 0, "grant(-5) booked experience");

        report.assertAllPassed(helper, "multiplierIsAppliedExactlyOnce");
        helper.succeed();
    }

    /** INV-1: the vanilla experience path must not be able to move RPG experience. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "xp")
    public static void vanillaAbsorptionDoesNotTouchRpgExperience(GameTestHelper helper) {
        ServerPlayer player = withMultiplier(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        long before = data.totalXp();
        player.giveExperiencePoints(1000);
        helper.assertTrue(data.totalXp() == before,
                "vanilla giveExperiencePoints changed RPG totalXp by " + (data.totalXp() - before)
                        + " - a second multiplier site would show up exactly here");
        helper.succeed();
    }

    /** The kill event books once and mirrors the granted amount into the vanilla orbs. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "xp")
    public static void killEventBooksExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = withMultiplier(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        LivingEntity victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));

        long before = data.totalXp();
        LivingExperienceDropEvent event = new LivingExperienceDropEvent(victim, player, 7);
        NeoForge.EVENT_BUS.post(event);
        long delta = data.totalXp() - before;

        // Default reward_mode is HEALTH_AND_ATTACK: a zombie is 20 hp + 3 attack * 9 = 47 raw,
        // and the vanilla original value (7) is deliberately ignored.
        helper.assertTrue(delta == 94L, "one kill booked " + delta + " xp, expected round(47*2)=94");
        helper.assertTrue(event.getDroppedExperience() == 94,
                "dropped experience not mirrored: " + event.getDroppedExperience());
        helper.succeed();
    }

    /**
     * Base kill reward = max_health * 1 + attack_damage * 9 (xp.kill defaults).
     *
     * <p>A zombie has 20 hp and 3 attack -> 47. A cow has 10 hp and no ATTACK_DAMAGE
     * attribute at all -> 10, not 28: attack credit requires the attribute to exist, because
     * reading it anyway would silently return the registry default of 2.0.</p>
     *
     * <p>The multiplier is 1.0 here (no xp_gain points), so the booked amount is the raw base.</p>
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "xp")
    public static void killRewardScalesWithHealthAndAttack(GameTestHelper helper) {
        helper.assertTrue(com.rpgleveling.RpgConfig.killRewardMode()
                        == com.rpgleveling.RpgConfig.KillRewardMode.HEALTH_AND_ATTACK,
                "this test needs the default reward_mode; a stale run/config kept "
                        + com.rpgleveling.RpgConfig.killRewardMode());

        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);

        LivingEntity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        long before = data.totalXp();
        NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(zombie, player, 999));
        long zombieXp = data.totalXp() - before;
        helper.assertTrue(zombieXp == 47L,
                "zombie paid " + zombieXp + ", expected round(20*1 + 3*9) = 47");

        LivingEntity cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 1));
        before = data.totalXp();
        NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(cow, player, 999));
        long cowXp = data.totalXp() - before;
        helper.assertTrue(cowXp == 10L,
                "cow paid " + cowXp + ", expected round(10*1 + 0*9) = 10 (no ATTACK_DAMAGE attribute)");

        GameTestSupport.evidence("xp.kill.zombie", zombieXp);
        GameTestSupport.evidence("xp.kill.cow", cowXp);
        helper.succeed();
    }

    /** A mob that killed itself, or was killed without a player, must not pay out. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "xp")
    public static void selfInflictedKillPaysNothing(GameTestHelper helper) {
        ServerPlayer player = withMultiplier(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        LivingEntity victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));

        long before = data.totalXp();
        NeoForge.EVENT_BUS.post(new LivingExperienceDropEvent(victim, victim instanceof net.minecraft.world.entity.player.Player p ? p : null, 7));
        helper.assertTrue(data.totalXp() == before, "a kill without a player attacker paid out");
        helper.succeed();
    }

    /** Requirement 5c: the five non-kill sources pay once per registry entry, never twice. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "xp")
    public static void discoveryPaysOnlyTheFirstTime(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);

        ItemStack stick = new ItemStack(Items.STICK);
        long before = data.totalXp();
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, stick, new SimpleContainer(1)));
        long afterFirst = data.totalXp();
        helper.assertTrue(afterFirst > before, "first craft of a new item paid nothing");

        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK), new SimpleContainer(1)));
        helper.assertTrue(data.totalXp() == afterFirst,
                "second craft of the same item paid again: +" + (data.totalXp() - afterFirst));

        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK), new SimpleContainer(1)));
        helper.assertTrue(data.totalXp() == afterFirst, "third craft of the same item paid again");

        // A different item is a different discovery and must still pay.
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.BONE), new SimpleContainer(1)));
        helper.assertTrue(data.totalXp() > afterFirst, "a genuinely new item did not pay");
        helper.succeed();
    }
}
