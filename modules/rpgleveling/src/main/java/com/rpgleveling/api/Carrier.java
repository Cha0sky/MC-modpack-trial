package com.rpgleveling.api;

import com.rpgleveling.registry.RpgAttributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * How a dimension is carried into the game. Only {@code AttributeApplier} and
 * {@code DerivedStats} are allowed to ask for the holder; nothing else touches an
 * {@code Attribute} directly.
 */
public enum Carrier {
    VANILLA_ATTACK_DAMAGE,
    VANILLA_MAX_HEALTH,
    VANILLA_ARMOR,
    VANILLA_ARMOR_TOUGHNESS,
    VANILLA_MOVEMENT_SPEED,
    VANILLA_KNOCKBACK_RESISTANCE,
    CUSTOM_HEALTH_REGEN,
    CUSTOM_DAMAGE_REDUCTION,
    CUSTOM_CRIT_CHANCE,
    CUSTOM_CRIT_DAMAGE,
    CUSTOM_LIFESTEAL,
    CUSTOM_DROP_RATE,
    CUSTOM_XP_GAIN;

    public static final Carrier[] VALUES = values();

    /** {@code true} for the seven attributes this mod registers itself. */
    public boolean isCustom() {
        return ordinal() >= CUSTOM_HEALTH_REGEN.ordinal();
    }

    /**
     * The attribute this dimension rides on.
     *
     * <p>Typed as {@link Holder}, <b>not</b> as {@code DeferredHolder}: the six vanilla
     * attributes are {@code Holder.Reference} instances and casting them to
     * {@code DeferredHolder} throws {@code ClassCastException} on the very first player
     * tick. Caught by {@code HarnessSmokeSuite} in T7.</p>
     */
    public Holder<Attribute> holder() {
        return switch (this) {
            case VANILLA_ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
            case VANILLA_MAX_HEALTH -> Attributes.MAX_HEALTH;
            case VANILLA_ARMOR -> Attributes.ARMOR;
            case VANILLA_ARMOR_TOUGHNESS -> Attributes.ARMOR_TOUGHNESS;
            case VANILLA_MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED;
            case VANILLA_KNOCKBACK_RESISTANCE -> Attributes.KNOCKBACK_RESISTANCE;
            case CUSTOM_HEALTH_REGEN -> RpgAttributes.HEALTH_REGEN;
            case CUSTOM_DAMAGE_REDUCTION -> RpgAttributes.DAMAGE_REDUCTION;
            case CUSTOM_CRIT_CHANCE -> RpgAttributes.CRIT_CHANCE;
            case CUSTOM_CRIT_DAMAGE -> RpgAttributes.CRIT_DAMAGE;
            case CUSTOM_LIFESTEAL -> RpgAttributes.LIFESTEAL;
            case CUSTOM_DROP_RATE -> RpgAttributes.DROP_RATE;
            case CUSTOM_XP_GAIN -> RpgAttributes.XP_GAIN;
        };
    }

    /** Registry name for the {@code attr.<name>} line of the {@code /rpg stats} contract. */
    public String attributeName() {
        return switch (this) {
            case VANILLA_ATTACK_DAMAGE -> "minecraft:generic.attack_damage";
            case VANILLA_MAX_HEALTH -> "minecraft:generic.max_health";
            case VANILLA_ARMOR -> "minecraft:generic.armor";
            case VANILLA_ARMOR_TOUGHNESS -> "minecraft:generic.armor_toughness";
            case VANILLA_MOVEMENT_SPEED -> "minecraft:generic.movement_speed";
            case VANILLA_KNOCKBACK_RESISTANCE -> "minecraft:generic.knockback_resistance";
            case CUSTOM_HEALTH_REGEN -> "rpgleveling:health_regen";
            case CUSTOM_DAMAGE_REDUCTION -> "rpgleveling:damage_reduction";
            case CUSTOM_CRIT_CHANCE -> "rpgleveling:crit_chance";
            case CUSTOM_CRIT_DAMAGE -> "rpgleveling:crit_damage";
            case CUSTOM_LIFESTEAL -> "rpgleveling:lifesteal";
            case CUSTOM_DROP_RATE -> "rpgleveling:drop_rate";
            case CUSTOM_XP_GAIN -> "rpgleveling:xp_gain";
        };
    }
}