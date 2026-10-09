package com.evolvingblade.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 成长仪表盘（G 键）。
 *
 * <p>层序与三选一界面相同：{@link #renderBackground} 只画纯色遮罩（原版背景会高斯模糊，
 * 把已经画好的文字糊掉——这是 Codex 在隔离客户端确认过的真实缺陷），
 * {@link #render} 先 {@code super.render(...)} 画按钮，再画文字。
 *
 * <p>中文/英文长句按窗口宽度用 {@code font.split(...)} <b>换行</b>（不只是纵向滚动），
 * 所有数值来自服务器快照，因此 854×480（逻辑 427×240）与 1920×1080 都能读全。
 */
public class DashboardScreen extends Screen {

    private static final int LINE = 10;
    private int scroll;

    public DashboardScreen() {
        super(Component.translatable("screen.evolvingblade.dashboard.title"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 24, 120, 20)
                .build());
    }

    /** 滚动提示独占条带（在 Done 按钮之上），与内容区互不重叠。 */
    private static final int HINT_BAND = 12;

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xF0101418);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int wrapWidth = Math.max(80, this.width - 20);
        List<FormattedCharSequence> visual = new ArrayList<>();
        for (Component line : ModeCardText.dashboardLines(EBClientState.snapshot())) {
            visual.addAll(this.font.split(line, wrapWidth));
        }
        int top = 18;
        int bottom = this.height - 30 - HINT_BAND; // 让出 Done 按钮与滚动提示两条带
        int visible = Math.max(1, (bottom - top) / LINE);
        int maxScroll = Math.max(0, visual.size() - visible);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFF2D25C);
        int y = top;
        for (int i = this.scroll; i < visual.size() && i < this.scroll + visible; i++) {
            graphics.drawString(this.font, visual.get(i), 10, y, 0xFFDCE6F2, false);
            y += LINE;
        }
        if (maxScroll > 0) {
            // 滚动提示放到独立底带，并按真实字体宽度居中（长中英文都不会越界）。
            Component hint = Component.translatable("screen.evolvingblade.dashboard.scroll", this.scroll, maxScroll);
            int hintY = this.height - 36;
            for (FormattedCharSequence hintLine : this.font.split(hint, Math.max(60, this.width - 16))) {
                graphics.drawString(this.font, hintLine, Math.max(4, (this.width - this.font.width(hintLine)) / 2),
                        hintY, 0xFF8FA3BF, false);
                hintY += LINE;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Math.max(0, this.scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }
}
