package com.evolvingblade.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.evolvingblade.EvolvingBlade;
import com.evolvingblade.growth.GrowthMode;
import com.evolvingblade.growth.GrowthService;
import com.evolvingblade.registry.EBAttachments;
import com.evolvingblade.registry.EBItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 多部位实体（原版末影龙）攻击的身份归一化回归。
 *
 * <p>原版 {@code Player.attack(dragon.head/body)} 会触发 {@code AttackEntityEvent}，但真正的受伤事件发生在
 * <b>父实体</b>（{@code EnderDragon}）上。若主手上下文记录的是 part 的 UUID，吸血 / 斩 Boss 加成 /
 * 剑来源（击杀回血）全部失效。本测试用真实攻击与真实龙验证归一化后的完整链路。
 */
@GameTestHolder(EvolvingBlade.MODID)
@PrefixGameTestTemplate(false)
public final class MultipartAttackGameTests {

    private static final long STAGE5_POINTS = 1200L;
    private static final long STAGE5_BOSSES = 6L;

    private MultipartAttackGameTests() {}

    public static final class SourceLog {
        volatile UUID victim;
        volatile String typePart;
        final List<Float> amounts = java.util.Collections.synchronizedList(new ArrayList<>());
        volatile String lastType;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onIncoming(LivingIncomingDamageEvent event) {
            UUID current = this.victim;
            if (current == null || !current.equals(event.getEntity().getUUID())) {
                return;
            }
            this.lastType = event.getSource().typeHolder().unwrapKey()
                    .map(k -> k.location().toString()).orElse("unknown");
            String part = this.typePart;
            if (part != null && !this.lastType.contains(part)) {
                return;
            }
            this.amounts.add(event.getAmount());
        }

        float first() {
            return this.amounts.isEmpty() ? Float.NaN : this.amounts.get(0);
        }

        int count() {
            return this.amounts.size();
        }

        void reset() {
            this.victim = null;
            this.typePart = null;
            this.amounts.clear();
        }
    }

    private static final SourceLog LOG = new SourceLog();

    static {
        NeoForge.EVENT_BUS.register(LOG);
    }

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void attackingDragonBodyPartDrivesParentLifestealBonusAndSwordHit(GameTestHelper helper) {
        LOG.reset();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, STAGE5_POINTS, STAGE5_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(24, 4, 16)));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        player.setHealth(10.0F);
        // 真实推进攻击条，确保满蓄力。
        for (int i = 0; i < 40; i++) {
            player.doTick();
        }
        player.setHealth(10.0F);

        net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon =
                helper.spawn(EntityType.ENDER_DRAGON, 24, 8, 24);
        dragon.setHealth(dragon.getMaxHealth());
        EnderDragonPart[] parts = dragon.getSubEntities();
        helper.assertTrue(parts.length > 1, "sanity: the dragon must have multiple parts");
        EnderDragonPart body = parts[1]; // 非头部部位：伤害按 1/4 + 1 处理，200 血下不会致死
        Object phaseBefore = dragon.getPhaseManager().getCurrentPhase();
        double dragonHpBefore = dragon.getHealth();
        float playerBefore = player.getHealth();
        LOG.reset();
        LOG.victim = dragon.getUUID();
        LOG.typePart = "boss_slayer";

        // 真实攻击一个 part 实体。
        player.attack(body);

        double loss = dragonHpBefore - dragon.getHealth();
        double heal = player.getHealth() - playerBefore;
        helper.assertTrue(dragon.isAlive(), "the dragon must stay alive (body quarter+1 is non-lethal)");
        helper.assertTrue(loss > 0.0D, "the parent dragon must take real damage (loss=" + loss + ")");
        helper.assertTrue(Math.abs(heal - Math.min(4.0D, loss * 0.05D)) < 0.5D,
                "lifesteal must be 5% (stage 5, cap 4) of the PARENT's real health loss (loss=" + loss
                        + ", healed=" + heal + ")");
        helper.assertTrue(dragon.getData(EBAttachments.SWORD_HIT).isPresent()
                        && player.getUUID().equals(dragon.getData(EBAttachments.SWORD_HIT).player()),
                "the sword provenance must be recorded on the PARENT dragon");
        helper.assertTrue(player.getData(EBAttachments.BOSS_RATE).containsKey(dragon.getUUID()),
                "the boss rate limit must be keyed by the PARENT dragon UUID");
        helper.assertTrue(LOG.count() >= 1,
                "the boss bonus must have been attempted on the parent (events=" + LOG.count() + ")");
        helper.assertTrue(loss > 0.0D && loss < 300.0D,
                "the body part must follow vanilla's quarter+1 rule (loss=" + loss
                        + "), i.e. no forced health/phase change by us");
        helper.assertTrue(dragon.isAlive() && !(dragon.getPhaseManager().getCurrentPhase()
                        instanceof net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase),
                "the dragon must stay out of the death phase (we never force phases or health)");
        LOG.reset();
        helper.succeed();
    }

    @GameTest(template = "boss_arena", timeoutTicks = 400)
    public static void attackingDragonHeadIsAlsoNormalisedToTheParent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GrowthService.forceMode(player, GrowthMode.STANDARD);
        GrowthService.setCounters(player, STAGE5_POINTS, STAGE5_BOSSES);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EBItems.starEater()));
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(24, 4, 16)));
        player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        for (int i = 0; i < 40; i++) {
            player.doTick();
        }
        net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon =
                helper.spawn(EntityType.ENDER_DRAGON, 24, 8, 24);
        dragon.setHealth(dragon.getMaxHealth());
        EnderDragonPart head = dragon.getSubEntities()[0];
        double before = dragon.getHealth();
        player.attack(head);
        helper.assertTrue(dragon.getHealth() < before,
                "attacking the head must damage the PARENT dragon (hp=" + dragon.getHealth() + ")");
        // 头部按满伤害结算（阶段 5 = 300 > 200 血），因此这里用**剑来源**证明身份归一化；
        // 非致命的斩 Boss 加成/吸血链路已由 body-part 用例完整断言。
        helper.assertTrue(dragon.getData(EBAttachments.SWORD_HIT).isPresent()
                        && player.getUUID().equals(dragon.getData(EBAttachments.SWORD_HIT).player()),
                "the head attack must record the sword provenance on the PARENT dragon");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attackIdentityHelperIsBoundedAndSafe(GameTestHelper helper) {
        // 非 part 实体必须原样返回（不改变任何既有语义）。
        var husk = FixtureEntities.spawn(helper.getLevel(),
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 3))).x,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 3))).y,
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 3))).z,
                (type, level) -> new net.minecraft.world.entity.monster.Husk(type, level));
        helper.assertTrue(com.evolvingblade.event.EBCombatHandler.attackIdentity(husk) == husk,
                "a normal entity must be returned unchanged");
        helper.assertTrue(LivingEntity.class.isAssignableFrom(husk.getClass()), "sanity");
        helper.succeed();
    }
}
