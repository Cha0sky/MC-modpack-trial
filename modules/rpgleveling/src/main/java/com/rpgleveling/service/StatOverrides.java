package com.rpgleveling.service;

import com.rpgleveling.api.Stat;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug-only stat overrides (DESIGN 11.4.1).
 *
 * <p>Purely in-memory: nothing here is ever written to an attachment or to NBT, so a
 * leftover override can never survive a restart. The saturating curve never reaches
 * its cap, so a test that needs "100% crit" has to bypass the curve - this is that
 * bypass, and it is OP-gated.</p>
 */
public final class StatOverrides {

    private static final Map<UUID, EnumMap<Stat, Double>> OVERRIDES = new ConcurrentHashMap<>();
    private static final Set<UUID> FORCE_CRIT = ConcurrentHashMap.newKeySet();

    private StatOverrides() {}

    public static void set(UUID player, Stat stat, double value) {
        OVERRIDES.computeIfAbsent(player, k -> new EnumMap<>(Stat.class)).put(stat, value);
    }

    public static void clear(UUID player, Stat stat) {
        EnumMap<Stat, Double> m = OVERRIDES.get(player);
        if (m == null) return;
        m.remove(stat);
        if (m.isEmpty()) OVERRIDES.remove(player);
    }

    public static void clearAll(UUID player) {
        OVERRIDES.remove(player);
        FORCE_CRIT.remove(player);
    }

    /** {@code null} when the dimension is not overridden. */
    public static Double get(UUID player, Stat stat) {
        EnumMap<Stat, Double> m = OVERRIDES.get(player);
        return (m == null) ? null : m.get(stat);
    }

    public static boolean has(UUID player, Stat stat) {
        return get(player, stat) != null;
    }

    public static Set<Stat> overridden(UUID player) {
        EnumMap<Stat, Double> m = OVERRIDES.get(player);
        return (m == null) ? Collections.emptySet() : Collections.unmodifiableSet(m.keySet());
    }

    public static int count(UUID player) {
        EnumMap<Stat, Double> m = OVERRIDES.get(player);
        return (m == null) ? 0 : m.size();
    }

    public static boolean forceCrit(UUID player) {
        return FORCE_CRIT.contains(player);
    }

    public static void setForceCrit(UUID player, boolean value) {
        if (value) FORCE_CRIT.add(player);
        else FORCE_CRIT.remove(player);
    }
}
