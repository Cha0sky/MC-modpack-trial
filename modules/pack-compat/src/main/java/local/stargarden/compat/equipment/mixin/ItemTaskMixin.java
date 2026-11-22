package local.stargarden.compat.equipment.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import java.util.Collection;
import local.stargarden.compat.equipment.WornInventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemTask.class, remap = false)
abstract class ItemTaskMixin {
    @ModifyExpressionValue(method = "submitTask", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbquests/util/PlayerInventorySummary;getRelevantItems(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Collection;"))
    private Collection<ItemStack> stargarden$includeWorn(Collection<ItemStack> inventory) {
        return WornInventory.includeWorn((ItemTask)(Object)this, inventory);
    }
}
