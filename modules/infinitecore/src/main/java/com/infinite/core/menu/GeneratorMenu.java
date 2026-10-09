package com.infinite.core.menu;

import com.infinite.core.block.GeneratorBlockEntity;
import com.infinite.core.block.IoMode;
import com.infinite.core.gen.GeneratorTier;
import com.infinite.core.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class GeneratorMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final GeneratorBlockEntity generator;
    private final ItemStackHandler inventory;
    // progress, actual interval, status, packed modes, neighbor capability bits, machine tier
    private final ContainerData data = new SimpleContainerData(6);
    private boolean ioPage;

    public GeneratorMenu(int id, Inventory playerInventory, GeneratorBlockEntity generator) {
        this(id, playerInventory, generator.getBlockPos(), generator);
    }
    private GeneratorMenu(int id, Inventory playerInventory, BlockPos pos, GeneratorBlockEntity generator) {
        super(ModContent.GENERATOR_MENU.get(), id);
        this.pos = pos.immutable();
        this.generator = generator;
        this.inventory = generator == null ? new ItemStackHandler(10) : generator.inventory();
        // Stable indices 0..8 resource, 9 template, 10..45 player inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(machineSlot(row * 3 + col, 12 + col * 18, 54 + row * 18, false));
            }
        }
        addSlot(machineSlot(9, 12, 26, true));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(playerSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 134 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(playerSlot(playerInventory, col, 8 + col * 18, 192));
        updateData();
        addDataSlots(data);
    }
    public static GeneratorMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        return new GeneratorMenu(id, playerInventory, buffer.readBlockPos(), null);
    }
    private Slot machineSlot(int index, int x, int y, boolean template) {
        return new SlotItemHandler(inventory, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return template ? ModContent.isTemplate(stack) : super.mayPlace(stack); }
            @Override public boolean isActive() { return !ioPage; }
        };
    }
    private Slot playerSlot(Inventory playerInventory, int index, int x, int y) {
        return new Slot(playerInventory, index, x, y) {
            @Override public boolean isActive() { return !ioPage; }
        };
    }
    private void updateData() {
        if (generator == null || generator.getLevel() == null || generator.getLevel().isClientSide) return;
        boolean initial = data.get(1) == 0;
        data.set(0, generator.progress());
        data.set(1, generator.interval());
        data.set(2, inventory.getStackInSlot(9).isEmpty() ? 0 : generator.hasRoomForTarget() ? 1 : 2);
        int modes = 0;
        for (int face = 0; face < 6; face++) modes |= generator.ioMode(face).id() << (face * 2);
        data.set(3, modes);
        // Only query capabilities while a menu is open; not every idle machine tick.
        if (initial || (generator.getLevel().getGameTime() % 10) == 0) data.set(4, generator.neighborCapabilityMask());
        data.set(5, generator.tier().ordinal());
    }
    @Override public void broadcastChanges() { updateData(); super.broadcastChanges(); }
    public BlockPos pos() { return pos; }
    public GeneratorBlockEntity generator() { return generator; }
    public void setIoPage(boolean value) { ioPage = value; }
    public boolean ioPage() { return ioPage; }
    public int progress() { return data.get(0); }
    public boolean ready() { return data.get(1) > 0; }
    public int interval() { return Math.max(1, data.get(1)); }
    public int status() { return data.get(2); }
    public IoMode ioMode(int face) { return IoMode.fromId((data.get(3) >> (face * 2)) & 3); }
    public boolean neighborHasItems(int face) { return (data.get(4) & (1 << face)) != 0; }
    public GeneratorTier tier() { return GeneratorTier.values()[Math.clamp(data.get(5), 0, 7)]; }
    public ItemStack template() { return inventory.getStackInSlot(9); }
    public ItemStack target() {
        if (template().isEmpty()) return ItemStack.EMPTY;
        ItemStack stored = template().get(ModContent.TARGET_ITEM.get());
        return stored != null && !stored.isEmpty() ? stored.copyWithCount(1) : new ItemStack(tier().output());
    }
    public int occupiedSlots() {
        int count = 0;
        for (int i = 0; i < 9; i++) if (!inventory.getStackInSlot(i).isEmpty()) count++;
        return count;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack current = slot.getItem();
        ItemStack original = current.copy();
        boolean moved;
        if (index < 10) moved = moveItemStackTo(current, 10, 46, true);
        else {
            moved = ModContent.isTemplate(current) && moveItemStackTo(current, 9, 10, false);
            if (!current.isEmpty()) moved = moveItemStackTo(current, 0, 9, false) || moved;
        }
        if (!moved || current.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, current);
        return original;
    }
    @Override public boolean stillValid(Player player) {
        return generator == null ? player.level().isClientSide
                : player.level().getBlockEntity(pos) == generator
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
