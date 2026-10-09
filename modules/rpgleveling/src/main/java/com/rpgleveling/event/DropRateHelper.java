package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Rounding;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.util.RpgLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Drop scaling. Kept out of {@code CombatEvents} so that class contains nothing but
 * one-parameter event handlers (S-07).
 */
public final class DropRateHelper {

    private DropRateHelper() {}

    /**
     * Scales a drop collection and spawns the overflow as fresh entities.
     *
     * <p>Never relies on {@code getDrops()} being mutable or on its return value: the
     * existing entity's count is rewritten and anything beyond one stack becomes a new
     * {@link ItemEntity}. Two budgets ({@code max_items_per_event},
     * {@code max_entities_per_event}) stop a 10x multiplier from flooding the world.</p>
     */
    public static void applyDropMultiplier(ServerPlayer player, ServerLevel level, Iterable<ItemEntity> drops) {
        double multiplier = DerivedStats.dropMultiplier(player);
        if (!Double.isFinite(multiplier) || multiplier <= 1.0) return;

        Rounding rounding = RpgConfig.dropRounding();
        int maxItems = RpgConfig.dropMaxItemsPerEvent();
        int maxEntities = RpgConfig.dropMaxEntitiesPerEvent();
        int spawned = 0;
        int totalItems = 0;

        for (ItemEntity itemEntity : drops) {
            if (itemEntity == null || !itemEntity.isAlive()) continue;
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;
            int maxStack = Math.max(1, stack.getMaxStackSize());
            double exact = (double) stack.getCount() * multiplier;
            int wanted = rounding.apply(exact, level.random);
            if (wanted <= stack.getCount()) continue;

            int room = maxItems - totalItems;
            if (room <= 0) break;
            if (wanted > room) wanted = room;

            int inPlace = Math.min(wanted, maxStack);
            totalItems += wanted;
            stack.setCount(inPlace);
            itemEntity.setItem(stack);

            int remaining = wanted - inPlace;
            while (remaining > 0 && spawned < maxEntities) {
                int chunk = Math.min(remaining, maxStack);
                ItemStack extra = stack.copy();
                extra.setCount(chunk);
                ItemEntity extraEntity = new ItemEntity(level,
                        itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), extra);
                extraEntity.setDefaultPickUpDelay();
                level.addFreshEntity(extraEntity);
                remaining -= chunk;
                spawned++;
            }
            if (spawned >= maxEntities) break;
        }
        if (spawned > 0) {
            RpgLog.LOG.debug("rpgleveling: drop multiplier {} spawned {} extra item entities for {}",
                    multiplier, spawned, player.getGameProfile().getName());
        }
    }
}