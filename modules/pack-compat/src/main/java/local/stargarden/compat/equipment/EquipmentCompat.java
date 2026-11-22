package local.stargarden.compat.equipment;

import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import local.stargarden.compat.equipment.mixin.DeferredDetectionAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

@Mod("stargarden_pack_compat")
public final class EquipmentCompat {
    public EquipmentCompat(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                ItemMatchingSystem.INSTANCE.registerFilterAdapter(new StarterAccessoryFilter())));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) DeferredDetectionAccess.stargarden$schedule(player, 1);
        });
        NeoForge.EVENT_BUS.addListener((CurioChangeEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && !event.getTo().isEmpty()
                    && !event.getFrom().is(event.getTo().getItem())) DeferredDetectionAccess.stargarden$schedule(player, 1);
        });
        NeoForge.EVENT_BUS.addListener((LivingEquipmentChangeEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getSlot() != net.minecraft.world.entity.EquipmentSlot.MAINHAND
                    && !event.getTo().isEmpty() && !event.getFrom().is(event.getTo().getItem()))
                DeferredDetectionAccess.stargarden$schedule(player, 1);
        });
    }
}
