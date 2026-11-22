package local.stargarden.compat.redemption.mixin;

import auviotre.enigmatic.legacy.registries.EnigmaticItems;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "auviotre.enigmatic.legacy.compat.jei.JEIHandler", remap = false)
abstract class TaintingRecipesMixin {
    @ModifyArg(method = "registerRecipes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Ingredient;of([Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/item/crafting/Ingredient;"), index = 0)
    private ItemLike[] stargarden$taintingRings(ItemLike[] options) {
        return options.length == 1 && options[0] == EnigmaticItems.CURSED_RING
                ? new ItemLike[]{EnigmaticItems.CURSED_RING, EnigmaticItems.REDEMPTION_RING} : options;
    }
}
