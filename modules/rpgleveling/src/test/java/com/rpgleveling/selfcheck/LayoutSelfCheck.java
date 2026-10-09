package com.rpgleveling.selfcheck;

import com.rpgleveling.client.screen.RpgLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline proof that the point-buy layout cannot overlap and cannot leave the screen.
 *
 * <p>RpgLayout is deliberately Minecraft-free, so the whole geometry can be exercised on a
 * plain JVM. This is the automated version of the lead's render mock: it asserts the
 * approved anchors at the reference size, strict column ordering with a minimum gap at every
 * supported window size, the panel clamp, and that the widest translated name still fits.</p>
 */
public final class LayoutSelfCheck {

    private LayoutSelfCheck() {}

    private static int checks;
    private static int failures;

    /** Window sizes from the smallest sane logical screen up to a large one. */
    private static final int[][] WINDOWS = {
            {320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}, {2560, 1440},
            {300, 200}, {200, 150},
    };

    /** @return {checks, failures} */
    public static int[] run() {
        checks = 0;
        failures = 0;

        referenceAnchors();
        for (int[] window : WINDOWS) {
            geometry(window[0], window[1]);
        }
        nineSlice();
        widestName();

        System.out.println("  [info] layout self check: " + (checks - failures) + "/" + checks + " passed");
        return new int[] { checks, failures };
    }

    /** At the reference window the anchors must be exactly the approved mock's. */
    private static void referenceAnchors() {
        RpgLayout.Frame frame = RpgLayout.frame(854, 480, 26);
        RpgLayout.Columns c = RpgLayout.columns(frame);
        int ix0 = frame.panel().left() + RpgLayout.PAD;
        int ix1 = frame.panel().right() - RpgLayout.PAD;
        eq("ref.panelWidth", 620, frame.panel().width());
        eq("ref.innerWidth", 560, ix1 - ix0);
        eq("ref.gutterX", ix0 + 2, c.gutterX());
        eq("ref.nameX", ix0 + 14, c.nameX());
        eq("ref.modeX", ix0 + 170, c.modeX());
        eq("ref.pointsRight", ix0 + 298, c.pointsRight());
        eq("ref.buttonsLeft", ix1 - RpgLayout.buttonsWidth(), c.buttonsLeft());
        eq("ref.valueRight", c.buttonsLeft() - 8, c.valueRight());
        eq("ref.buttonStep", 23, RpgLayout.buttonLeft(c, 1) - RpgLayout.buttonLeft(c, 0));
        eq("ref.buttonsWidth", 66, RpgLayout.buttonsWidth());
        check(c.nameMaxW() >= 120, "reference name column too narrow: " + c.nameMaxW());
    }

    /** No window size may produce overlapping columns or a panel outside the screen. */
    private static void geometry(int screenW, int screenH) {
        String tag = screenW + "x" + screenH;
        RpgLayout.Frame frame = RpgLayout.frame(screenW, screenH, 26);
        RpgLayout.Rect panel = frame.panel();

        if (screenW >= 2 * RpgLayout.SCREEN_MARGIN + 40) {
            check(panel.left() >= RpgLayout.SCREEN_MARGIN, tag + ": panel left " + panel.left());
            check(panel.right() <= screenW - RpgLayout.SCREEN_MARGIN, tag + ": panel right " + panel.right());
        }
        if (screenH >= 2 * RpgLayout.SCREEN_MARGIN + 40) {
            check(panel.top() >= RpgLayout.SCREEN_MARGIN, tag + ": panel top " + panel.top());
            check(panel.bottom() <= screenH - RpgLayout.SCREEN_MARGIN, tag + ": panel bottom " + panel.bottom());
        }
        check(panel.width() <= RpgLayout.MAX_PANEL_W, tag + ": panel wider than the maximum");
        check(frame.viewport().height() >= 0, tag + ": negative viewport");
        check(frame.maxScroll() >= 0, tag + ": negative scroll range");
        check(frame.respec().bottom() <= panel.bottom(), tag + ": respec button below the panel");
        check(frame.respec().top() >= frame.viewport().bottom(), tag + ": respec button overlaps the list");

        RpgLayout.Columns c = RpgLayout.columns(frame);
        int ix0 = panel.left() + RpgLayout.PAD;

        if (c.showValue()) {
            // Each column is strictly right of the previous one, separated by at least COL_GAP.
            int nameAnchor = c.showMode() ? c.modeX()
                    : (c.showPoints() ? c.pointsRight() - RpgLayout.POINTS_W : c.valueRight() - RpgLayout.VALUE_W);
            check(c.nameX() + c.nameMaxW() + RpgLayout.COL_GAP <= nameAnchor, tag + ": name overlaps mode");
            if (c.showMode()) {
                int right = c.showPoints() ? c.pointsRight() - RpgLayout.POINTS_W : c.valueRight() - RpgLayout.VALUE_W;
                check(c.modeX() + RpgLayout.MODE_W + RpgLayout.COL_GAP <= right, tag + ": mode overlaps points");
            }
            if (c.showPoints()) {
                check(c.pointsRight() <= c.valueRight() - RpgLayout.VALUE_W, tag + ": points overlaps value");
            }
            check(c.valueRight() + RpgLayout.VALUE_BTN_GAP <= c.buttonsLeft(), tag + ": value overlaps buttons");
            check(c.buttonsLeft() >= ix0, tag + ": button zone starts left of the content area");
            check(c.buttonsRight() <= panel.right(), tag + ": button zone outside the panel");
            check(c.gutterX() + 6 <= c.nameX(), tag + ": clamp marker gutter touches the name");
            check(c.accentX() + 2 < c.gutterX(), tag + ": accent bar touches the clamp gutter");
        } else {
            check(c.buttonsLeft() - RpgLayout.COL_GAP >= c.nameX(), tag + ": compact fallback still overlaps");
        }
    }

    /** The nine-slice must have a non-negative centre cell exactly when it is usable. */
    private static void nineSlice() {
        check(RpgLayout.nineSliceFits(RpgLayout.panel(854, 480)), "reference panel should support the nine-slice");
        check(!RpgLayout.nineSliceFits(new RpgLayout.Rect(0, 0, 100, 100)), "a 100px panel must fall back");
        RpgLayout.Rect panel = RpgLayout.panel(854, 480);
        check(panel.width() - 2 * RpgLayout.SLICE > 0 && panel.height() - 2 * RpgLayout.SLICE > 0,
                "nine-slice centre cell is not positive");
        eq("nineSlice.panelTex", 3 * RpgLayout.SLICE, RpgLayout.PANEL_TEX_W);
    }

    /** The longest translated line name must fit the name column at the preferred width. */
    private static void widestName() {
        List<String> names = new ArrayList<>();
        for (var locale : new String[] { "zh_cn", "en_us" }) {
            java.nio.file.Path path = java.nio.file.Path.of("src/main/resources/assets/rpgleveling/lang/" + locale + ".json");
            if (!java.nio.file.Files.exists(path)) continue;
            try {
                String json = java.nio.file.Files.readString(path);
                int index = 0;
                while ((index = json.indexOf("\"rpgleveling.line.", index)) >= 0) {
                    int colon = json.indexOf(':', index);
                    int end = json.indexOf('"', colon + 3);
                    if (colon < 0 || end < 0) break;
                    names.add(json.substring(colon + 2, end));
                    index = end;
                }
            } catch (Exception e) {
                check(false, "could not read " + path + ": " + e);
                return;
            }
        }
        if (names.isEmpty()) {
            // Standalone runs from a different working directory: fall back to a synthetic worst case.
            names.add("\u57fa\u7840\u76d2\u7532\u97e7\u6027\uff08\u6570\u503c\uff09");
        }
        int widest = 0;
        for (String name : names) widest = Math.max(widest, RpgLayout.estimateTextWidth(name));

        RpgLayout.Frame preferred = RpgLayout.frame(2 * RpgLayout.MIN_PANEL_W, 480, 26);
        RpgLayout.Columns c = RpgLayout.columns(preferred);
        check(c.nameMaxW() >= widest,
                "widest line name needs " + widest + " px but the name column only reserves " + c.nameMaxW());
        System.out.println("  [info] widest line name " + widest + " px, name column " + c.nameMaxW() + " px");
    }

    private static void eq(String what, int expected, int actual) {
        checks++;
        if (expected != actual) fail(what + ": expected " + expected + " but got " + actual);
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) fail(message);
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  FAIL [layout] " + message);
    }
}
