package local.lostworld.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * L2 3.0.18 accepts only whole levels per dimension. Divide the accumulated
 * dimension bonus after multiplication, so four visits still yield ten levels.
 */
@Pseudo
@Mixin(targets = "dev.xkmc.l2hostility.content.capability.player.PlayerDifficulty", remap = false)
abstract class HostilityDimensionGrowthMixin {
    @ModifyVariable(method = "getExtraLevel", index = 2, at = @At(value = "STORE", ordinal = 0),
            slice = @Slice(from = @At(value = "FIELD", target =
                    "Ldev/xkmc/l2hostility/init/data/LHConfig$Server;dimensionFactor:Lnet/neoforged/neoforge/common/ModConfigSpec$IntValue;")),
            require = 1, allow = 1)
    private int lostworld$dimensionBonus(int dimensionLevels) {
        // This store precedes the addition of the player's equipment attribute.
        return dimensionLevels / 4;
    }

    @ModifyVariable(method = "getPlayerDifficultyDetail", index = 3, at = @At("STORE"), require = 1, allow = 1)
    private int lostworld$displayDimensionBonus(int dimensionLevels) {
        return dimensionLevels / 4;
    }
}
