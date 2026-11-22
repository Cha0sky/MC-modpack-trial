package local.stargarden.compat.equipment;

import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public final class WornInventory {
    // FTB builds one summary on the server thread before checking the player's tasks.
    private static final Map<Item, List<ItemStack>> BY_ITEM = new IdentityHashMap<>();
    private static final List<ItemStack> ALL = new ArrayList<>();

    private WornInventory() {}

    public static void build(ServerPlayer player) {
        BY_ITEM.clear();
        ALL.clear();
        player.getInventory().armor.forEach(WornInventory::add);
        player.getInventory().offhand.forEach(WornInventory::add);
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var slots = curios.getEquippedCurios();
            for (int slot = 0; slot < slots.getSlots(); slot++) add(slots.getStackInSlot(slot));
        });
    }

    private static void add(ItemStack stack) {
        if (stack.isEmpty()) return;
        ALL.add(stack);
        BY_ITEM.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(stack);
    }

    public static Collection<ItemStack> includeWorn(ItemTask task, Collection<ItemStack> inventory) {
        boolean starter = task.id == EquipmentPages.STARTER_POSSESSION;
        if (!starter && !EquipmentPages.includes(task.getQuest().getChapter())) return inventory;
        List<ItemStack> worn = starter ? ALL : BY_ITEM.get(task.getItemStack().getItem());
        if (worn == null || worn.isEmpty()) return inventory;
        if (inventory.isEmpty()) return worn;
        List<ItemStack> combined = new ArrayList<>(inventory);
        combined.addAll(worn);
        return combined;
    }
}
