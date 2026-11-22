package local.lostworld.mixin;

import local.lostworld.BossBalanceEvents;
import local.lostworld.BossSummonOrigin;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.proj.LavaPillar", remap = false)
abstract class FleshHillPillarDamageMixin {
    @ModifyArg(method = "doHurt", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 1, allow = 1)
    private DamageSource lostworld$identifyHillLava(DamageSource original) {
        // The native owner also controls the pillar's target filter. Keep it empty
        // and attach the budget solely to the already-constructed lava source.
        Entity pillar = (Entity)(Object)this;
        if (original.getEntity() == null && BossSummonOrigin.HILL.equals(pillar.getPersistentData().getString(BossSummonOrigin.KEY)))
            return new BossBalanceEvents.HillFieldDamage(original);
        return original;
    }
}
