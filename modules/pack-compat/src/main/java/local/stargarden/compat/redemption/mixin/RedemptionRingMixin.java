package local.stargarden.compat.redemption.mixin;

import auviotre.enigmatic.legacy.contents.item.rings.CursedRing;
import auviotre.enigmatic.legacy.contents.item.rings.RedemptionRing;
import auviotre.enigmatic.legacy.handlers.EnigmaticHandler;
import auviotre.enigmatic.legacy.registries.EnigmaticItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.SlotContext;

@Mixin(value = RedemptionRing.class, remap = false)
abstract class RedemptionRingMixin {
    private static final String BONUS = "Lauviotre/enigmatic/legacy/contents/item/rings/RedemptionRing$Helper;getBonus(Lnet/minecraft/world/item/ItemStack;I)I";

    @Redirect(method = "getLootingLevel", at = @At(value = "INVOKE", target = BONUS))
    private int stargarden$looting(ItemStack ring, int nativeBase, SlotContext slot, LootContext loot, ItemStack equipped) {
        return stargarden$bonus(ring, nativeBase, slot, CursedRing.lootingBonus.getAsInt());
    }

    @Redirect(method = "getFortuneLevel", at = @At(value = "INVOKE", target = BONUS))
    private int stargarden$fortune(ItemStack ring, int nativeBase, SlotContext slot, LootContext loot, ItemStack equipped) {
        return stargarden$bonus(ring, nativeBase, slot, CursedRing.fortuneBonus.getAsInt());
    }

    private static int stargarden$bonus(ItemStack ring, int nativeBase, SlotContext slot, int inherited) {
        int combined = Math.max(inherited, RedemptionRing.Helper.getBonus(ring, nativeBase));
        return EnigmaticHandler.hasCurio(slot.entity(), EnigmaticItems.CURSED_RING) ? combined - inherited : combined;
    }
}
