package local.lostworld.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.confluence.mod.common.attachment.EverBeneficial", remap = false)
abstract class LifeCrystalLimitMixin {
    @Shadow public abstract int getUsedLifeCrystals();

    @ModifyConstant(method = "isLifeCrystalsMaximum()Z", constant = @Constant(intValue = 15), require = 1)
    private int lostworld$doubleCrystalCapacity(int original) {
        return original * 2;
    }

    // Fruit originally unlocks after 15 crystals. Keep that progression for
    // both returning players and new worlds while additional crystals remain usable.
    @Redirect(method = "increaseFruits()Z", at = @At(value = "INVOKE",
            target = "Lorg/confluence/mod/common/attachment/EverBeneficial;isLifeCrystalsMaximum()Z"), require = 1)
    private boolean lostworld$fruitUnlocked(@Coerce Object attachment) {
        return getUsedLifeCrystals() >= 15;
    }
}
