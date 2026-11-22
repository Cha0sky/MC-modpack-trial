package local.stargarden.compat.redemption.mixin;

import auviotre.enigmatic.legacy.contents.item.scrolls.CursedScroll;
import auviotre.enigmatic.legacy.handlers.EnigmaticHandler;
import auviotre.enigmatic.legacy.registries.EnigmaticItems;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CursedScroll.class, remap = false)
abstract class CursedScrollMixin {
    @Inject(method = "getItemCurseLevel", at = @At("RETURN"), cancellable = true)
    private static void stargarden$sevenCurses(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack.is(EnigmaticItems.REDEMPTION_RING)) cir.setReturnValue(cir.getReturnValueI() + 7);
    }

    @Redirect(method = "getCurseAmount", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;get()I"))
    private static int stargarden$oneRingBonus(AtomicInteger total, LivingEntity wearer) {
        // The rings normally exclude each other. Command-equipped pairs still contribute
        // the intrinsic seven once, before the scroll applies its native decay curve.
        boolean both = EnigmaticHandler.hasCurio(wearer, EnigmaticItems.REDEMPTION_RING)
                && EnigmaticHandler.hasCurio(wearer, EnigmaticItems.CURSED_RING);
        return both ? total.get() - 7 : total.get();
    }
}
