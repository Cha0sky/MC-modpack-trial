package com.evolvingblade.registry;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.item.StarEaterItem;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册：噬星之刃本体。
 */
public final class EBItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EvolvingBlade.MODID);

    /** {@code evolvingblade:star_eater} —— 噬星之刃。 */
    public static final DeferredItem<StarEaterItem> STAR_EATER =
            ITEMS.register(StarEaterItem.ITEM_ID, () -> new StarEaterItem(StarEaterItem.createProperties()));

    private EBItems() {}

    /** 供配方/命令使用的默认实例。 */
    public static Item starEater() {
        return STAR_EATER.get();
    }
}
