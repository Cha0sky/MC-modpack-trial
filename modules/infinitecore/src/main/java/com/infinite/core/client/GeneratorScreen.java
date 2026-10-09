package com.infinite.core.client;

import com.infinite.core.InfiniteCore;
import com.infinite.core.block.IoMode;
import com.infinite.core.gen.GenerationMath;
import com.infinite.core.gen.GeneratorTier;
import com.infinite.core.menu.GeneratorMenu;
import com.infinite.core.network.IoPackets;
import com.infinite.core.registry.ModContent;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Two pages of one live container: IO navigation never closes/reopens the server menu. */
public final class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final ResourceLocation MAIN = ResourceLocation.fromNamespaceAndPath(InfiniteCore.MODID, "textures/gui/generator.png");
    private static final ResourceLocation IO = ResourceLocation.fromNamespaceAndPath(InfiniteCore.MODID, "textures/gui/generator_io.png");
    private static final int TEXT = 0xDDE3E7;
    private static final int MUTED = 0x9BA7B0;
    private static final int[] FACES = {1, 0, 2, 3, 4, 5};
    private final Button[] faceButtons = new Button[6];
    private final int[] pendingMode = new int[6];
    private final int[] pendingAge = new int[6];
    private Button pageButton;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 216;
        Arrays.fill(pendingMode, -1);
    }
    @Override protected void init() {
        super.init();
        pageButton = addRenderableWidget(Button.builder(Component.translatable("gui.infinitecore.io_config"), button -> {
            menu.setIoPage(!menu.ioPage());
            updateButtons();
        }).bounds(leftPos + 124, topPos + 4, 44, 18).build());
        for (int row = 0; row < 6; row++) {
            final int face = FACES[row];
            faceButtons[row] = addRenderableWidget(Button.builder(Component.empty(), button -> {
                IoMode next = menu.ioMode(face).next();
                pendingMode[face] = next.id();
                pendingAge[face] = 0;
                PacketDistributor.sendToServer(new IoPackets.Update(menu.pos(), face, next));
                updateButtons();
            }).bounds(leftPos + 110, topPos + 33 + row * 23, 58, 18).build());
        }
        updateButtons();
    }
    @Override protected void containerTick() {
        super.containerTick();
        for (int face = 0; face < 6; face++) {
            if (pendingMode[face] >= 0 && (menu.ioMode(face).id() == pendingMode[face] || ++pendingAge[face] >= 40)) {
                pendingMode[face] = -1;
            }
        }
        updateButtons();
    }
    private void updateButtons() {
        if (pageButton == null) return;
        pageButton.setMessage(Component.translatable(menu.ioPage() ? "gui.infinitecore.back" : "gui.infinitecore.io_config"));
        pageButton.active = menu.getCarried().isEmpty();
        for (int row = 0; row < 6; row++) {
            int face = FACES[row];
            Button button = faceButtons[row];
            button.visible = menu.ioPage();
            button.active = menu.ready() && pendingMode[face] < 0;
            button.setMessage(Component.translatable(pendingMode[face] < 0 ? menu.ioMode(face).key() : "gui.infinitecore.syncing"));
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && menu.ioPage()) {
            menu.setIoPage(false);
            updateButtons();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(menu.ioPage() ? IO : MAIN, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int accent = 0xFF000000 | menu.tier().color();
        graphics.fill(leftPos + 8, topPos + 16, leftPos + 119, topPos + 18, accent);
        if (menu.ioPage()) {
            for (int row = 0; row < 6; row++) {
                graphics.fill(leftPos + 8, topPos + 32 + row * 23, leftPos + 11, topPos + 51 + row * 23,
                        0xFF000000 | menu.ioMode(FACES[row]).color());
            }
            return;
        }
        ItemStack target = menu.target();
        if (!target.isEmpty()) graphics.renderFakeItem(target, leftPos + 42, topPos + 26);
        int filled = menu.status() == 1 ? Math.clamp((int) Math.round(78.0 * menu.progress() / menu.interval()), 0, 78) : 0;
        if (menu.status() == 1 && menu.interval() == 1) filled = 78;
        graphics.fill(leftPos + 83, topPos + 103, leftPos + 83 + filled, topPos + 107, accent);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFit(graphics, menu.ioPage() ? Component.translatable("gui.infinitecore.io_title") : title, 8, 6, 112, TEXT);
        if (menu.ioPage()) {
            for (int row = 0; row < 6; row++) {
                int face = FACES[row];
                int y = 31 + row * 23;
                drawFit(graphics, Component.translatable("gui.infinitecore.direction." + Direction.from3DDataValue(face).getName()), 14, y + 6, 20, TEXT);
                drawFit(graphics, neighborName(face), 36, y + 2, 68, TEXT);
                drawFit(graphics, Component.translatable(menu.neighborHasItems(face) ? "gui.infinitecore.can_transfer" : "gui.infinitecore.no_interface"),
                        36, y + 12, 68, menu.neighborHasItems(face) ? 0x83CFA6 : MUTED);
            }
            drawFit(graphics, Component.translatable("gui.infinitecore.io_input_hint"), 8, 178, 160, MUTED);
            drawFit(graphics, Component.translatable("gui.infinitecore.io_output_hint"), 8, 191, 160, MUTED);
            return;
        }
        ItemStack target = menu.target();
        drawFit(graphics, target.isEmpty() ? Component.translatable("gui.infinitecore.status.no_template") : target.getHoverName(), 64, 27, 104, TEXT);
        drawFit(graphics, Component.translatable("gui.infinitecore.inventory_slots", menu.occupiedSlots()), 83, 54, 80, MUTED);
        drawFit(graphics, Component.translatable("gui.infinitecore.interval", menu.interval()), 83, 67, 80, TEXT);
        drawFit(graphics, Component.translatable("gui.infinitecore.rate", String.format(Locale.ROOT, "%.2f", 20.0 / menu.interval())), 83, 79, 80, MUTED);
        GeneratorTier templateTier = ModContent.templateTier(menu.template());
        if (templateTier != null) drawFit(graphics, Component.translatable("gui.infinitecore.template_tier",
                Component.translatable("gui.infinitecore.tier." + templateTier.id())), 64, 38, 104, MUTED);
        double multiplier = templateTier == null ? 1.0 : GenerationMath.multiplier(menu.tier().ordinal(), templateTier.ordinal());
        drawFit(graphics, Component.translatable("gui.infinitecore.multiplier", String.format(Locale.ROOT, "%.1f", multiplier)), 83, 91, 80, MUTED);
        String status = !menu.ready() ? "syncing" : switch (menu.status()) { case 1 -> "running"; case 2 -> "full"; default -> "no_template"; };
        drawFit(graphics, Component.translatable("gui.infinitecore.status." + status), 12, 113, 152, menu.status() == 2 ? 0xE4B469 : 0xAFD2BD);
        drawFit(graphics, playerInventoryTitle, 8, 123, 160, MUTED);
    }
    private void drawFit(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        String value = text.getString();
        if (font.width(value) > width) value = font.plainSubstrByWidth(value, width - font.width("…")) + "…";
        graphics.drawString(font, value, x, y, color, false);
    }
    private Component neighborName(int face) {
        if (minecraft == null || minecraft.level == null) return Component.translatable("gui.infinitecore.neighbor.none");
        var state = minecraft.level.getBlockState(menu.pos().relative(Direction.from3DDataValue(face)));
        return state.isAir() ? Component.translatable("gui.infinitecore.neighbor.none") : state.getBlock().getName();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (!menu.ioPage() && menu.getCarried().isEmpty() && !menu.target().isEmpty()
                && isHovering(42, 26, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, menu.target(), mouseX, mouseY);
        }
        if (!menu.ioPage() && menu.template().isEmpty() && isHovering(12, 26, 16, 16, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.infinitecore.template_slot"), mouseX, mouseY);
        }
        if (menu.ioPage()) {
            for (int row = 0; row < 6; row++) {
                if (isHovering(14, 31 + row * 23, 20, 21, mouseX, mouseY)) {
                    graphics.renderTooltip(font, Component.translatable("gui.infinitecore.direction_full."
                            + Direction.from3DDataValue(FACES[row]).getName()), mouseX, mouseY);
                }
                if (isHovering(36, 31 + row * 23, 68, 21, mouseX, mouseY)) {
                    graphics.renderTooltip(font, neighborName(FACES[row]), mouseX, mouseY);
                }
            }
        } else if (isHovering(64, 26, 104, 18, mouseX, mouseY) && !menu.target().isEmpty()) {
            graphics.renderTooltip(font, menu.target().getHoverName(), mouseX, mouseY);
        } else if (isHovering(83, 65, 80, 43, mouseX, mouseY)) {
            graphics.renderTooltip(font, font.split(Component.translatable("gui.infinitecore.rate_hint"), 190), mouseX, mouseY);
        }
    }
}
