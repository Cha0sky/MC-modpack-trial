package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.boss.KingSlime", remap = false)
abstract class KingSlimeSummonMixin {
    @ModifyArg(method = "spawnSlime", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 2, allow = 2)
    private Entity lostworld$markRoyalSlime(Entity child) {
        return BossSummonOrigin.mark(child, BossSummonOrigin.KING);
    }
}
