package com.rpgleveling.data;

import com.rpgleveling.service.XpSource;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * First-time discovery ledger (DESIGN 8.7).
 *
 * <p>One {@code long[]} bitmap per source, indexed by the registry <b>numeric</b> id:
 * a lookup is a shift and a mask, the whole ledger is well under 1 KB per player
 * and there is no per-tick scan anywhere.</p>
 */
public final class DiscoveryData implements INBTSerializable<CompoundTag> {

    /** Which representation this instance uses; mirrors {@code xp.discovery.storage}. */
    public enum Storage { BITSET, STRING_SET }

    private final Map<XpSource, long[]> bits = new EnumMap<>(XpSource.class);
    private final Map<XpSource, java.util.Set<String>> strings = new EnumMap<>(XpSource.class);
    private final Map<XpSource, Integer> sizes = new EnumMap<>(XpSource.class);
    private Storage storage = Storage.BITSET;
    private String modFingerprint = "";
    private boolean warnedOverflow;

    public DiscoveryData() {}

    public boolean isUnlocked(XpSource src, int registryId) {
        long[] a = bits.get(src);
        if (storage == Storage.STRING_SET) a = null;
        if (a == null || registryId < 0) return false;
        int w = registryId >>> 6;
        return w < a.length && (a[w] & (1L << (registryId & 63))) != 0L;
    }

    /**
     * Sets one bit. Returns {@code true} only when this call is the first one for the
     * given id - that is exactly when the reward must be granted.
     */
    public Storage storage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = (storage == null) ? Storage.BITSET : storage;
    }

    /**
     * Sets one entry. Returns {@code true} only when this call is the first one for the
     * given entry - exactly when the reward must be paid.
     *
     * @param stableKey registry name of the entry; only used by {@link Storage#STRING_SET}
     */
    public boolean unlock(XpSource src, int registryId, String stableKey, int registrySize, int maxBits) {
        if (storage == Storage.STRING_SET) {
            return unlockString(src, stableKey, registrySize, maxBits);
        }
        return unlockBits(src, registryId, registrySize, maxBits);
    }

    /**
     * {@code STRING_SET} mode: bound by entry count instead of by bit budget, so a pack
     * that adds and removes mods cannot mis-map an id. Larger on disk, chosen by
     * {@code xp.discovery.storage = STRING_SET}.
     */
    private boolean unlockString(XpSource src, String stableKey, int registrySize, int maxBits) {
        if (stableKey == null || stableKey.isEmpty()) return false;
        java.util.Set<String> set = strings.computeIfAbsent(src, k -> new java.util.LinkedHashSet<>());
        if (set.size() >= maxBits) {
            if (!warnedOverflow) {
                warnedOverflow = true;
                com.rpgleveling.util.RpgLog.LOG.warn(
                        "rpgleveling: discovery string set for {} reached the {} entry budget", src, maxBits);
            }
            return false;
        }
        if (!set.add(stableKey)) return false;
        sizes.put(src, Math.min(registrySize, maxBits));
        return true;
    }

    private boolean unlockBits(XpSource src, int registryId, int registrySize, int maxBits) {
        int limit = Math.min(registrySize, maxBits);
        if (registryId < 0 || registryId >= limit) {
            if (!warnedOverflow && registryId >= limit && limit > 0) {
                warnedOverflow = true;
                com.rpgleveling.util.RpgLog.LOG.warn(
                        "rpgleveling: discovery id {} is beyond the configured bit budget {} - "
                                + "no further rewards will be granted for source {}", registryId, limit, src);
            }
            return false;
        }
        long[] a = bits.computeIfAbsent(src, k -> new long[(limit + 63) >>> 6]);
        int w = registryId >>> 6;
        if (w >= a.length) return false;
        long mask = 1L << (registryId & 63);
        if ((a[w] & mask) != 0L) return false;
        a[w] |= mask;
        sizes.put(src, limit);
        return true;
    }

    /** Number of unlocked entries. Only ever called from commands / the codex UI. */
    public int count(XpSource src) {
        java.util.Set<String> set = strings.get(src);
        if (set != null) return set.size();
        long[] a = bits.get(src);
        if (a == null) return 0;
        int c = 0;
        for (long w : a) c += Long.bitCount(w);
        return c;
    }

    /** {@code true} when the given entry was already unlocked (used by the codex UI). */
    public boolean known(XpSource src, int registryId) {
        return isUnlocked(src, registryId);
    }

    public String modFingerprint() {
        return modFingerprint;
    }

    public void setModFingerprint(String fingerprint) {
        this.modFingerprint = (fingerprint == null) ? "" : fingerprint;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<XpSource, long[]> e : bits.entrySet()) {
            if (e.getValue() == null || e.getValue().length == 0) continue;
            tag.putLongArray("bits_" + e.getKey().name(), e.getValue());
            tag.putInt("size_" + e.getKey().name(), sizes.getOrDefault(e.getKey(), 0));
        }
        for (Map.Entry<XpSource, java.util.Set<String>> e : strings.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            tag.putString("str_" + e.getKey().name(), String.join("\n", e.getValue()));
            tag.putInt("size_" + e.getKey().name(), sizes.getOrDefault(e.getKey(), 0));
        }
        tag.putString("storage", storage.name());
        tag.putString("mod_fingerprint", modFingerprint);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        bits.clear();
        strings.clear();
        sizes.clear();
        try {
            storage = Storage.valueOf(tag.getString("storage"));
        } catch (IllegalArgumentException unknown) {
            storage = Storage.BITSET;
        }
        for (XpSource src : XpSource.VALUES) {
            String key = "bits_" + src.name();
            if (!tag.contains(key)) continue;
            long[] a = tag.getLongArray(key);
            int declared = tag.getInt("size_" + src.name());
            int expected = (Math.min(Math.max(declared, 0), MAX_ACCEPTED_BITS) + 63) >>> 6;
            if (declared <= 0 || a.length != expected) {
                com.rpgleveling.util.RpgLog.LOG.warn(
                        "rpgleveling: discarding discovery bitmap for {} (length {} vs expected {})",
                        src, a.length, expected);
                continue;
            }
            bits.put(src, a);
            sizes.put(src, declared);
        }
        for (XpSource src : XpSource.VALUES) {
            String key = "str_" + src.name();
            if (!tag.contains(key)) continue;
            String raw = tag.getString(key);
            if (raw.isEmpty()) continue;
            java.util.Set<String> set = new java.util.LinkedHashSet<>();
            for (String part : raw.split("\n")) {
                if (!part.isEmpty()) set.add(part);
            }
            strings.put(src, set);
            sizes.put(src, Math.max(set.size(), tag.getInt("size_" + src.name())));
        }
        modFingerprint = tag.getString("mod_fingerprint");
    }

    /** Absolute upper bound accepted from disk, independent of the live config. */
    public static final int MAX_ACCEPTED_BITS = 1 << 20;
}