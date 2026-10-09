package com.rpgleveling.item;

import com.rpgleveling.service.RpgService;

/**
 * Respec token: refunds every invested point.
 *
 * <p>Refused (and not consumed) when nothing is invested, so a full stack cannot be
 * wasted by a misclick.</p>
 */
public class RespecTokenItem extends RpgConsumableItem {

    public RespecTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String itemId() {
        return "respec_token";
    }

    @Override
    protected boolean apply(net.minecraft.server.level.ServerPlayer player) {
        if (RpgService.data(player).spentPoints() <= 0L) {
            return false;
        }
        return RpgService.respec(player);
    }
}
