package local.lostworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** A field depot is a real barrel. Inspecting it records its stock without moving it. */
public final class FrontlineDepot {
    private static final int ORDER_SIZE = 2;

    private FrontlineDepot() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void inspect(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(Items.COMPASS) || event.getEntity().isSpectator()
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BarrelBlock)) return;
        if (event.isCanceled()) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) || !barrel.canOpen(player)) return;
        String dimension = level.dimension().location().toString();
        boolean nearSite = HeritageWorldData.get(level).sites().stream().anyMatch(site ->
                site.dimension().equals(dimension) && !HeritageProgress.surveyQuest(site.structure()).isEmpty()
                        && HeritageProgress.inDepotRange(pos.getX(), pos.getZ(), site.pos().getX(), site.pos().getZ()));
        if (!nearSite) {
            tell(player, "查阅归档地图：在地下墓穴或林地府邸标记的水平 128 格内设站。");
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (CampfireBlock.isLitCampfire(level.getBlockState(pos.relative(side)))) {
                audit(player, barrel);
                return;
            }
        }
        tell(player, "在木桶同高度的东、西、南、北任意一侧紧贴放置并点燃营火，再用指南针查验。");
    }

    private static void audit(ServerPlayer player, BarrelBlockEntity barrel) {
        int trail = barrel.countItem(WorkshopContent.TRAIL_SUPPLY_CRATE.get());
        int rescue = barrel.countItem(WorkshopContent.RESCUE_SUPPLY_CRATE.get());
        ChapterEvents.award(player, "frontline/station");
        recordStock(player, "trail", trail);
        recordStock(player, "rescue", rescue);
        if (trail >= 4 && rescue >= 4) ChapterEvents.award(player, "frontline/mixed_reserve");
        if (FrontlineFreight.checkReceipt(player, barrel)) return;
        tell(player, "营地盘点完成。野外补给包 " + trail + " / 2，救援补给包 " + rescue + " / 2。物资可随时取用。");
    }

    private static void recordStock(ServerPlayer player, String kind, int count) {
        if (count > 0) ChapterEvents.award(player, "frontline/" + kind + "_prepared");
        if (count >= ORDER_SIZE) ChapterEvents.award(player, "frontline/" + kind + "_delivery");
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
