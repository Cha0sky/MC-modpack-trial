package com.evolvingblade.client;

import java.util.List;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.registry.EBDataComponents;
import com.evolvingblade.registry.EBItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** The brass-rimmed field instrument shared by the blade screens. */
final class BladeFolio {
    static final int INK = 0xFF10202B, PANEL = 0xFF192D38, RIM = 0xFF46616A;
    static final int TEXT = 0xFFF1E7D1, GOLD = 0xFFE2BB74, QUIET = 0xFFADBEBD, AQUA = 0xFF80CECC;
    private static final ResourceLocation DIAL = ResourceLocation.fromNamespaceAndPath(
            EvolvingBlade.MODID, "textures/gui/astrolabe.png");
    private static final ItemStack[] BLADES = new ItemStack[8];

    record Page(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        int footer() { return bottom() - 25; }
    }

    static Page page(int width, int height) {
        int w = Math.min(504, width - 8), h = Math.min(270, height - 8);
        return new Page((width - w) / 2, (height - h) / 2, w, h);
    }

    static void frame(GuiGraphics g, Page p, int width, int height) {
        g.fill(0, 0, width, height, 0xA0080E16);
        g.fillGradient(p.x, p.y, p.right(), p.bottom(), PANEL, INK);
        g.renderOutline(p.x, p.y, p.width, p.height, RIM);
        g.fill(p.x+1, p.y+1, p.right()-1, p.y+3, 0xFF93754C);
        g.hLine(p.x+9, p.right()-10, p.y+24, RIM);
        g.hLine(p.x+9, p.right()-10, p.footer()-4, RIM);
    }

    static void sword(GuiGraphics g, int stage, int x, int y, int size) {
        if (BLADES[stage] == null) {
            ItemStack stack = new ItemStack(EBItems.starEater());
            stack.set(EBDataComponents.STAGE.get(), stage);
            BLADES[stage] = stack;
        }
        g.blit(DIAL, x, y, size, size, 0f, 0f, 96, 96, 96, 96);
        g.pose().pushPose();
        float scale = (size - 8) / 16f;
        g.pose().translate(x + 4, y + 4, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(BLADES[stage], 0, 0);
        g.pose().popPose();
    }

    static void meter(GuiGraphics g, Font font, int x, int y, int width, Component label,
            long count, long threshold, int color) {
        g.drawString(font, label, x, y, QUIET, false);
        String tally = threshold == 0 ? title("free").getString() : count + " / " + threshold;
        g.drawString(font, tally, x + width - font.width(tally), y, TEXT, false);
        g.fill(x, y + 12, x + width, y + 15, 0xFF2E424B);
        double fraction = threshold == 0 ? 1 : Math.min((double) count / threshold, 1.0);
        g.fill(x, y + 12, x + (int) Math.round(width * fraction), y + 15, color);
    }

    static Component title(String name) {
        return Component.translatable("screen.evolvingblade.folio." + name);
    }

    static List<FormattedCharSequence> wrap(Font font, List<Component> paragraphs, int width) {
        return paragraphs.stream().flatMap(line -> font.split(line, width).stream()).toList();
    }
}
