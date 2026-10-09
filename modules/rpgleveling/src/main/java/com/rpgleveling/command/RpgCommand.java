package com.rpgleveling.command;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.DiscoveryData;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.registry.RpgAttachments;
import com.rpgleveling.service.RpgService;
import com.rpgleveling.service.StatOverrides;
import com.rpgleveling.service.XpSource;
import com.rpgleveling.stat.AttributeApplier;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rpg} and its subcommands.
 *
 * <p>{@code /rpg stats} and {@code /rpg debug stats} emit the <b>exact</b> key=value
 * contract of DESIGN 11.4. Three independent channels are printed on purpose:
 * {@code line.*.points} proves the point accounting, {@code line.*.value} proves the
 * curve maths and {@code attr.*} proves the modifier really landed on the attribute.
 * Reading only one of them would let a broken implementation pass.</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class RpgCommand {

    private RpgCommand() {}

    private static final SuggestionProvider<CommandSourceStack> LINE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(StatLine.allIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> STAT_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(Stat.VALUES).map(s -> s.id).toList(), builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("rpg")
                .executes(ctx -> info(ctx, self(ctx)))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> info(ctx, target(ctx)))))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> stats(ctx, target(ctx)))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> list(ctx, target(ctx)))))
                .then(Commands.literal("spend")
                        .then(Commands.argument("line", StringArgumentType.word()).suggests(LINE_SUGGEST)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(-100, 100))
                                        .executes(ctx -> spend(ctx, self(ctx))))))
                .then(Commands.literal("respec")
                        .executes(ctx -> respec(ctx, self(ctx))))
                .then(Commands.literal("discovery")
                        .executes(ctx -> discovery(ctx, self(ctx)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> discovery(ctx, target(ctx)))))
                .then(Commands.literal("debug").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("stats")
                                .executes(ctx -> stats(ctx, self(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> stats(ctx, target(ctx)))))
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("n", IntegerArgumentType.integer(-1_000_000, 1_000_000))
                                                .executes(RpgCommand::debugAddPoints))))
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 100_000_000))
                                                .executes(RpgCommand::debugSetLevel))))
                        .then(Commands.literal("override")
                                .then(Commands.literal("list")
                                        .executes(ctx -> debugOverrideList(ctx, self(ctx)))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> debugOverrideList(ctx, target(ctx)))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("dim", StringArgumentType.word()).suggests(STAT_SUGGEST)
                                                .then(Commands.literal("clear")
                                                        .executes(RpgCommand::debugOverrideClear))
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1.0E9, 1.0E9))
                                                        .executes(RpgCommand::debugOverrideSet)))))
                        .then(Commands.literal("forcecrit")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(RpgCommand::debugForceCrit)))))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 100_000_000))
                                                .executes(RpgCommand::adminSetLevel))))
                        .then(Commands.literal("addxp")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(-2_000_000_000, 2_000_000_000))
                                                .executes(RpgCommand::adminAddXp))))
                        .then(Commands.literal("setpoints")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("line", StringArgumentType.word()).suggests(LINE_SUGGEST)
                                                .then(Commands.argument("points", IntegerArgumentType.integer(0, 100_000_000))
                                                        .executes(RpgCommand::adminSetPoints)))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(RpgCommand::adminReset)))));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getPlayer();
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) {
        try {
            return EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // subcommands
    // ------------------------------------------------------------------

    private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        PlayerRpgData data = RpgService.data(player);
        CommandFeedback.reply(ctx, "player=" + player.getGameProfile().getName());
        CommandFeedback.reply(ctx, "level=" + data.level());
        CommandFeedback.reply(ctx, "total_xp=" + data.totalXp());
        CommandFeedback.reply(ctx, "xp_into_level=" + data.xpIntoLevel());
        CommandFeedback.reply(ctx, "xp_for_next_level=" + data.xpForNextLevel());
        CommandFeedback.reply(ctx, "unspent_points=" + data.unspentPoints());
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        PlayerRpgData data = RpgService.data(player);
        LineCfg[] cfgs = RpgConfig.snapshot();
        for (StatLine line : StatLine.VALUES) {
            int points = data.points(line);
            double value = RpgMath.lineValue(points, cfgs[line.index()]);
            CommandFeedback.reply(ctx, String.format(Locale.ROOT, "%-28s points=%-6d value=%.4f", line.id(), points, value));
        }
        return 1;
    }

    private static int spend(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        String id = StringArgumentType.getString(ctx, "line");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        StatLine line = StatLine.byId(id);
        if (line == null) {
            CommandFeedback.reply(ctx, "unknown line: " + id);
            return 0;
        }
        boolean ok = RpgService.spend(player, line.index(), amount);
        CommandFeedback.reply(ctx, (ok ? "ok " : "rejected ") + id + " " + amount);
        return ok ? 1 : 0;
    }

    private static int respec(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        boolean ok = RpgService.respec(player);
        CommandFeedback.reply(ctx, ok ? "respec ok" : "respec rejected");
        return ok ? 1 : 0;
    }

    /**
     * The machine readable contract of DESIGN 11.4. Every line is {@code key=value}.
     * Both {@code /rpg stats} and {@code /rpg debug stats} emit exactly this.
     */
    static List<String> statsLines(ServerPlayer player) {
        PlayerRpgData data = RpgService.data(player);
        LineCfg[] cfgs = RpgConfig.snapshot();
        List<String> out = new ArrayList<>(80);

        out.add("level=" + data.level());
        out.add("total_xp=" + data.totalXp());
        out.add("xp_into_level=" + data.xpIntoLevel());
        out.add("xp_for_next_level=" + data.xpForNextLevel());
        out.add("earned_points=" + data.earnedPoints());
        out.add("spent_points=" + data.spentPoints());
        out.add("unspent_points=" + data.unspentPoints());
        out.add("respec_count=" + data.respecCount());

        for (StatLine line : StatLine.VALUES) {
            int points = data.points(line);
            double value = RpgMath.lineValue(points, cfgs[line.index()]);
            out.add("line." + line.id() + ".points=" + points);
            out.add(String.format(Locale.ROOT, "line.%s.value=%.4f", line.id(), value));
        }

        // T6 / DESIGN 1.16.5: the only evidence of a silent sanitizeValue truncation.
        for (Stat stat : Stat.VALUES) {
            AttributeInstance inst = player.getAttribute(stat.carrier.holder());
            double computed = DerivedStats.rawAttributeValue(player, stat);
            double effective = (inst != null) ? inst.getValue() : DerivedStats.finalValue(player, stat);
            if (!Double.isFinite(computed)) computed = 0.0;
            if (!Double.isFinite(effective)) effective = 0.0;
            boolean clamped = Math.abs(effective - computed) > 1.0E-6;
            out.add(String.format(Locale.ROOT, "stat.%s.computed=%.4f", stat.id, computed));
            if (RpgConfig.statsReportEffective()) {
                out.add(String.format(Locale.ROOT, "stat.%s.effective=%.4f", stat.id, effective));
            }
            out.add("stat." + stat.id + ".clamped=" + clamped);
        }

        for (Stat stat : Stat.VALUES) {
            AttributeInstance inst = player.getAttribute(stat.carrier.holder());
            double value = (inst != null) ? inst.getValue() : DerivedStats.finalValue(player, stat);
            if (!Double.isFinite(value)) value = 0.0;
            out.add(String.format(Locale.ROOT, "attr.%s=%.4f", stat.carrier.attributeName(), value));
        }

        DiscoveryData discovery = player.getData(RpgAttachments.DISCOVERY);
        out.add("discovery.mine.unlocked=" + discovery.count(XpSource.MINE));
        out.add("discovery.mine.total=" + BuiltInRegistries.BLOCK.size());
        out.add("discovery.craft.unlocked=" + discovery.count(XpSource.CRAFT));
        out.add("discovery.craft.total=" + BuiltInRegistries.ITEM.size());
        out.add("discovery.smelt.unlocked=" + discovery.count(XpSource.SMELT));
        out.add("discovery.smelt.total=" + BuiltInRegistries.ITEM.size());
        out.add("discovery.fish.unlocked=" + discovery.count(XpSource.FISH));
        out.add("discovery.fish.total=" + BuiltInRegistries.ITEM.size());
        out.add("discovery.trade.unlocked=" + discovery.count(XpSource.TRADE));
        out.add("discovery.trade.total=" + Math.max(1, BuiltInRegistries.VILLAGER_PROFESSION.size()) * 1000);

        out.add("debug.override.count=" + StatOverrides.count(player.getUUID()));
        out.add("debug.forcecrit=" + StatOverrides.forceCrit(player.getUUID()));
        return out;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        for (String line : statsLines(player)) CommandFeedback.reply(ctx, line);
        return 1;
    }

    private static int discovery(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        DiscoveryData data = player.getData(RpgAttachments.DISCOVERY);
        int[] totals = {
                BuiltInRegistries.BLOCK.size(),
                BuiltInRegistries.ITEM.size(),
                BuiltInRegistries.ITEM.size(),
                BuiltInRegistries.ITEM.size(),
                Math.max(1, BuiltInRegistries.VILLAGER_PROFESSION.size()) * 1000
        };
        int i = 0;
        int unlocked = 0;
        int total = 0;
        for (XpSource src : XpSource.DISCOVERY) {
            int u = data.count(src);
            unlocked += u;
            total += totals[i];
            CommandFeedback.reply(ctx, String.format(Locale.ROOT, "discovery.%s=%d/%d", src.key(), u, totals[i]));
            i++;
        }
        CommandFeedback.reply(ctx, String.format(Locale.ROOT, "discovery.total=%d/%d", unlocked, total));
        return 1;
    }

    private static int debugAddPoints(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        int n = IntegerArgumentType.getInteger(ctx, "n");
        long total = RpgService.addPoints(player, n);
        CommandFeedback.reply(ctx, "debug.addpoints ok bonus=" + total + " unspent=" + RpgService.data(player).unspentPoints());
        return 1;
    }

    private static int debugSetLevel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        int n = IntegerArgumentType.getInteger(ctx, "n");
        RpgService.setLevel(player, n);
        CommandFeedback.reply(ctx, "debug.setlevel ok level=" + RpgService.data(player).level()
                + " total_xp=" + RpgService.data(player).totalXp());
        return 1;
    }

    private static int debugOverrideSet(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        Stat stat = statById(StringArgumentType.getString(ctx, "dim"));
        if (stat == null) {
            CommandFeedback.reply(ctx, "unknown dimension");
            return 0;
        }
        double value = DoubleArgumentType.getDouble(ctx, "value");
        if (!Double.isFinite(value)) {
            CommandFeedback.reply(ctx, "value must be finite");
            return 0;
        }
        StatOverrides.set(player.getUUID(), stat, value);
        AttributeApplier.apply(player, RpgService.data(player));
        CommandFeedback.reply(ctx, "debug.override " + stat.id + "=" + value);
        return 1;
    }

    private static int debugOverrideClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        Stat stat = statById(StringArgumentType.getString(ctx, "dim"));
        if (stat == null) {
            CommandFeedback.reply(ctx, "unknown dimension");
            return 0;
        }
        StatOverrides.clear(player.getUUID(), stat);
        AttributeApplier.apply(player, RpgService.data(player));
        CommandFeedback.reply(ctx, "debug.override " + stat.id + " cleared");
        return 1;
    }

    private static int debugOverrideList(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (player == null) return 0;
        var overridden = StatOverrides.overridden(player.getUUID());
        if (overridden.isEmpty()) {
            CommandFeedback.reply(ctx, "debug.override.count=0");
        } else {
            for (Stat stat : overridden) {
                CommandFeedback.reply(ctx, "debug.override." + stat.id + "=" + StatOverrides.get(player.getUUID(), stat));
            }
        }
        CommandFeedback.reply(ctx, "debug.forcecrit=" + StatOverrides.forceCrit(player.getUUID()));
        return 1;
    }

    private static int debugForceCrit(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        boolean value = BoolArgumentType.getBool(ctx, "value");
        StatOverrides.setForceCrit(player.getUUID(), value);
        CommandFeedback.reply(ctx, "debug.forcecrit=" + value);
        return 1;
    }

    private static int adminSetLevel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        int level = IntegerArgumentType.getInteger(ctx, "level");
        RpgService.setLevel(player, level);
        CommandFeedback.reply(ctx, "admin.setlevel ok level=" + RpgService.data(player).level());
        return 1;
    }

    private static int adminAddXp(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        RpgService.addXp(player, amount);
        CommandFeedback.reply(ctx, "admin.addxp ok total_xp=" + RpgService.data(player).totalXp());
        return 1;
    }

    private static int adminSetPoints(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        StatLine line = StatLine.byId(StringArgumentType.getString(ctx, "line"));
        if (line == null) {
            CommandFeedback.reply(ctx, "unknown line");
            return 0;
        }
        int points = IntegerArgumentType.getInteger(ctx, "points");
        RpgService.setPoints(player, line, points);
        CommandFeedback.reply(ctx, "admin.setpoints ok " + line.id() + "=" + points);
        return 1;
    }

    private static int adminReset(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = target(ctx);
        if (player == null) return 0;
        RpgService.resetPlayer(player);
        CommandFeedback.reply(ctx, "admin.reset ok");
        return 1;
    }

    private static Stat statById(String id) {
        for (Stat stat : Stat.VALUES) {
            if (stat.id.equals(id)) return stat;
        }
        return null;
    }
}