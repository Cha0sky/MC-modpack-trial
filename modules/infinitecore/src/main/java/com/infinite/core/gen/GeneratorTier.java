package com.infinite.core.gen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Never reorder: ordinals are part of the retained generation curve. */
public enum GeneratorTier {
    DIRT("dirt", 360, Items.DIRT, 0x876044),
    WOOD("wood", 180, Items.OAK_LOG, 0xBA8852),
    STONE("stone", 72, Items.STONE, 0x727F89),
    COPPER("copper", 36, Items.COPPER_INGOT, 0xC87546),
    IRON("iron", 24, Items.IRON_INGOT, 0xC8D2DA),
    GOLD("gold", 12, Items.GOLD_INGOT, 0xD9B447),
    DIAMOND("diamond", 6, Items.DIAMOND, 0x43C8CB),
    NETHERITE("netherite", 1, Items.NETHERITE_INGOT, 0x65596D);

    private final String id;
    private final int baseTicks;
    private final Item output;
    private final int color;

    GeneratorTier(String id, int baseTicks, Item output, int color) {
        this.id = id;
        this.baseTicks = baseTicks;
        this.output = output;
        this.color = color;
    }

    public String id() { return id; }
    public int baseTicks() { return baseTicks; }
    public Item output() { return output; }
    public int color() { return color; }
    public int interval(GeneratorTier template) {
        return template == null ? baseTicks : GenerationMath.interval(baseTicks, ordinal(), template.ordinal());
    }
}
