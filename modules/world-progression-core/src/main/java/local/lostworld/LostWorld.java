package local.lostworld;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LostWorld.ID)
public final class LostWorld {
    public static final String ID = "world_progression_core";
    public static final Logger LOG = LogUtils.getLogger();
    public LostWorld() {
        NeoForge.EVENT_BUS.register(ChapterEvents.class);
    }
}
