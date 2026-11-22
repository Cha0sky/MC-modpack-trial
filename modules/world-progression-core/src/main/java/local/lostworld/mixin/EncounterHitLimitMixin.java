package local.lostworld.mixin;

import local.lostworld.BossBalanceEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommonHooks.class, remap = false)
abstract class EncounterHitLimitMixin {
    // The player code reads BOTH the return value (absorption) and the mutable
    // container (health loss). Cap them after every LivingDamageEvent.Pre listener.
    @Inject(method = "onLivingDamagePre", at = @At("RETURN"), cancellable = true, require = 1)
    private static void lostworld$finishBossHit(LivingEntity victim, DamageContainer damage,
                                                 CallbackInfoReturnable<Float> result) {
        if (!(victim instanceof ServerPlayer)) return;
        float amount = result.getReturnValueF();
        float capped = BossBalanceEvents.finalDamage(damage.getSource(), amount);
        if (capped < amount) {
            damage.setNewDamage(capped);
            result.setReturnValue(capped);
        }
    }
}
