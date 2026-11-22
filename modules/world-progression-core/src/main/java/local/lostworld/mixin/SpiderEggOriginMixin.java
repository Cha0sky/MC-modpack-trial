package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.neutral.SpiderEgg", remap = false)
abstract class SpiderEggOriginMixin {
    @ModifyArg(method = "hatchEgg", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private Entity lostworld$inheritOnHatch(Entity hatchling) {
        return BossSummonOrigin.inherit((Entity)(Object)this, hatchling);
    }
}
