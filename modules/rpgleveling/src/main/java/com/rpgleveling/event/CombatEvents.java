package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.api.Rounding;
import com.rpgleveling.net.RpgNet;
import com.rpgleveling.service.PlayerCache;
import com.rpgleveling.service.StatOverrides;
import com.rpgleveling.stat.DerivedStats;
import com.rpgleveling.util.RpgLog;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The combat half of the mod: critical hits, damage reduction, life steal and drop rate.
 *
 * <p>Every handler is server-only and reads its numbers through {@code DerivedStats},
 * never by evaluating a curve locally.</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class CombatEvents {

    private CombatEvents() {}

    /**
     * Hard-coded, <b>not removable</b> bypass set (DESIGN 1.9 conclusion 3.1).
     * Configuration may only append to this: {@code /kill} and the void must always
     * be able to end a player, no matter how many points were invested.
     */
    private static final Set<ResourceKey<DamageType>> ALWAYS_BYPASS = Set.of(
            DamageTypes.GENERIC_KILL,
            DamageTypes.FELL_OUT_OF_WORLD);

    private static final TagKey<DamageType>[] ALWAYS_BYPASS_TAGS = new TagKey[] {
            DamageTypeTags.BYPASSES_INVULNERABILITY
    };

    private static volatile Set<ResourceKey<DamageType>> extraBypass = Set.of();

    /**
     * Victim health captured in {@code LivingDamageEvent.Pre} and consumed in
     * {@code Post}, keyed by entity id.
     *
     * <p>Necessary because {@code DamageContainer#getNewDamage()} is <b>not</b> clamped to the
     * victim's remaining health: a 100 damage hit on a 20 hp mob reports 100, so stealing a
     * percentage of it would heal five times what the design promises ("steal the health
     * actually lost", DESIGN 1.10). The map is bounded and cleared defensively so a
     * cancelled event cannot make it grow.</p>
     */
    private static final java.util.Map<Integer, Float> PRE_HIT_HEALTH = new java.util.concurrent.ConcurrentHashMap<>();

    /** Upper bound on the capture map; a value this high only happens under a bug. */
    private static final int PRE_HIT_HEALTH_LIMIT = 4096;

    private static Set<ResourceKey<DamageType>> extraBypass() {
        Set<ResourceKey<DamageType>> cached = extraBypass;
        if (cached == null || cached.isEmpty()) {
            java.util.HashSet<ResourceKey<DamageType>> set = new java.util.HashSet<>();
            for (String raw : RpgConfig.extraBypassDamageTypes()) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null) set.add(ResourceKey.create(Registries.DAMAGE_TYPE, id));
            }
            cached = Set.copyOf(set);
            extraBypass = cached;
        }
        return cached;
    }

    /** Called on config reload so the appended list takes effect. */
    public static void invalidateBypassCache() {
        extraBypass = Set.of();
    }

    // ------------------------------------------------------------------
    // critical hits
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        // Fired on both sides; only the server may change the outcome (V4).
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double chance = DerivedStats.critChancePercent(player);
        boolean critical = StatOverrides.forceCrit(player.getUUID())
                || (chance > 0.0 && player.getRandom().nextDouble() * 100.0 < chance);
        if (critical) {
            event.setCriticalHit(true);
            float multiplier = DerivedStats.critMultiplier(player);
            if (Float.isFinite(multiplier) && multiplier > 0.0F) {
                event.setDamageMultiplier(multiplier);
            }
        }
    }

    // ------------------------------------------------------------------
    // damage reduction (step 9) and life steal (step 12)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof ServerPlayer) {
            if (PRE_HIT_HEALTH.size() > PRE_HIT_HEALTH_LIMIT) PRE_HIT_HEALTH.clear();
            PRE_HIT_HEALTH.put(victim.getId(), victim.getHealth());
        }
        if (!(victim instanceof ServerPlayer player)) return;
        DamageSource source = event.getSource();

        ResourceKey<DamageType> key = source.typeHolder().unwrapKey().orElse(null);
        if (key != null && (ALWAYS_BYPASS.contains(key) || extraBypass().contains(key))) return;
        for (TagKey<DamageType> tag : ALWAYS_BYPASS_TAGS) {
            if (source.is(tag)) return;
        }
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) && !RpgConfig.damageReductionAppliesToBypassArmor()) return;

        double reduction = DerivedStats.damageReductionPercent(player);
        if (!(reduction > 0.0)) return;
        if (reduction > 95.0) reduction = 95.0;   // numeric backstop only
        float damage = event.getNewDamage();
        if (!(damage > 0.0F)) return;
        double after = damage * (1.0 - reduction / 100.0);
        if (!Double.isFinite(after) || after < 0.0) after = 0.0;
        event.setNewDamage((float) after);
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        LivingEntity victim = event.getEntity();
        if (victim == attacker) return;
        if (event.getSource().is(DamageTypes.THORNS) && !RpgConfig.lifestealIncludeThorns()) return;

        double percent = DerivedStats.lifestealPercent(attacker);
        if (!(percent > 0.0)) return;

        PlayerCache.Entry cache = PlayerCache.of(attacker.getUUID());
        if (cache.lifestealBudgetTick != attacker.tickCount) {
            cache.lifestealBudgetTick = attacker.tickCount;
            // T19: no budget by default. Note we cannot simply use 0 as "off" here -
            // the guard below treats a non-positive budget as "skip", which would stop
            // lifesteal entirely rather than uncapping it.
            cache.lifestealBudget = RpgConfig.lifestealHpsCapEnabled()
                    ? RpgConfig.lifestealHpsCap() / 20.0
                    : Double.MAX_VALUE;
        }
        if (!(cache.lifestealBudget > 0.0)) return;

        // Steal a share of the health the victim really lost, never of the raw overkill
        // amount (DESIGN 1.10: "a 100 damage hit on a one-heart mob steals one heart's worth").
        double damageDealt = event.getNewDamage();
        Float healthBefore = PRE_HIT_HEALTH.remove(victim.getId());
        if (healthBefore != null) damageDealt = Math.min(damageDealt, healthBefore);
        if (!(damageDealt > 0.0)) return;
        double heal = damageDealt * percent / 100.0;
        if (!Double.isFinite(heal) || heal <= 0.0) return;
        heal = Math.min(heal, cache.lifestealBudget);
        cache.lifestealBudget -= heal;
        cache.lifestealAccumulator += heal;
        float whole = (float) Math.floor(cache.lifestealAccumulator);
        if (whole >= 1.0F) {
            cache.lifestealAccumulator -= whole;
            attacker.heal(whole);
        }
    }

    // ------------------------------------------------------------------
    // drop rate
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        DropRateHelper.applyDropMultiplier(player, level, event.getDrops());
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!RpgConfig.dropApplyToBlocks()) return;
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        DropRateHelper.applyDropMultiplier(player, event.getLevel(), event.getDrops());
    }

    /** Diagnostics: the damage types that permanently ignore the reduction. */
    public static List<String> bypassSummary() {
        return ALWAYS_BYPASS.stream().map(k -> k.location().toString()).sorted().toList();
    }
}