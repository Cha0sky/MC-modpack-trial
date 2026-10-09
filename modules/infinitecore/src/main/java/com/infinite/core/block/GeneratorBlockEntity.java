package com.infinite.core.block;

import com.infinite.core.gen.GeneratorTier;
import com.infinite.core.menu.GeneratorMenu;
import com.infinite.core.registry.ModContent;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int RESOURCE_SLOTS = 9;
    public static final int TEMPLATE_SLOT = 9;
    public static final int SLOT_COUNT = 10;
    public static final int IO_INTERVAL = 4;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IoMode[] ioModes = new IoMode[6];
    private int progress;
    private int ioCounter;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GENERATOR_ENTITY.get(), pos, state);
        Arrays.fill(ioModes, IoMode.NONE);
    }

    public GeneratorTier tier() {
        return getBlockState().getBlock() instanceof GeneratorBlock block ? block.tier() : GeneratorTier.DIRT;
    }
    public ItemStackHandler inventory() { return inventory; }
    public int progress() { return progress; }
    public int interval() { return tier().interval(ModContent.templateTier(inventory.getStackInSlot(TEMPLATE_SLOT))); }
    public IoMode ioMode(int face) { return face >= 0 && face < 6 ? ioModes[face] : IoMode.NONE; }
    public void setIoMode(int face, IoMode mode) {
        if (face < 0 || face >= 6 || mode == null) return;
        ioModes[face] = mode;
        setChanged();
    }
    public byte[] ioModes() {
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) result[i] = (byte) ioModes[i].id();
        return result;
    }
    public void applyIoModes(byte[] modes) {
        for (int i = 0; i < Math.min(6, modes.length); i++) ioModes[i] = IoMode.fromId(modes[i]);
    }

    public ItemStack target() {
        ItemStack template = inventory.getStackInSlot(TEMPLATE_SLOT);
        if (template.isEmpty()) return ItemStack.EMPTY;
        ItemStack stored = template.get(ModContent.TARGET_ITEM.get());
        return stored != null && !stored.isEmpty() ? stored.copyWithCount(1) : new ItemStack(tier().output());
    }
    public boolean hasRoomForTarget() {
        ItemStack output = target();
        if (output.isEmpty()) return false;
        for (int slot = 0; slot < RESOURCE_SLOTS; slot++) {
            if (inventory.insertItem(slot, output, true).isEmpty()) return true;
        }
        return false;
    }

    /** Retains baseline: progress cycles even when empty/full, no catch-up or fuel. */
    public void tickServer() {
        if (level == null || level.isClientSide) return;
        if (++progress >= interval()) {
            progress = 0;
            ItemStack output = target();
            if (!output.isEmpty()) {
                for (int slot = 0; slot < RESOURCE_SLOTS; slot++) {
                    if (inventory.insertItem(slot, output.copy(), false).isEmpty()) break;
                }
            }
        }
        if (++ioCounter >= IO_INTERVAL) {
            ioCounter = 0;
            for (Direction direction : Direction.values()) {
                IoMode mode = ioMode(direction.get3DDataValue());
                if (mode == IoMode.OUTPUT || mode == IoMode.BOTH) pushTo(direction);
                if (mode == IoMode.INPUT || mode == IoMode.BOTH) pullTemplate(direction);
            }
        }
    }

    private IItemHandler neighbor(Direction direction) {
        if (level == null) return null;
        BlockPos adjacent = worldPosition.relative(direction);
        if (!level.isLoaded(adjacent)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, adjacent, direction.getOpposite());
    }
    public int neighborCapabilityMask() {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (neighbor(direction) != null) mask |= 1 << direction.get3DDataValue();
        }
        return mask;
    }
    private void pushTo(Direction direction) {
        IItemHandler destination = neighbor(direction);
        if (destination == null) return;
        for (int slot = 0; slot < RESOURCE_SLOTS; slot++) {
            ItemStack remainder = inventory.extractItem(slot, 64, false);
            if (remainder.isEmpty()) continue;
            for (int targetSlot = 0; targetSlot < destination.getSlots() && !remainder.isEmpty(); targetSlot++) {
                remainder = destination.insertItem(targetSlot, remainder, false);
            }
            if (!remainder.isEmpty()) inventory.insertItem(slot, remainder, false);
            setChanged();
        }
    }
    private void pullTemplate(Direction direction) {
        if (!inventory.getStackInSlot(TEMPLATE_SLOT).isEmpty()) return;
        IItemHandler source = neighbor(direction);
        if (source == null) return;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            if (!ModContent.isTemplate(source.getStackInSlot(slot))) continue;
            ItemStack extracted = source.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                inventory.setStackInSlot(TEMPLATE_SLOT, extracted);
                return;
            }
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putByteArray("io_modes", ioModes());
        tag.putInt("progress", progress);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("io_modes")) applyIoModes(tag.getByteArray("io_modes"));
        progress = tag.getInt("progress");
    }
    @Override public Component getDisplayName() { return Component.translatable("block.infinitecore." + tier().id() + "_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new GeneratorMenu(id, inventory, this); }
}
