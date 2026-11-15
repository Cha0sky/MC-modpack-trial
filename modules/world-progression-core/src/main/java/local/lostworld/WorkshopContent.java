package local.lostworld;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
    // Only the four anchored devices are protected; the authored building stays editable.
    public static final DeferredBlock<WorkshopRelayBlock> RELAY=BLOCKS.register("workshop_relay",
            ()->new WorkshopRelayBlock(BlockBehaviour.Properties.of().strength(-1F,3600000F)
                    .sound(SoundType.COPPER).noLootTable().lightLevel(s->s.getValue(WorkshopRelayBlock.CALIBRATED)?8:0)));
    public static final DeferredBlock<WorkshopConsoleBlock> CONSOLE=BLOCKS.register("workshop_console",
            ()->new WorkshopConsoleBlock(BlockBehaviour.Properties.of().strength(-1F,3600000F)
                    .sound(SoundType.COPPER).noLootTable().lightLevel(s->s.getValue(WorkshopConsoleBlock.ACTIVE)?12:0)));
    public static final DeferredItem<BlockItem> RELAY_ITEM=ITEMS.registerSimpleBlockItem(RELAY);
    public static final DeferredItem<BlockItem> CONSOLE_ITEM=ITEMS.registerSimpleBlockItem(CONSOLE);
    private WorkshopContent() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus); }
}
