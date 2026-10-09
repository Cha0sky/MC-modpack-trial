package local.stargarden;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

@Mod(StarGardenSanctuary.ID)
public final class StarGardenSanctuary {
    public static final String ID = "stargarden_sanctuary";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredBlock<WardBlock> WARD = BLOCKS.register("sanctuary_ward",
        () -> new WardBlock(BlockBehaviour.Properties.of().strength(2.0F).lightLevel(state -> 12)));
    public static final DeferredItem<BlockItem> WARD_ITEM = ITEMS.register("sanctuary_ward",
        () -> new BlockItem(WARD.get(), new Item.Properties()) {
            @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                    List<Component> tooltip, TooltipFlag flag) {
                super.appendHoverText(stack, context, tooltip, flag);
                tooltip.add(Component.translatable("tooltip.stargarden_sanctuary.ward_1"));
                tooltip.add(Component.translatable("tooltip.stargarden_sanctuary.ward_2"));
                tooltip.add(Component.translatable("tooltip.stargarden_sanctuary.ward_3"));
            }
        });
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RADIUS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        RADIUS = b.comment("庇护石水平保护半径，覆盖该范围的全部高度；多块可重叠扩建。")
            .defineInRange("ward_radius", 48, 8, 256);
        SPEC = b.build();
    }

    public StarGardenSanctuary(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        bus.addListener(StarGardenSanctuary::creativeTab);
        NeoForge.EVENT_BUS.addListener(WardProtection::join);
        NeoForge.EVENT_BUS.addListener(WardProtection::tick);
        NeoForge.EVENT_BUS.addListener(WardProtection::incomingDamage);
        NeoForge.EVENT_BUS.addListener(WardProtection::effect);
        NeoForge.EVENT_BUS.addListener(WardProtection::target);
        NeoForge.EVENT_BUS.addListener(WardProtection::grief);
        NeoForge.EVENT_BUS.addListener(WardProtection::explosion);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(WARD_ITEM);
    }

    public static final class WardBlock extends Block {
        public static final MapCodec<WardBlock> CODEC = simpleCodec(WardBlock::new);
        public WardBlock(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }

        @Override protected void onPlace(BlockState state, Level level, BlockPos pos,
                BlockState previous, boolean moved) {
            super.onPlace(state, level, pos, previous, moved);
            if (level instanceof ServerLevel server && !previous.is(this)) WardData.get(server).add(pos);
        }

        @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                BlockState next, boolean moved) {
            if (level instanceof ServerLevel server && !next.is(this)) WardData.get(server).remove(pos);
            super.onRemove(state, level, pos, next, moved);
        }

        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (placer instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("庇护石已启用：水平半径 " + RADIUS.get()
                    + " 格，覆盖全部高度。可用多块扩建；拆除后对应区域解除保护。"), false);
            }
        }

        @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
                BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("庇护范围：距庇护石水平 "
                + RADIUS.get() + " 格以内。右键只查看信息；拆除这块石头才会解除它的保护。"), false);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
}
