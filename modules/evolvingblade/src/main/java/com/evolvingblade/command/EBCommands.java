package com.evolvingblade.command;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.boss.BossClassification;
import com.evolvingblade.boss.BossClassifier;
import com.evolvingblade.config.EBConfig;
import com.evolvingblade.growth.GrowthData;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.growth.GrowthSettings;
import com.evolvingblade.growth.GrowthThresholds;
import com.evolvingblade.growth.StageCalculator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /evolvingblade} 管理员命令。全部在服务器主线程执行，且按 SPEC 分级授权：
 * <ul>
 *   <li>{@code info}：查询自己无需权限；查询他人需要权限 2；</li>
 *   <li>{@code mode} / {@code set} / {@code bosscheck}：一律需要权限 2；</li>
 *   <li>计数参数使用 {@code longArg(0)}，负数在 Brigadier 层就被拒绝；</li>
 *   <li>{@code bosscheck} 只接受单个生物实体选择器，并回显真实实体 ID、判定结论与获胜理由。</li>
 * </ul>
 *
 * <p>所有回显都走语言键（{@code commands.evolvingblade.*}），中英双语齐全。
 */
@EventBusSubscriber(modid = EvolvingBlade.MODID)
public final class EBCommands {

    /** 管理命令所需权限等级。 */
    public static final int ADMIN_PERMISSION = 2;

    private static final String KEY_HEADER = "commands.evolvingblade.info.header";
    private static final String KEY_STATUS = "commands.evolvingblade.info.status";
    private static final String KEY_COUNTERS = "commands.evolvingblade.info.counters";
    private static final String KEY_NEXT = "commands.evolvingblade.info.next";
    private static final String KEY_NEXT_POINTS = "commands.evolvingblade.info.next.points";
    private static final String KEY_NEXT_BOSSES = "commands.evolvingblade.info.next.bosses";
    private static final String KEY_MAX_STAGE = "commands.evolvingblade.info.max_stage";
    private static final String KEY_MODE_NONE = "commands.evolvingblade.mode.none";
    private static final String KEY_MODE_UNKNOWN = "commands.evolvingblade.mode.unknown";
    private static final String KEY_MODE_SUCCESS = "commands.evolvingblade.mode.success";
    private static final String KEY_SET_SUCCESS = "commands.evolvingblade.set.success";
    private static final String KEY_SET_INVALID = "commands.evolvingblade.set.invalid";
    private static final String KEY_CHECK_NOT_LIVING = "commands.evolvingblade.bosscheck.not_living";
    private static final String KEY_CHECK_RESULT = "commands.evolvingblade.bosscheck.result";
    private static final String KEY_CHECK_BOSS = "commands.evolvingblade.bosscheck.boss";
    private static final String KEY_CHECK_NOT_BOSS = "commands.evolvingblade.bosscheck.not_boss";
    private static final String KEY_CHECK_HOSTILE = "commands.evolvingblade.bosscheck.hostile";
    private static final String KEY_CHECK_NOT_HOSTILE = "commands.evolvingblade.bosscheck.not_hostile";

    private EBCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("evolvingblade")
                .then(Commands.literal("info")
                        .executes(context -> infoSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(ADMIN_PERMISSION))
                                .executes(context -> info(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("mode")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String id : GrowthMode.ids()) {
                                                builder.suggest(id);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(EBCommands::setMode))))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("hostileKills", LongArgumentType.longArg(0L))
                                        .then(Commands.argument("bossKills", LongArgumentType.longArg(0L))
                                                .executes(EBCommands::setCounters)))))
                .then(Commands.literal("bosscheck")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(context -> bossCheck(context.getSource(),
                                        EntityArgument.getEntity(context, "entity"))))));
    }

    private static int infoSelf(CommandSourceStack source) throws CommandSyntaxException {
        return info(source, source.getPlayerOrException());
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        GrowthSettings settings = EBConfig.settings();
        GrowthData data = GrowthService.data(target);
        int stage = data.stage(settings);
        String name = target.getGameProfile().getName();
        source.sendSuccess(() -> Component.translatable(KEY_HEADER, name), false);
        source.sendSuccess(() -> Component.translatable(KEY_STATUS,
                data.hasMode() ? Component.literal(data.mode().id()) : Component.translatable(KEY_MODE_NONE),
                stage, GrowthMode.STAGE_COUNT - 1), false);
        source.sendSuccess(() -> Component.translatable(KEY_COUNTERS,
                data.growthPoints(), data.hostileKills(), data.bossKills(), data.giftState().name()), false);
        if (data.hasMode()) {
            GrowthThresholds thresholds = settings.thresholds(data.mode());
            Component nextPoints;
            Component nextBosses;
            if (stage >= GrowthMode.STAGE_COUNT - 1) {
                nextPoints = Component.translatable(KEY_MAX_STAGE);
                nextBosses = Component.translatable(KEY_MAX_STAGE);
            } else {
                nextPoints = Component.translatable(KEY_NEXT_POINTS,
                        StageCalculator.pointsForNextStage(data.growthPoints(), data.bossKills(), thresholds),
                        thresholds.pointAt(stage + 1));
                nextBosses = Component.translatable(KEY_NEXT_BOSSES,
                        StageCalculator.bossesForNextStage(data.growthPoints(), data.bossKills(), thresholds),
                        thresholds.bossAt(stage + 1));
            }
            source.sendSuccess(() -> Component.translatable(KEY_NEXT, nextPoints, nextBosses), false);
        }
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String raw = StringArgumentType.getString(context, "mode");
        GrowthMode mode = GrowthMode.byId(raw).orElse(null);
        if (mode == null) {
            source.sendFailure(Component.translatable(KEY_MODE_UNKNOWN, raw, String.join("/", GrowthMode.ids())));
            return 0;
        }
        GrowthService.forceMode(target, mode);
        source.sendSuccess(() -> Component.translatable(KEY_MODE_SUCCESS, target.getGameProfile().getName(), mode.id()), true);
        return 1;
    }

    private static int setCounters(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long hostileKills = LongArgumentType.getLong(context, "hostileKills");
        long bossKills = LongArgumentType.getLong(context, "bossKills");
        GrowthData updated;
        try {
            updated = GrowthService.setCounters(target, hostileKills, bossKills);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.translatable(KEY_SET_INVALID, ex.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(KEY_SET_SUCCESS, target.getGameProfile().getName(),
                updated.hostileKills(), updated.bossKills(), updated.growthPoints()), true);
        return 1;
    }

    private static int bossCheck(CommandSourceStack source, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.translatable(KEY_CHECK_NOT_LIVING, BossClassifier.entityId(entity)));
            return 0;
        }
        BossClassification classification = BossClassifier.classify(living, EBConfig.settings().bossRules());
        boolean hostile = BossClassifier.isHostile(living, EBConfig.settings().hostileRules());
        source.sendSuccess(() -> Component.translatable(KEY_CHECK_RESULT,
                Component.translatable(classification.boss() ? KEY_CHECK_BOSS : KEY_CHECK_NOT_BOSS),
                classification.entityId(),
                Component.translatable(classification.reason().translationKey()),
                Component.translatable(hostile ? KEY_CHECK_HOSTILE : KEY_CHECK_NOT_HOSTILE)), false);
        return 1;
    }
}
