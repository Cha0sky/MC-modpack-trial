package com.infinite.core.block;

public enum IoMode {
    NONE(0x6E6E6E), INPUT(0xB05050), OUTPUT(0x5080C8), BOTH(0xC070C0);

    private final int color;
    IoMode(int color) { this.color = color; }
    public int id() { return ordinal(); }
    public int color() { return color; }
    public String key() { return "gui.infinitecore.mode." + name().toLowerCase(java.util.Locale.ROOT); }
    public IoMode next() { return values()[(ordinal() + 1) % 4]; }
    public static IoMode fromId(int id) { return id >= 0 && id < 4 ? values()[id] : NONE; }
}
