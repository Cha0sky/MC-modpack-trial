package com.rpgleveling.service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All transient, per-player runtime state in one place: it is created on demand and
 * dropped in {@code PlayerLoggedOutEvent} so nothing can leak between sessions.
 */
public final class PlayerCache {

    private static final Map<UUID, Entry> MAP = new ConcurrentHashMap<>();

    private PlayerCache() {}

    public static Entry of(UUID id) {
        return MAP.computeIfAbsent(id, k -> new Entry());
    }

    public static void clear(UUID id) {
        MAP.remove(id);
    }

    public static int size() {
        return MAP.size();
    }

    /** Mutable scratch space. Never persisted. */
    public static final class Entry {
        /** Last maximum health we pushed to the player; {@code <= 0} means "unknown". */
        public double lastAppliedMaxHealth;
        /** Fractional health-regeneration carry-over. */
        public double regenAccumulator;
        /** Fractional life-steal carry-over (shares the same semantics as regen). */
        public double lifestealAccumulator;
        /** Remaining life-steal budget for the current tick. */
        public double lifestealBudget;
        public int lifestealBudgetTick = Integer.MIN_VALUE;
        /** Tick of the last successful respec, for the cooldown. */
        public int lastRespecTick = Integer.MIN_VALUE;
        /** Generic "at most N calls per window" counters. */
        public final Map<String, int[]> windows = new HashMap<>();
        /** Rolling 60s experience window: [startTick, amount]. */
        public final ArrayDeque<long[]> xpWindow = new ArrayDeque<>();
        /** Suspicious-packet counter. */
        public int violations;
        /** Dimensions already reported as truncated by the vanilla ceiling (log once each). */
        public final java.util.Set<com.rpgleveling.api.Stat> clampWarned =
                java.util.EnumSet.noneOf(com.rpgleveling.api.Stat.class);
    }
}