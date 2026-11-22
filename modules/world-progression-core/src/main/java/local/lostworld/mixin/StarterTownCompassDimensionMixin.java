package local.lostworld.mixin;

import java.util.List;
import local.lostworld.StarterTownLocator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.util.StructureUtils", remap = false)
abstract class StarterTownCompassDimensionMixin {
    @Inject(method = "getGeneratingDimensionKeys", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lostworld$townDimension(ServerLevel level, Structure structure,
                                               CallbackInfoReturnable<List<ResourceLocation>> callback) {
        // Other dimensions may reuse overworld biomes, but only the overworld owns a town.
        if (StarterTownLocator.isTown(level, structure)) callback.setReturnValue(List.of(Level.OVERWORLD.location()));
    }
}
