package local.lostworld.mixin;

import local.lostworld.EncounterOwner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.api.entities.IOwned", remap = false)
public interface GoetyEncounterOwnerMixin extends EncounterOwner {
    @Shadow LivingEntity getTrueOwner();

    @Override
    default Entity lostworld$encounterOwner() { return getTrueOwner(); }
}
