package com.infinite.core.registry;

import com.infinite.core.InfiniteCore;
import com.infinite.core.block.GeneratorBlock;
import com.infinite.core.block.GeneratorBlockEntity;
import com.infinite.core.gen.GeneratorTier;
import com.infinite.core.item.TemplateItem;
import com.infinite.core.menu.GeneratorMenu;
import com.infinite.core.recipe.TemplateCustomizeRecipe;
import java.util.EnumMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(InfiniteCore.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InfiniteCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, InfiniteCore.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, InfiniteCore.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, InfiniteCore.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, InfiniteCore.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, InfiniteCore.MODID);

    public static final EnumMap<GeneratorTier, DeferredBlock<GeneratorBlock>> GENERATOR_BLOCKS = new EnumMap<>(GeneratorTier.class);
    public static final EnumMap<GeneratorTier, DeferredItem<BlockItem>> GENERATORS = new EnumMap<>(GeneratorTier.class);
    public static final EnumMap<GeneratorTier, DeferredItem<TemplateItem>> TEMPLATES = new EnumMap<>(GeneratorTier.class);
    // Compatibility placeholders only. Never accepted as operating templates or displayed in the tab.
    private static final EnumMap<GeneratorTier, DeferredItem<Item>> LEGACY_BLANKS = new EnumMap<>(GeneratorTier.class);

    static {
        for (GeneratorTier tier : GeneratorTier.values()) {
            GENERATOR_BLOCKS.put(tier, BLOCKS.register(tier.id() + "_generator", () -> new GeneratorBlock(tier,
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F).sound(SoundType.STONE).requiresCorrectToolForDrops())));
            GENERATORS.put(tier, ITEMS.register(tier.id() + "_generator", () -> new BlockItem(GENERATOR_BLOCKS.get(tier).get(), new Item.Properties())));
            TEMPLATES.put(tier, ITEMS.register("template_" + tier.id(), () -> new TemplateItem(new Item.Properties())));
            LEGACY_BLANKS.put(tier, ITEMS.register("blank_template_" + tier.id(), () -> new Item(new Item.Properties())));
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> TARGET_ITEM = COMPONENTS.register("target_item",
            () -> DataComponentType.<ItemStack>builder().persistent(ItemStack.OPTIONAL_CODEC)
                    .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC).build());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_ENTITY = ENTITIES.register("generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new,
                    GENERATOR_BLOCKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU = MENUS.register("generator",
            () -> IMenuTypeExtension.create(GeneratorMenu::fromNetwork));
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<TemplateCustomizeRecipe>> TEMPLATE_CUSTOMIZE = RECIPES.register("template_customize",
            () -> new SimpleCraftingRecipeSerializer<>(TemplateCustomizeRecipe::new));

    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("infinite_core",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.infinitecore"))
                    .icon(() -> new ItemStack(GENERATORS.get(GeneratorTier.NETHERITE).get()))
                    .displayItems((parameters, output) -> {
                        for (GeneratorTier tier : GeneratorTier.values()) output.accept(TEMPLATES.get(tier).get());
                        for (GeneratorTier tier : GeneratorTier.values()) output.accept(GENERATORS.get(tier).get());
                    }).build());

    private ModContent() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        COMPONENTS.register(bus);
        RECIPES.register(bus);
        TABS.register(bus);
    }

    public static GeneratorTier templateTier(ItemStack stack) {
        for (GeneratorTier tier : GeneratorTier.values()) if (stack.is(TEMPLATES.get(tier).get())) return tier;
        return null;
    }
    public static boolean isTemplate(ItemStack stack) { return !stack.isEmpty() && templateTier(stack) != null; }
}
