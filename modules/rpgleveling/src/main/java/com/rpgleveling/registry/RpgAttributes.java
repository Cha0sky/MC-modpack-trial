package com.rpgleveling.registry;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The seven attributes this mod registers.
 *
 * <p>All of them are {@code setSyncable(true)} so the vanilla attribute packet keeps
 * the client mirror up to date, and they are attached to {@code EntityType.PLAYER}
 * in {@link #onEntityAttributeModification} - without that listener
 * {@code player.getAttribute(...)} returns {@code null} and every bonus silently
 * does nothing.</p>
 */
public final class RpgAttributes {

    public static final String MODID = "rpgleveling";

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, MODID);

    /** HP per second. The real ceiling comes from the saturating curve. */
    public static final DeferredHolder<Attribute, Attribute> HEALTH_REGEN = ATTRIBUTES.register("health_regen",
            () -> new RangedAttribute("attribute.rpgleveling.health_regen", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Damage reduction in percentage points. */
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_REDUCTION = ATTRIBUTES.register("damage_reduction",
            () -> new PercentageAttribute("attribute.rpgleveling.damage_reduction", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Critical strike chance in percentage points. */
    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
            () -> new PercentageAttribute("attribute.rpgleveling.crit_chance", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Critical damage bonus in percentage points ({@code +50} turns the 1.5x into 2.0x). */
    public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = ATTRIBUTES.register("crit_damage",
            () -> new RangedAttribute("attribute.rpgleveling.crit_damage", 0.0, 0.0, 1.0E9)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Life steal in percentage points of the damage actually dealt. */
    public static final DeferredHolder<Attribute, Attribute> LIFESTEAL = ATTRIBUTES.register("lifesteal",
            () -> new PercentageAttribute("attribute.rpgleveling.lifesteal", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Drop multiplier in percentage points ({@code 150} = 2.5x). */
    public static final DeferredHolder<Attribute, Attribute> DROP_RATE = ATTRIBUTES.register("drop_rate",
            () -> new PercentageAttribute("attribute.rpgleveling.drop_rate", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    /** Experience multiplier in percentage points ({@code 100} = 2x). */
    public static final DeferredHolder<Attribute, Attribute> XP_GAIN = ATTRIBUTES.register("xp_gain",
            () -> new PercentageAttribute("attribute.rpgleveling.xp_gain", 0.0, 0.0, 1.0E6)
                    .setSyncable(true).setSentiment(Attribute.Sentiment.POSITIVE));

    public static final List<DeferredHolder<Attribute, Attribute>> ALL_CUSTOM = List.of(
            HEALTH_REGEN, DAMAGE_REDUCTION, CRIT_CHANCE, CRIT_DAMAGE, LIFESTEAL, DROP_RATE, XP_GAIN);

    private RpgAttributes() {}

    /** Mod-bus listener; registered explicitly from the {@code @Mod} constructor. */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (DeferredHolder<Attribute, Attribute> holder : ALL_CUSTOM) {
            if (!event.has(EntityType.PLAYER, holder)) {
                event.add(EntityType.PLAYER, holder);
            }
        }
    }
}
