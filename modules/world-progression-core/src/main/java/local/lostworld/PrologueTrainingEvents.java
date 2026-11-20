package local.lostworld;

import com.evolvingblade.growth.GrowthService;
import com.rpgleveling.registry.RpgAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LostWorld.ID)
public final class PrologueTrainingEvents {
    private PrologueTrainingEvents() {}

    @SubscribeEvent
    public static void readPlayerProgress(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 20 != 0) return;
        ChapterData chapter = ChapterData.find(player.serverLevel());
        if (chapter == null || !chapter.state().active()) return;
        if (chapter.kingDefeatedBeforeTraining()) ChapterEvents.award(player, "prologue_training/veteran");
        if (ModList.get().isLoaded("rpgleveling") && Role.hasAllocation(player)) {
            ChapterEvents.award(player, "prologue_training/allocate");
        }
        if (GrowthService.data(player).waveReadyAt() > 0) {
            ChapterEvents.award(player, "prologue_training/wave");
        }
        if (GrowthService.data(player).burstReadyAt() > 0) ChapterEvents.award(player, "prologue_training/burst");
    }

    // The chapter core can also run in an instance without the optional RPG module.
    private static final class Role {
        private static boolean hasAllocation(ServerPlayer player) {
            return player.hasData(RpgAttachments.PLAYER_DATA) && player.getData(RpgAttachments.PLAYER_DATA).spentPoints() > 0;
        }
    }
}
