package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.neutral.AbstractBroodMother", remap = false)
abstract class BroodMotherEggMixin {
    @ModifyArg(method = "attackAI", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private Entity lostworld$markHostileMothersEgg(Entity egg) {
        // The hostile boss and the player's craftable servant share this AI.
        if (BossSummonOrigin.is((Entity)(Object)this, BossSummonOrigin.BROOD))
            BossSummonOrigin.mark(egg, BossSummonOrigin.BROOD);
        return egg;
    }
}
