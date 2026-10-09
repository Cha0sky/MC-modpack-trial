package com.rpgleveling.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.UnknownNullability;

import java.util.List;

/**
 * Shared behaviour of the three mod items.
 *
 * <p>Server-authoritative by construction: the client branch returns before touching any
 * state, and the effect only ever runs through {@code RpgService}, the single writer of
 * player data. A failed use leaves the stack untouched.</p>
 */
public abstract class RpgConsumableItem extends Item {

    protected RpgConsumableItem(Properties properties) {
        super(properties);
    }

    /** The id used for the language keys, e.g. {@code respec_token}. */
    protected abstract String itemId();

    /**
     * Applies the effect. Runs on the server only.
     *
     * @return {@code true} when the item should be consumed; {@code false} when the use
     *         was refused and the stack must be kept
     */
    protected abstract boolean apply(ServerPlayer player);

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // The client predicts the swing only; it never mutates player data.
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!apply(serverPlayer)) {
            player.displayClientMessage(Component.translatable("rpgleveling.item." + itemId() + ".failed"), true);
            return InteractionResultHolder.fail(stack);
        }

        stack.shrink(1);
        player.displayClientMessage(Component.translatable("rpgleveling.item." + itemId() + ".used"), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rpgleveling." + itemId() + ".tooltip"));
    }

    @Override
    public @UnknownNullability Component getName(ItemStack stack) {
        return Component.translatable("item.rpgleveling." + itemId());
    }
}
