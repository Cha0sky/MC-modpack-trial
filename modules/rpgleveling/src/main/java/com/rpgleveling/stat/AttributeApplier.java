package com.rpgleveling.stat;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.data.PlayerRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.service.PlayerCache;
import com.rpgleveling.service.StatOverrides;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * <b>The only place in the mod that writes an {@code AttributeInstance}.</b>
 *
 * <p>Idempotent by construction: every modifier has a fixed {@link ResourceLocation}
 * and is applied with {@code addOrUpdateTransientModifier}, never
 * {@code addTransientModifier} (which throws on a duplicate id and would crash the
 * server). Permanent modifiers are not used at all - they would be written to the
 * player NBT and fight with the attachment for ownership of the truth.</p>
 *
 * <p>No {@code net.minecraft.client.*} import: this class must stay loadable on a
 * dedicated server and inside a headless GameTest.</p>
 */
public final class AttributeApplier {

    private AttributeApplier() {}

    private static ResourceLocation overrideId(Stat stat) {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", "override_" + stat.id);
    }

    /** Recomputes all 26 lines for one player. Safe to call every tick. */
    public static void apply(ServerPlayer player, PlayerRpgData data) {
        LineCfg[] cfgs = RpgConfig.snapshot();
        UUID id = player.getUUID();

        for (StatLine line : StatLine.VALUES) {
            AttributeInstance inst = player.getAttribute(line.stat.carrier.holder());
            if (inst == null) continue;   // defensive: the attribute is not registered on this entity

            if (StatOverrides.has(id, line.stat)) {
                // The override drives the whole dimension: drop both regular lines.
                inst.removeModifier(line.modifierId());
                inst.removeModifier(line.pctBasisModifierId());
                continue;
            }

            double amount = RpgMath.lineValue(data.points(line), cfgs[line.index()]);
            if (!Double.isFinite(amount) || amount < 0.0) amount = 0.0;   // a broken config must never poison an attribute
            if (line.mode == Mode.FLAT) {
                if (amount <= 0.0) {
                    inst.removeModifier(line.modifierId());
                } else {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(
                            line.modifierId(), amount, AttributeModifier.Operation.ADD_VALUE));
                }
                inst.removeModifier(line.pctBasisModifierId());
            } else if (cfgs[line.index()].basis() == com.rpgleveling.api.Basis.TOTAL) {
                if (amount <= 0.0) {
                    inst.removeModifier(line.modifierId());
                } else {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(
                            line.modifierId(), amount / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                }
                inst.removeModifier(line.pctBasisModifierId());
            } else {
                double contribution = RpgMath.pctContributionBaseline(cfgs[line.index()].basisValue(), amount);
                if (!Double.isFinite(contribution) || contribution < 0.0) contribution = 0.0;
                if (contribution <= 0.0) {
                    inst.removeModifier(line.pctBasisModifierId());
                } else {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(
                            line.pctBasisModifierId(), contribution, AttributeModifier.Operation.ADD_VALUE));
                }
                inst.removeModifier(line.modifierId());
            }
        }

        applyOverrides(player);
        clampRuntimeHardCaps(player, data);
    }

    /** Forces the overridden dimensions to the requested value. */
    private static void applyOverrides(ServerPlayer player) {
        UUID id = player.getUUID();
        for (Stat stat : Stat.VALUES) {
            Double value = StatOverrides.get(id, stat);
            AttributeInstance inst = player.getAttribute(stat.carrier.holder());
            if (inst == null) continue;
            if (value == null || !Double.isFinite(value)) {
                inst.removeModifier(overrideId(stat));
            } else {
                inst.addOrUpdateTransientModifier(new AttributeModifier(
                        overrideId(stat), value - inst.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /**
     * Runtime hard clamps (DESIGN 1.7 / 1.8).
     *
     * <p><b>T17:</b> the movement speed clamp now defaults to <b>disabled</b>
     * ({@code movement_speed.hard_cap_attr = 0}), because the user asked for every
     * mod-side growth limit to be removed. MOVEMENT_SPEED follows the attribute's own
     * range instead; the thing a very fast player hits first is the vanilla
     * "moved too quickly!" pull-back, which is a server mechanic and not this clamp.
     * An admin can still set a positive value to restore a local backstop.</p>
     */
    private static void clampRuntimeHardCaps(ServerPlayer player, PlayerRpgData data) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double cap = RpgConfig.movementSpeedHardCap();
            if (!RpgConfig.movementSpeedHardCapEnabled() || !Double.isFinite(speed.getValue())) {
                speed.removeModifier(CLAMP_SPEED_ID);
            } else if (speed.getValue() > cap) {
                double base = speed.getBaseValue();
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        CLAMP_SPEED_ID, cap - base, AttributeModifier.Operation.ADD_VALUE));
            } else {
                speed.removeModifier(CLAMP_SPEED_ID);
            }
        }
        handleMaxHealthChange(player);
    }

    private static final ResourceLocation CLAMP_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("rpgleveling", "runtime_speed_cap");

    /**
     * Keeps the current health sensible when the maximum changes (DESIGN 6.2).
     * {@code lastAppliedMaxHealth} is transient and starts at 0 ("unknown"), so
     * logging in or respawning never hands out free health.
     */
    private static void handleMaxHealthChange(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        PlayerCache.Entry cache = PlayerCache.of(player.getUUID());
        double newMax = maxHealth.getValue();
        double previous = cache.lastAppliedMaxHealth;

        if (previous > 0.0 && newMax > previous && RpgConfig.healOnIncrease()) {
            float current = player.getHealth();
            float target;
            switch (RpgConfig.healMode()) {
                case ADD_DELTA -> target = (float) Math.min(newMax, current + (newMax - previous));
                case KEEP_RATIO -> target = (float) Math.min(newMax, newMax * (current / previous));
                case NONE -> target = current;
                default -> target = current;
            }
            if (target > current) player.setHealth(Math.min(target, (float) newMax));
        } else if (newMax < player.getHealth()) {
            player.setHealth((float) Math.max(0.0, newMax));
        }
        cache.lastAppliedMaxHealth = newMax;
    }

    /** Clears every modifier this mod owns - used by {@code /rpg admin reset} and logout cleanup. */
    public static void clearAll(ServerPlayer player) {
        for (StatLine line : StatLine.VALUES) {
            AttributeInstance inst = player.getAttribute(line.stat.carrier.holder());
            if (inst == null) continue;
            inst.removeModifier(line.modifierId());
            inst.removeModifier(line.pctBasisModifierId());
        }
        for (Stat stat : Stat.VALUES) {
            AttributeInstance inst = player.getAttribute(stat.carrier.holder());
            if (inst != null) inst.removeModifier(overrideId(stat));
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CLAMP_SPEED_ID);
    }

    /** Modifier ids owned by this mod for the given dimension, for tests and diagnostics. */
    public static ResourceLocation @Nullable [] ownedIds(Stat stat) {
        return new ResourceLocation[] {
                stat.line(Mode.FLAT).modifierId(),
                stat.line(Mode.PERCENT).modifierId(),
                stat.line(Mode.PERCENT).pctBasisModifierId(),
                overrideId(stat)
        };
    }
}