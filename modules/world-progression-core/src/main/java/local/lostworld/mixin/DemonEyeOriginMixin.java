package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.monster.demoneye.DemonEye", remap = false)
abstract class DemonEyeOriginMixin {
    @Inject(method = "minion_setOwner", at = @At("RETURN"), require = 1)
    private void lostworld$markBossServant(Entity owner, CallbackInfo callback) {
        if (BossSummonOrigin.is(owner, BossSummonOrigin.EYE))
            BossSummonOrigin.mark((Entity)(Object)this, BossSummonOrigin.EYE);
    }
}
