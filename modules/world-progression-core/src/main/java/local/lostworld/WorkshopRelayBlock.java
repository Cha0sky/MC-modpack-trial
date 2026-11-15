package local.lostworld;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class WorkshopRelayBlock extends HorizontalDirectionalBlock {
    public enum Kind implements StringRepresentable {
        POWER("power","动力"),DIRECTION("direction","传动"),SIGNAL("signal","信号");
        private final String id,label;
        Kind(String id,String label) { this.id=id;this.label=label; }
        @Override public String getSerializedName() { return id; }
        public String label() { return label; }
    }
    public static final EnumProperty<Kind> KIND=EnumProperty.create("kind",Kind.class);
    public static final BooleanProperty CALIBRATED=BooleanProperty.create("calibrated");
    public static final MapCodec<WorkshopRelayBlock> CODEC=simpleCodec(WorkshopRelayBlock::new);
    public WorkshopRelayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)
                .setValue(KIND,Kind.POWER).setValue(CALIBRATED,false));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(FACING,KIND,CALIBRATED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        return stack.is(WorkshopContent.TUNER.get())?ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION:
                ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,
            Player player,BlockHitResult hit) {
        if (player instanceof ServerPlayer server) WorkshopInteraction.describe(server,pos,state);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
