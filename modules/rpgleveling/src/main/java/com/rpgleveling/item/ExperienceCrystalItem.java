package com.rpgleveling.item;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.service.RpgService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Experience crystal: books experience.
 *
 * <p>Routed through {@code XpService} (via {@code RpgService.grantXp}) rather than writing
 * {@code totalXp} directly, so the {@code xp_gain} multiplier is applied exactly once and
 * the "single experience entry point" invariant is preserved.</p>
 */
public class ExperienceCrystalItem extends RpgConsumableItem {

    public ExperienceCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String itemId() {
        return "experience_crystal";
    }

    @Override
    protected boolean apply(ServerPlayer player) {
        int xp = RpgConfig.experienceCrystalXp();
        if (xp <= 0) return false;
        return RpgService.grantXp(player, xp) > 0;
    }
}
