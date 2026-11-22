package local.lostworld;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/** A successful Create delivery leaves a receipt with the receiving station, even while its crew is offline. */
public final class FrontlineFreight {
    private static final String RECEIPT = "lostworld_freight_receipt";

    private FrontlineFreight() {}

    public static void received(FrogportBlockEntity port) {
        if (!(port.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = port.getBlockPos().below();
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return;
        ItemStack parcel = port.animatedPackage;
        String filter = port.getFilterString();
        if (parcel == null || !PackageItem.isPackage(parcel) || filter == null || filter.isBlank()
                || PackageItem.getAddress(parcel).isBlank() || !PackageItem.matchAddress(parcel, filter)) return;
        var contents = PackageItem.getContents(parcel);
        boolean trail = false;
        boolean rescue = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack stack = contents.getStackInSlot(i);
            trail |= stack.is(WorkshopContent.TRAIL_SUPPLY_CRATE.get());
            rescue |= stack.is(WorkshopContent.RESCUE_SUPPLY_CRATE.get());
        }
        if ((!trail && !rescue) || !hasSignalFire(level, pos)) return;
        String dimension = level.dimension().location().toString();
        boolean nearSite = HeritageWorldData.get(level).sites().stream().anyMatch(site ->
                site.dimension().equals(dimension) && !HeritageProgress.surveyQuest(site.structure()).isEmpty()
                        && HeritageProgress.inDepotRange(pos.getX(), pos.getZ(), site.pos().getX(), site.pos().getZ()));
        if (!nearSite) return;

        CompoundTag receipt = barrel.getPersistentData().getCompound(RECEIPT);
        boolean mixed = trail && rescue;
        if (matchesStation(receipt, pos, dimension)) {
            if (!mixed || receipt.getBoolean("mixed")) return;
        } else {
            receipt = new CompoundTag();
            receipt.putString("dimension", dimension);
            receipt.putLong("position", pos.asLong());
        }
        receipt.putBoolean("mixed", mixed);
        barrel.getPersistentData().put(RECEIPT, receipt);
        barrel.setChanged();
    }

    private static boolean hasSignalFire(ServerLevel level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (level.hasChunkAt(next) && CampfireBlock.isLitCampfire(level.getBlockState(next))) return true;
        }
        return false;
    }

    private static boolean matchesStation(CompoundTag receipt, BlockPos pos, String dimension) {
        return dimension.equals(receipt.getString("dimension")) && pos.asLong() == receipt.getLong("position");
    }

    /** Called only after the ordinary compass audit has validated and counted this barrel. */
    public static boolean checkReceipt(ServerPlayer player, BarrelBlockEntity barrel) {
        CompoundTag receipt = barrel.getPersistentData().getCompound(RECEIPT);
        if (matchesStation(receipt, barrel.getBlockPos(), player.level().dimension().location().toString())) {
            ChapterEvents.award(player, "frontline/freight_delivery");
            if (receipt.getBoolean("mixed")) ChapterEvents.award(player, "frontline/mixed_freight");
        }
        ItemStack slip = player.getOffhandItem();
        if (!ItemStack.isSameItemSameComponents(slip, new ItemStack(Items.PAPER))) return false;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            ItemStack stock = barrel.getItem(slot);
            if (!stock.is(WorkshopContent.TRAIL_SUPPLY_CRATE.get()) && !stock.is(WorkshopContent.RESCUE_SUPPLY_CRATE.get())) continue;
            ItemStack issued = stock.copyWithCount(1);
            var inventory = player.getInventory();
            if (inventory.getSlotWithRemainingSpace(issued) < 0 && inventory.getFreeSlot() < 0) {
                tell(player, "先为补给包腾出一格背包，再来领用。");
                return true;
            }
            Component name = stock.getHoverName();
            if (!inventory.add(issued)) return true;
            barrel.removeItem(slot, 1);
            barrel.setChanged();
            slip.consume(1, player);
            ChapterEvents.award(player, "frontline/issued");
            player.displayClientMessage(Component.literal("领到 1 个").append(name).append("。在前线对空气右键拆用。"), true);
            return true;
        }
        tell(player, "领用单待办：先把未拆的野外或救援补给包存进营地木桶。");
        return true;
    }

    private static void tell(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
