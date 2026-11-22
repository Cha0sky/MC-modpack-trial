package local.lostworld.mixin;

import local.lostworld.BossBalanceEvents;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.boss.hillofflesh.HillOfFlesh", remap = false)
abstract class HillFieldBalanceMixin {
    @ModifyArg(method = "doMagicDamage()V", index = 0, require = 3, allow = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private DamageSource lostworld$identifyRing(DamageSource original) {
        return new BossBalanceEvents.HillFieldDamage(original);
    }
}
