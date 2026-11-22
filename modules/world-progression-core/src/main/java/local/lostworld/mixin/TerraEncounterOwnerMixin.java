package local.lostworld.mixin;

import java.util.UUID;
import local.lostworld.EncounterOwner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "org.confluence.terraentity.api.entity.IMinion", remap = false)
public interface TerraEncounterOwnerMixin extends EncounterOwner {
    @Shadow UUID minion_getOwnerUUID();

    @Override
    default Entity lostworld$encounterOwner() {
        UUID id = minion_getOwnerUUID();
        return id != null && this instanceof Entity entity && entity.level() instanceof ServerLevel server
                ? server.getEntity(id) : null;
    }
}
