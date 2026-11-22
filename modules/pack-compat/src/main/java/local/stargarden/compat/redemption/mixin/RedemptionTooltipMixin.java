package local.stargarden.compat.redemption.mixin;

import auviotre.enigmatic.legacy.contents.item.rings.RedemptionRing;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RedemptionRing.class, remap = false)
abstract class RedemptionTooltipMixin {
    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void stargarden$inheritedBlessings(ItemStack stack, Item.TooltipContext context, List<Component> lines,
                                               TooltipFlag flag, CallbackInfo ci) {
        lines.add(Component.translatable("tooltip.stargarden_pack_compat.redemption.blessings"));
    }
}
