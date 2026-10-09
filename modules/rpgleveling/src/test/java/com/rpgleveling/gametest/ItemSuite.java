package com.rpgleveling.gametest;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.registry.RpgCreativeTabs;
import com.rpgleveling.registry.RpgItems;
import com.rpgleveling.service.RpgService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * T11: the three mod items.
 *
 * <p>Registration, the server-side effect, the client safety property and the
 * "a refused use must not consume the stack" rule.</p>
 */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class ItemSuite {

    private ItemSuite() {}

    private static void expectRegistered(GameTestHelper helper, ItemStack stack, String id) {
        helper.assertTrue(!stack.isEmpty() && stack.getItem() != Items.AIR, id + " is not registered");
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        helper.assertTrue(key.toString().equals("rpgleveling:" + id),
                id + " registered under " + key + " instead of rpgleveling:" + id);
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void itemsAndCreativeTabAreRegistered(GameTestHelper helper) {
        expectRegistered(helper, new ItemStack(RpgItems.RESPEC_TOKEN.get()), "respec_token");
        expectRegistered(helper, new ItemStack(RpgItems.SKILL_SCROLL.get()), "skill_scroll");
        expectRegistered(helper, new ItemStack(RpgItems.EXPERIENCE_CRYSTAL.get()), "experience_crystal");

        helper.assertTrue(RpgCreativeTabs.MAIN.get() != null, "creative tab is not registered");
        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(RpgCreativeTabs.MAIN.get());
        helper.assertTrue(tabKey != null && tabKey.toString().equals("rpgleveling:main"),
                "creative tab key is " + tabKey);
        helper.assertTrue(RpgCreativeTabs.MAIN.get().getIconItem().getItem() == RpgItems.SKILL_SCROLL.get(),
                "creative tab icon is not the skill scroll");
        helper.assertTrue(new ItemStack(RpgItems.RESPEC_TOKEN.get()).getMaxStackSize() == 16
                        && new ItemStack(RpgItems.SKILL_SCROLL.get()).getMaxStackSize() == 16
                        && new ItemStack(RpgItems.EXPERIENCE_CRYSTAL.get()).getMaxStackSize() == 16,
                "the items must stack to 16");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void skillScrollGrantsPointsAndIsConsumed(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        long before = data.unspentPoints();
        long xpBefore = data.totalXp();
        int levelBefore = data.level();

        ItemStack stack = new ItemStack(RpgItems.SKILL_SCROLL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.getResult().consumesAction(), "a successful use must consume the action");
        helper.assertTrue(data.unspentPoints() == before + RpgConfig.skillScrollPoints(),
                "points went from " + before + " to " + data.unspentPoints()
                        + ", expected +" + RpgConfig.skillScrollPoints());
        helper.assertTrue(stack.getCount() == 0, "the scroll was not consumed: " + stack.getCount() + " left");
        helper.assertTrue(data.totalXp() == xpBefore && data.level() == levelBefore,
                "the scroll must not change experience or level");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void experienceCrystalBooksExperienceOncePerUse(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        RpgService.setPoints(player, StatLine.XP_GAIN_FLAT, 0);

        long before = data.totalXp();
        ItemStack stack = new ItemStack(RpgItems.EXPERIENCE_CRYSTAL.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        long gained = data.totalXp() - before;
        helper.assertTrue(gained == RpgConfig.experienceCrystalXp(),
                "one crystal booked " + gained + " xp, expected " + RpgConfig.experienceCrystalXp());
        helper.assertTrue(stack.getCount() == 2, "only one crystal should be consumed, left " + stack.getCount());
        helper.succeed();
    }

    /** The multiplier must be applied by XpService, exactly once. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void crystalRoutesThroughTheSingleXpEntryPoint(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        // xp_gain.percent @40 -> LINEAR 5.0*40 = 200, B_ref 50 * 200/100 = 100.0 -> exactly 2x
        RpgService.setPoints(player, StatLine.XP_GAIN_PCT, 40);

        long before = data.totalXp();
        ItemStack stack = new ItemStack(RpgItems.EXPERIENCE_CRYSTAL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        long gained = data.totalXp() - before;
        long expected = Math.round(RpgConfig.experienceCrystalXp() * 2.0);
        helper.assertTrue(gained == expected,
                "crystal booked " + gained + " xp, expected " + expected + " (base x 2, applied once)");
        helper.succeed();
    }

    /** A refused use must keep the stack: the reported defect class for consumables. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void respecTokenIsNotConsumedWhenNothingIsInvested(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        helper.assertTrue(data.spentPoints() == 0L, "setup: the player should have nothing invested");

        ItemStack stack = new ItemStack(RpgItems.RESPEC_TOKEN.get(), 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(!result.getResult().consumesAction(), "a refused use must not report success");
        helper.assertTrue(stack.getCount() == 5, "a refused use consumed the stack: " + stack.getCount() + " left");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void respecTokenRefundsInvestedPoints(GameTestHelper helper) {
        ServerPlayer player = GameTestSupport.join(helper);
        PlayerRpgData data = GameTestSupport.data(player);
        RpgService.setPoints(player, StatLine.ARMOR_FLAT, 12);
        RpgService.setPoints(player, StatLine.XP_GAIN_PCT, 5);
        helper.assertTrue(data.spentPoints() == 17L, "setup: spent " + data.spentPoints());
        int levelBefore = data.level();

        ItemStack stack = new ItemStack(RpgItems.RESPEC_TOKEN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResultHolder<ItemStack> result = stack.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.getResult().consumesAction(), "a successful respec must consume the action");
        helper.assertTrue(data.spentPoints() == 0L, "points still invested: " + data.spentPoints());
        helper.assertTrue(data.points(StatLine.ARMOR_FLAT) == 0, "armor points survived the token");
        helper.assertTrue(stack.getCount() == 0, "the token was not consumed");
        helper.assertTrue(data.level() == levelBefore, "the token must not change the level");
        helper.succeed();
    }

    /**
     * Client safety: on the client the acting player is a plain {@code Player}, never a
     * {@code ServerPlayer}. This asserts the second line of defence - even without the
     * {@code isClientSide} early return, nothing can be written.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "items")
    public static void aNonServerPlayerCanNeverMutateAnything(GameTestHelper helper) {
        Player clientLike = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        PlayerRpgData data = clientLike.getData(RpgAttachments.PLAYER_DATA);
        long xpBefore = data.totalXp();
        long spentBefore = data.spentPoints();

        for (var item : new net.minecraft.world.item.Item[] {
                RpgItems.RESPEC_TOKEN.get(), RpgItems.SKILL_SCROLL.get(), RpgItems.EXPERIENCE_CRYSTAL.get() }) {
            ItemStack stack = new ItemStack(item, 4);
            clientLike.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResultHolder<ItemStack> result = item.use(clientLike.level(), clientLike, InteractionHand.MAIN_HAND);
            helper.assertTrue(!result.getResult().consumesAction(),
                    item + " reported success for a non-server player");
            helper.assertTrue(stack.getCount() == 4, item + " was consumed by a non-server player");
        }

        helper.assertTrue(data.totalXp() == xpBefore, "a non-server player gained experience");
        helper.assertTrue(data.spentPoints() == spentBefore, "a non-server player changed its points");
        helper.assertTrue(data.unspentPoints() == 0L, "a non-server player gained allocatable points");
        helper.succeed();
    }
}
