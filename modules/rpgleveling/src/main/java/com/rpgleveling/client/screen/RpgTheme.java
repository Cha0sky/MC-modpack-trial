package com.rpgleveling.client.screen;

import net.minecraft.resources.ResourceLocation;

/**
 * Palette and texture ids of the point-buy screen.
 *
 * <p>panel.png is a 192x192 nine-slice (64 px cells) drawn with nine explicit blit calls;
 * backdrop.png is a 512x288 atmospheric plate stretched over the whole screen. Both are
 * read-only art assets.</p>
 */
public final class RpgTheme {

    private RpgTheme() {}

    private static final String MODID = "rpgleveling";

    public static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath(MODID, "textures/gui/panel.png");
    public static final ResourceLocation BACKDROP =
            ResourceLocation.fromNamespaceAndPath(MODID, "textures/gui/backdrop.png");

    public static final int SCREEN_BASE = 0xFF05060C;
    public static final int SCREEN_DIM = 0x66000000;

    public static final int GOLD = 0xFFC8A94E;
    public static final int GOLD_BRIGHT = 0xFFF0D67A;
    public static final int DIVIDER = 0x80C8A94E;
    public static final int DIVIDER_SOFT = 0x50C8A94E;
    public static final int OUTLINE_DARK = 0xFF05060C;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFB9C0CC;
    public static final int TEXT_MUTED = 0xFF8A93A6;
    public static final int TEXT_VALUE = 0xFFD8E4FF;
    public static final int TEXT_POINTS = 0xFFFFD24A;
    public static final int TEXT_TITLE = 0xFFF0D67A;
    public static final int TEXT_WARN = 0xFFFF6B6B;

    public static final int ROW_ODD = 0x14FFFFFF;
    public static final int ROW_HOVER = 0x26C8A94E;
    public static final int ROW_DISABLED = 0x44000000;

    public static final int ACCENT_FLAT = 0xFF4488FF;
    public static final int ACCENT_PERCENT = 0xFFFFAA33;

    public static final int BTN_FACE = 0xFF262C42;
    public static final int BTN_FACE_HOVER = 0xFF3A4668;
    public static final int BTN_FACE_PRESSED = 0xFF1A1F30;
    public static final int BTN_BORDER = 0xB0C8A94E;
    public static final int BTN_HIGHLIGHT = 0x1EFFFFFF;

    public static final int BAR_TRACK = 0xFF05060C;
    public static final int BAR_FILL_TOP = 0xFF6FE3F5;
    public static final int BAR_FILL_BOTTOM = 0xFF2E7FB8;
    public static final int BAR_NOTCH = 0x33000000;

    public static final int SCROLL_TRACK = 0x50000000;
    public static final int SCROLL_THUMB = 0xFF6C7280;
}
