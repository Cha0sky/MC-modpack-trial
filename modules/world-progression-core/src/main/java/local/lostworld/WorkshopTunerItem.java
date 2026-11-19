package local.lostworld;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

public final class WorkshopTunerItem extends Item {
    private static final String TARGET="workshop_calibrating";
    public WorkshopTunerItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player=context.getPlayer();
        if (player != null && context.getHand() == InteractionHand.MAIN_HAND
                && context.getLevel().dimension() == Level.NETHER
                && context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.MAGMA_BLOCK)) {
            if (player instanceof ServerPlayer server) {
                ItemStack binder = server.getOffhandItem();
                if (!binder.is(WorkshopContent.BINDER.get())) {
                    server.displayClientMessage(Component.literal("副手放 1 份共振胶，再用调谐器对岩浆块取样。"), true);
                    return InteractionResult.FAIL;
                }
                binder.shrink(1);
                WorkshopEvents.addSpent(server, LostWorld.ID + ":resonant_binder", 1);
                ItemStack samples = new ItemStack(WorkshopContent.HEAT_SAMPLE.get(), 2);
                if (!server.getInventory().add(samples)) server.drop(samples, false);
                server.getCooldowns().addCooldown(this, 20);
                ChapterEvents.award(server, "world_turn/thermal_survey");
                server.displayClientMessage(Component.literal("已取得两份热样胶，可以带回工坊压封行军餐。"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (player==null || !context.getLevel().getBlockState(context.getClickedPos()).is(WorkshopContent.RELAY.get())) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server) {
            if (!WorkshopInteraction.ready(server,context.getClickedPos(),context.getLevel().getBlockState(context.getClickedPos()),true)) return InteractionResult.FAIL;
            server.getPersistentData().putLong(TARGET,context.getClickedPos().asLong());
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            server.getPersistentData().remove(TARGET);
            WorkshopInteraction.giveMap(server);
            player.getCooldowns().addCooldown(this,100);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BRUSH; }
    @Override public void onUseTick(Level level,LivingEntity entity,ItemStack stack,int remaining) {
        if (!(entity instanceof ServerPlayer player) || !player.getPersistentData().contains(TARGET)) return;
        BlockPos target=BlockPos.of(player.getPersistentData().getLong(TARGET));
        var hit=player.pick(6,0,false);
        if (!(hit instanceof BlockHitResult block) || !block.getBlockPos().equals(target)
                || !WorkshopInteraction.ready(player,target,level.getBlockState(target),false)) {
            player.getPersistentData().remove(TARGET);player.stopUsingItem();return;
        }
        int elapsed=getUseDuration(stack,entity)-remaining;
        if (elapsed%20==0) player.displayClientMessage(Component.literal("正在校准…… "+Math.min(3,elapsed/20)+" / 3 秒"),true);
        if (elapsed>=60) {
            WorkshopInteraction.calibrate(player,target);
            player.getPersistentData().remove(TARGET);player.stopUsingItem();
        }
    }
    @Override public void releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining) {
        if (entity instanceof ServerPlayer player) player.getPersistentData().remove(TARGET);
    }
}
