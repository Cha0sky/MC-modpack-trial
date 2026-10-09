package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.service.PlayerCache;
import com.rpgleveling.stat.DerivedStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Tick driven health regeneration (DESIGN 7).
 *
 * <p>{@code PlayerTickEvent.Post} fires on both sides, so the very first check is
 * {@code isClientSide}. The fractional remainder is carried in
 * {@link PlayerCache} - calling {@code heal(0.25f)} every tick would spam
 * {@code LivingHealEvent} and drift.</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class PlayerTickHandler {

    private PlayerTickHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        double hps = DerivedStats.healthRegenPerSecond(sp);
        // T17: no clamp by default - the attribute's own range is the only bound.
        // An admin can still configure a backstop; see the line below.
        if (RpgConfig.regenHardCapEnabled()) {
            double hardCap = RpgConfig.regenHardCapHps();
            if (hps > hardCap) hps = hardCap;
        }
        if (!(hps > 0.0)) return;
        if (RpgConfig.regenOnlyWhenHurt() && !sp.isHurt()) return;
        int neededFood = RpgConfig.regenRequireFoodLevel();
        if (neededFood > 0 && sp.getFoodData().getFoodLevel() < neededFood) return;

        PlayerCache.Entry cache = PlayerCache.of(sp.getUUID());
        cache.regenAccumulator += hps / 20.0;
        if (!Double.isFinite(cache.regenAccumulator)) {
            cache.regenAccumulator = 0.0;
            return;
        }
        float whole = (float) Math.floor(cache.regenAccumulator);
        if (whole >= 1.0F) {
            cache.regenAccumulator -= whole;
            sp.heal(whole);   // clamped to max health inside LivingEntity#heal
        }
    }
}
