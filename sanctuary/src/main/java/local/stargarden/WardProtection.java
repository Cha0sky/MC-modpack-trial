package local.stargarden;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public final class WardProtection {
    private WardProtection() {}
    private static final ClassValue<Optional<Method>> OWNER = new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            for (String name : new String[]{"getOwnerUUID", "getOwnerId", "getOwner", "getTrueOwner"}) {
                try { return Optional.of(type.getMethod(name)); }
                catch (NoSuchMethodException ignored) {}
            }
            return Optional.empty();
        }
    };
    private static boolean playerOwner(Entity entity, Object value) {
        if (value instanceof Optional<?> optional) return playerOwner(entity, optional.orElse(null));
        if (value instanceof UUID uuid && entity.level() instanceof ServerLevel level)
            return level.getServer().getPlayerList().getPlayer(uuid) != null || level.getEntity(uuid) instanceof Player;
        return value instanceof Player;
    }
    private static boolean owned(Entity entity) {
        if (entity instanceof Player) return true;
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            // Vanilla pets are retained while their owner is offline. An Enemy with
            // an owner must resolve to a player, not merely another hostile mob.
            return !(entity instanceof Enemy) || playerOwner(entity, ownable.getOwnerUUID());
        }
        Optional<Method> method = OWNER.get(entity.getClass());
        if (method.isPresent()) {
            try { return playerOwner(entity, method.get().invoke(entity)); }
            catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }
    private static boolean protectedAt(Entity entity) {
        return entity.level() instanceof ServerLevel level && WardData.get(level).covers(entity.getX(), entity.getZ());
    }
    private static boolean hostile(Entity entity) {
        if (owned(entity)) return false;
        if (entity instanceof Enemy) return true;
        return entity instanceof Mob mob && mob.getTarget() != null && protectedAt(mob.getTarget());
    }
    private static boolean enemyProjectile(Entity entity) {
        if (!(entity instanceof Projectile projectile)) return false;
        Entity owner = projectile.getOwner();
        return owner == null || hostile(owner);
    }
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && protectedAt(event.getEntity())
                && (hostile(event.getEntity()) || enemyProjectile(event.getEntity()))) event.setCanceled(true);
    }
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            WardData wards = WardData.get(level);
            if (wards.isEmpty()) continue;
            wards.pruneLoadedAnchors(level);
            ArrayList<Entity> remove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (wards.covers(entity.getX(), entity.getZ()) && (hostile(entity) || enemyProjectile(entity))) remove.add(entity);
            }
            // Discard is not a kill: no loot/XP/growth, and no artificial farm loop.
            for (Entity entity : remove) entity.discard();
        }
    }
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!protectedAt(victim)) return;
        // The player's sanctuary is fully restful, including falls/fire/hunger.
        // Livestock remains harvestable by players; hostile removal grants no XP.
        if (victim instanceof Player || owned(victim)) {
            event.setCanceled(true);
            return;
        }
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        boolean playerFriendlyFire = attacker instanceof Player && (victim instanceof Player || owned(victim));
        boolean hostileAttack = attacker instanceof LivingEntity && !(attacker instanceof Player) && !owned(attacker);
        boolean unknownProjectile = source.is(DamageTypeTags.IS_PROJECTILE) && !(attacker instanceof Player) && (attacker == null || !owned(attacker));
        if (hostileAttack || unknownProjectile || source.is(DamageTypeTags.IS_EXPLOSION) || playerFriendlyFire) event.setCanceled(true);
    }
    public static void effect(MobEffectEvent.Applicable event) {
        if (protectedAt(event.getEntity()) && !event.getEffectInstance().getEffect().value().isBeneficial())
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    public static void target(LivingChangeTargetEvent event) {
        LivingEntity victim = event.getNewAboutToBeSetTarget();
        if (victim != null && protectedAt(victim) && !owned(event.getEntity()))
            event.setNewAboutToBeSetTarget(null);
    }
    public static void grief(EntityMobGriefingEvent event) {
        if (protectedAt(event.getEntity()) && hostile(event.getEntity())) event.setCanGrief(false);
    }
    public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        WardData wards = WardData.get(level);
        if (wards.isEmpty()) return;
        event.getAffectedBlocks().removeIf(pos -> wards.covers(pos.getX() + 0.5, pos.getZ() + 0.5));
        event.getAffectedEntities().removeIf(entity -> wards.covers(entity.getX(), entity.getZ()));
    }
}
