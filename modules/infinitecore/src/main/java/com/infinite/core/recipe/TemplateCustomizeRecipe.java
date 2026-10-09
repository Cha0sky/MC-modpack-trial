package com.infinite.core.recipe;

import com.infinite.core.registry.ModContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Retains full target components and ordinary crafting-remainder semantics. */
public final class TemplateCustomizeRecipe extends CustomRecipe {
    public TemplateCustomizeRecipe(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        int templates = 0;
        int targets = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (ModContent.isTemplate(stack)) templates++;
            else targets++;
        }
        return templates == 1 && targets == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack template = ItemStack.EMPTY;
        ItemStack target = ItemStack.EMPTY;
        int occupied = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            occupied++;
            if (ModContent.isTemplate(stack)) {
                if (!template.isEmpty()) return ItemStack.EMPTY;
                template = stack;
            } else {
                if (!target.isEmpty()) return ItemStack.EMPTY;
                target = stack;
            }
        }
        if (occupied != 2 || template.isEmpty() || target.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = template.copyWithCount(1);
        result.set(ModContent.TARGET_ITEM.get(), target.copyWithCount(1));
        return result;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.TEMPLATE_CUSTOMIZE.get(); }
}
