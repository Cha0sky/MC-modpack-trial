package com.rpgleveling.item;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.service.RpgService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Skill scroll: grants allocatable points without touching experience or level.
 *
 * <p>The points are granted through {@code RpgService}, so the "single writer of player
 * data" invariant still holds; they are persisted, so a relog cannot eat them.</p>
 */
public class SkillScrollItem extends RpgConsumableItem {

    public SkillScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String itemId() {
        return "skill_scroll";
    }

    @Override
    protected boolean apply(ServerPlayer player) {
        int points = RpgConfig.skillScrollPoints();
        if (points <= 0) return false;
        RpgService.grantPoints(player, points);
        return true;
    }
}
