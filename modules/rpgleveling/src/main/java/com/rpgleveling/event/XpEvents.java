package com.rpgleveling.event;

import com.rpgleveling.RpgConfig;
import com.rpgleveling.service.XpService;
import com.rpgleveling.service.XpSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Every experience source of the mod.
 *
 * <p>Kills pay every time; mining, crafting, smelting, fishing and trading pay exactly
 * once per registry entry, which makes automated farms worthless for experience and
 * removes the need for a per-minute patch (DESIGN 8.2 / 8.7). Every handler here is
 * server-only, takes exactly one event parameter and does one O(1) bit test - there is
 * no scan anywhere on an event path.</p>
 *
 * <p>There is deliberately no {@code the vanilla player-experience event} listener in this mod: booking RPG
 * experience on the vanilla absorption path as well would square the multiplier (INV-1).</p>
 */
@EventBusSubscriber(modid = "rpgleveling")
public final class XpEvents {

    /** Hard ceiling on a single kill reward, so an absurd modded mob cannot overflow anything. */
    private static final int MAX_KILL_REWARD = 1_000_000;

    private XpEvents() {}

    /**
     * Base kill reward = {@code max_health * health_coefficient + attack_damage * attack_coefficient}
     * (default coefficients 1 and 9).
     *
     * <p>Max health, not current health: the victim is already dead at this point. Attack damage
     * comes from the {@code ATTACK_DAMAGE} attribute, so a mob holding a weapon is worth more.</p>
     *
     * <p>Attack credit is only given when the entity actually <b>has</b> the attribute
     * ({@code hasAttribute}). {@code LivingEntity.createLivingAttributes()} deliberately omits
     * {@code ATTACK_DAMAGE}, so animals like cows and pigs have none - and reading it anyway would
     * silently return the attribute's registry default of 2.0, handing a cow 18 phantom points.</p>
     *
     * <p>The result is the <b>raw</b> value: {@link XpService#grant} still applies the
     * {@code xp_gain} dimension and the rate limits exactly once.</p>
     */
    static int healthAndAttackReward(LivingEntity victim) {
        double health = Math.max(0.0, victim.getMaxHealth());
        double attack = victim.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? Math.max(0.0, victim.getAttributeValue(Attributes.ATTACK_DAMAGE))
                : 0.0;
        double raw = health * RpgConfig.killHealthCoefficient()
                + attack * RpgConfig.killAttackCoefficient();
        if (!Double.isFinite(raw) || raw <= 0.0) return 0;
        if (raw > MAX_KILL_REWARD) raw = MAX_KILL_REWARD;
        return (int) Math.round(raw);
    }

    // ------------------------------------------------------------------
    // kills - paid every time
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player attacker = event.getAttackingPlayer();
        if (!(attacker instanceof ServerPlayer killer)) return;
        LivingEntity victim = event.getEntity();
        // No self-damage farming, and automation without a player gets nothing.
        if (killer == victim) return;
        if (!RpgConfig.countSpawnerMobs() && Boolean.TRUE.equals(victim.getData(com.rpgleveling.registry.RpgAttachments.SPAWNER_MOB))) {
            return;
        }

        int raw = switch (RpgConfig.killRewardMode()) {
            case VANILLA -> event.getOriginalExperience();
            case FIXED -> RpgConfig.killFixedReward();
            case HEALTH_AND_ATTACK -> healthAndAttackReward(victim);
        };
        int granted = XpService.grant(killer, XpSource.KILL, raw);
        if (granted > 0) {
            // The orbs follow the multiplier too; absorption itself stays vanilla (INV-3).
            event.setDroppedExperience(granted);
        }
    }

    // ------------------------------------------------------------------
    // discovery sources - paid exactly once per registry entry
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!RpgConfig.discoveryEnabled()) return;
        if (!RpgConfig.discoverySourceEnabled(XpSource.MINE)) return;
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof ServerPlayer player)) return;
        if (!RpgConfig.discoveryCreative() && player.isCreative()) return;

        int id = BuiltInRegistries.BLOCK.getId(event.getState().getBlock());
        int size = BuiltInRegistries.BLOCK.size();
        Component label = event.getState().getBlock().getName();
        String key = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        if (DiscoveryHelper.discover(player, XpSource.MINE, id, key, size) && RpgConfig.discoveryNotify()) {
            DiscoveryHelper.announce(player, XpSource.MINE, label, size);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RpgConfig.discoveryEnabled() || !RpgConfig.discoverySourceEnabled(XpSource.CRAFT)) return;
        ItemStack stack = event.getCrafting();
        if (stack.isEmpty()) return;
        int id = BuiltInRegistries.ITEM.getId(stack.getItem());
        int size = BuiltInRegistries.ITEM.size();
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (DiscoveryHelper.discover(player, XpSource.CRAFT, id, key, size) && RpgConfig.discoveryNotify()) {
            DiscoveryHelper.announce(player, XpSource.CRAFT, stack.getHoverName(), size);
        }
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RpgConfig.discoveryEnabled() || !RpgConfig.discoverySourceEnabled(XpSource.SMELT)) return;
        ItemStack stack = event.getSmelting();
        if (stack.isEmpty()) return;
        int id = BuiltInRegistries.ITEM.getId(stack.getItem());
        int size = BuiltInRegistries.ITEM.size();
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (DiscoveryHelper.discover(player, XpSource.SMELT, id, key, size) && RpgConfig.discoveryNotify()) {
            DiscoveryHelper.announce(player, XpSource.SMELT, stack.getHoverName(), size);
        }
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RpgConfig.discoveryEnabled() || !RpgConfig.discoverySourceEnabled(XpSource.FISH)) return;
        int size = BuiltInRegistries.ITEM.size();
        // One catch can unlock several items; the loop is bounded by the number of drops.
        for (ItemStack stack : event.getDrops()) {
            if (stack.isEmpty()) continue;
            int id = BuiltInRegistries.ITEM.getId(stack.getItem());
            String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (DiscoveryHelper.discover(player, XpSource.FISH, id, key, size) && RpgConfig.discoveryNotify()) {
                DiscoveryHelper.announce(player, XpSource.FISH, stack.getHoverName(), size);
            }
        }
    }

    @SubscribeEvent
    public static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RpgConfig.discoveryEnabled() || !RpgConfig.discoverySourceEnabled(XpSource.TRADE)) return;
        AbstractVillager villager = event.getAbstractVillager();
        MerchantOffer offer = event.getMerchantOffer();
        int profession = 0;
        if (villager instanceof Villager v) {
            profession = BuiltInRegistries.VILLAGER_PROFESSION.getId(v.getVillagerData().getProfession());
        }
        int index = Math.max(0, villager.getOffers().indexOf(offer));
        int id = profession * 1000 + index;
        int size = Math.max(1, BuiltInRegistries.VILLAGER_PROFESSION.size()) * 1000;
        String stableKey = "trade:" + profession + ":" + index;
        Component label = Component.literal(stableKey);
        if (DiscoveryHelper.discover(player, XpSource.TRADE, id, stableKey, size) && RpgConfig.discoveryNotify()) {
            DiscoveryHelper.announce(player, XpSource.TRADE, label, size);
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!RpgConfig.advancementEnabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int reward = RpgConfig.advancementReward();
        if (reward > 0) XpService.grant(player, XpSource.ADVANCEMENT, reward);
    }

    /** Stable id of a source, used by the codex command. */
    public static ResourceLocation sourceId(XpSource source) {
        return ResourceLocation.fromNamespaceAndPath("rpgleveling", source.key());
    }
}