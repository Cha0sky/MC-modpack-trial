package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class CodexFixtureGeometryGameTests {
    private CodexFixtureGeometryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyHasActualNineByFiveByNineBounds(GameTestHelper helper) {
        helper.assertTrue(helper.getBounds().getXsize() == 9.0D && helper.getBounds().getYsize() == 5.0D
                && helper.getBounds().getZsize() == 9.0D, "Actual empty structure bounds must be 9x5x9: " + helper.getBounds());
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 100)
    public static void bossArenaHasActualFortyEightByThirtyTwoByFortyEightBounds(GameTestHelper helper) {
        helper.assertTrue(helper.getBounds().getXsize() == 48.0D && helper.getBounds().getYsize() == 32.0D
                && helper.getBounds().getZsize() == 48.0D, "Actual boss arena bounds must be 48x32x48: " + helper.getBounds());
        helper.succeed();
    }
}
