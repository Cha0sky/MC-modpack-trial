package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.monster.slime.BaseSlime", remap = false)
abstract class HoneySlimeOriginMixin {
    @ModifyArg(method = "addHoneySoakTime", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private Entity lostworld$inheritOnHoneyConversion(Entity child) {
        return BossSummonOrigin.inherit((Entity)(Object)this, child);
    }
}
