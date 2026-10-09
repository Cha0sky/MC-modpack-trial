package com.rpgleveling.client.screen;

import com.rpgleveling.api.Mode;
import com.rpgleveling.api.StatLine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One rendered point-buy row.
 *
 * <p>Every column comes from RpgLayout.Columns, derived from the button zone, so the value
 * text can never sit on a button. The clamped marker has its own gutter instead of being
 * drawn over the name. All text is drawn with a drop shadow.</p>
 */
public final class StatRow {

    public static final int HEIGHT = RpgLayout.ROW_H;

    public final StatLine line;
    private final int y;
    private final RpgLayout.Columns columns;
    private final boolean odd;

    public StatRow(StatLine line, int y, RpgLayout.Columns columns, boolean odd) {
        this.line = line;
        this.y = y;
        this.columns = columns;
        this.odd = odd;
    }

    public int y() { return y; }

    public int bottom() { return y + HEIGHT; }

    /** Button hit test. @return -1 / +1 / +10, or 0 when nothing was hit. */
    public int hitButton(double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + HEIGHT) return 0;
        for (int i = 0; i < 3; i++) {
            int left = RpgLayout.buttonLeft(columns, i);
            if (mouseX >= left && mouseX < left + RpgLayout.BTN_W) {
                return switch (i) {
                    case 0 -> -1;
                    case 1 -> 1;
                    default -> 10;
                };
            }
        }
        return 0;
    }

    /** Index of the action button under the cursor, or -1. */
    public int hoveredButton(double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + HEIGHT) return -1;
        for (int i = 0; i < 3; i++) {
            int left = RpgLayout.buttonLeft(columns, i);
            if (mouseX >= left && mouseX < left + RpgLayout.BTN_W) return i;
        }
        return -1;
    }

    public boolean hovered(double mouseX, double mouseY) {
        return mouseY >= y && mouseY < y + HEIGHT
                && mouseX >= columns.rowLeft() && mouseX < columns.buttonsRight();
    }

    public void render(GuiGraphics graphics, Font font, boolean enabled, int points, String valueText,
                       boolean clamped, double mouseX, double mouseY, int pressedButton, int hoveredButton) {
        boolean over = hovered(mouseX, mouseY);
        if (over) {
            graphics.fill(columns.rowLeft(), y, columns.buttonsRight(), y + HEIGHT - 1, RpgTheme.ROW_HOVER);
        } else if (odd) {
            graphics.fill(columns.rowLeft(), y, columns.buttonsRight(), y + HEIGHT - 1, RpgTheme.ROW_ODD);
        }
        if (!enabled) {
            graphics.fill(columns.rowLeft(), y, columns.buttonsRight(), y + HEIGHT - 1, RpgTheme.ROW_DISABLED);
        }

        // 2 px mode accent bar in its own lane, left of everything else.
        int accent = line.mode == Mode.FLAT ? RpgTheme.ACCENT_FLAT : RpgTheme.ACCENT_PERCENT;
        graphics.fill(columns.accentX(), y + 2, columns.accentX() + 2, y + HEIGHT - 2, accent);

        int textY = y + (HEIGHT - 8) / 2;

        if (clamped) {
            graphics.drawString(font, "!", columns.gutterX(), textY, RpgTheme.TEXT_WARN);
        }

        String name = Component.translatable(line.langKey()).getString();
        int room = Math.max(0, columns.nameMaxW());
        if (font.width(name) > room) {
            name = font.plainSubstrByWidth(name, Math.max(0, room - 6)) + "\u2026";
        }
        graphics.drawString(font, name, columns.nameX(), textY, enabled ? RpgTheme.TEXT : RpgTheme.TEXT_MUTED);

        if (columns.showMode()) {
            String mode = Component.translatable(line.mode == Mode.FLAT
                    ? "gui.rpgleveling.mode.flat" : "gui.rpgleveling.mode.percent").getString();
            graphics.drawString(font, mode, columns.modeX(), textY, accent);
        }
        if (columns.showPoints()) {
            String pointsText = Integer.toString(points);
            graphics.drawString(font, pointsText, columns.pointsRight() - font.width(pointsText), textY,
                    RpgTheme.TEXT_POINTS);
        }
        if (columns.showValue()) {
            graphics.drawString(font, valueText, columns.valueRight() - font.width(valueText), textY,
                    RpgTheme.TEXT_VALUE);
        }

        drawButton(graphics, font, "-1", 0, hoveredButton == 0, pressedButton == 0, mouseX, mouseY);
        drawButton(graphics, font, "+1", 1, hoveredButton == 1, pressedButton == 1, mouseX, mouseY);
        drawButton(graphics, font, "+10", 2, hoveredButton == 2, pressedButton == 2, mouseX, mouseY);
    }

    private void drawButton(GuiGraphics graphics, Font font, String label, int index,
                            boolean hovered, boolean pressed, double mouseX, double mouseY) {
        int left = RpgLayout.buttonLeft(columns, index);
        int top = y + (HEIGHT - RpgLayout.BTN_H) / 2 + (pressed ? 1 : 0);
        int right = left + RpgLayout.BTN_W;
        int bottom = top + RpgLayout.BTN_H;

        int face = pressed ? RpgTheme.BTN_FACE_PRESSED
                : (hovered ? RpgTheme.BTN_FACE_HOVER : RpgTheme.BTN_FACE);
        graphics.fill(left, top, right, bottom, face);
        graphics.fill(left, top, right, top + 1, RpgTheme.BTN_HIGHLIGHT);
        int border = hovered ? RpgTheme.GOLD_BRIGHT : RpgTheme.BTN_BORDER;
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top + 1, right, bottom, border);
        graphics.fill(left + 1, bottom - 1, right, bottom, border);

        graphics.drawCenteredString(font, label, left + RpgLayout.BTN_W / 2, top + (RpgLayout.BTN_H - 8) / 2,
                hovered ? 0xFFFFFFFF : 0xFFDCE2F0);
    }
}
