package com.evolvingblade.registry;

import com.evolvingblade.EvolvingBlade;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 本模组使用的实体标签。
 *
 * <p>{@link #BOSSES} 与 {@link #HOSTILE} 由数据包提供（见 {@code data/evolvingblade/tags/entity_type/}），
 * 让整合包作者无需改配置即可互操作；{@link #C_BOSSES} 是社区通用标签。
 */
public final class EBTags {

    /** {@code evolvingblade:bosses} —— 可靠的 Boss 判定输入之一。 */
    public static final TagKey<EntityType<?>> BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "bosses"));

    /** {@code evolvingblade:hostile} —— 可靠的普通敌对判定输入之一。 */
    public static final TagKey<EntityType<?>> HOSTILE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(EvolvingBlade.MODID, "hostile"));

    /** {@code c:bosses} —— 社区通用 Boss 标签。 */
    public static final TagKey<EntityType<?>> C_BOSSES =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private EBTags() {}
}
