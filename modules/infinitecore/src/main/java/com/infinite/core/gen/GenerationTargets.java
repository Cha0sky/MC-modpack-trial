package com.infinite.core.gen;

import com.infinite.core.InfiniteCore;
import com.infinite.core.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class GenerationTargets {
    private static final TagKey<Item> ALLOWED = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(InfiniteCore.MODID, "replicable_resources"));

    private GenerationTargets() {}

    public static boolean allows(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ALLOWED);
    }

    public static ItemStack resolve(ItemStack template, GeneratorTier machineTier) {
        if (!ModContent.isTemplate(template)) return ItemStack.EMPTY;
        ItemStack stored = template.get(ModContent.TARGET_ITEM.get());
        ItemStack candidate = stored == null || stored.isEmpty() ? new ItemStack(machineTier.defaultOutput()) : stored;
        return allows(candidate) ? candidate.copyWithCount(1) : ItemStack.EMPTY;
    }
}
