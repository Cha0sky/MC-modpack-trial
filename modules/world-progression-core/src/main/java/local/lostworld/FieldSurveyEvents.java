package local.lostworld;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class FieldSurveyEvents {
    private static final String MEAL_UNTIL = "nether_meal_until";
    private static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID, "survey_ruins"));

    private FieldSurveyEvents() {}

    @SubscribeEvent
    public static void finishMeal(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == Level.NETHER
                && event.getItem().is(WorkshopContent.SEALED_RATION.get())) {
            WorkshopEvents.data(player).putLong(MEAL_UNTIL, player.level().getGameTime() + 90 * 20);
        }
    }

    @SubscribeEvent
    public static void netherPatrol(LivingDropsEvent event) {
        var mob = event.getEntity();
        if (mob.level().dimension() != Level.NETHER ||
                (mob.getType() != EntityType.BLAZE && mob.getType() != EntityType.MAGMA_CUBE)) return;
        if (mob.getKillCredit() instanceof ServerPlayer player && player.level().dimension() == Level.NETHER
                && player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && WorkshopEvents.data(player).getLong(MEAL_UNTIL) > player.level().getGameTime()) {
            ChapterEvents.award(player, "world_turn/vanguard");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void investigate(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isSpectator()) return;
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        boolean lantern = level.dimension() == Level.NETHER && event.getItemStack().is(WorkshopContent.TUNER.get())
                && isLantern(level, pos);
        boolean notebook = event.getEntity().isShiftKeyDown() && event.getItemStack().is(Items.FEATHER)
                && event.getEntity().getOffhandItem().is(Items.PAPER);
        if (!lantern && !notebook) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (lantern) markRoute(player, pos);
            else recordRuins(player, pos);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean isLantern(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LANTERN) || level.getBlockState(pos).is(Blocks.SOUL_LANTERN);
    }

    private static void markRoute(ServerPlayer player, BlockPos pos) {
        CompoundTag progress = WorkshopEvents.data(player);
        Level level = player.level();
        BlockPos first = BlockPos.of(progress.getLong("beacon_start"));
        // A distant new lantern starts a fresh route without loading the old site.
        if (!progress.contains("beacon_start") || pos.equals(first) || pos.distSqr(first) > 64 * 64
                || !level.isLoaded(first) || !isLantern(level, first)) {
            progress.putLong("beacon_start", pos.asLong());
            progress.remove("beacon_middle");
            say(player, "起点已标记。沿退路走 12～32 格，点第二盏灯。");
            return;
        }
        if (!progress.contains("beacon_middle")) {
            if (validLeg(first, pos)) {
                progress.putLong("beacon_middle", pos.asLong());
                say(player, "中途灯已标记。再走 12～32 格，终点离起点至少 24 格。");
            } else say(player, "下一盏灯需离起点 12～32 格。");
            return;
        }
        BlockPos middle = BlockPos.of(progress.getLong("beacon_middle"));
        if (!level.isLoaded(middle) || !isLantern(level, middle)) {
            progress.remove("beacon_middle");
            say(player, "中途灯缺失，请补灯后重新标记。");
            return;
        }
        if (validLeg(middle, pos) && first.distSqr(pos) >= 24 * 24) {
            ChapterEvents.award(player, "world_turn/beacons");
            progress.remove("beacon_start");
            progress.remove("beacon_middle");
            say(player, "三盏灯已连成退路。");
        } else say(player, "终点距中途灯 12～32 格，距起点至少 24 格。重标起点可另选路线。");
    }

    private static boolean validLeg(BlockPos from, BlockPos to) {
        double distance = from.distSqr(to);
        return distance >= 12 * 12 && distance <= 32 * 32;
    }

    private static void recordRuins(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        var ruin = level.structureManager().getStructureWithPieceAt(pos, RUINS);
        if (!ruin.isValid()) {
            say(player, "走进地下墓穴或林地府邸，在遗址内执笔记录。");
            return;
        }
        var id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(ruin.getStructure());
        String site = id != null && id.getNamespace().equals("irons_spellbooks") ? "地下墓穴" : "林地府邸";
        ItemStack note = new ItemStack(Items.PAPER);
        note.set(DataComponents.CUSTOM_NAME, Component.literal(site + " · 勘察手记"));
        note.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(pos.toShortString()))));
        player.getOffhandItem().consume(1, player);
        player.getInventory().placeItemBackInInventory(note);
        ChapterEvents.award(player, "relic_expeditions/notebook");
        say(player, "已记下" + site + "的勘察坐标。");
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
