package com.rpgleveling.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Command output helper. Lives outside {@code RpgCommand} on purpose: S-07 asserts that
 * every {@code static void} method in a class carrying {@code @SubscribeEvent} takes
 * exactly one event parameter, and keeping helpers separate makes that check meaningful
 * rather than noisy.
 */
public final class CommandFeedback {

    private CommandFeedback() {}

    /** Sends one {@code key=value} line back to the command source. */
    public static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }
}
