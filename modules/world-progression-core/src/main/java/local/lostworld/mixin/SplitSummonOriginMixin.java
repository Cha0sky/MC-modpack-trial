package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.xkmc.l2hostility.events.MiscHandlers", remap = false)
abstract class SplitSummonOriginMixin {
    // L2 calls this when a slime or a mob carrying its Split trait produces a new living entity.
    @Inject(method = "copyCap", at = @At("HEAD"), require = 1)
    private static void lostworld$inheritOnSplit(LivingEntity parent, LivingEntity child, CallbackInfo callback) {
        BossSummonOrigin.inherit(parent, child);
    }
}
