package com.rpgleveling.gametest;

import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.service.RpgService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Requirement 7 (VERIFICATION 7.2 / 7.3): every out-of-contract request is rejected by
 * the server and changes nothing. This is the same code path a forged client packet takes.
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class AuthoritySuite {

    private AuthoritySuite() {}

    @GameTest(template = "empty", timeoutTicks = 300, batch = "authority")
    public static void malformedRequestsAreRejected(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        GameTestSupport.Report report = new GameTestSupport.Report();

        RpgService.addPoints(player, 500);   // enough budget: rejections must not be about funds

        report.check(!RpgService.spend(player, -1, 1), "lineIndex -1 accepted");
        report.check(!RpgService.spend(player, StatLine.COUNT, 1), "lineIndex past the end accepted");
        report.check(!RpgService.spend(player, 9999, 1), "lineIndex 9999 accepted");
        report.check(!RpgService.spend(player, 0, 0), "amount 0 accepted");
        report.check(!RpgService.spend(player, 0, 101), "amount 101 accepted");
        report.check(!RpgService.spend(player, 0, -101), "amount -101 accepted");
        report.check(!RpgService.spend(player, 0, Integer.MAX_VALUE), "amount Integer.MAX_VALUE accepted");
        report.check(!RpgService.spend(player, 0, Integer.MIN_VALUE), "amount Integer.MIN_VALUE accepted");

        for (StatLine line : StatLine.VALUES) {
            report.check(data.points(line) == 0, "a rejected request still moved " + line.id());
        }
        report.assertAllPassed(helper, "malformedRequestsAreRejected");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "authority")
    public static void overspendingAndNegativeRefundsAreRejected(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        GameTestSupport.Report report = new GameTestSupport.Report();

        // A brand new player has no points: the very first spend must fail.
        report.check(data.unspentPoints() == 0L, "fresh player should have no points, has " + data.unspentPoints());
        report.check(!RpgService.spend(player, 0, 1), "spend succeeded with an empty balance");
        report.check(data.points(StatLine.byIndex(0)) == 0, "failed spend still moved points");

        // Refunding below zero is refused and leaves the line untouched.
        RpgService.setPoints(player, StatLine.byIndex(0), 5);
        int before = data.points(StatLine.byIndex(0));
        report.check(!RpgService.spend(player, 0, -10), "refund below zero accepted");
        report.check(data.points(StatLine.byIndex(0)) == before,
                "rejected refund changed the line: " + data.points(StatLine.byIndex(0)));

        // Spending more than the balance is refused.
        report.check(!RpgService.spend(player, 1, 50), "overspend accepted");
        report.check(data.points(StatLine.byIndex(1)) == 0, "rejected overspend moved points");
        report.assertAllPassed(helper, "overspendingAndNegativeRefundsAreRejected");
        helper.succeed();
    }

    /** Packet flooding must be rate limited, and the limiter must not corrupt state. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "authority")
    public static void packetFloodIsRateLimited(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        RpgService.addPoints(player, 500);

        int admitted = 0;
        for (int i = 0; i < 60; i++) {
            if (RpgService.spend(player, 0, 1)) admitted++;
        }
        helper.assertTrue(admitted <= 20, "rate limiter admitted " + admitted + " of 60 requests in one tick");
        helper.assertTrue(admitted > 0, "rate limiter rejected everything, including the first requests");
        helper.assertTrue(GameTestSupport.data(player).points(StatLine.byIndex(0)) == admitted,
                "point total does not match the number of admitted requests");
        helper.succeed();
    }

    /** A rejected request must leave the live attributes exactly as they were. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "authority")
    public static void rejectedRequestsDoNotTouchAttributes(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        RpgService.setPoints(player, StatLine.ARMOR_FLAT, 10);
        double before = GameTestSupport.attribute(player, com.rpgleveling.api.Stat.ARMOR).getValue();

        RpgService.spend(player, -5, 1);
        RpgService.spend(player, StatLine.COUNT + 3, 1);
        RpgService.spend(player, StatLine.ARMOR_FLAT.index(), 10_000);

        double after = GameTestSupport.attribute(player, com.rpgleveling.api.Stat.ARMOR).getValue();
        helper.assertTrue(Math.abs(before - after) < 1.0E-9,
                "a rejected request changed armor: " + before + " -> " + after);
        helper.succeed();
    }
}