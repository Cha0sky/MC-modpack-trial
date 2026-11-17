package local.lostworld;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.confluence.mod.common.data.saved.HardmodeConvertor;
import org.confluence.mod.common.data.saved.KillBoard;
import org.confluence.mod.mixed.IMinecraftServer;

/** Observes the native world transition and reports it as player quest progress. */
public final class WorldTurnEvents {
    private static final ResourceLocation ARCHIVE_OPEN = id("heritage/archive_open");
    private static final TagKey<Item> FIRST_INGOTS = TagKey.create(Registries.ITEM, id("world_turn_ingots"));

    private WorldTurnEvents() {}

    enum Conversion { NOT_STARTED, IN_PROGRESS, COMPLETED }

    record Status(boolean fleshDefeated, boolean hardmodeEnabled, Conversion conversion) {
        boolean reachedWorldTurn() {
            return fleshDefeated && hardmodeEnabled;
        }

        String archivePage() {
            String terrain = switch (conversion) {
                case NOT_STARTED -> "尚未开始";
                case IN_PROGRESS -> "转换中";
                case COMPLETED -> "已完成";
            };
            return "世界转折\n\n血肉首领：" + (fleshDefeated ? "已击败" : "仍待挑战")
                    + "\n困难模式：" + (hardmodeEnabled ? "已开启" : "尚未开启")
                    + "\n地形转换：" + terrain
                    + "\n\n使用档案台可刷新此页。";
        }
    }

    static Optional<Status> read(MinecraftServer server) {
        if (!ModList.get().isLoaded("confluence")) {
            return Optional.empty();
        }
        return Optional.of(Confluence.read(server));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || player.tickCount % 20 != 0) {
            return;
        }
        var archive = player.server.getAdvancements().get(ARCHIVE_OPEN);
        if (archive == null || !player.getAdvancements().getOrStartProgress(archive).isDone()) {
            return;
        }
        read(player.server).ifPresent(status -> {
            if (status.fleshDefeated()) {
                ChapterEvents.award(player, "world_turn/flesh_defeated");
            }
            if (status.reachedWorldTurn()) {
                ChapterEvents.award(player, "world_turn/hardmode_open");
                if (player.getInventory().contains(stack -> stack.is(FIRST_INGOTS))) {
                    ChapterEvents.award(player, "world_turn/first_ingot");
                }
            }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LostWorld.ID, path);
    }

    // Resolve native classes only after checking that the optional mod is present.
    private static final class Confluence {
        private static final ResourceLocation WALL = ResourceLocation.parse("terra_entity:wall_of_flesh");
        private static final ResourceLocation HILL = ResourceLocation.parse("terra_entity:hill_of_flesh");

        private static Status read(MinecraftServer server) {
            boolean defeated = defeated(WALL) || defeated(HILL);
            boolean hardmode = IMinecraftServer.isHardmode(server);
            HardmodeConvertor converter = HardmodeConvertor.INSTANCE;
            Conversion conversion = converter.isCompleted() ? Conversion.COMPLETED
                    : converter.isStarted() ? Conversion.IN_PROGRESS : Conversion.NOT_STARTED;
            return new Status(defeated, hardmode, conversion);
        }

        private static boolean defeated(ResourceLocation id) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .map(type -> KillBoard.INSTANCE.isDefeated(type))
                    .orElse(false);
        }
    }
}
