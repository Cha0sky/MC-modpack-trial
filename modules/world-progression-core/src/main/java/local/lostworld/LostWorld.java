package local.lostworld;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;

@Mod(LostWorld.ID)
public final class LostWorld {
    public static final String ID = "world_progression_core";
    public static final Logger LOG = LogUtils.getLogger();
    public LostWorld(IEventBus modBus) {
        WorkshopContent.register(modBus);
        NeoForge.EVENT_BUS.register(ChapterEvents.class);
        NeoForge.EVENT_BUS.register(WorkshopEvents.class);
        NeoForge.EVENT_BUS.register(HeritageEvents.class);
        NeoForge.EVENT_BUS.register(HeritageArchive.class);
        NeoForge.EVENT_BUS.register(WorldTurnEvents.class);
        NeoForge.EVENT_BUS.register(FieldEquipmentEvents.class);
    }
}
