package local.lostworld;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The archive compiles shared discoveries; it never invents an undiscovered destination. */
public final class HeritageArchive {
    private static final String BOOK_MARKER="lostworld_heritage_archive";
    private HeritageArchive() {}
    @SubscribeEvent
    public static void selectWhileSneaking(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand()!=InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || event.getItemStack().getItem() instanceof BlockItem
                || !event.getLevel().getBlockState(event.getPos()).is(WorkshopContent.ARCHIVE.get())) return;
        // Minecraft normally bypasses block use while sneaking with an item; maps/books
        // must still be able to select a destination. Building blocks retain placement.
        if (event.getEntity() instanceof ServerPlayer player) use(player);
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
    public static void use(ServerPlayer player) {
        HeritageWorldData world=HeritageWorldData.get(player.serverLevel());
        if (!HeritageEvents.enabled(player) || !world.ready()) {
            message(player,"档案台需要至少两类调查成果。目前已汇集 "+Integer.bitCount(world.routes())+" / 3 类。");return;
        }
        HeritageEvents.mark(player,"archive_craft");
        var sites=world.sites();
        var personal=HeritageEvents.data(player);
        int selected=sites.isEmpty()?0:Math.floorMod(personal.getInt("map_selection"),sites.size());
        if (player.isShiftKeyDown()) {
            if (sites.isEmpty()) { message(player,"尚无已发现地点。调查遗迹后，档案会记录每类地点的首个坐标。");return; }
            selected=personal.getBoolean("selection_started")?(selected+1)%sites.size():selected;
            personal.putBoolean("selection_started",true);personal.putInt("map_selection",selected);
            var site=sites.get(selected);
            message(player,"已选择 "+(selected+1)+" / "+sites.size()+"："+name(site.structure())+"，"+coordinates(site)
                    +"。主手持空地图右键可绘制。");return;
        }
        if (player.getMainHandItem().is(Items.MAP)) {
            if (sites.isEmpty()) { message(player,"尚无已发现地点可供制图。");return; }
            var site=sites.get(selected);
            ServerLevel level=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(site.dimension())));
            if (level==null) { message(player,"这份记录的维度目前不可访问，可先查阅档案书中的坐标。");return; }
            ItemStack map=MapItem.create(level,site.pos().getX(),site.pos().getZ(),(byte)2,true,true);
            MapItem.renderBiomePreviewMap(level,map);
            MapItemSavedData.addTargetDecoration(map,site.pos(),"heritage",MapDecorationTypes.TARGET_X);
            map.set(DataComponents.CUSTOM_NAME,Component.literal(name(site.structure())+" · 归档地图"));
            map.set(DataComponents.LORE,new ItemLore(List.of(Component.literal(coordinates(site)))));
            player.getMainHandItem().shrink(1);give(player,map);
            message(player,"已绘制 "+name(site.structure())+" 的地图。潜行右键可切换其他已发现地点。");return;
        }
        int existing=archiveSlot(player);
        if (existing<0 && personal.getBoolean("book_received")) {
            int blank=blankBookSlot(player);
            if (blank<0) { message(player,"把已有遗产档案书带在身上即可更新；若已遗失，带一本普通书补领。");return; }
            player.getInventory().getItem(blank).shrink(1);
        }
        ItemStack book=book(world);
        if (existing>=0) player.getInventory().setItem(existing,book);else give(player,book);
        personal.putBoolean("book_received",true);
        HeritageEvents.mark(player,"archive_open");
        message(player,"遗产档案已整理。书中记录共享成果与已发现地点；潜行右键选择地点，手持空地图可制图。");
    }
    private static ItemStack book(HeritageWorldData world) {
        List<Filterable<Component>> pages=new ArrayList<>();
        int mask=world.routes();
        pages.add(page("遗产调查档案\n\n工程："+((mask&1)!=0?"已汇集":"尚待调查")+"\n奥术："+((mask&2)!=0?"已汇集":"尚待调查")
                +"\n远征："+((mask&4)!=0?"已汇集":"尚待调查")+"\n\n任意两类成果已足以建立共同档案。第三条路线仍可自由继续。"));
        if ((mask&1)!=0) pages.add(page("工程记录\n\n热源、黄铜、机械手与流体加工，让旧工坊具备了更完整的生产能力。精密构件沿用原生装配工艺；取得成果，也意味着掌握了可继续扩展的工具。"));
        if ((mask&2)!=0) pages.add(page("奥术记录\n\n亲自施法与仪式召唤，是两种可以选择的研究实践。记录保留方法差异，成果共同进入档案。它们曾与机械一样，服务于同一个文明的求知与生活。"));
        if ((mask&4)!=0) pages.add(page("远征记录\n\n到达遗迹、了解危险、完成一次挑战，为记录留下了可追溯的地点。记录保留了地点与遭遇；它们之间的联系，还需要更完整的史料来解释。"));
        for (var site:world.sites()) pages.add(page(name(site.structure())+"\n\n"+coordinates(site)+"\n\n记录该类遗迹的首个发现地点。回到档案台，可将它绘成地图。"));
        pages.add(page("继续整理\n\n带着这本书再次使用档案台，可更新共同的发现。\n\n这里尚有空白的篇页。你可以继续远征，或回到家园，把已掌握的工艺变成日常生活的一部分。"));
        ItemStack book=new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("遗产调查档案"),"聚落档案",0,pages,true));
        CompoundTag tag=new CompoundTag();tag.putBoolean(BOOK_MARKER,true);
        book.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        return book;
    }
    private static Filterable<Component> page(String text) { return Filterable.passThrough(Component.literal(text)); }
    private static int archiveSlot(ServerPlayer player) {
        for (int i=0;i<player.getInventory().getContainerSize();i++) {
            ItemStack stack=player.getInventory().getItem(i);
            CustomData tag=stack.get(DataComponents.CUSTOM_DATA);
            if (stack.is(Items.WRITTEN_BOOK) && tag!=null && tag.copyTag().getBoolean(BOOK_MARKER)) return i;
        }
        return -1;
    }
    private static int blankBookSlot(ServerPlayer player) {
        for (int i=0;i<player.getInventory().getContainerSize();i++) if (player.getInventory().getItem(i).is(Items.BOOK)) return i;
        return -1;
    }
    private static String coordinates(HeritageWorldData.Site site) {
        String dimension=switch (site.dimension()) {
            case "minecraft:overworld"->"主世界";case "minecraft:the_nether"->"下界";case "minecraft:the_end"->"末地";default->site.dimension();
        };
        return dimension+"  X "+site.pos().getX()+"，Y "+site.pos().getY()+"，Z "+site.pos().getZ();
    }
    private static String name(String id) {
        if (id.startsWith("explorify:watchtower/")) return "瞭望塔";
        if (id.startsWith("explorify:supply_cache/")) return "补给藏点";
        if (id.startsWith("explorify:guide_post_")) return "野外路标";
        return switch (id) {
            case "mowziesmobs:wrought_chamber"->"钢铁守护者地下房间";
            case "confluence:enchanted_sword_shrine"->"附魔剑冢";
            case "cataclysm:desert_site"->"沙漠遗址";
            case "cataclysm:ancient_factory"->"古代工厂";
            case "explorify:campsite"->"露营地";case "explorify:farmstead"->"农庄";case "explorify:ruins"->"野外废墟";
            default->"远征遗迹";
        };
    }
    private static void give(ServerPlayer player,ItemStack stack) { if (!player.getInventory().add(stack)) player.drop(stack,false); }
    private static void message(ServerPlayer player,String text) { player.displayClientMessage(Component.literal(text),false); }
}
