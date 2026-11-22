package local.lostworld;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Fold individual machine operators' advancements into the restored workshop's shared log. */
@EventBusSubscriber(modid = LostWorld.ID)
public final class EngineeringCommissionEvents {
    private EngineeringCommissionEvents() {}

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HeritageEvents.enabled(player)) reconcile(player);
    }

    @SubscribeEvent
    public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return;
        String id = event.getAdvancement().id().toString();
        if (id.equals(LostWorld.ID + ":heritage/welcome")) {
            reconcile(player);
        } else if (EngineeringCommission.SOURCES.contains(id) && HeritageEvents.enabled(player)) {
            HeritageWorldData world = HeritageWorldData.get(player.serverLevel());
            if (world.recordEngineering(id)) publish(player, world);
        }
    }

    private static void reconcile(ServerPlayer player) {
        HeritageWorldData world = HeritageWorldData.get(player.serverLevel());
        boolean changed = false;
        for (String id : EngineeringCommission.SOURCES) {
            var advancement = player.server.getAdvancements().get(ResourceLocation.parse(id));
            if (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                changed |= world.recordEngineering(id);
            }
        }
        if (changed) publish(player, world);
        else sync(player, world.engineeringEvidence());
    }

    private static void publish(ServerPlayer operator, HeritageWorldData world) {
        world.contribute(HeritageProgress.ENGINEERING);
        Set<String> facts = world.engineeringEvidence();
        for (ServerPlayer player : operator.server.getPlayerList().getPlayers()) {
            sync(player, facts);
            HeritageEvents.sync(player);
        }
    }

    private static void sync(ServerPlayer player, Set<String> facts) {
        if (facts.contains(EngineeringCommission.PUMP)) HeritageEvents.mark(player, "pump_station");
        if (facts.contains(EngineeringCommission.SOURCE)) HeritageEvents.mark(player, "source_intake");
        if (facts.contains(EngineeringCommission.DRAIN)) HeritageEvents.mark(player, "recovery_intake");
        if (facts.contains(EngineeringCommission.SPOUT) || facts.contains(EngineeringCommission.PROTOTYPE))
            HeritageEvents.mark(player, "spout_station");
        if (facts.contains(EngineeringCommission.PROTOTYPE)) HeritageEvents.mark(player, "controller_built");
        if (facts.contains(EngineeringCommission.REGULATED)) HeritageEvents.mark(player, "controller_regulated");
        if (EngineeringCommission.heat(facts)) HeritageEvents.mark(player, "engineering_heat");
        if (EngineeringCommission.assembly(facts)) HeritageEvents.mark(player, "engineering_assembly");
    }
}
