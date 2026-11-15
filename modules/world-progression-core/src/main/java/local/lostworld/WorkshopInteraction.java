package local.lostworld;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class WorkshopInteraction {
    public static final TagKey<Structure> SITES=TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(LostWorld.ID,"workshop_sites"));
    private WorkshopInteraction() {}
    public static StructureStart structure(ServerLevel level,BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos,SITES);
    }
    public static float speed(ServerLevel level,BlockPos pos,BlockState state) {
        Direction face=state.getValue(WorkshopRelayBlock.FACING);
        BlockPos input=pos.relative(face);
        if (!level.hasChunkAt(input)) return 0;
        BlockState shaft=level.getBlockState(input);
        if (!(shaft.getBlock() instanceof IRotate rotate)
                || !rotate.hasShaftTowards(level,input,shaft,face.getOpposite())) return 0;
        return level.getBlockEntity(input) instanceof KineticBlockEntity kinetic?kinetic.getSpeed():0;
    }
    public static String fault(ServerLevel level,BlockPos pos,BlockState state) {
        float rpm=speed(level,pos,state);
        if (Math.abs(rpm)<8) return "正面接口没有足够动力（当前 "+(int)rpm+" RPM，至少需要 8 RPM）。请将传动轴端对准接口。";
        if (state.getValue(WorkshopRelayBlock.KIND)==WorkshopRelayBlock.Kind.DIRECTION && rpm<0)
            return "传动方向相反（"+(int)rpm+" RPM）。改变齿轮啮合，使接口显示正转。";
        return null;
    }
    public static void describe(ServerPlayer player,BlockPos pos,BlockState state) {
        if (!WorkshopEvents.enabled(player)) { message(player,"先完成史莱姆王的挑战，再调查这些设备。");return; }
        String name=state.getValue(WorkshopRelayBlock.KIND).label();
        String fault=fault(player.serverLevel(),pos,state);
        message(player,name+"继电器："+(fault!=null?fault:state.getValue(WorkshopRelayBlock.CALIBRATED)?
                "动力正常，已经校准。":"动力正常；携带 1 份共振胶，用调谐器持续校准 3 秒。"));
    }
    public static boolean ready(ServerPlayer player,BlockPos pos,BlockState state,boolean explain) {
        String error=null;
        if (!WorkshopEvents.enabled(player)) error="史莱姆王的阻隔尚未解除。";
        else if (!state.is(WorkshopContent.RELAY.get())) error="请对准工坊继电器。";
        else if (!structure(player.serverLevel(),pos).isValid()) error="这里不是地图标记的古代工坊设备。";
        else if (state.getValue(WorkshopRelayBlock.CALIBRATED)) error="这处设备已经校准，不会再次消耗材料。";
        else if ((error=fault(player.serverLevel(),pos,state))==null && binderSlot(player)<0) error="需要携带 1 份共振胶。";
        if (error!=null && explain) message(player,error);
        return error==null;
    }
    public static void calibrate(ServerPlayer player,BlockPos pos) {
        ServerLevel level=player.serverLevel();BlockState state=level.getBlockState(pos);
        if (!ready(player,pos,state,true)) return;
        int slot=binderSlot(player);
        player.getInventory().getItem(slot).shrink(1);
        WorkshopEvents.addSpent(player,"world_progression_core:resonant_binder",1);
        level.setBlock(pos,state.setValue(WorkshopRelayBlock.CALIBRATED,true),3);
        level.playSound(null,pos,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,0.6F,1.3F);
        message(player,state.getValue(WorkshopRelayBlock.KIND).label()+"节点已校准。其余节点就绪后，操作中央控制台。");
        var relays=relays(level,pos);
        if (relays.size()==3 && relays.values().stream().allMatch(p->level.getBlockState(p).getValue(WorkshopRelayBlock.CALIBRATED)))
            for (ServerPlayer nearby:level.players()) if (nearby.blockPosition().distSqr(pos)<64*64) WorkshopEvents.mark(nearby,"calibrate");
    }
    private static int binderSlot(ServerPlayer player) {
        for (int i=0;i<player.getInventory().getContainerSize();i++)
            if (player.getInventory().getItem(i).is(WorkshopContent.BINDER.get())) return i;
        return -1;
    }
    private static EnumMap<WorkshopRelayBlock.Kind,BlockPos> relays(ServerLevel level,BlockPos pos) {
        var result=new EnumMap<WorkshopRelayBlock.Kind,BlockPos>(WorkshopRelayBlock.Kind.class);
        StructureStart start=structure(level,pos);
        if (!start.isValid()) return result;
        var box=start.getBoundingBox();
        for (BlockPos point:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
            if (!level.hasChunkAt(point)) continue;
            BlockState state=level.getBlockState(point);
            if (state.is(WorkshopContent.RELAY.get())) result.put(state.getValue(WorkshopRelayBlock.KIND),point.immutable());
        }
        return result;
    }
    public static void restore(ServerPlayer player,BlockPos pos,BlockState state) {
        if (!WorkshopEvents.enabled(player)) { message(player,"先完成史莱姆王的挑战，再调查旧工坊。");return; }
        ServerLevel level=player.serverLevel();
        if (state.getValue(WorkshopConsoleBlock.ACTIVE)) {
            message(player,"维修簿：这里曾为边境村镇修理农具与运送零件。新的遗产调查，将从这些普通工匠留下的记录开始。");
            giveRecord(player);return;
        }
        var relays=relays(level,pos);
        if (relays.size()!=3) { message(player,"请先走近并调查工坊的三处设备，确保它们所在的房间已经加载。");return; }
        for (var entry:relays.entrySet()) {
            BlockState relay=level.getBlockState(entry.getValue());
            if (!relay.getValue(WorkshopRelayBlock.CALIBRATED)) { message(player,entry.getKey().label()+"节点尚未校准。");return; }
            String fault=fault(level,entry.getValue(),relay);
            if (fault!=null) { message(player,entry.getKey().label()+"节点："+fault);return; }
        }
        level.setBlock(pos,state.setValue(WorkshopConsoleBlock.ACTIVE,true),3);
        WorkshopWorldData.get(level).restore(pos);
        WorkshopEvents.syncRestored(level);
        giveRecord(player);
        level.playSound(null,pos,SoundEvents.BEACON_POWER_SELECT,SoundSource.BLOCKS,1F,0.8F);
        for (ServerPlayer nearby:level.players()) if (nearby.blockPosition().distSqr(pos)<64*64)
            nearby.sendSystemMessage(Component.literal("旧工坊重新亮起。保存下来的工艺记录，正等待被带回家园。可在任务书领取工程补给。"));
    }
    private static void giveRecord(ServerPlayer player) {
        var data=WorkshopEvents.data(player);
        if (data.getBoolean("record_received")) return;
        ItemStack book=new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("边境工坊维修簿"),
                "旧工坊的工匠",0,List.of(
                Filterable.passThrough(Component.literal("这里曾为边境村镇修理农具，制作水轮零件。维修簿上最常出现的，并非武器，而是轴承、犁头与磨盘。")),
                Filterable.passThrough(Component.literal("最后几页记着：原料仍能取得，但旧设备读数开始偏移。工匠留下了三处校准接口，希望后来者还能让它们重新工作。")),
                Filterable.passThrough(Component.literal("你恢复了一门可用的工艺。记录还提到工业设施、奥术档案与偏远遗址；这些线索将通向下一阶段的遗产调查。"))),true));
        give(player,book);data.putBoolean("record_received",true);
    }
    public static void giveMap(ServerPlayer player) {
        if (!WorkshopEvents.enabled(player)) { message(player,"先完成史莱姆王的挑战。");return; }
        if (player.level().dimension()!=Level.OVERWORLD) { message(player,"请回到主世界勘测工坊。");return; }
        ServerLevel level=player.serverLevel();var data=WorkshopEvents.data(player);
        BlockPos target;
        if (data.contains("map_target")) target=BlockPos.of(data.getLong("map_target"));
        else {
            target=level.findNearestMapStructure(SITES,player.blockPosition(),100,false);
            if (target==null) { message(player,"附近没有找到可定位的工坊。请朝尚未探索的陆地前进，再次勘测。");return; }
            data.putLong("map_target",target.asLong());
        }
        ItemStack map=MapItem.create(level,target.getX(),target.getZ(),(byte)2,true,true);
        MapItem.renderBiomePreviewMap(level,map);
        MapItemSavedData.addTargetDecoration(map,target,"workshop",MapDecorationTypes.TARGET_X);
        map.set(DataComponents.CUSTOM_NAME,Component.literal("废弃工坊勘测图"));
        map.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("旧址坐标：X "+target.getX()+"，Z "+target.getZ()),
                Component.literal("带上调谐器、3 份共振胶，以及水车、水桶、传动杆、齿轮和扳手。"))));
        give(player,map);
        message(player,"工坊已标记在地图上：X "+target.getX()+"，Z "+target.getZ()+"。旧区域不会补建遗迹，可继续探索新区域。");
    }
    private static void give(ServerPlayer player,ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack,false);
    }
    private static void message(ServerPlayer player,String text) { player.displayClientMessage(Component.literal(text),false); }
}
