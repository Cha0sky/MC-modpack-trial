package local.lostworld;

import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorkshopContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(LostWorld.ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(LostWorld.ID);
    public static final DeferredItem<Item> FIBER=ITEMS.registerSimpleItem("tainted_fiber");
    public static final DeferredItem<Item> BINDER=ITEMS.registerSimpleItem("resonant_binder");
    public static final DeferredItem<WorkshopTunerItem> TUNER=ITEMS.register("rough_tuner",
            ()->new WorkshopTunerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HEAT_SAMPLE = ITEMS.registerSimpleItem("thermal_binder");
    public static final DeferredItem<Item> SEALED_RATION = ITEMS.register("sealed_ration", () ->
            new Item(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(8).saturationModifier(0.6F).alwaysEdible()
                    .effect(() -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 90 * 20), 1.0F).build())));
    public static final DeferredItem<Item> COBALT_CHARGE = ITEMS.registerSimpleItem("cobalt_furnace_charge");
    public static final DeferredItem<Item> PALLADIUM_CHARGE = ITEMS.registerSimpleItem("palladium_furnace_charge");
    public static final DeferredItem<SupplyCrateItem> TRAIL_SUPPLY_CRATE = ITEMS.register("trail_supply_crate",
            () -> new SupplyCrateItem(() -> List.of(
                    new ItemStack(SEALED_RATION.get(), 2), new ItemStack(Items.TORCH, 8))));
    public static final DeferredItem<SupplyCrateItem> RESCUE_SUPPLY_CRATE = ITEMS.register("rescue_supply_crate",
            () -> new SupplyCrateItem(() -> List.of(
                    new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.SCAFFOLDING, 6))));
    // Only the four anchored devices are protected; the authored building stays editable.
    public static final DeferredBlock<WorkshopRelayBlock> RELAY=BLOCKS.register("workshop_relay",
            ()->new WorkshopRelayBlock(BlockBehaviour.Properties.of().strength(-1F,3600000F)
                    .sound(SoundType.COPPER).noLootTable().lightLevel(s->s.getValue(WorkshopRelayBlock.CALIBRATED)?8:0)));
    public static final DeferredBlock<WorkshopConsoleBlock> CONSOLE=BLOCKS.register("workshop_console",
            ()->new WorkshopConsoleBlock(BlockBehaviour.Properties.of().strength(-1F,3600000F)
                    .sound(SoundType.COPPER).noLootTable().lightLevel(s->s.getValue(WorkshopConsoleBlock.ACTIVE)?12:0)));
    public static final DeferredItem<BlockItem> RELAY_ITEM=ITEMS.registerSimpleBlockItem(RELAY);
    public static final DeferredItem<BlockItem> CONSOLE_ITEM=ITEMS.registerSimpleBlockItem(CONSOLE);
    public static final DeferredBlock<HeritageArchiveBlock> ARCHIVE=BLOCKS.register("heritage_archive",
            ()->new HeritageArchiveBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));
    public static final DeferredItem<BlockItem> ARCHIVE_ITEM=ITEMS.registerSimpleBlockItem(ARCHIVE);
    private WorkshopContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus); }
}
