package com.rpgleveling.client.screen;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Basis;
import com.rpgleveling.api.Mode;
import com.rpgleveling.api.Stat;
import com.rpgleveling.api.StatLine;
import com.rpgleveling.client.ClientRpgData;
import com.rpgleveling.data.RpgMath;
import com.rpgleveling.net.ClientboundRpgDataPayload;
import com.rpgleveling.net.RpgNet;
import com.rpgleveling.net.ServerboundRequestDataPayload;
import com.rpgleveling.net.ServerboundRespecPayload;
import com.rpgleveling.net.ServerboundSpendPointPayload;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.stat.LineCfg;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The point-buy screen (opened with K).
 *
 * <p>Rendering decisions, each fixing a reported defect:</p>
 * <ol>
 *   <li>renderBackground paints an opaque base plus the atmospheric backdrop and never calls
 *       super: Screen#renderBackground runs a full-screen gaussian blur of the world, which
 *       showed through as a grey smear.</li>
 *   <li>The panel is the 192x192 nine-slice drawn with nine explicit blit calls, so the
 *       ornate border keeps its proportions instead of being squashed by a whole-texture
 *       stretch.</li>
 *   <li>Every string is drawn with a drop shadow.</li>
 * </ol>
 *
 * <p>Geometry comes exclusively from RpgLayout, which is Minecraft-free and covered by an
 * offline check. No vanilla widgets are used, so super.render never repaints the background
 * over the panel.</p>
 */
public class RpgScreen extends Screen {

    private final List<StatRow> rows = new ArrayList<>(StatLine.COUNT);
    private int scroll;
    private int pressedButton = -1;
    private StatRow pressedRow;
    private boolean respecPressed;

    public RpgScreen() {
        super(Component.translatable("gui.rpgleveling.title"));
    }

    @Override
    protected void init() {
        rebuild();
        if (!ClientRpgData.has()) {
            PacketDistributor.sendToServer(ServerboundRequestDataPayload.INSTANCE);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        rebuild();
    }

    private RpgLayout.Frame frame() {
        return RpgLayout.frame(width, height, StatLine.COUNT);
    }

    private void rebuild() {
        RpgLayout.Frame frame = frame();
        RpgLayout.Columns columns = RpgLayout.columns(frame);
        rows.clear();
        for (int i = 0; i < StatLine.COUNT; i++) {
            rows.add(new StatRow(StatLine.byIndex(i), RpgLayout.rowY(frame, i, scroll), columns, i % 2 == 1));
        }
    }

    // ------------------------------------------------------------------
    // background: opaque base + backdrop, never the world blur
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately NOT calling super: Screen#renderBackground runs renderBlurredBackground,
        // which blurs the entire world behind the panel.
        graphics.fill(0, 0, width, height, RpgTheme.SCREEN_BASE);
        graphics.blit(RpgTheme.BACKDROP, 0, 0, width, height, 0.0F, 0.0F,
                RpgLayout.BACKDROP_TEX_W, RpgLayout.BACKDROP_TEX_H,
                RpgLayout.BACKDROP_TEX_W, RpgLayout.BACKDROP_TEX_H);
        graphics.fill(0, 0, width, height, RpgTheme.SCREEN_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        RpgLayout.Frame frame = frame();
        RpgLayout.Columns columns = RpgLayout.columns(frame);
        drawPanel(graphics, frame.panel());

        ClientboundRpgDataPayload data = ClientRpgData.get();
        if (data == null) {
            graphics.drawCenteredString(font, Component.translatable("gui.rpgleveling.syncing"),
                    frame.panel().centerX(), frame.panel().top() + RpgLayout.LIST_TOP_Y, RpgTheme.TEXT_POINTS);
            drawFooter(graphics, frame, mouseX, mouseY);
            return;
        }

        drawHeader(graphics, frame, columns, data);
        drawRows(graphics, frame, columns, data, mouseX, mouseY);
        drawScrollbar(graphics, frame);
        drawFooter(graphics, frame, mouseX, mouseY);
        drawTooltip(graphics, frame, data, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // nine-slice panel
    // ------------------------------------------------------------------

    private void drawPanel(GuiGraphics graphics, RpgLayout.Rect panel) {
        if (!RpgLayout.nineSliceFits(panel)) {
            graphics.blit(RpgTheme.PANEL, panel.left(), panel.top(), panel.width(), panel.height(),
                    0.0F, 0.0F, RpgLayout.PANEL_TEX_W, RpgLayout.PANEL_TEX_H,
                    RpgLayout.PANEL_TEX_W, RpgLayout.PANEL_TEX_H);
            return;
        }
        int s = RpgLayout.SLICE;
        int tw = RpgLayout.PANEL_TEX_W;
        int th = RpgLayout.PANEL_TEX_H;
        int x0 = panel.left();
        int y0 = panel.top();
        int x1 = panel.right();
        int y1 = panel.bottom();
        int midW = panel.width() - 2 * s;
        int midH = panel.height() - 2 * s;

        // four corners, fixed size
        graphics.blit(RpgTheme.PANEL, x0, y0, s, s, 0.0F, 0.0F, s, s, tw, th);
        graphics.blit(RpgTheme.PANEL, x1 - s, y0, s, s, (float) (tw - s), 0.0F, s, s, tw, th);
        graphics.blit(RpgTheme.PANEL, x0, y1 - s, s, s, 0.0F, (float) (th - s), s, s, tw, th);
        graphics.blit(RpgTheme.PANEL, x1 - s, y1 - s, s, s, (float) (tw - s), (float) (th - s), s, s, tw, th);
        // four edges, stretched along one axis only
        graphics.blit(RpgTheme.PANEL, x0 + s, y0, midW, s, (float) s, 0.0F, tw - 2 * s, s, tw, th);
        graphics.blit(RpgTheme.PANEL, x0 + s, y1 - s, midW, s, (float) s, (float) (th - s), tw - 2 * s, s, tw, th);
        graphics.blit(RpgTheme.PANEL, x0, y0 + s, s, midH, 0.0F, (float) s, s, th - 2 * s, tw, th);
        graphics.blit(RpgTheme.PANEL, x1 - s, y0 + s, s, midH, (float) (tw - s), (float) s, s, th - 2 * s, tw, th);
        // centre, stretched both ways
        graphics.blit(RpgTheme.PANEL, x0 + s, y0 + s, midW, midH, (float) s, (float) s,
                tw - 2 * s, th - 2 * s, tw, th);
    }

    // ------------------------------------------------------------------
    // header
    // ------------------------------------------------------------------

    private void drawHeader(GuiGraphics graphics, RpgLayout.Frame frame, RpgLayout.Columns columns,
                            ClientboundRpgDataPayload data) {
        RpgLayout.Rect panel = frame.panel();
        RpgLayout.Rect inner = frame.inner();
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + RpgLayout.TITLE_Y, RpgTheme.TEXT_TITLE);

        int dividerY = panel.top() + RpgLayout.DIVIDER_Y;
        graphics.fill(inner.left(), dividerY, inner.right(), dividerY + 1, RpgTheme.DIVIDER);

        int levelY = panel.top() + RpgLayout.LEVEL_Y;
        String level = Component.translatable("gui.rpgleveling.level", data.level()).getString();
        graphics.drawString(font, level, inner.left(), levelY, RpgTheme.TEXT);
        String points = Component.translatable("gui.rpgleveling.points", data.unspentPoints()).getString();
        graphics.drawString(font, points, inner.right() - font.width(points), levelY, RpgTheme.TEXT_POINTS);

        drawXpBar(graphics, frame, data);

        int feedback = ClientRpgData.lastFeedback();
        if (feedback > 0) {
            String text = Component.translatable(RpgNet.feedbackKey(feedback)).getString();
            graphics.drawString(font, text, inner.right() - font.width(text),
                    panel.top() + RpgLayout.XP_TEXT_Y, RpgTheme.TEXT_WARN);
        }

        int captionY = frame.listTop();
        graphics.fill(inner.left(), captionY + RpgLayout.LIST_DIVIDER_DY,
                inner.right(), captionY + RpgLayout.LIST_DIVIDER_DY + 1, RpgTheme.DIVIDER_SOFT);
        // The caption shares the name lane, so it must obey the same width budget as the
        // rows. StatRow already truncates row names; without doing the same here the
        // caption overflowed into the Mode caption on narrow windows (T12-D1).
        String statCaption = Component.translatable("gui.rpgleveling.column.stat").getString();
        int captionRoom = Math.max(0, columns.nameMaxW());
        if (font.width(statCaption) > captionRoom) {
            statCaption = font.plainSubstrByWidth(statCaption, Math.max(0, captionRoom - 6)) + "\u2026";
        }
        graphics.drawString(font, statCaption, columns.nameX(), captionY, RpgTheme.TEXT_MUTED);
        if (columns.showMode()) {
            graphics.drawString(font, Component.translatable("gui.rpgleveling.column.mode").getString(),
                    columns.modeX(), captionY, RpgTheme.TEXT_MUTED);
        }
        if (columns.showPoints()) {
            String caption = Component.translatable("gui.rpgleveling.column.points").getString();
            graphics.drawString(font, caption, columns.pointsRight() - font.width(caption), captionY,
                    RpgTheme.TEXT_MUTED);
        }
        if (columns.showValue()) {
            String caption = Component.translatable("gui.rpgleveling.column.value").getString();
            graphics.drawString(font, caption, columns.valueRight() - font.width(caption), captionY,
                    RpgTheme.TEXT_MUTED);
        }
        String action = Component.translatable("gui.rpgleveling.column.action").getString();
        graphics.drawString(font, action, columns.buttonsRight() - font.width(action), captionY, RpgTheme.TEXT_MUTED);
    }

    private void drawXpBar(GuiGraphics graphics, RpgLayout.Frame frame, ClientboundRpgDataPayload data) {
        RpgLayout.Rect inner = frame.inner();
        int y1 = frame.panel().top() + RpgLayout.BAR_Y;
        int y2 = y1 + RpgLayout.BAR_H;
        int x1 = inner.left();
        int x2 = inner.right();
        long need = Math.max(1L, data.xpForNextLevel());

        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, RpgTheme.OUTLINE_DARK);
        graphics.fill(x1, y1, x2, y2, RpgTheme.BAR_TRACK);
        int filled = (int) Math.min(x2 - x1, (long) (x2 - x1) * data.xpIntoLevel() / need);
        if (filled > 0) {
            graphics.fill(x1, y1, x1 + filled, y1 + RpgLayout.BAR_H / 2, RpgTheme.BAR_FILL_TOP);
            graphics.fill(x1, y1 + RpgLayout.BAR_H / 2, x1 + filled, y2, RpgTheme.BAR_FILL_BOTTOM);
            graphics.fill(x1, y1, x1 + filled, y1 + 1, RpgTheme.BAR_NOTCH);
        }
        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y1, RpgTheme.GOLD);
        graphics.fill(x1 - 1, y2, x2 + 1, y2 + 1, RpgTheme.GOLD);

        String progress = data.xpIntoLevel() + " / " + data.xpForNextLevel();
        graphics.drawString(font, progress, inner.left(), frame.panel().top() + RpgLayout.XP_TEXT_Y,
                RpgTheme.TEXT_DIM);
    }

    // ------------------------------------------------------------------
    // rows, scrollbar, footer
    // ------------------------------------------------------------------

    private void drawRows(GuiGraphics graphics, RpgLayout.Frame frame, RpgLayout.Columns columns,
                          ClientboundRpgDataPayload data, int mouseX, int mouseY) {
        LineCfg[] cfgs = RpgConfig.snapshot();
        RpgLayout.Rect viewport = frame.viewport();
        graphics.enableScissor(viewport.left() - RpgLayout.ACCENT_W, viewport.top(),
                viewport.right(), viewport.bottom());
        for (StatRow row : rows) {
            if (row.bottom() <= viewport.top() || row.y() >= viewport.bottom()) continue;
            LineCfg cfg = cfgs[row.line.index()];
            int points = data.points()[row.line.index()];
            String value = String.format(Locale.ROOT, "%.2f", RpgMath.lineValue(points, cfg));
            int pressed = (pressedRow == row) ? pressedButton : -1;
            row.render(graphics, font, cfg.enabled(), points, value, isClamped(row.line.stat),
                    mouseX, mouseY, pressed, row.hoveredButton(mouseX, mouseY));
        }
        graphics.disableScissor();
    }

    private void drawScrollbar(GuiGraphics graphics, RpgLayout.Frame frame) {
        if (frame.maxScroll() <= 0) return;
        RpgLayout.Rect viewport = frame.viewport();
        int trackX = frame.inner().right() + 6;
        graphics.fill(trackX, viewport.top(), trackX + 3, viewport.bottom(), RpgTheme.SCROLL_TRACK);
        int content = StatLine.COUNT * RpgLayout.ROW_H;
        int thumbH = Math.max(16, viewport.height() * viewport.height() / Math.max(1, content));
        int travel = viewport.height() - thumbH;
        int thumbY = viewport.top() + (int) ((double) scroll / frame.maxScroll() * travel);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, RpgTheme.SCROLL_THUMB);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + 2, RpgTheme.GOLD);
        graphics.fill(trackX, thumbY + thumbH - 2, trackX + 3, thumbY + thumbH, RpgTheme.GOLD);
    }

    private void drawFooter(GuiGraphics graphics, RpgLayout.Frame frame, int mouseX, int mouseY) {
        RpgLayout.Rect respec = frame.respec();
        boolean hovered = respec.contains(mouseX, mouseY);
        int top = respec.top() + (respecPressed ? 1 : 0);
        int bottom = respec.bottom() + (respecPressed ? 1 : 0);
        int face = respecPressed ? RpgTheme.BTN_FACE_PRESSED
                : (hovered ? RpgTheme.BTN_FACE_HOVER : RpgTheme.BTN_FACE);
        graphics.fill(respec.left(), top, respec.right(), bottom, face);
        graphics.fill(respec.left(), top, respec.right(), top + 1, RpgTheme.BTN_HIGHLIGHT);
        int border = hovered ? RpgTheme.GOLD_BRIGHT : RpgTheme.BTN_BORDER;
        graphics.fill(respec.left(), top, respec.right(), top + 1, border);
        graphics.fill(respec.left(), top, respec.left() + 1, bottom, border);
        graphics.fill(respec.right() - 1, top + 1, respec.right(), bottom, border);
        graphics.fill(respec.left() + 1, bottom - 1, respec.right(), bottom, border);
        graphics.drawCenteredString(font, Component.translatable("gui.rpgleveling.respec"),
                respec.centerX(), top + 7, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------
    // tooltip + input
    // ------------------------------------------------------------------

    /**
     * The "!" marker means "further points do nothing here". That is true both when a
     * vanilla/attribute ceiling truncated the value and when one of this mod's functional
     * ceilings (crit chance 100, knockback resistance 1.0, damage reduction 90) did.
     */
    private static boolean isClamped(Stat stat) {
        LocalPlayer local = Minecraft.getInstance().player;
        return local != null && DerivedStats.isEffectivelyCapped(local, stat);
    }

    private void drawTooltip(GuiGraphics graphics, RpgLayout.Frame frame, ClientboundRpgDataPayload data,
                             int mouseX, int mouseY) {
        if (mouseY < frame.viewport().top() || mouseY >= frame.viewport().bottom()) return;
        StatRow hovered = null;
        for (StatRow row : rows) {
            if (row.hovered(mouseX, mouseY)) {
                hovered = row;
                break;
            }
        }
        if (hovered == null) return;

        LineCfg[] cfgs = RpgConfig.snapshot();
        LineCfg cfg = cfgs[hovered.line.index()];
        int points = data.points()[hovered.line.index()];
        double value = RpgMath.lineValue(points, cfg);
        boolean showHint = true;
        try {
            showHint = RpgConfig.SHOW_CURVE_HINT.get();
        } catch (RuntimeException ignored) {
            // keep the default
        }
        List<Component> tip = new ArrayList<>(RpgTooltips.forLine(hovered.line, cfg, points, value, showHint));
        if (isClamped(hovered.line.stat)) {
            tip.add(Component.translatable("gui.rpgleveling.clamped",
                    String.format(Locale.ROOT, "%.1f", hovered.line.stat.hardMax())));
        }
        if (hovered.line.mode == Mode.PERCENT && cfg.basis() == Basis.TOTAL) {
            Stat stat = hovered.line.stat;
            boolean zeroBasis = !stat.hasExternalBasis() && data.points()[stat.line(Mode.FLAT).index()] == 0;
            if (zeroBasis) tip.add(RpgTooltips.zeroBasisHint(stat));
        }
        graphics.renderTooltip(font, tip, Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            RpgLayout.Frame frame = frame();
            if (frame.respec().contains(mouseX, mouseY)) {
                respecPressed = true;
                PacketDistributor.sendToServer(ServerboundRespecPayload.INSTANCE);
                return true;
            }
            if (mouseY >= frame.viewport().top() && mouseY < frame.viewport().bottom()) {
                for (StatRow row : rows) {
                    int index = row.hoveredButton(mouseX, mouseY);
                    if (index >= 0) {
                        pressedRow = row;
                        pressedButton = index;
                        PacketDistributor.sendToServer(
                                new ServerboundSpendPointPayload(row.line.index(), row.hitButton(mouseX, mouseY)));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedButton = -1;
        pressedRow = null;
        respecPressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        RpgLayout.Frame frame = frame();
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * RpgLayout.ROW_H * 2, 0, frame.maxScroll());
        rebuild();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
