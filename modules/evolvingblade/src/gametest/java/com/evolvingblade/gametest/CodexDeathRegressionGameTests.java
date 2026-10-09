package com.evolvingblade.gametest;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBAttachments;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Independent Codex regressions: event provenance and attachment default reads. */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class CodexDeathRegressionGameTests {
    private CodexDeathRegressionGameTests() {}

    private static final OneShotReviver REVIVER = new OneShotReviver();
    static { NeoForge.EVENT_BUS.register(REVIVER); }
    public static final class OneShotReviver {
        java.util.UUID target;
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onDeath(LivingDeathEvent event) {
            if (target != null && target.equals(event.getEntity().getUUID())) {
                target = null;
                event.getEntity().setHealth(event.getEntity().getMaxHealth());
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void syntheticAliveEventCannotClaimLaterEnvironmentalDeath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        victim.setNoAi(true);
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(victim, helper.getLevel().damageSources().playerAttack(player)));
        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(victim.isAlive(), "Synthetic event must not kill the live victim");
            helper.assertTrue(victim.getKillCredit() == null, "No actual player damage or vanilla kill credit occurred");
            helper.assertTrue(GrowthService.data(player).hostileKills() == 0L, "Synthetic event must not count");
            victim.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
            helper.runAfterDelay(3L, () -> {
                helper.assertTrue(GrowthService.data(player).hostileKills() == 0L,
                        "A stale synthetic candidate must not claim the later unowned environmental death");
                helper.assertTrue(GrowthService.data(player).growthPoints() == 0L,
                        "No growth for either the synthetic event or the later unowned death");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void readingFalseCreditMarkerCannotSuppressRealKill(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
        victim.setNoAi(true);
        helper.assertFalse(victim.getData(EBAttachments.KILL_CREDITED), "Fresh marker is false");
        victim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(GrowthService.data(player).hostileKills() == 1L,
                    "Merely reading a default false attachment must not mark this life credited");
            helper.assertTrue(GrowthService.data(player).growthPoints() == 1L, "Real hostile kill grants one point");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void revivedNonDragonCannotRetainAnOldClaimForUnownedDeath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // A resurrected zombie can burn after sunlight wears out its damaged helmet.
        // Husk preserves the hostile/death semantics without this unrelated random death.
        net.minecraft.world.entity.monster.Husk victim = helper.spawn(EntityType.HUSK, 3, 2, 3);
        victim.setNoAi(true);
        REVIVER.target = victim.getUUID();
        victim.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        helper.runAfterDelay(401L, () -> {
            helper.assertTrue(victim.isAlive(), "The later death hook restored this non-dragon to full life");
            helper.assertTrue(GrowthService.data(player).hostileKills() == 0L, "Revived death must not count");
            helper.assertTrue(victim.getKillCredit() == null, "Old vanilla attack attribution must have expired");
            victim.hurt(helper.getLevel().damageSources().genericKill(), 1000.0F);
            helper.runAfterDelay(3L, () -> {
                helper.assertTrue(GrowthService.data(player).hostileKills() == 0L,
                        "A revived ordinary mob must discard the old candidate, rather than later credit an unowned death");
                helper.succeed();
            });
        });
    }
}
