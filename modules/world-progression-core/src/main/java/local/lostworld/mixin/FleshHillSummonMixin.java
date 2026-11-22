package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
        "org.confluence.terraentity.entity.boss.hillofflesh.HillOfFlesh$SummonFleshSlimeGoal",
        "org.confluence.terraentity.entity.boss.hillofflesh.HillOfFlesh$SummonLeechGoal"
}, remap = false)
abstract class FleshHillSummonMixin {
    // SummonGoal invokes this erased bridge for both typed createMinion implementations.
    @Inject(method = "createMinion(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"), require = 1, allow = 1)
    private void lostworld$markHillMinion(LivingEntity target, ServerLevel level, CallbackInfoReturnable<Entity> callback) {
        Entity child = callback.getReturnValue();
        if (child != null) BossSummonOrigin.mark(child, BossSummonOrigin.HILL);
    }
}
