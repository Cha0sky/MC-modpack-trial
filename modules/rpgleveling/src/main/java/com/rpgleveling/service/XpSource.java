package com.rpgleveling.service;

/**
 * Where an experience grant came from.
 *
 * <p>{@link #KILL} pays every time, the five discovery sources pay exactly once per
 * registry entry (DESIGN 8.2 / 8.7).</p>
 */
public enum XpSource {
    KILL,
    MINE,
    CRAFT,
    SMELT,
    FISH,
    TRADE,
    ADVANCEMENT,
    COMMAND,
    OTHER;

    public static final XpSource[] VALUES = values();

    /** The five "first time only" sources. */
    public static final XpSource[] DISCOVERY = { MINE, CRAFT, SMELT, FISH, TRADE };

    public boolean isDiscovery() {
        return this != KILL && this != ADVANCEMENT && this != COMMAND && this != OTHER;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
