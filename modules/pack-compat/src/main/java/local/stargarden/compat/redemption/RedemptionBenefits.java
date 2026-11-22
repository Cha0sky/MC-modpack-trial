package local.stargarden.compat.redemption;

import auviotre.enigmatic.legacy.contents.item.rings.CursedRing;
import auviotre.enigmatic.legacy.handlers.EnigmaticHandler;
import auviotre.enigmatic.legacy.registries.EnigmaticItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class RedemptionBenefits {
    private RedemptionBenefits() {}

    public static boolean hasAccess(LivingEntity entity) {
        return entity != null && EnigmaticHandler.isTheOne(entity);
    }

    /** When the seven-curses drop option is off, keep the native leveled redemption path. */
    public static boolean usesSevenCurseDrops(LivingEntity entity) {
        return EnigmaticHandler.isTheCursedOne(entity)
                || (CursedRing.enableSpecialDrops.get() && EnigmaticHandler.isTheBlessedOne(entity));
    }

    public static boolean isWorthyForMonsterBoost(LivingEntity entity) {
        return entity != null && EnigmaticHandler.isTheCursedOne(entity) && EnigmaticHandler.isTheWorthyOne(entity);
    }

    public static boolean shouldEject(ItemStack stack, LivingEntity wearer) {
        return EnigmaticHandler.isCursedItem(stack)
                && !(EnigmaticHandler.hasCurio(wearer, EnigmaticItems.REDEMPTION_RING)
                     && EnigmaticHandler.canUse(wearer, stack));
    }
}
