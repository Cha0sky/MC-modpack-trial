package local.lostworld.mixin;

import local.lostworld.BossSummonOrigin;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.entity.boss.hillofflesh.HillOfFlesh$GenerateFirePillar", remap = false)
abstract class FleshHillPillarSpawnMixin {
    @ModifyArg(method = "doAction", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private Entity lostworld$markHillPillar(Entity child) {
        return BossSummonOrigin.mark(child, BossSummonOrigin.HILL);
    }
}
