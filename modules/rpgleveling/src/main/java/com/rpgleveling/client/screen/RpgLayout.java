package com.rpgleveling.client.screen;

/**
 * The <b>only</b> place the point-buy screen computes a coordinate.
 *
 * <p>Free of every Minecraft type on purpose: LayoutSelfCheck drives it with synthetic text
 * metrics and asserts that no column can ever overlap the action buttons, that the panel
 * always stays inside the screen with a margin, and that the nine-slice geometry is well
 * formed. The runtime passes widths measured with Font#width.</p>
 *
 * <p>Column anchors are the ones from the approved mock, but each is applied through
 * min(preferred, neighbour - gap) starting at the button zone, so a longer translation or a
 * narrower window shifts a column instead of drawing over its neighbour.</p>
 */
public final class RpgLayout {

    private RpgLayout() {}

    // ---- panel chrome ----
    /** Nine-slice source texture: 192x192 with 64 px cells. */
    public static final int SLICE = 64;
    public static final int PANEL_TEX_W = 192;
    public static final int PANEL_TEX_H = 192;
    public static final int BACKDROP_TEX_W = 512;
    public static final int BACKDROP_TEX_H = 288;

    /** Inner padding from the panel edge; the AI frame is wide. */
    public static final int PAD = 30;
    public static final int SCREEN_MARGIN = 8;
    public static final int MIN_PANEL_W = 480;
    public static final int MAX_PANEL_W = 620;

    // ---- header offsets relative to the panel top ----
    public static final int TITLE_Y = 22;
    public static final int DIVIDER_Y = 40;
    public static final int LEVEL_Y = 48;
    public static final int BAR_Y = 62;
    public static final int BAR_H = 8;
    public static final int XP_TEXT_Y = 73;
    public static final int LIST_TOP_Y = 82;
    public static final int LIST_DIVIDER_DY = 13;
    public static final int LIST_GAP = 16;
    public static final int BOTTOM_PAD = 40;

    // ---- footer ----
    public static final int RESPEC_W = 120;
    public static final int RESPEC_H = 22;
    public static final int RESPEC_DY = 32;

    // ---- rows ----
    public static final int ROW_H = 24;
    public static final int BTN_W = 20;
    public static final int BTN_H = 18;
    public static final int BTN_GAP = 3;
    public static final int VALUE_W = 42;
    public static final int POINTS_W = 36;
    public static final int MODE_W = 42;
    public static final int COL_GAP = 10;
    public static final int PREF_GUTTER_X = 2;
    public static final int PREF_NAME_X = 14;
    public static final int PREF_MODE_X = 170;
    public static final int PREF_POINTS_RIGHT = 298;
    public static final int VALUE_BTN_GAP = 8;
    public static final int ACCENT_W = 12;

    /** An axis-aligned rectangle. */
    public record Rect(int left, int top, int right, int bottom) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public int centerX() { return (left + right) / 2; }
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /** Column anchors; all x values are absolute screen coordinates. */
    public record Columns(int rowLeft, int accentX,
                          int gutterX, int nameX, int nameMaxW,
                          int modeX,
                          int pointsRight,
                          int valueRight,
                          int buttonsLeft, int buttonsRight,
                          boolean showMode, boolean showPoints, boolean showValue) {}

    /** Panel, list viewport, footer button and scroll geometry. */
    public record Frame(Rect panel, Rect inner, int listTop, int listBottom, Rect viewport,
                        int maxScroll, Rect respec) {}

    public static int buttonsWidth() {
        return 3 * BTN_W + 2 * BTN_GAP;
    }

    /** Panel rectangle, clamped into the screen with a margin on every side. */
    public static Rect panel(int screenWidth, int screenHeight) {
        int available = Math.max(1, screenWidth - 2 * SCREEN_MARGIN);
        int width = Math.min(available, MAX_PANEL_W);
        int height = Math.min(Math.max(1, screenHeight - 2 * SCREEN_MARGIN), Math.max(1, screenHeight - 2));
        int left = (screenWidth - width) / 2;
        int top = (screenHeight - height) / 2;
        return new Rect(left, top, left + width, top + height);
    }

    public static Frame frame(int screenWidth, int screenHeight, int rowCount) {
        Rect panel = panel(screenWidth, screenHeight);
        int ix0 = panel.left() + PAD;
        int ix1 = Math.max(ix0 + 1, panel.right() - PAD);
        Rect inner = new Rect(ix0, panel.top(), ix1, panel.bottom());

        int listTop = panel.top() + LIST_TOP_Y;
        int listBottom = panel.bottom() - BOTTOM_PAD;
        if (listBottom - listTop < ROW_H) {
            listTop = Math.max(panel.top() + 4, panel.bottom() - BOTTOM_PAD - ROW_H);
            listBottom = Math.max(listTop + 1, panel.bottom() - BOTTOM_PAD);
        }
        int rowsTop = listTop + LIST_GAP;
        Rect viewport = new Rect(ix0, rowsTop, ix1, Math.max(rowsTop, listBottom));
        int maxScroll = Math.max(0, rowCount * ROW_H - viewport.height());

        int btnY = panel.bottom() - RESPEC_DY;
        Rect respec = new Rect(panel.centerX() - RESPEC_W / 2, btnY,
                panel.centerX() + RESPEC_W / 2, btnY + RESPEC_H);
        return new Frame(panel, inner, listTop, listBottom, viewport, maxScroll, respec);
    }

    /**
     * Column anchors, built right to left from the button zone: buttons, value, points,
     * mode, and the name takes the remainder. Overlap is impossible by construction.
     */
    public static Columns columns(Frame frame) {
        Rect panel = frame.panel();
        int ix0 = panel.left() + PAD;
        int ix1 = Math.max(ix0 + 1, panel.right() - PAD);
        int rowLeft = ix0 - ACCENT_W;
        int accentX = rowLeft;
        int gutterX = ix0 + PREF_GUTTER_X;
        int nameX = ix0 + PREF_NAME_X;

        int buttonsRight = ix1;
        int buttonsLeft = buttonsRight - buttonsWidth();
        int valueRight = buttonsLeft - VALUE_BTN_GAP;
        int valueX = valueRight - VALUE_W;

        if (valueX < nameX + COL_GAP) {
            return new Columns(rowLeft, accentX, gutterX, nameX,
                    Math.max(0, buttonsLeft - COL_GAP - nameX), nameX, nameX, nameX,
                    buttonsLeft, buttonsRight, false, false, false);
        }

        // Cascade the fit decision leftwards: a column that cannot sit at least COL_GAP
        // right of the name is dropped rather than drawn on top of it.
        int pointsRight = Math.min(ix0 + PREF_POINTS_RIGHT, valueX - COL_GAP);
        int pointsX = pointsRight - POINTS_W;
        boolean showPoints = pointsX >= nameX + COL_GAP;

        int modeAnchor = showPoints ? pointsX : valueX;
        int modeX = Math.min(ix0 + PREF_MODE_X, modeAnchor - COL_GAP - MODE_W);
        boolean showMode = modeX >= nameX + COL_GAP;

        int nameAnchor = showMode ? modeX : modeAnchor;
        int nameMaxW = Math.max(0, nameAnchor - COL_GAP - nameX);
        return new Columns(rowLeft, accentX, gutterX, nameX, nameMaxW, modeX, pointsRight, valueRight,
                buttonsLeft, buttonsRight, showMode, showPoints, true);
    }

    /** Left edge of the index-th action button (0 = "-1", 1 = "+1", 2 = "+10"). */
    public static int buttonLeft(Columns columns, int index) {
        return columns.buttonsLeft() + index * (BTN_W + BTN_GAP);
    }

    public static int rowY(Frame frame, int rowIndex, int scroll) {
        return frame.viewport().top() + rowIndex * ROW_H - scroll;
    }

    public static int scrollToShow(Frame frame, int rowIndex, int scroll) {
        int top = rowIndex * ROW_H;
        int bottom = top + ROW_H;
        if (top < scroll) return top;
        if (bottom > scroll + frame.viewport().height()) return bottom - frame.viewport().height();
        return scroll;
    }

    /** True when the nine-slice has a non-negative centre cell. */
    public static boolean nineSliceFits(Rect panel) {
        return panel.width() >= 2 * SLICE && panel.height() >= 2 * SLICE;
    }

    /**
     * Conservative upper bound of the default font width: 6 px per ASCII character,
     * 9 px per anything wider. Offline verification only; the client measures with Font#width.
     */
    public static int estimateTextWidth(String text) {
        if (text == null) return 0;
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += (cp < 0x2E80) ? 6 : 9;
            i += Character.charCount(cp);
        }
        return width;
    }
}
