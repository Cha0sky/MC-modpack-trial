package local.stargarden.compat.equipment.mixin;

import dev.ftb.mods.ftbquests.util.PlayerInventorySummary;
import local.stargarden.compat.equipment.WornInventory;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerInventorySummary.class, remap = false)
abstract class InventorySummaryMixin {
    @Inject(method = "build", at = @At("TAIL"))
    private static void stargarden$collectWorn(ServerPlayer player, CallbackInfo ci) {
        WornInventory.build(player);
    }
}
