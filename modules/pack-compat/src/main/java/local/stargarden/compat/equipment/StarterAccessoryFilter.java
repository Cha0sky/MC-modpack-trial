package local.stargarden.compat.equipment;

import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class StarterAccessoryFilter implements ItemFilterAdapter {
    private static final TagKey<Item> STARTERS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("stargarden_pack_compat", "starter_accessories"));

    @Override public String getName() { return "stargarden_starter_accessories"; }

    @Override
    public boolean isFilterStack(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(getName()) && data.copyTag().getBoolean(getName());
    }

    @Override
    public boolean doesItemMatch(ItemStack filter, ItemStack candidate, HolderLookup.Provider registries) {
        return candidate.is(STARTERS);
    }

    @Override
    public Matcher getMatcher(ItemStack filter, HolderLookup.Provider registries) {
        return candidate -> candidate.is(STARTERS);
    }

    @Override public ItemStack makeTagFilterStack(TagKey<Item> tag) { return ItemStack.EMPTY; }
}
