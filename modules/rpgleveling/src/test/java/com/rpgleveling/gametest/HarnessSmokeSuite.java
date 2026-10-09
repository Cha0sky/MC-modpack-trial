package com.rpgleveling.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Harness smoke check: proves the template, the mock player and the namespace wiring work. */
@GameTestHolder("rpgleveling")
@PrefixGameTestTemplate(false)
public final class HarnessSmokeSuite {

    private HarnessSmokeSuite() {}

    /**
     * T9 wiring check: the combat / tick / xp effect layer is reached through the event bus,
     * so a behavioural test alone does not prove the handler class is still registered.
     * Dropping {@code @EventBusSubscriber} would silently disable an entire dimension, which
     * is exactly the failure mode VERIFICATION S-05 warns about.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "smoke")
    public static void everyEffectHandlerIsStillRegistered(GameTestHelper helper) {
        helper.assertTrue(isSubscribed(com.rpgleveling.event.CombatEvents.class),
                "CombatEvents lost its @EventBusSubscriber - damage reduction, lifesteal, crit and drop rate would all go silent");
        helper.assertTrue(isSubscribed(com.rpgleveling.event.PlayerTickHandler.class),
                "PlayerTickHandler lost its @EventBusSubscriber - health regeneration would stop");
        helper.assertTrue(isSubscribed(com.rpgleveling.event.XpEvents.class),
                "XpEvents lost its @EventBusSubscriber - no experience would ever be granted");
        helper.assertTrue(isSubscribed(com.rpgleveling.event.PlayerLifecycleEvents.class),
                "PlayerLifecycleEvents lost its @EventBusSubscriber - modifiers would not be rebuilt");
        helper.assertTrue(isSubscribed(com.rpgleveling.event.MobSpawnEvents.class),
                "MobSpawnEvents lost its @EventBusSubscriber - spawner mobs would grant experience");
        helper.assertTrue(isSubscribed(com.rpgleveling.command.RpgCommand.class),
                "RpgCommand lost its @EventBusSubscriber - /rpg would not exist");

        // ConfigEvents and RpgPayloads are registered explicitly from the @Mod constructor,
        // so their guarantee is a public entry point instead of an annotation.
        try {
            helper.assertTrue(java.lang.reflect.Modifier.isStatic(
                            com.rpgleveling.event.ConfigEvents.class
                                    .getMethod("init", net.neoforged.bus.api.IEventBus.class).getModifiers()),
                    "ConfigEvents.init(IEventBus) is not static - the @Mod constructor cannot register it");
            helper.assertTrue(java.lang.reflect.Modifier.isStatic(
                            com.rpgleveling.registry.RpgPayloads.class
                                    .getMethod("onRegisterPayloads",
                                            net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class)
                                    .getModifiers()),
                    "RpgPayloads.onRegisterPayloads is not static - the @Mod constructor cannot register it");
        } catch (NoSuchMethodException missing) {
            helper.fail("a mod-bus registration entry point disappeared: " + missing.getMessage());
        }

        // The hard-wired bypass set must still contain the two defaults the design pins.
        var bypass = com.rpgleveling.event.CombatEvents.bypassSummary();
        helper.assertTrue(bypass.contains("minecraft:generic_kill"),
                "/kill is no longer hard-wired to bypass damage reduction: " + bypass);
        helper.assertTrue(bypass.contains("minecraft:out_of_world"),
                "the void is no longer hard-wired to bypass damage reduction: " + bypass);
        helper.succeed();
    }

    private static boolean isSubscribed(Class<?> type) {
        net.neoforged.fml.common.EventBusSubscriber annotation =
                type.getAnnotation(net.neoforged.fml.common.EventBusSubscriber.class);
        return annotation != null && "rpgleveling".equals(annotation.modid());
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "smoke")
    public static void mockPlayerHasEveryAttribute(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        for (com.rpgleveling.api.Stat stat : com.rpgleveling.api.Stat.VALUES) {
            helper.assertTrue(player.getAttribute(stat.carrier.holder()) != null,
                    "missing attribute " + stat.id);
        }
        helper.succeed();
    }
}