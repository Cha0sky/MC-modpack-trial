package com.evolvingblade.client;

import java.util.ArrayList;
import java.util.List;
import com.evolvingblade.network.EBPayloads;
import net.minecraft.network.chat.Component;

final class ModeCardText {
    private ModeCardText() {}

    static List<Component> stats(EBPayloads.StateSnapshot s) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.evolvingblade.dashboard.stats",format(s.stageBaseAttack()),format(s.stageAttackSpeed())));
        if (s.holdingStarEater() && Double.isFinite(s.effectiveHeldAttack()))
            lines.add(Component.translatable("screen.evolvingblade.dashboard.effective_attack",format(s.effectiveHeldAttack())));
        lines.add(Component.translatable("screen.evolvingblade.folio.kills",s.hostileKills(),s.bossKills()));
        if (!s.giftDelivered())
            lines.add(Component.translatable("screen.evolvingblade.dashboard.gift",
                    Component.translatable("evolvingblade.gift."+s.giftState().toLowerCase(java.util.Locale.ROOT))));
        return lines;
    }

    static List<Component> skills(EBPayloads.StateSnapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.evolvingblade.folio.controls",
                Component.keybind("key.use"),Component.keybind("key.sneak"),Component.keybind("key.use")));
        lines.add(Component.empty());
        lines.add(abilityLine("kill_heal", 1, snapshot.stage(), -1L));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.kill_heal_value",
                format(snapshot.killHeal())));
        lines.add(abilityLine("wave", snapshot.waveUnlockStage(), snapshot.stage(), snapshot.waveRemaining()));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.wave_value",
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_DAMAGE)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_RANGE)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_COOLDOWN)),
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_WAVE_FINAL_COOLDOWN))));
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
                seconds(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BURST_FINAL_COOLDOWN))));
        lines.add(abilityLine("boss_bonus", snapshot.bossBonusUnlockStage(), snapshot.stage(), -1L));
        lines.add(Component.translatable("screen.evolvingblade.dashboard.boss_bonus_value",
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_CAP)),
                percent(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FINAL_FRACTION)),
                format(snapshot.abilityValue(EBPayloads.StateSnapshot.A_BOSS_FINAL_CAP))));
        lines.add(Component.empty());
        if (!snapshot.abilityDamageFromAttack()) lines.add(Component.translatable("screen.evolvingblade.dashboard.ability_inherit_off"));
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
