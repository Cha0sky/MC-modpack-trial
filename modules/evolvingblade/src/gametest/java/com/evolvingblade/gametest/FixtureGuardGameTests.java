package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 夹具自检：结构<b>实际尺寸</b>、中心点是否落在<b>已加载且实体可 tick</b> 的区块内。
 *
 * <p>PHASE 1 曾经因为"结构跨区块、相邻区块不 ticking"导致测试挂起，
 * 所以这类前提必须由测试自己断言，而不是假定框架已经准备好。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class FixtureGuardGameTests {

    private FixtureGuardGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void emptyFixtureCentreIsLoadedAndEntityTicking(GameTestHelper helper) {
        helper.assertTrue(helper.getBounds().getXsize() == 9.0D && helper.getBounds().getYsize() == 5.0D
                        && helper.getBounds().getZsize() == 9.0D,
                "actual empty bounds must be 9x5x9: " + helper.getBounds());
        BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.assertTrue(helper.getBounds().contains(centre.getX() + 0.5D, centre.getY() + 0.5D, centre.getZ() + 0.5D),
                "the fixture centre must be inside the actual bounds (centre=" + centre + " bounds=" + helper.getBounds() + ")");
        helper.assertTrue(helper.getLevel().isLoaded(centre),
                "the fixture centre chunk must be loaded (centre=" + centre + ")");
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(centre),
                "the fixture centre chunk must be entity-ticking (centre=" + centre + ")");
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 100)
    public static void bossArenaFixtureCentreIsLoadedAndEntityTicking(GameTestHelper helper) {
        helper.assertTrue(helper.getBounds().getXsize() == 48.0D && helper.getBounds().getYsize() == 32.0D
                        && helper.getBounds().getZsize() == 48.0D,
                "actual boss_arena bounds must be 48x32x48: " + helper.getBounds());
        BlockPos centre = helper.absolutePos(new BlockPos(24, 16, 24));
        helper.assertTrue(helper.getBounds().contains(centre.getX() + 0.5D, centre.getY() + 0.5D, centre.getZ() + 0.5D),
                "the arena centre must be inside the actual bounds (centre=" + centre + ")");
        helper.assertTrue(helper.getLevel().isLoaded(centre) && helper.getLevel().isPositionEntityTicking(centre),
                "the arena centre chunk must be loaded and entity-ticking (centre=" + centre + ")");
        helper.succeed();
    }
}
