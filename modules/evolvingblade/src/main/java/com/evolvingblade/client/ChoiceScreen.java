package com.evolvingblade.client;

import java.util.List;

import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.network.EBPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 首次登录的三选一"修行卡"界面（中文优先，en_us 全量对齐）。
 *
 * <p>绘制层序（修复了"文字被背景模糊糊掉"的真实缺陷）：
 * <ol>
 *   <li>覆写 {@link #renderBackground} 只画纯色遮罩，<b>不</b>调用原版背景——原版会对世界做高斯模糊；</li>
 *   <li>{@link #render} 先调用 {@code super.render(...)}（背景 + 确认/取消按钮），
 *       之后才画卡片与文字。这样文字永远在按钮之上，也不会被模糊覆盖。</li>
 * </ol>
 *
 * <p>卡片本身<b>不使用原版按钮</b>（原版按钮会盖住描述文字），而是自绘矩形 + 命中检测；
 * 每个模式显示该模式<b>全部 8 个阶段</b>的服务器门槛，超高时可用滚轮在卡片内滚动。
 * 流程保持：选中卡片 → 第二次确认（明确"永久锁定"）→ 才发送 C2S 意图。
 */
public class ChoiceScreen extends Screen {

    private static final int PAD = 8;
    private static final int GAP = 8;
    private static final int LINE = 10;
    /** 底部三条保留带（自下而上）：按钮 20px、红色锁定警告最多 2 行、灰色提示 1 行。 */
    private static final int BUTTON_BAND = 30;
    private static final int WARNING_BAND = 24;
    private static final int HINT_BAND = 12;
    /** 卡片内为"滚轮提示"保留的顶部高度，避免压住最后一行内容。 */
    private static final int SCROLL_HINT_HEIGHT = 10;

    private GrowthMode selected;
    private int cardScroll;

    public ChoiceScreen() {
        super(Component.translatable("screen.evolvingblade.choice.title"));
    }

    private int cardWidth() {
        return Math.max(88, Math.min(180, (this.width - PAD * 2 - GAP * 2) / 3));
    }

    private int cardTop() {
        return 24;
    }

    /** 卡片底部：让出"提示 + 锁定警告 + 按钮"三条独立的带，互不重叠。 */
    private int cardBottom() {
        return this.height - BUTTON_BAND - WARNING_BAND - HINT_BAND;
    }

    private int hintY() {
        return this.height - BUTTON_BAND - WARNING_BAND - HINT_BAND + 2;
    }

    private int warningY() {
        return this.height - BUTTON_BAND - WARNING_BAND + 4;
    }

    private int cardLeft(int index) {
        int total = cardWidth() * 3 + GAP * 2;
        return Math.max(PAD, (this.width - total) / 2) + index * (cardWidth() + GAP);
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int buttonWidth = Math.min(140, cardWidth());
        int y = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.evolvingblade.choice.confirm"),
                        button -> this.confirm())
                .bounds(this.width / 2 - buttonWidth - 6, y, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(this.width / 2 + 6, y, buttonWidth, 20)
                .build());
        // 默认不预选：必须由玩家点一张卡。
        this.selected = null;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 纯色遮罩：绝不调用原版背景（它会高斯模糊世界，把已经画好的文字糊掉）。
        graphics.fill(0, 0, this.width, this.height, 0xE0101018);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先画背景与确认/取消按钮，再画卡片与文字：文字永远在上层。
        super.render(graphics, mouseX, mouseY, partialTick);
        EBPayloads.StateSnapshot snapshot = EBClientState.snapshot();
        int cardWidth = cardWidth();
        int top = cardTop();
        int bottom = cardBottom();
        int visible = Math.max(2, (bottom - top - 6 - SCROLL_HINT_HEIGHT) / LINE);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFF2D25C);
        // 灰色提示单独一条带，长句中英文都按宽度换行。
        int hintY = hintY();
        for (net.minecraft.util.FormattedCharSequence hintLine : this.font.split(
                Component.translatable("screen.evolvingblade.choice.hint"), Math.max(80, this.width - 16))) {
            graphics.drawCenteredString(this.font, hintLine, this.width / 2, hintY, 0xFF8FA3BF);
            hintY += LINE;
        }

        GrowthMode[] modes = GrowthMode.values();
        for (int index = 0; index < modes.length; index++) {
            GrowthMode mode = modes[index];
            int x = cardLeft(index);
            boolean chosen = mode == this.selected;
            graphics.fill(x, top, x + cardWidth, bottom, chosen ? 0xFF2F3E5C : 0xFF1C2029);
            graphics.renderOutline(x, top, cardWidth, bottom - top, chosen ? 0xFFF2D25C : 0xFF3A4152);

            // 逻辑行 → 按其宽度换行 → 再按滚动偏移渲染。
            List<FormattedCharSequence> visual = new java.util.ArrayList<>();
            for (Component line : ModeCardText.describe(mode, snapshot)) {
                visual.addAll(this.font.split(line, cardWidth - 8));
            }
            int maxScroll = Math.max(0, visual.size() - visible);
            int scroll = Math.min(this.cardScroll, maxScroll);
            if (maxScroll > 0) {
                // 滚轮提示画在卡片的保留顶带里，绝不与内容行重叠。
                graphics.drawString(this.font, Component.translatable("screen.evolvingblade.choice.scroll"),
                        x + 4, top + 3, 0xFF8FA3BF, false);
            }
            int y = top + 4 + SCROLL_HINT_HEIGHT;
            for (int i = scroll; i < visual.size() && i < scroll + visible; i++) {
                graphics.drawString(this.font, visual.get(i), x + 4, y, 0xFFE8EEF7, false);
                y += LINE;
            }
        }

        if (this.selected != null) {
            // 红色锁定警告占用自己的带（最多两行），与灰色提示/按钮都不重叠。
            int warningY = warningY();
            for (FormattedCharSequence warningLine : this.font.split(
                    Component.translatable("screen.evolvingblade.choice.lock_warning",
                            Component.translatable("evolvingblade.mode." + this.selected.id())),
                    Math.max(80, this.width - 16))) {
                graphics.drawCenteredString(this.font, warningLine, this.width / 2, warningY, 0xFFFF7A7A);
                warningY += LINE;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int top = cardTop();
            int bottom = cardBottom();
            if (mouseY >= top && mouseY <= bottom) {
                GrowthMode[] modes = GrowthMode.values();
                for (int index = 0; index < modes.length; index++) {
                    int x = cardLeft(index);
                    int cardWidth = cardWidth();
                    if (mouseX >= x && mouseX <= x + cardWidth) {
                        this.selected = modes[index];
                        this.cardScroll = 0;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.cardScroll = Math.max(0, this.cardScroll - (int) Math.signum(scrollY) * 2);
        return true;
    }

    /**
     * 第二次确认：使用原版 {@link net.minecraft.client.gui.screens.ConfirmScreen} 明确写出"锁定"警告后，
     * 才把 C2S 意图发给服务器。取消则回到三选一界面，不发送任何包。
     */
    private void confirm() {
        if (this.selected == null || this.minecraft == null) {
            return;
        }
        final GrowthMode target = this.selected;
        Component warning = Component.translatable("screen.evolvingblade.choice.lock_warning",
                Component.translatable("evolvingblade.mode." + target.id()));
        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                answer -> {
                    if (answer) {
                        EBClientState.dismiss();
                        PacketDistributor.sendToServer(new EBPayloads.ChooseMode(target));
                        this.minecraft.setScreen(null);
                    } else {
                        this.minecraft.setScreen(this);
                    }
                },
                Component.translatable("screen.evolvingblade.choice.confirm_title"),
                warning,
                Component.translatable("screen.evolvingblade.choice.confirm_yes"),
                Component.translatable("gui.cancel")));
    }

    @Override
    public void onClose() {
        EBClientState.dismiss();
        super.onClose();
    }
}
