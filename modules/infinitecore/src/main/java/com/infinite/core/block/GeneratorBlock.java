package com.infinite.core.block;

import com.infinite.core.gen.GeneratorTier;
import com.infinite.core.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** No new state properties, loot rules or external item capability. */
public final class GeneratorBlock extends Block implements EntityBlock {
    private final GeneratorTier tier;
    public GeneratorBlock(GeneratorTier tier, Properties properties) { super(properties); this.tier = tier; }
    public GeneratorTier tier() { return tier; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GeneratorBlockEntity(pos, state); }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModContent.GENERATOR_ENTITY.get()) return null;
        return (world, pos, blockState, entity) -> {
            if (entity instanceof GeneratorBlockEntity generator) generator.tickServer();
        };
    }
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator ? generator : null;
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            MenuProvider provider = getMenuProvider(state, level, pos);
            if (provider != null) player.openMenu(provider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
