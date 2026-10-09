package com.rpgleveling.registry;

import com.rpgleveling.item.ExperienceCrystalItem;
import com.rpgleveling.item.RespecTokenItem;
import com.rpgleveling.item.SkillScrollItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The three mod items. All are consumables stacked to 16. */
public final class RpgItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RpgAttributes.MODID);

    public static final DeferredItem<Item> RESPEC_TOKEN = ITEMS.register("respec_token",
            () -> new RespecTokenItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> SKILL_SCROLL = ITEMS.register("skill_scroll",
            () -> new SkillScrollItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> EXPERIENCE_CRYSTAL = ITEMS.register("experience_crystal",
            () -> new ExperienceCrystalItem(new Item.Properties().stacksTo(16)));

    private RpgItems() {}
}
