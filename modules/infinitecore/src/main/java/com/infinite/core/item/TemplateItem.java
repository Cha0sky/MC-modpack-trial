package com.infinite.core.item;

import com.infinite.core.registry.ModContent;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class TemplateItem extends Item {
    public TemplateItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        ItemStack target = stack.get(ModContent.TARGET_ITEM.get());
        lines.add(target != null && !target.isEmpty()
                ? Component.translatable("tooltip.infinitecore.target_item", target.getHoverName()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.infinitecore.template_hint").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.infinitecore.no_consumption").withStyle(ChatFormatting.DARK_GRAY));
    }
}
