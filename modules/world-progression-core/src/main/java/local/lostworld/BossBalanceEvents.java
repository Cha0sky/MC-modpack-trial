package local.lostworld;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = LostWorld.ID)
public final class BossBalanceEvents {
    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath(LostWorld.ID, "encounter_health");
    private static final ResourceLocation TERRA = ResourceLocation.parse("terra_entity:difficulty_modifier_max_health");
    private static final ResourceLocation HOSTILITY = ResourceLocation.parse("l2hostility:hostility_health");
    private static final ResourceLocation HILL = ResourceLocation.parse("terra_entity:hill_of_flesh");

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof LivingEntity mob) reconcile(mob);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        // Some natural/structure spawns acquire native modifiers after joining.
        if (event.getEntity() instanceof LivingEntity mob && !mob.level().isClientSide && mob.tickCount % 20 == 1)
            reconcile(mob);
    }

    private static void reconcile(LivingEntity mob) {
        var budget = BossBalance.PROFILES.get(mob.getType().builtInRegistryHolder().key().location());
        if (budget == null || budget.nativeHealthConfig() || !mob.isAlive()) return;
        var attr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        var l2 = attr.getModifier(HOSTILITY);
        double adjustment = budget.healthMultiplier(attr.hasModifier(TERRA), l2 == null ? 0 : l2.amount()) - 1;
        var existing = attr.getModifier(HEALTH);
        if (existing == null ? adjustment == 0 : existing.amount() == adjustment) return;
        double fraction = mob.getHealth() / (double)mob.getMaxHealth();
        if (adjustment == 0) attr.removeModifier(HEALTH);
        else attr.addOrReplacePermanentModifier(new AttributeModifier(HEALTH, adjustment, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        mob.setHealth((float)(mob.getMaxHealth() * fraction));
    }

    private static BossBalance.Profile ownerBudget(Entity entity) {
        for (int depth = 0; entity != null && depth < 8; depth++) {
            if (entity instanceof ServerPlayer) return null;
            var budget = BossBalance.PROFILES.get(entity.getType().builtInRegistryHolder().key().location());
            if (budget != null) return budget;
            String origin = entity.getPersistentData().getString(BossSummonOrigin.KEY);
            if (!origin.isEmpty()) return BossBalance.PROFILES.get(ResourceLocation.parse(origin));
            Entity summonOwner = entity instanceof EncounterOwner summon ? summon.lostworld$encounterOwner() : null;
            if (summonOwner != null) entity = summonOwner;
            else if (entity instanceof PartEntity<?> part) entity = part.getParent();
            else if (entity instanceof TraceableEntity traced) entity = traced.getOwner();
            else if (entity instanceof OwnableEntity owned) entity = owned.getOwner();
            else return null;
        }
        return null;
    }

    private static BossBalance.Profile attack(DamageSource source) {
        if (source instanceof HillFieldDamage) return BossBalance.PROFILES.get(HILL);
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer) return null;
        var budget = ownerBudget(attacker);
        return budget == null && source.getDirectEntity() != attacker ? ownerBudget(source.getDirectEntity()) : budget;
    }

    /** These three ring hits are vanilla ownerless magic. Keep their damage type,
     * aggro and kill credit intact while identifying the encounter for the budget. */
    public static final class HillFieldDamage extends DamageSource {
        public HillFieldDamage(DamageSource magic) { super(magic.typeHolder()); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        var budget = attack(event.getSource());
        if (budget != null) event.setAmount(budget.incoming(event.getAmount()));
    }

    public static float finalDamage(DamageSource source, float amount) {
        var budget = attack(source);
        return budget == null ? amount : budget.finalLimit(amount);
    }

    private BossBalanceEvents() {}
}
