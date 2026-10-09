package com.evolvingblade.client;

import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.network.EBPayloads;

import net.minecraft.network.chat.Component;

/**
 * 界面文本生成：<b>全部数值来自服务器下发的权威快照</b>，客户端不硬编码任何门槛或数值，
 * 因此永远不会出现"界面显示与配置不一致"。
 *
 * <p>这里只产出"逻辑行"，实际的换行由界面按自身宽度用 {@code font.split(...)} 处理，
 * 以便在 854×480（GUI scale 2，逻辑 427×240）这种小窗口下也能读全中英文长句。
 */
public final class ModeCardText {

    private ModeCardText() {}

    /** 三选一卡片的文字：模式名、说明、以及<b>该模式全部 8 个阶段</b>的服务器门槛。 */
    public static List<Component> describe(GrowthMode mode, EBPayloads.StateSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("evolvingblade.mode." + mode.id()));
        lines.add(Component.translatable("evolvingblade.mode." + mode.id() + ".desc"));
        if (snapshot == null) {
            lines.add(Component.translatable("screen.evolvingblade.choice.waiting"));
            return lines;
        }
        lines.add(Component.translatable("screen.evolvingblade.choice.thresholds"));
        for (int stage = 0; stage < EBPayloads.StateSnapshot.STAGES; stage++) {
            lines.add(Component.translatable("screen.evolvingblade.choice.stage_line",
                    Component.translatable("evolvingblade.stage." + stage),
                    snapshot.stagePoints(mode, stage),
                    snapshot.stageBosses(mode, stage)));
        }
        return lines;
    }

    /** 仪表盘的全部行（当前状态 / 双门槛 / 八阶段路线 / 能力数值与冷却）。 */
    public static List<Component> dashboardLines(EBPayloads.StateSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        if (snapshot == null) {
            lines.add(Component.translatable("screen.evolvingblade.dashboard.waiting"));
            return lines;
        }
        lines.add(Component.translatable("screen.evolvingblade.dashboard.header"));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.mode",
                snapshot.hasMode() ? Component.translatable("evolvingblade.mode." + snapshot.modeId())
                        : Component.translatable("evolvingblade.mode.none"),
                Component.translatable("evolvingblade.stage." + snapshot.stage())));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.counters",
                snapshot.growthPoints(), snapshot.hostileKills(), snapshot.bossKills()));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.stats",
                format(snapshot.stageBaseAttack()), format(snapshot.stageAttackSpeed())));
        // 1.1.0：把"阶段基础攻击（配置口径）"与"当前持剑有效攻击（服务器属性最终值）"分成两行，
        // 并且只在真的主手持剑时显示有效值——预览别的剑不会借用当前武器的加成。
        lines.add(Component.translatable("screen.evolvingblade.dashboard.attack_source"));
        if (snapshot.holdingStarEater() && Double.isFinite(snapshot.effectiveHeldAttack())) {
            lines.add(Component.translatable("screen.evolvingblade.dashboard.effective_attack",
                    format(snapshot.effectiveHeldAttack())));
        } else {
            lines.add(Component.translatable("screen.evolvingblade.dashboard.effective_attack_not_held"));
        }
        lines.add(Component.translatable("screen.evolvingblade.dashboard.effective_note"));
        if (!snapshot.abilityDamageFromAttack()) {
            lines.add(Component.translatable("screen.evolvingblade.dashboard.ability_inherit_off"));
        }
        lines.add(Component.translatable("screen.evolvingblade.dashboard.gift",
                Component.translatable("evolvingblade.gift." + snapshot.giftState().toLowerCase(java.util.Locale.ROOT))));
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.evolvingblade.dashboard.next",
                snapshot.nextPoints(), snapshot.nextBosses()));
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.evolvingblade.dashboard.route"));
        for (int stage = 0; stage < snapshot.thresholdsPoints().size(); stage++) {
            lines.add(Component.translatable("screen.evolvingblade.dashboard.route_line",
                    Component.translatable("evolvingblade.stage." + stage),
                    snapshot.thresholdsPoints().get(stage),
                    snapshot.thresholdsBosses().get(stage),
                    stage == snapshot.stage() ? Component.translatable("screen.evolvingblade.dashboard.here")
                            : Component.empty()));
        }
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.evolvingblade.dashboard.abilities"));
        // 每条能力：先一行状态（未解锁/冷却中/就绪），再一行**真实数值**（伤害/射程/冷却/比例/上限）。
        lines.add(abilityLine("kill_heal", 1, snapshot.stage(), -1L));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.kill_heal_value",
                format(snapshot.killHeal())));
        lines.add(abilityLine("wave", snapshot.waveUnlockStage(), snapshot.stage(), snapshot.waveRemaining()));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.wave_value",
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_DAMAGE)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_RANGE)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_COOLDOWN)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_FINAL_COOLDOWN)),
                (long) snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_MAX_TARGETS)));
        lines.add(abilityLine("lifesteal", snapshot.lifestealUnlockStage(), snapshot.stage(), -1L));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.lifesteal_value",
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_LIFESTEAL_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_LIFESTEAL_CAP)),
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_LIFESTEAL_HIGH_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_LIFESTEAL_HIGH_CAP)),
                snapshot.stage()));
        lines.add(abilityLine("burst", snapshot.burstUnlockStage(), snapshot.stage(), snapshot.burstRemaining()));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.burst_value",
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_DAMAGE)),
                format(snapshot.burstRadius()),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_FINAL_RADIUS)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_COOLDOWN)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_FINAL_COOLDOWN)),
                (long) snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_MAX_TARGETS)));
        lines.add(abilityLine("boss_bonus", snapshot.bossBonusUnlockStage(), snapshot.stage(), -1L));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.boss_bonus_value",
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_CAP)),
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FINAL_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FINAL_CAP))));
        lines.add(Component.empty());
        lines.add(Component.translatable("screen.evolvingblade.dashboard.hint"));
        return lines;
    }

    private static Component abilityLine(String key, int unlockStage, int currentStage, long remainingTicks) {
        Component name = Component.translatable("evolvingblade.ability." + key);
        if (currentStage < unlockStage) {
            return Component.translatable("screen.evolvingblade.dashboard.ability_locked", name,
                    Component.translatable("evolvingblade.stage." + unlockStage));
        }
        if (remainingTicks > 0L) {
            return Component.translatable("screen.evolvingblade.dashboard.ability_cooldown", name,
                    String.format(java.util.Locale.ROOT, "%.1f", remainingTicks / 20.0D));
        }
        return Component.translatable("screen.evolvingblade.dashboard.ability_ready", name);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String percent(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.2f", fraction * 100.0D);
    }

    private static String seconds(double ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
