package local.lostworld;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class StarterTownLocator {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(LostWorld.ID, "starter_town");

    private StarterTownLocator() {}

    public static boolean isTown(ServerLevel level, Structure structure) {
        return ID.equals(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure));
    }

    public static BlockPos nextHome(ChapterData chapter, List<BlockPos> previous) {
        if (chapter == null || !chapter.hasSettlement()) return null;
        BlockPos home = chapter.home();
        return previous.contains(home) ? null : home;
    }
}
