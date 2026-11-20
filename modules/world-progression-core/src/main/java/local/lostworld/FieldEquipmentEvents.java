package local.lostworld;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class FieldEquipmentEvents {
    private static final TagKey<Item> MINING_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID, "field_equipment/mining_tools"));

    private FieldEquipmentEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOreHarvest(BlockDropsEvent event) {
        if (!event.isCanceled() && event.getBreaker() instanceof ServerPlayer player
                && !player.isCreative() && !player.isSpectator() && event.getTool().is(MINING_TOOLS)
                && !event.getDrops().isEmpty()) {
            ResourceLocation ore = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
            boolean secondTier = ore.getNamespace().equals("confluence")
                    && (ore.getPath().equals("deepslate_mythril_ore") || ore.getPath().equals("deepslate_orichalcum_ore"));
            if (!secondTier && !event.getState().is(Tags.Blocks.ORES)) return;
            ChapterEvents.award(player, "field_equipment/tool");
            ChapterEvents.award(player, "field_equipment/trial");
            if (secondTier) ChapterEvents.award(player, "field_equipment/prospect");
        }
    }
}
