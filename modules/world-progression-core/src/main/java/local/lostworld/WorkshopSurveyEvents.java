package local.lostworld;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = LostWorld.ID)
public final class WorkshopSurveyEvents {
    private WorkshopSurveyEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void readRelay(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseItem() == TriState.FALSE || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || !event.getItemStack().is(WorkshopContent.TUNER.get())) return;
        var pos = event.getPos();
        var level = player.serverLevel();
        if (level.getBlockState(pos).is(WorkshopContent.RELAY.get()) && WorkshopEvents.enabled(player)
                && WorkshopInteraction.structure(level, pos).isValid()) {
            ChapterEvents.award(player, "workshop/diagnose");
        }
    }
}
