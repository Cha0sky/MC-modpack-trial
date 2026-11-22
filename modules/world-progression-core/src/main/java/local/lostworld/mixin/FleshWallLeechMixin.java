package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.boss.wallofflesh.WallOfFleshMouth", remap = false)
abstract class FleshWallLeechMixin {
    @ModifyArg(method = "spawnLeech", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private Entity lostworld$markWallLeech(Entity child) {
        return BossSummonOrigin.mark(child, BossSummonOrigin.WALL);
    }
}
