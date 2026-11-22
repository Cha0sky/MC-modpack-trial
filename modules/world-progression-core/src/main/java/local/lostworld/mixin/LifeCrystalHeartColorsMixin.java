package local.lostworld.mixin;

import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.confluence.mod.client.gui.hud.TerraStyleHealthHud", remap = false)
abstract class LifeCrystalHeartColorsMixin {
    @Shadow @Final @Mutable private static int[] HEALTH;
    @Shadow @Final @Mutable private static int[] HEALTH_HIGH;
    @Shadow @Final @Mutable private static int[] HEALTH_LOW;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void lostworld$addHeartColors(CallbackInfo info) {
        // Per 20 HP: lapis, amethyst, diamond. Each set of three RGB samples
        // comes from the corresponding vanilla 1.21.1 item sprite. The HUD
        // draws its usual full/half heart masks and status overlays on top.
        HEALTH      = lostworld$append(HEALTH,      0x345EC3, 0xB38EF3, 0x4AEDD9);
        HEALTH_HIGH = lostworld$append(HEALTH_HIGH, 0x7497EA, 0xFECBE6, 0xD5FFF6);
        HEALTH_LOW  = lostworld$append(HEALTH_LOW,  0x1A3D8F, 0x6F4FAB, 0x1C919A);
    }

    @Unique
    private static int[] lostworld$append(int[] palette, int lapis, int amethyst, int diamond) {
        int[] expanded = Arrays.copyOf(palette, palette.length + 3);
        expanded[palette.length] = lapis;
        expanded[palette.length + 1] = amethyst;
        expanded[palette.length + 2] = diamond;
        return expanded;
    }
}
