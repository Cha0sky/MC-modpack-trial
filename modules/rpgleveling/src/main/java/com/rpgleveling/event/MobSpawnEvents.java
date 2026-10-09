package com.rpgleveling.event;

import com.rpgleveling.registry.RpgAttachments;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Flags spawner-spawned mobs so a farm tower cannot be used to farm experience.
 *
 * <p>The marker is a memory-only attachment: a mob that unloads and reloads loses it.
 * That trade-off is accepted and documented (DESIGN 8.3).</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class MobSpawnEvents {

    private MobSpawnEvents() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!MobSpawnType.isSpawner(event.getSpawnType())) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        mob.setData(RpgAttachments.SPAWNER_MOB, Boolean.TRUE);
    }
}